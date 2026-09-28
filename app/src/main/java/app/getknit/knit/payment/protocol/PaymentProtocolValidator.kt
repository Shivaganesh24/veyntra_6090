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
        val basicCheck = checkBasicFields(payload, currentClock, supportedCurrency)
        if (basicCheck != PaymentValidationResult.VALID) {
            return basicCheck
        }

        return checkSignatureAndStorage(payload, signature, paymentDao)
    }

    private fun checkBasicFields(
        payload: PaymentPayload,
        currentClock: Long,
        supportedCurrency: String,
    ): PaymentValidationResult {
        if (payload.transactionId.isBlank()) return PaymentValidationResult.MALFORMED_PAYLOAD
        if (payload.amount <= 0) return PaymentValidationResult.INVALID_AMOUNT

        val currencyCheck = checkCurrency(payload.currency, supportedCurrency)
        if (currencyCheck != PaymentValidationResult.VALID) return currencyCheck

        val identityCheck = checkIdentities(payload)
        if (identityCheck != PaymentValidationResult.VALID) return identityCheck

        val timeCheck = checkTimestamps(payload.timestamp, payload.expiryTime, currentClock)
        if (timeCheck != PaymentValidationResult.VALID) return timeCheck

        return PaymentValidationResult.VALID
    }

    private fun checkCurrency(currency: String, supportedCurrency: String): PaymentValidationResult {
        if (currency.isBlank()) return PaymentValidationResult.INVALID_CURRENCY
        if (currency != supportedCurrency) return PaymentValidationResult.INVALID_CURRENCY
        return PaymentValidationResult.VALID
    }

    private fun checkIdentities(payload: PaymentPayload): PaymentValidationResult {
        if (payload.senderPublicKey.isBlank()) return PaymentValidationResult.INVALID_IDENTITY
        if (payload.receiverPublicKey.isBlank()) return PaymentValidationResult.INVALID_IDENTITY
        if (payload.senderWalletId.isBlank()) return PaymentValidationResult.INVALID_IDENTITY
        if (payload.receiverWalletId.isBlank()) return PaymentValidationResult.INVALID_IDENTITY
        return PaymentValidationResult.VALID
    }

    private fun checkTimestamps(timestamp: Long, expiryTime: Long, currentClock: Long): PaymentValidationResult {
        if (expiryTime <= currentClock) return PaymentValidationResult.EXPIRED
        if (timestamp > currentClock + Protocol.MAX_FUTURE_SKEW_MS) return PaymentValidationResult.MALFORMED_PAYLOAD
        return PaymentValidationResult.VALID
    }

    private suspend fun checkSignatureAndStorage(
        payload: PaymentPayload,
        signature: String,
        paymentDao: PaymentDao?,
    ): PaymentValidationResult {
        if (signature.isBlank()) {
            return PaymentValidationResult.INVALID_SIGNATURE
        }
        val senderBundle = PublicKeyBundle.decode(payload.senderPublicKey)
            ?: return PaymentValidationResult.INVALID_IDENTITY

        val isSigValid = PaymentSigner.verify(payload, signature, senderBundle)
        if (!isSigValid) {
            return PaymentValidationResult.INVALID_SIGNATURE
        }

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
