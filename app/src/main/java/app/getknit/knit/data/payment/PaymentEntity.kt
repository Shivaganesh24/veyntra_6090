package app.getknit.knit.data.payment

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Represents an offline/online payment transaction in the MST OfflinePay ledger.
 */
@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["senderPublicKey"]),
        Index(value = ["receiverPublicKey"]),
        Index(value = ["status"]),
        Index(value = ["nonce"]),
        Index(value = ["timestamp"]),
    ],
)
data class PaymentEntity(
    @PrimaryKey val transactionId: String,
    val senderPublicKey: String,
    val receiverPublicKey: String,
    val senderWalletId: String,
    val receiverWalletId: String,
    val amount: Long, // in lowest currency unit (paise)
    val currency: String = "INR",
    val timestamp: Long,
    val nonce: Long,
    val previousTransactionReference: String? = null,
    val status: String, // OFFLINE_SENT, RECEIVED, VERIFIED, PENDING_SETTLEMENT, SUBMITTED, SETTLED, REJECTED, EXPIRED, CONFLICT
    val signature: String, // Base64 Ed25519 signature
    val createdOffline: Boolean = true,
    val settlementStatus: String = "PENDING", // PENDING, CONFIRMED, FAILED
    val blockchainTransactionHash: String? = null,
    val hopCount: Int = 0,
    val expiryTime: Long,
)
