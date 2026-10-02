@file:OptIn(ExperimentalSerializationApi::class)

package app.getknit.knit.payment.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * CBOR-serializable payment payload carried in Layer 3 of a [app.getknit.knit.mesh.protocol.RelayEnvelope].
 *
 * Contains all transaction metadata needed for offline processing and verification.
 * NO private keys are ever stored or transmitted in this payload.
 */
@Serializable
data class PaymentPayload(
    val transactionId: String,
    val senderPublicKey: String,
    val receiverPublicKey: String,
    val senderWalletId: String,
    val receiverWalletId: String,
    val amount: Long, // in lowest currency unit (paise / cents)
    val currency: String = "INR",
    val timestamp: Long,
    val nonce: Long,
    val previousTransactionReference: String? = null,
    val createdOffline: Boolean = true,
    val expiryTime: Long,
    val signature: String = "",
    val receiverEvmAddress: String = "",
)
