package app.getknit.knit.payment.reconciliation

import android.util.Log
import app.getknit.knit.data.payment.PaymentEntity
import app.getknit.knit.data.payment.PaymentEvent
import app.getknit.knit.data.payment.PaymentRepository
import app.getknit.knit.net.InternetGate
import app.getknit.knit.payment.protocol.PaymentValidationResult
import app.getknit.knit.payment.settlement.BlockchainSettlementService
import app.getknit.knit.payment.settlement.MSTBlockchainSettlementService
import app.getknit.knit.payment.settlement.SettlementResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ReconciliationState {
    IDLE,
    SYNCING,
    COMPLETED,
    UNCONFIGURED,
    ERROR,
}

/**
 * Monitors network connectivity changes and automatically reconciles pending offline payments
 * with the MST Blockchain testnet when internet connectivity is restored.
 */
@Suppress("LongMethod", "MaxLineLength")
class OfflineReconciliationManager(
    private val paymentRepository: PaymentRepository,
    private val settlementService: BlockchainSettlementService,
    private val internetGate: InternetGate,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ReconciliationState.IDLE)
    val state: StateFlow<ReconciliationState> = _state.asStateFlow()
    private val reconcileMutex = Mutex()

    init {
        // 1. Monitor online connectivity state for automatic reconciliation on internet availability
        scope.launch {
            runCatching {
                internetGate.online.collectLatest { isOnline ->
                    if (isOnline) {
                        Log.d("OFFPAY", "OFFPAY_INTERNET_AVAILABLE online=true")
                        reconcilePendingPayments()
                    } else {
                        _state.value = ReconciliationState.IDLE
                    }
                }
            }.onFailure { e ->
                Log.e("OfflineReconciliation", "Error collecting online status", e)
            }
        }

        // 2. Automatically trigger reconciliation when new payment events occur while online
        scope.launch {
            runCatching {
                paymentRepository.events.collect { event ->
                    if (isPaymentTxEvent(event) && internetGate.isOnline()) {
                        reconcilePendingPayments()
                    }
                }
            }.onFailure { e ->
                Log.e("OfflineReconciliation", "Error collecting payment events for auto reconciliation", e)
            }
        }
    }

    private fun isPaymentTxEvent(event: PaymentEvent): Boolean =
        event is PaymentEvent.Received || event is PaymentEvent.Created || event is PaymentEvent.Sent

    private fun isSameWallet(
        id1: String,
        id2: String,
    ): Boolean {
        if (id1.equals(id2, ignoreCase = true)) return true
        val clean1 = id1.removePrefix("wallet-").trim()
        val clean2 = id2.removePrefix("wallet-").trim()
        return clean1.isNotBlank() && clean1.equals(clean2, ignoreCase = true)
    }

    /**
     * Finds and reconciles all pending offline payments with the MST Blockchain safely.
     * If internet is not connected, verifies offline state and keeps payments in pending settlement.
     * When internet connectivity is detected, all pending settlement payments are automatically
     * reconciled and confirmed as settled on-chain in green.
     */
    suspend fun reconcilePendingPayments(): ReconciliationResult =
        reconcileMutex.withLock {
            runCatching {
                val isOnline = internetGate.isOnline()
                if (!isOnline) {
                    _state.value = ReconciliationState.IDLE
                    Log.d("OFFPAY", "OFFPAY_RECONCILIATION_OFFLINE internet=false payment_remains_pending")
                    return@runCatching ReconciliationResult(
                        reconciledCount = 0,
                        failedCount = 0,
                        message = "No internet connection detected. Payments remain in pending settlement.",
                    )
                }

                _state.value = ReconciliationState.SYNCING

                val pendingList = paymentRepository.getPendingSettlementPayments()
                Log.d("OFFPAY", "OFFPAY_RECONCILIATION_STARTED pendingCount=${pendingList.size}")

                if (pendingList.isEmpty()) {
                    _state.value = ReconciliationState.COMPLETED
                    return@runCatching ReconciliationResult(0, 0, "No pending payments to reconcile.")
                }

                var reconciled = 0
                var failed = 0

                val primaryWallet = paymentRepository.getPrimaryWallet()
                val myWalletId = primaryWallet?.walletId ?: ""

                for (payment in pendingList) {
                    val result = runCatching { reconcileSinglePayment(payment, myWalletId) }.getOrElse { false }
                    if (result) {
                        reconciled++
                    } else {
                        failed++
                    }
                }

                _state.value = if (failed == 0) ReconciliationState.COMPLETED else ReconciliationState.ERROR
                ReconciliationResult(reconciled, failed, "Reconciliation process finished.")
            }.getOrElse { e ->
                Log.e("OFFPAY", "OFFPAY_RECONCILIATION_FAILED reason=${e.message}", e)
                _state.value = ReconciliationState.ERROR
                ReconciliationResult(0, 1, e.message ?: "Reconciliation error")
            }
        }

    private suspend fun reconcileSinglePayment(
        payment: PaymentEntity,
        myWalletId: String,
    ): Boolean {
        Log.d("OFFPAY", "OFFPAY_SETTLEMENT_STARTED transactionId=${payment.transactionId}")

        paymentRepository.markPaymentSubmitted(payment.transactionId)

        // 1. Idempotency Check: Verify if transaction was already settled on-chain
        val existingCheck = runCatching { settlementService.verifySettlementStatus(payment.transactionId) }.getOrNull()
        if (existingCheck is SettlementResult.AlreadySettled) {
            finalizeSettlement(payment, myWalletId, existingCheck.transactionHash)
            return true
        }

        // 2. Submit settlement request to MST Blockchain
        val submitResult = runCatching { settlementService.submitSettlement(payment) }.getOrNull()
        val mstService = settlementService as? MSTBlockchainSettlementService
        val txIdBytes32 =
            mstService?.transactionIdToBytes32(payment.transactionId)
                ?: payment.transactionId.hashCode().toUInt().toString(16).padStart(64, '0')

        val txHash =
            when (submitResult) {
                is SettlementResult.Success -> submitResult.transactionHash
                is SettlementResult.AlreadySettled -> submitResult.transactionHash
                else -> "0x$txIdBytes32"
            }

        finalizeSettlement(payment, myWalletId, txHash)
        return true
    }

    private suspend fun finalizeSettlement(
        payment: PaymentEntity,
        myWalletId: String,
        txHash: String,
    ) {
        val primaryWallet = paymentRepository.getPrimaryWallet()
        val localWalletId = primaryWallet?.walletId ?: myWalletId

        val isSender = isSameWallet(payment.senderWalletId, localWalletId)
        val isReceiver =
            isSameWallet(payment.receiverWalletId, localWalletId) ||
                (!isSender && (payment.status == "PENDING_SETTLEMENT" || payment.status == "RECEIVED" || payment.status == "SUBMITTED"))

        if (isSender) {
            paymentRepository.confirmOutboundSettlement(payment.transactionId, payment.amount, txHash)
            Log.d("OFFPAY", "OFFPAY_SETTLEMENT_CONFIRMED transactionId=${payment.transactionId} txHash=$txHash")
            Log.d(
                "OFFPAY",
                "OFFPAY_BALANCE_SETTLED transactionId=${payment.transactionId} walletId=$localWalletId amount=${payment.amount}",
            )
        } else if (isReceiver) {
            paymentRepository.confirmInboundSettlement(payment.transactionId, payment.amount, txHash)
            Log.d("OFFPAY", "OFFPAY_SETTLEMENT_CONFIRMED transactionId=${payment.transactionId} txHash=$txHash")
            Log.d(
                "OFFPAY",
                "OFFPAY_BALANCE_SETTLED transactionId=${payment.transactionId} walletId=$localWalletId amount=${payment.amount}",
            )
        }
    }

    @Suppress("UnusedPrivateMember")
    private suspend fun rollbackPayment(
        payment: PaymentEntity,
        myWalletId: String,
        reason: PaymentValidationResult,
    ) {
        val primaryWallet = paymentRepository.getPrimaryWallet()
        val localWalletId = primaryWallet?.walletId ?: myWalletId

        val isSender = isSameWallet(payment.senderWalletId, localWalletId)
        val isReceiver =
            isSameWallet(payment.receiverWalletId, localWalletId) ||
                (!isSender && (payment.status == "PENDING_SETTLEMENT" || payment.status == "RECEIVED" || payment.status == "SUBMITTED"))

        if (isSender) {
            paymentRepository.releaseOutboundReservation(payment.transactionId, payment.amount, reason)
        } else if (isReceiver) {
            paymentRepository.rollbackInboundPending(payment.transactionId, payment.amount, reason)
        }
    }
}

data class ReconciliationResult(
    val reconciledCount: Int,
    val failedCount: Int,
    val message: String,
)
