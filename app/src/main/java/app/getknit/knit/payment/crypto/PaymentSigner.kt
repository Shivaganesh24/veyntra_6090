package app.getknit.knit.payment.crypto

import app.getknit.knit.mesh.crypto.PublicKeyBundle
import app.getknit.knit.payment.protocol.PaymentPayload
import com.google.crypto.tink.PublicKeyVerify
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Deterministic cryptographic signing and verification helper for MST OfflinePay transactions.
 *
 * Domain separation label: "mst/payment/v1"
 *
 * Canonical byte layout covered by Ed25519 signature:
 * 1. "mst/payment/v1" domain label
 * 2. transactionId (UTF-8 bytes)
 * 3. senderPublicKey (UTF-8 bytes)
 * 4. receiverPublicKey (UTF-8 bytes)
 * 5. senderWalletId (UTF-8 bytes)
 * 6. receiverWalletId (UTF-8 bytes)
 * 7. amount (8 bytes, BigEndian Long)
 * 8. currency (UTF-8 bytes)
 * 9. timestamp (8 bytes, BigEndian Long)
 * 10. nonce (8 bytes, BigEndian Long)
 * 11. previousTransactionReference (UTF-8 bytes, or empty string if null)
 * 12. createdOffline (1 byte: 0x01 if true, 0x00 if false)
 * 13. expiryTime (8 bytes, BigEndian Long)
 */
object PaymentSigner {
    private const val SIGNING_LABEL = "mst/payment/v1"

    /**
     * Constructs the canonical, deterministic byte buffer for a given [PaymentPayload].
     */
    fun canonicalSigningBytes(payload: PaymentPayload): ByteArray {
        val labelBytes = SIGNING_LABEL.toByteArray(StandardCharsets.UTF_8)
        val txIdBytes = payload.transactionId.toByteArray(StandardCharsets.UTF_8)
        val senderPkBytes = payload.senderPublicKey.toByteArray(StandardCharsets.UTF_8)
        val receiverPkBytes = payload.receiverPublicKey.toByteArray(StandardCharsets.UTF_8)
        val senderWalletBytes = payload.senderWalletId.toByteArray(StandardCharsets.UTF_8)
        val receiverWalletBytes = payload.receiverWalletId.toByteArray(StandardCharsets.UTF_8)
        val currencyBytes = payload.currency.toByteArray(StandardCharsets.UTF_8)
        val prevRefBytes = (payload.previousTransactionReference ?: "").toByteArray(StandardCharsets.UTF_8)

        val totalLength =
            labelBytes.size + txIdBytes.size + senderPkBytes.size + receiverPkBytes.size +
                senderWalletBytes.size + receiverWalletBytes.size + 8 + currencyBytes.size +
                8 + 8 + prevRefBytes.size + 1 + 8

        val buffer = ByteBuffer.allocate(totalLength)
        buffer.put(labelBytes)
        buffer.put(txIdBytes)
        buffer.put(senderPkBytes)
        buffer.put(receiverPkBytes)
        buffer.put(senderWalletBytes)
        buffer.put(receiverWalletBytes)
        buffer.putLong(payload.amount)
        buffer.put(currencyBytes)
        buffer.putLong(payload.timestamp)
        buffer.putLong(payload.nonce)
        buffer.put(prevRefBytes)
        buffer.put(if (payload.createdOffline) 1.toByte() else 0.toByte())
        buffer.putLong(payload.expiryTime)

        return buffer.array()
    }

    /**
     * Signs [payload] using Knit's Ed25519 raw signer callback ([signRaw]), returning a Base64-encoded signature.
     */
    fun sign(
        payload: PaymentPayload,
        signRaw: (ByteArray) -> ByteArray,
    ): String {
        val signingBytes = canonicalSigningBytes(payload)
        val rawSig = signRaw(signingBytes)
        return Base64.getEncoder().encodeToString(rawSig)
    }

    /**
     * Verifies that [signatureBase64] is a valid Ed25519 signature over [payload] made by [senderBundle].
     */
    fun verify(
        payload: PaymentPayload,
        signatureBase64: String,
        senderBundle: PublicKeyBundle,
    ): Boolean =
        runCatching {
            val verifier = senderBundle.verifier()
            verify(payload, signatureBase64, verifier)
        }.getOrDefault(false)

    /**
     * Verifies that [signatureBase64] is a valid signature over [payload] using Tink's [verifier].
     */
    fun verify(
        payload: PaymentPayload,
        signatureBase64: String,
        verifier: PublicKeyVerify,
    ): Boolean =
        runCatching {
            val sigBytes = Base64.getDecoder().decode(signatureBase64)
            val signingBytes = canonicalSigningBytes(payload)
            verifier.verify(sigBytes, signingBytes)
            true
        }.getOrDefault(false)
}
