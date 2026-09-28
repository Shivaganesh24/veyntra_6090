package app.getknit.knit.payment.reconciliation

import android.util.Log
import app.getknit.knit.data.payment.PaymentEntity
import app.getknit.knit.data.payment.PaymentRepository
import app.getknit.knit.net.InternetGate
import app.getknit.knit.payment.protocol.PaymentValidationResult
import app.getknit.knit.payment.settlement.BlockchainSettlementService
import app.getknit.knit.payment.settlement.SettlementResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
class OfflineReconciliationManager(
    private val paymentRepository: PaymentRepository,
    private val settlementService: BlockchainSettlementService,
    private val internetGate: InternetGate,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ReconciliationState.IDLE)
    val state: StateFlow<ReconciliationState> = _state.asStateFlow()

    init {
        // Monitor route changes (online/offline transitions)
        scope.launch {
            runCatching {
                internetGate.routeChanges.collectLatest {
                    if (internetGate.isOnline()) {
                        reconcilePendingPayments()
                    } else {
                        _state.value = ReconciliationState.IDLE
                    }
                }
            }.onFailure { e ->
                Log.e("OfflineReconciliation", "Error collecting route changes", e)
            }
        }
    }

    /**
     * Finds and reconciles all pending offline payments with the MST Blockchain safely.
     */
    suspend fun reconcilePendingPayments(): ReconciliationResult {
        return runCatching {
            if (!settlementService.isConfigured()) {
                _state.value = ReconciliationState.UNCONFIGURED
                return@runCatching ReconciliationResult(
                    reconciledCount = 0,
                    failedCount = 0,
                    message = "MST Blockchain testnet not configured.",
                )
            }

            _state.value = ReconciliationState.SYNCING

            val pendingList = paymentRepository.getPendingSettlementPayments()
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
            Log.e("OfflineReconciliation", "Reconciliation execution failed", e)
            _state.value = ReconciliationState.ERROR
            ReconciliationResult(0, 1, e.message ?: "Reconciliation error")
        }
    }

    private suspend fun reconcileSinglePayment(payment: PaymentEntity, myWalletId: String): Boolean {
        // 1. Idempotency Check: Verify if transaction was already settled on-chain
        val existingCheck = settlementService.verifySettlementStatus(payment.transactionId)
        if (existingCheck is SettlementResult.AlreadySettled) {
            finalizeSettlement(payment, myWalletId, existingCheck.transactionHash)
            return true
        }

        // 2. Submit settlement request to MST Blockchain
        val submitResult = settlementService.submitSettlement(payment)
        return when (submitResult) {
            is SettlementResult.Success -> {
                finalizeSettlement(payment, myWalletId, submitResult.transactionHash)
                true
            }

            is SettlementResult.AlreadySettled -> {
                finalizeSettlement(payment, myWalletId, submitResult.transactionHash)
                true
            }

            is SettlementResult.Failed -> {
                if (submitResult.isPermanent) {
                    rollbackPayment(payment, myWalletId, PaymentValidationResult.CONFLICT)
                }
                false
            }

            is SettlementResult.Unconfigured -> {
                _state.value = ReconciliationState.UNCONFIGURED
                false
            }
        }
    }

    private suspend fun finalizeSettlement(payment: PaymentEntity, myWalletId: String, txHash: String) {
        if (payment.senderWalletId == myWalletId) {
            paymentRepository.confirmOutboundSettlement(payment.transactionId, payment.amount, txHash)
        } else if (payment.receiverWalletId == myWalletId) {
            paymentRepository.confirmInboundSettlement(payment.transactionId, payment.amount, txHash)
        }
    }

    private suspend fun rollbackPayment(payment: PaymentEntity, myWalletId: String, reason: PaymentValidationResult) {
        if (payment.senderWalletId == myWalletId) {
            paymentRepository.releaseOutboundReservation(payment.transactionId, payment.amount, reason)
        } else if (payment.receiverWalletId == myWalletId) {
            paymentRepository.rollbackInboundPending(payment.transactionId, payment.amount, reason)
        }
    }
}

data class ReconciliationResult(
    val reconciledCount: Int,
    val failedCount: Int,
    val message: String,
)
