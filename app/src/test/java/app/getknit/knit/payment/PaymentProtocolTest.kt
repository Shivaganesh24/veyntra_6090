package app.getknit.knit.payment

import app.getknit.knit.mesh.crypto.MessageCrypto
import app.getknit.knit.mesh.crypto.PublicKeyBundle
import app.getknit.knit.mesh.crypto.TinkInit
import app.getknit.knit.mesh.protocol.WireCodec
import app.getknit.knit.payment.crypto.PaymentSigner
import app.getknit.knit.payment.protocol.PaymentPayload
import app.getknit.knit.payment.protocol.PaymentProtocolValidator
import app.getknit.knit.payment.protocol.PaymentValidationResult
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.Base64
import kotlin.reflect.full.memberProperties

class PaymentProtocolTest {
    private lateinit var senderHybridPrivate: KeysetHandle
    private lateinit var senderSigPrivate: KeysetHandle
    private lateinit var senderBundle: PublicKeyBundle

    private lateinit var receiverHybridPrivate: KeysetHandle
    private lateinit var receiverSigPrivate: KeysetHandle
    private lateinit var receiverBundle: PublicKeyBundle

    private lateinit var senderCrypto: MessageCrypto

    @Before
    fun setUp() {
        TinkInit.ensure()
        senderHybridPrivate = KeysetHandle.generateNew(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"))
        senderSigPrivate = KeysetHandle.generateNew(KeyTemplates.get("ED25519_RAW"))
        senderBundle = PublicKeyBundle.fromPrivate(senderHybridPrivate, senderSigPrivate)

        receiverHybridPrivate = KeysetHandle.generateNew(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW"))
        receiverSigPrivate = KeysetHandle.generateNew(KeyTemplates.get("ED25519_RAW"))
        receiverBundle = PublicKeyBundle.fromPrivate(receiverHybridPrivate, receiverSigPrivate)

        senderCrypto = MessageCrypto(senderHybridPrivate, senderSigPrivate)
    }

    private fun createValidPayload(): PaymentPayload {
        val now = System.currentTimeMillis()
        return PaymentPayload(
            transactionId = "tx-001",
            senderPublicKey = senderBundle.encoded,
            receiverPublicKey = receiverBundle.encoded,
            senderWalletId = "wallet-sender",
            receiverWalletId = "wallet-receiver",
            amount = 10000L, // ₹100.00
            currency = "INR",
            timestamp = now,
            nonce = 101L,
            previousTransactionReference = null,
            createdOffline = true,
            expiryTime = now + 86400000L, // +24 hours
        )
    }

    @Test
    fun `1 valid signature is accepted`() =
        runTest {
            val payload = createValidPayload()
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)

            val bytes = PaymentSigner.canonicalSigningBytes(payload)
            val sigBytes = Base64.getDecoder().decode(sig)
            senderBundle.verifier().verify(sigBytes, bytes) // throws directly if invalid

            val result = PaymentProtocolValidator.validate(payload, sig)
            assertEquals(PaymentValidationResult.VALID, result)
        }

    @Test
    fun `2 modified amount causes signature verification failure`() =
        runTest {
            val payload = createValidPayload()
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)

            val tamperedPayload = payload.copy(amount = 20000L) // ₹200.00
            val result = PaymentProtocolValidator.validate(tamperedPayload, sig)
            assertEquals(PaymentValidationResult.INVALID_SIGNATURE, result)
        }

    @Test
    fun `3 modified receiver causes signature verification failure`() =
        runTest {
            val payload = createValidPayload()
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)

