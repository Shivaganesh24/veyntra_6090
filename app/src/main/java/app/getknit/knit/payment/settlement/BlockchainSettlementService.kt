package app.getknit.knit.payment.settlement

import app.getknit.knit.data.payment.PaymentEntity

/**
 * Result states for MST Blockchain settlement requests.
 */
sealed interface SettlementResult {
    data class Success(val transactionHash: String, val blockNumber: Long? = null) : SettlementResult
    data class AlreadySettled(val transactionHash: String) : SettlementResult
    data class Failed(val reason: String, val isPermanent: Boolean = false) : SettlementResult
    data class Unconfigured(val message: String) : SettlementResult
}

/**
 * Interface defining operations for on-chain settlement and status verification with the MST Blockchain.
 */
interface BlockchainSettlementService {
    suspend fun submitSettlement(payment: PaymentEntity): SettlementResult
    suspend fun verifySettlementStatus(transactionId: String): SettlementResult
    fun isConfigured(): Boolean
    fun getNetworkName(): String
    fun getChainId(): Long
    fun getContractAddress(): String
}
