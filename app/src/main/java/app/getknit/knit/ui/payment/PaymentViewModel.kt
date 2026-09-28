package app.getknit.knit.ui.payment

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.getknit.knit.data.crypto.IdentityKeyStore
import app.getknit.knit.data.payment.PaymentEntity
import app.getknit.knit.data.payment.PaymentEvent
import app.getknit.knit.data.payment.PaymentRepository
import app.getknit.knit.data.payment.WalletEntity
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

class PaymentViewModel(
    private val paymentRepository: PaymentRepository,
    private val settlementService: BlockchainSettlementService,
    private val reconciliationManager: OfflineReconciliationManager,
    private val internetGate: InternetGate,
    private val identityKeyStore: IdentityKeyStore,
    private val meshManager: MeshManager,
) : ViewModel() {
    val wallet: StateFlow<WalletEntity?> =
        paymentRepository.observePrimaryWallet()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val payments: StateFlow<List<PaymentEntity>> =
        paymentRepository.observeAllPayments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val isOnline: StateFlow<Boolean> = internetGate.online

    val reconciliationState: StateFlow<ReconciliationState> = reconciliationManager.state

    val events = paymentRepository.events

    val neighbors = meshManager.neighbors

    init {
        viewModelScope.launch {
            runCatching {
                val pubKey = runCatching { identityKeyStore.keys().publicBundle.encoded }.getOrElse { "fallback-pub-key" }
                val walletId = "wallet-" + pubKey.take(8)
                paymentRepository.initializeDemoWallet(
                    walletId = walletId,
                    displayName = "OffPay Wallet",
                    publicKey = pubKey,
                    initialBalance = 50000L, // ₹500.00
                )
            }.onFailure { e ->
                Log.e("PaymentViewModel", "Failed to initialize demo wallet during init", e)
            }
        }
    }

    fun resetDemoWallet(isSender: Boolean) {
        viewModelScope.launch {
            runCatching {
                val initial = if (isSender) 50000L else 10000L // ₹500 vs ₹100
                val name = if (isSender) "Phone A (Sender)" else "Phone B (Receiver)"
                val pubKey = runCatching { identityKeyStore.keys().publicBundle.encoded }.getOrElse { "fallback-pub-key" }
                val walletId = "wallet-" + pubKey.take(8)
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

    fun sendPayment(
        receiverWalletId: String,
        receiverPublicKey: String,
        amount: Long, // in paise
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val keys = runCatching { identityKeyStore.keys() }.getOrNull()
                if (keys == null) {
                    onError("Identity keys not available. Please restart app.")
                    return@launch
                }
                val messageCrypto = MessageCrypto(
                    keys.hybridPrivate,
                    keys.sigPrivate,
                )
                val result = paymentRepository.createOutboundPayment(
                    receiverWalletId = receiverWalletId,
                    receiverPublicKey = receiverPublicKey,
                    amount = amount,
                    signRaw = messageCrypto::signRaw,
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