            val tamperedPayload = payload.copy(receiverWalletId = "attacker-wallet")
            val result = PaymentProtocolValidator.validate(tamperedPayload, sig)
            assertEquals(PaymentValidationResult.INVALID_SIGNATURE, result)
        }

    @Test
    fun `4 modified transaction ID causes signature verification failure`() =
        runTest {
            val payload = createValidPayload()
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)

            val tamperedPayload = payload.copy(transactionId = "tx-tampered")
            val result = PaymentProtocolValidator.validate(tamperedPayload, sig)
            assertEquals(PaymentValidationResult.INVALID_SIGNATURE, result)
        }

    @Test
    fun `5 modified nonce causes signature verification failure`() =
        runTest {
            val payload = createValidPayload()
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)

            val tamperedPayload = payload.copy(nonce = 999L)
            val result = PaymentProtocolValidator.validate(tamperedPayload, sig)
            assertEquals(PaymentValidationResult.INVALID_SIGNATURE, result)
        }

    @Test
    fun `6 modified timestamp causes signature verification failure`() =
        runTest {
            val payload = createValidPayload()
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)

            val tamperedPayload = payload.copy(timestamp = payload.timestamp + 500L)
            val result = PaymentProtocolValidator.validate(tamperedPayload, sig)
            assertEquals(PaymentValidationResult.INVALID_SIGNATURE, result)
        }

    @Test
    fun `7 wrong public key causes signature verification failure`() =
        runTest {
            val payload = createValidPayload()
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)

            // Claimed sender key changed to receiver's bundle
            val tamperedPayload = payload.copy(senderPublicKey = receiverBundle.encoded)
            val result = PaymentProtocolValidator.validate(tamperedPayload, sig)
            assertEquals(PaymentValidationResult.INVALID_SIGNATURE, result)
        }

    @Test
    fun `9 expired transaction is rejected`() =
        runTest {
            val now = System.currentTimeMillis()
            val expiredPayload =
                createValidPayload().copy(
                    expiryTime = now - 1000L, // expired 1s ago
                )
            val sig = PaymentSigner.sign(expiredPayload, senderCrypto::signRaw)

            val result = PaymentProtocolValidator.validate(expiredPayload, sig, currentClock = now)
            assertEquals(PaymentValidationResult.EXPIRED, result)
        }

    @Test
    fun `10 11 12 zero or negative amount is rejected`() =
        runTest {
            val zeroPayload = createValidPayload().copy(amount = 0L)
            val zeroSig = PaymentSigner.sign(zeroPayload, senderCrypto::signRaw)
            assertEquals(PaymentValidationResult.INVALID_AMOUNT, PaymentProtocolValidator.validate(zeroPayload, zeroSig))

            val negativePayload = createValidPayload().copy(amount = -500L)
            val negSig = PaymentSigner.sign(negativePayload, senderCrypto::signRaw)
            assertEquals(PaymentValidationResult.INVALID_AMOUNT, PaymentProtocolValidator.validate(negativePayload, negSig))
        }

    @Test
    fun `13 unsupported currency is rejected`() =
        runTest {
            val usdPayload = createValidPayload().copy(currency = "USD")
            val usdSig = PaymentSigner.sign(usdPayload, senderCrypto::signRaw)
            assertEquals(
                PaymentValidationResult.INVALID_CURRENCY,
                PaymentProtocolValidator.validate(usdPayload, usdSig, supportedCurrency = "INR"),
            )
        }

    @Test
    fun `14 deterministic serialization produces identical signing bytes`() {
        val payload1 = createValidPayload()
        val payload2 = createValidPayload()

        val bytes1 = PaymentSigner.canonicalSigningBytes(payload1)
        val bytes2 = PaymentSigner.canonicalSigningBytes(payload2)

        assertArrayEquals(bytes1, bytes2)
    }

    @Test
    fun `15 valid payload serialize and deserialize retains valid signature`() =
        runTest {
            val payload = createValidPayload()
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)

            // Encode to CBOR via WireCodec
            val cborBytes = WireCodec.encodePayload(payload)
            val decodedPayload = WireCodec.decodePayload<PaymentPayload>(cborBytes)

            assertNotNull(decodedPayload)
            assertEquals(payload, decodedPayload)

            // Signature verification passes on decoded payload
            val result = PaymentProtocolValidator.validate(decodedPayload!!, sig)
            assertEquals(PaymentValidationResult.VALID, result)
        }

    @Test
    fun `16 private keys never appear in PaymentPayload properties or serialized payload`() {
        val payload = createValidPayload()

        // Reflection check: no field contains "private" or "secret"
        val propertyNames = PaymentPayload::class.memberProperties.map { it.name }
        for (prop in propertyNames) {
            assertFalse(prop.lowercase().contains("private"))
            assertFalse(prop.lowercase().contains("secret"))
            assertFalse(prop.lowercase().contains("seed"))
        }

        // Serialized CBOR content check
        val cborString = String(WireCodec.encodePayload(payload))
        assertFalse(cborString.lowercase().contains("private"))
        assertFalse(cborString.lowercase().contains("secret"))
    }
}
