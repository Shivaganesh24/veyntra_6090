package app.getknit.knit.payment.protocol

import app.getknit.knit.data.payment.PaymentDao
import app.getknit.knit.mesh.crypto.PublicKeyBundle
import app.getknit.knit.mesh.protocol.Protocol
import app.getknit.knit.payment.crypto.PaymentSigner

/**
 * Protocol-level validation result states for MST OfflinePay transactions.
 */
enum class PaymentValidationResult {
    VALID,
    INVALID_SIGNATURE,
    DUPLICATE_TRANSACTION,
    INVALID_NONCE,
    EXPIRED,
    INVALID_AMOUNT,
    INVALID_CURRENCY,
    INVALID_IDENTITY,
    MALFORMED_PAYLOAD,
    CONFLICT,
}

/**
 * Protocol validator for verifying incoming and outbound payment transactions.
 * Enforces cryptographic authenticity, nonces, timestamps, currency, amounts, and deduplication.
 */
object PaymentProtocolValidator {
    /**
     * Validates a [PaymentPayload] and its [signature].
     *
     * @param payload The payment transaction payload.
     * @param signature Base64 Ed25519 signature.
     * @param paymentDao Optional database handle for duplicate/nonce checks against local storage.
     * @param currentClock System wall-clock timestamp (defaults to current time).
     * @param supportedCurrency Expected currency code (defaults to "INR").
     */
    suspend fun validate(
        payload: PaymentPayload,
        signature: String,
        paymentDao: PaymentDao? = null,
        currentClock: Long = System.currentTimeMillis(),
        supportedCurrency: String = "INR",
    ): PaymentValidationResult {
        // 1. Validate transaction ID
        if (payload.transactionId.isBlank()) {
            return PaymentValidationResult.MALFORMED_PAYLOAD
        }

        // 2. Validate amount (> 0)
        if (payload.amount <= 0) {
            return PaymentValidationResult.INVALID_AMOUNT
        }

        // 3. Validate currency
        if (payload.currency.isBlank() || payload.currency != supportedCurrency) {
            return PaymentValidationResult.INVALID_CURRENCY
        }

        // 4. Validate identities
        if (payload.senderPublicKey.isBlank() ||
            payload.receiverPublicKey.isBlank() ||
            payload.senderWalletId.isBlank() ||
            payload.receiverWalletId.isBlank()
        ) {
            return PaymentValidationResult.INVALID_IDENTITY
        }

        // 5. Validate timestamp & expiry
        if (payload.expiryTime <= currentClock) {
            return PaymentValidationResult.EXPIRED
        }
        if (payload.timestamp > currentClock + Protocol.MAX_FUTURE_SKEW_MS) {
            return PaymentValidationResult.MALFORMED_PAYLOAD
        }

        // 6. Verify cryptographic signature
        if (signature.isBlank()) {
            return PaymentValidationResult.INVALID_SIGNATURE
        }
        val senderBundle = PublicKeyBundle.decode(payload.senderPublicKey)
        if (senderBundle == null) {
            return PaymentValidationResult.INVALID_IDENTITY
        }
        val isSigValid = PaymentSigner.verify(payload, signature, senderBundle)
        if (!isSigValid) {
            return PaymentValidationResult.INVALID_SIGNATURE
        }

        // 7. Database anti-replay checks (if DAO provided)
        if (paymentDao != null) {
            if (paymentDao.getPaymentById(payload.transactionId) != null) {
                return PaymentValidationResult.DUPLICATE_TRANSACTION
            }
            if (paymentDao.getPaymentByNonce(payload.senderPublicKey, payload.nonce) != null) {
                return PaymentValidationResult.INVALID_NONCE
            }
        }

        return PaymentValidationResult.VALID
    }
}
