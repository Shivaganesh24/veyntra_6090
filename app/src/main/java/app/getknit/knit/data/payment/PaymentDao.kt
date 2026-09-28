package app.getknit.knit.data.payment

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity)

    @Query("SELECT * FROM payments WHERE transactionId = :transactionId")
    suspend fun getPaymentById(transactionId: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE senderPublicKey = :senderPublicKey AND nonce = :nonce LIMIT 1")
    suspend fun getPaymentByNonce(senderPublicKey: String, nonce: Long): PaymentEntity?

    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    fun observeAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    suspend fun getAllPayments(): List<PaymentEntity>

    @Query(
        "SELECT * FROM payments WHERE status IN " +
            "('OFFLINE_SENT', 'RECEIVED', 'VERIFIED', 'PENDING_SETTLEMENT') " +
            "ORDER BY timestamp ASC",
    )
    suspend fun getPendingSettlementPayments(): List<PaymentEntity>

    @Query(
        "UPDATE payments SET status = :status, " +
            "settlementStatus = :settlementStatus, " +
            "blockchainTransactionHash = COALESCE(:txHash, blockchainTransactionHash) " +
            "WHERE transactionId = :transactionId",
    )
    suspend fun updateStatus(
        transactionId: String,
        status: String,
        settlementStatus: String,
        txHash: String? = null,
    )
}
