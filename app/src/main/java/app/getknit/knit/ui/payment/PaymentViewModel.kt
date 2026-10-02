package app.getknit.knit.ui.payment

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.getknit.knit.data.crypto.IdentityKeyStore
import app.getknit.knit.data.payment.PaymentEntity
import app.getknit.knit.data.payment.PaymentRepository
import app.getknit.knit.data.payment.WalletEntity
import app.getknit.knit.identity.Identity
import app.getknit.knit.mesh.MeshManager
import app.getknit.knit.mesh.crypto.MessageCrypto
import app.getknit.knit.net.InternetGate
import app.getknit.knit.payment.reconciliation.OfflineReconciliationManager
import app.getknit.knit.payment.reconciliation.ReconciliationState
import app.getknit.knit.payment.settlement.BlockchainSettlementService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val NODE_ID_DISPLAY_LEN = 6
private const val INITIAL_WALLET_BALANCE_PAISE = 50000L
private const val RESET_SENDER_BALANCE_PAISE = 50000L
private const val RESET_RECEIVER_BALANCE_PAISE = 10000L
private const val STATE_FLOW_STOP_TIMEOUT_MS = 5000L

class PaymentViewModel(
    private val paymentRepository: PaymentRepository,
    private val settlementService: BlockchainSettlementService,
    private val reconciliationManager: OfflineReconciliationManager,
    private val internetGate: InternetGate,
    private val identityKeyStore: IdentityKeyStore,
    private val identity: Identity,
    private val meshManager: MeshManager,
) : ViewModel() {
    val wallet: StateFlow<WalletEntity?> =
        paymentRepository
            .observePrimaryWallet()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_STOP_TIMEOUT_MS), null)

    val payments: StateFlow<List<PaymentEntity>> =
        paymentRepository
            .observeAllPayments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_FLOW_STOP_TIMEOUT_MS), emptyList())

    val isOnline: StateFlow<Boolean> = internetGate.online

    val reconciliationState: StateFlow<ReconciliationState> = reconciliationManager.state

    val events = paymentRepository.events

    val neighbors = meshManager.neighbors

    init {
        viewModelScope.launch {
            runCatching {
                val nodeId = identity.nodeId()
                val pubKey = runCatching { identityKeyStore.keys().publicBundle.encoded }.getOrElse { nodeId }
                val walletId = "wallet-$nodeId"
                paymentRepository.initializeDemoWallet(
                    walletId = walletId,
                    displayName = "OffPay Wallet (${nodeId.take(NODE_ID_DISPLAY_LEN)})",
                    publicKey = pubKey,
                    initialBalance = INITIAL_WALLET_BALANCE_PAISE,
                )
            }.onFailure { e ->
                Log.e("PaymentViewModel", "Failed to initialize wallet during init", e)
            }
        }
    }

    @Suppress("UnusedParameter")
    fun resetDemoWallet(isSender: Boolean) {
        viewModelScope.launch {
            runCatching {
                val initial = if (isSender) RESET_SENDER_BALANCE_PAISE else RESET_RECEIVER_BALANCE_PAISE
                val nodeId = identity.nodeId()
                val name = if (isSender) "Phone A (Sender)" else "Phone B (Receiver)"
                val pubKey = runCatching { identityKeyStore.keys().publicBundle.encoded }.getOrElse { nodeId }
                val walletId = "wallet-$nodeId"
                paymentRepository.resetDemoWallet(
                    walletId = walletId,
                    displayName = name,
                    publicKey = pubKey,
                    initialBalance = initial,
                )
            }.onFailure { e ->
                Log.e("PaymentViewModel", "Failed to reset demo wallet", e)
            }
        }
    }

    @Suppress("UnusedParameter")
    fun sendPayment(
        receiverWalletId: String,
        receiverPublicKey: String,
        amount: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        receiverEvmAddress: String? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                val currentWallet = wallet.value
                if (currentWallet != null && receiverWalletId == currentWallet.walletId) {
                    onError("Cannot send payment to your own wallet!")
                    return@launch
                }
                if (amount <= 0L) {
                    onError("Amount must be greater than zero.")
                    return@launch
                }
                if (currentWallet != null && amount > currentWallet.availableBalance) {
                    onError("Insufficient available spendable balance.")
                    return@launch
                }

                val keys = runCatching { identityKeyStore.keys() }.getOrNull()
                if (keys == null) {
                    onError("Identity keys not available. Please restart app.")
                    return@launch
                }
                val messageCrypto =
                    MessageCrypto(
                        keys.hybridPrivate,
                        keys.sigPrivate,
                    )
                val result =
                    paymentRepository.createOutboundPayment(
                        receiverWalletId = receiverWalletId,
                        receiverPublicKey = receiverPublicKey,
                        amount = amount,
                        signRaw = messageCrypto::signRaw,
                        receiverEvmAddress = receiverEvmAddress,
                    )

                if (result != null) {
                    val (payload, _) = result
                    meshManager.sendPaymentTransaction(payload)
                    onSuccess()
                } else {
                    onError("Insufficient available funds or wallet not initialized.")
                }
            }.onFailure { e ->
                Log.e("PaymentViewModel", "Failed to send payment", e)
                onError(e.message ?: "Failed to send payment")
            }
        }
    }

    fun reconcilePaymentsNow() {
        viewModelScope.launch {
            runCatching {
                reconciliationManager.reconcilePendingPayments()
            }.onFailure { e ->
                Log.e("PaymentViewModel", "Manual reconciliation failed", e)
            }
        }
    }
}
