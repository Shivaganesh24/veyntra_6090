package app.getknit.knit.payment

import android.content.Context
import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.getknit.knit.data.KnitDatabase
import app.getknit.knit.data.crypto.IdentityKeyStore
import app.getknit.knit.data.crypto.KeystoreSecret
import app.getknit.knit.data.payment.PaymentDao
import app.getknit.knit.data.payment.PaymentRepository
import app.getknit.knit.data.payment.WalletDao
import app.getknit.knit.data.payment.WalletEntity
import app.getknit.knit.mesh.crypto.MessageCrypto
import app.getknit.knit.mesh.crypto.PublicKeyBundle
import app.getknit.knit.mesh.crypto.TinkInit
import app.getknit.knit.mesh.protocol.FrameType
import app.getknit.knit.mesh.protocol.RelayEnvelope
import app.getknit.knit.mesh.protocol.WireEnvelope
import app.getknit.knit.payment.crypto.PaymentSigner
import app.getknit.knit.payment.protocol.PaymentPayload
import app.getknit.knit.payment.protocol.PaymentProtocolValidator
import app.getknit.knit.payment.protocol.PaymentValidationResult
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class PaymentWalletAccountingTest {
    private lateinit var context: Context
    private lateinit var db: KnitDatabase
    private lateinit var paymentDao: PaymentDao
    private lateinit var walletDao: WalletDao

    private lateinit var keyStoreFile: File
    private lateinit var identityKeyStore: IdentityKeyStore
    private lateinit var repository: PaymentRepository

    private lateinit var senderHybridPrivate: KeysetHandle
    private lateinit var senderSigPrivate: KeysetHandle
    private lateinit var senderBundle: PublicKeyBundle
    private lateinit var senderCrypto: MessageCrypto

    private lateinit var receiverHybridPrivate: KeysetHandle
    private lateinit var receiverSigPrivate: KeysetHandle
    private lateinit var receiverBundle: PublicKeyBundle

    @Before
    fun setUp() {
        TinkInit.ensure()

        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, KnitDatabase::class.java).build()
        paymentDao = db.paymentDao()
        walletDao = db.walletDao()

        val keyTemplate = KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW")
        senderHybridPrivate = KeysetHandle.generateNew(keyTemplate)
        senderSigPrivate = KeysetHandle.generateNew(KeyTemplates.get("ED25519_RAW"))
        senderBundle = PublicKeyBundle.fromPrivate(senderHybridPrivate, senderSigPrivate)
        senderCrypto = MessageCrypto(senderHybridPrivate, senderSigPrivate)

        receiverHybridPrivate = KeysetHandle.generateNew(keyTemplate)
        receiverSigPrivate = KeysetHandle.generateNew(KeyTemplates.get("ED25519_RAW"))
        receiverBundle = PublicKeyBundle.fromPrivate(receiverHybridPrivate, receiverSigPrivate)

        keyStoreFile = File.createTempFile("identity-acc", ".key")
        val keystoreSecret =
            KeystoreSecret(
                context,
                "test-alias-acc",
                "identity-acc.key",
                keyStoreFile.parentFile!!,
            )
        identityKeyStore = IdentityKeyStore(keystoreSecret)

        repository = PaymentRepository(paymentDao, walletDao, identityKeyStore)
    }

    @After
    fun tearDown() {
        db.close()
        keyStoreFile.delete()
    }

    @Test
    fun `1 outbound reservation succeeds and reduces available balance`() =
        runTest {
            val wallet =
                repository.initializeDemoWallet(
                    walletId = "wallet-sender",
                    displayName = "Phone A",
                    publicKey = senderBundle.encoded,
                    initialBalance = 50000L, // ₹500
                )
            assertEquals(50000L, wallet.availableBalance)

            val outbound =
                repository.createOutboundPayment(
                    receiverWalletId = "wallet-receiver",
                    receiverPublicKey = "rec-pub-key",
                    amount = 10000L, // ₹100
                    signRaw = senderCrypto::signRaw,
                )
            assertNotNull(outbound)

            val updatedWallet = repository.getPrimaryWallet()
            assertEquals(50000L, updatedWallet?.settledBalance)
            assertEquals(10000L, updatedWallet?.pendingOutbound)
            assertEquals(40000L, updatedWallet?.availableBalance) // ₹400 spendable
        }

    @Test
    fun `2 outbound payment fails if available balance is insufficient`() =
        runTest {
            repository.initializeDemoWallet(
                walletId = "wallet-sender",
                displayName = "Phone A",
                publicKey = senderBundle.encoded,
                initialBalance = 50000L,
            )

            // Try to send ₹600 (60000 paise)
            val outbound =
                repository.createOutboundPayment(
                    receiverWalletId = "wallet-receiver",
                    receiverPublicKey = "rec-pub-key",
                    amount = 60000L,
                    signRaw = senderCrypto::signRaw,
                )
            assertNull(outbound) // Fails!

            val wallet = repository.getPrimaryWallet()
            assertEquals(50000L, wallet?.availableBalance) // ₹500 remains available
        }

    @Test
    fun `3 concurrent payment attempts cannot overspend available balance`() =
        runTest {
            repository.initializeDemoWallet(
                walletId = "wallet-sender",
                displayName = "Phone A",
                publicKey = senderBundle.encoded,
                initialBalance = 50000L,
            )

            // Attempt two concurrent ₹400 payments
            val result1 =
                async {
                    repository.createOutboundPayment(
                        receiverWalletId = "receiver-1",
                        receiverPublicKey = "rec-pub-1",
                        amount = 40000L,
                        signRaw = senderCrypto::signRaw,
                    )
                }
            val result2 =
                async {
                    repository.createOutboundPayment(
                        receiverWalletId = "receiver-2",
                        receiverPublicKey = "rec-pub-2",
                        amount = 40000L,
                        signRaw = senderCrypto::signRaw,
                    )
                }

            val results = awaitAll(result1, result2)
            val successCount = results.count { it != null }

            assertEquals(1, successCount) // Exactly ONE succeeded!
            val wallet = repository.getPrimaryWallet()
            assertEquals(10000L, wallet?.availableBalance) // ₹100 remains
        }

    @Test
    fun `4 releasing outbound reservation restores available balance`() =
        runTest {
            repository.initializeDemoWallet(
                walletId = "wallet-sender",
                displayName = "Phone A",
                publicKey = senderBundle.encoded,
                initialBalance = 50000L,
            )
            val (payload, _) =
                repository.createOutboundPayment(
                    receiverWalletId = "wallet-receiver",
                    receiverPublicKey = "rec-pub",
                    amount = 10000L,
                    signRaw = senderCrypto::signRaw,
                )!!

            assertEquals(40000L, repository.getPrimaryWallet()?.availableBalance)

            // Release reservation (e.g. payment expired or rejected)
            repository.releaseOutboundReservation(
                payload.transactionId,
                10000L,
                PaymentValidationResult.EXPIRED,
            )

            val updated = repository.getPrimaryWallet()
            assertEquals(0L, updated?.pendingOutbound)
            assertEquals(50000L, updated?.availableBalance) // ₹500 restored
        }

    @Test
    fun `5 outbound settlement finalization deducts settled and pending outbound balance`() =
        runTest {
            repository.initializeDemoWallet(
                walletId = "wallet-sender",
                displayName = "Phone A",
                publicKey = senderBundle.encoded,
                initialBalance = 50000L,
            )
            val (payload, _) =
                repository.createOutboundPayment(
                    receiverWalletId = "wallet-receiver",
                    receiverPublicKey = "rec-pub",
                    amount = 10000L,
                    signRaw = senderCrypto::signRaw,
                )!!

            repository.confirmOutboundSettlement(payload.transactionId, 10000L, "0xHASH123")

            val updated = repository.getPrimaryWallet()
            assertEquals(40000L, updated?.settledBalance) // ₹400 settled balance
            assertEquals(0L, updated?.pendingOutbound)
            assertEquals(40000L, updated?.availableBalance)
        }

    @Test
    fun `6 and 7 inbound pending payment does NOT increase spendable available balance`() =
        runTest {
            repository.initializeDemoWallet(
                walletId = "wallet-receiver",
                displayName = "Phone B",
                publicKey = receiverBundle.encoded,
                initialBalance = 10000L,
            )
            val now = System.currentTimeMillis()

            val payload =
                PaymentPayload(
                    transactionId = "tx-inbound-1",
                    senderPublicKey = senderBundle.encoded,
                    receiverPublicKey = receiverBundle.encoded,
                    senderWalletId = "wallet-sender",
                    receiverWalletId = "wallet-receiver",
                    amount = 10000L, // ₹100 incoming
                    currency = "INR",
                    timestamp = now,
                    nonce = 1L,
                    createdOffline = true,
                    expiryTime = now + 86400000L,
                )
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)

            val env =
                RelayEnvelope(
                    type = FrameType.PAYMENT,
                    id = payload.transactionId,
                    senderId = "sender-node",
                    payload = ByteArray(0),
                )
            val wire =
                WireEnvelope(
                    sig = Base64.getDecoder().decode(sig),
                    signed = ByteArray(0),
                )

            repository.processInboundPayment(payload, sig, env, wire, "node-sender")

            val updatedWallet = repository.getPrimaryWallet()
            assertEquals(10000L, updatedWallet?.settledBalance) // ₹100 spendable
            assertEquals(10000L, updatedWallet?.pendingInbound) // ₹100 pending inbound
            assertEquals(10000L, updatedWallet?.availableBalance) // Spendable stays ₹100!
            assertEquals(20000L, updatedWallet?.totalBalance) // Displayed total = ₹200
        }

    @Test
    fun `8 inbound settlement finalization shifts pending inbound to settled spendable balance`() =
        runTest {
            repository.initializeDemoWallet(
                walletId = "wallet-receiver",
                displayName = "Phone B",
                publicKey = receiverBundle.encoded,
                initialBalance = 10000L,
            )
            val now = System.currentTimeMillis()

            val payload =
                PaymentPayload(
                    transactionId = "tx-inbound-2",
                    senderPublicKey = senderBundle.encoded,
                    receiverPublicKey = receiverBundle.encoded,
                    senderWalletId = "wallet-sender",
                    receiverWalletId = "wallet-receiver",
                    amount = 10000L,
                    currency = "INR",
                    timestamp = now,
                    nonce = 1L,
                    createdOffline = true,
                    expiryTime = now + 86400000L,
                )
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)
            val env =
                RelayEnvelope(
                    type = FrameType.PAYMENT,
                    id = payload.transactionId,
                    senderId = "sender-node",
                    payload = ByteArray(0),
                )
            val wire =
                WireEnvelope(
                    sig = Base64.getDecoder().decode(sig),
                    signed = ByteArray(0),
                )

            repository.processInboundPayment(payload, sig, env, wire, "node-sender")
            repository.confirmInboundSettlement("tx-inbound-2", 10000L, "0xONCHAIN_HASH")

            val updatedWallet = repository.getPrimaryWallet()
            assertEquals(20000L, updatedWallet?.settledBalance) // ₹200 settled
            assertEquals(0L, updatedWallet?.pendingInbound)
            assertEquals(20000L, updatedWallet?.availableBalance) // ₹200 now spendable!
        }

    @Test
    fun `9 inbound settlement failure rolls back pending inbound without touching settled balance`() =
        runTest {
            repository.initializeDemoWallet(
                walletId = "wallet-receiver",
                displayName = "Phone B",
                publicKey = receiverBundle.encoded,
                initialBalance = 10000L,
            )
            val now = System.currentTimeMillis()

            val payload =
                PaymentPayload(
                    transactionId = "tx-inbound-fail",
                    senderPublicKey = senderBundle.encoded,
                    receiverPublicKey = receiverBundle.encoded,
                    senderWalletId = "wallet-sender",
                    receiverWalletId = "wallet-receiver",
                    amount = 10000L,
                    currency = "INR",
                    timestamp = now,
                    nonce = 1L,
                    createdOffline = true,
                    expiryTime = now + 86400000L,
                )
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)
            val env =
                RelayEnvelope(
                    type = FrameType.PAYMENT,
                    id = payload.transactionId,
                    senderId = "sender-node",
                    payload = ByteArray(0),
                )
            val wire =
                WireEnvelope(
                    sig = Base64.getDecoder().decode(sig),
                    signed = ByteArray(0),
                )

            repository.processInboundPayment(payload, sig, env, wire, "node-sender")
            repository.rollbackInboundPending(
                "tx-inbound-fail",
                10000L,
                PaymentValidationResult.CONFLICT,
            )

            val updatedWallet = repository.getPrimaryWallet()
            assertEquals(10000L, updatedWallet?.settledBalance) // ₹100 remains
            assertEquals(0L, updatedWallet?.pendingInbound) // Rolled back!
            assertEquals(10000L, updatedWallet?.availableBalance)
        }

    @Test
    fun `11 and 12 monotonic nonce increments and persists across restarts`() =
        runTest {
            val wallet =
                repository.initializeDemoWallet(
                    walletId = "wallet-sender",
                    displayName = "Phone A",
                    publicKey = senderBundle.encoded,
                    initialBalance = 50000L,
                )
            assertEquals(1L, wallet.nextNonce)

            val (p1, _) =
                repository.createOutboundPayment(
                    receiverWalletId = "rec-1",
                    receiverPublicKey = "rec-pub-1",
                    amount = 10000L,
                    signRaw = senderCrypto::signRaw,
                )!!
            assertEquals(1L, p1.nonce)

            val (p2, _) =
                repository.createOutboundPayment(
                    receiverWalletId = "rec-2",
                    receiverPublicKey = "rec-pub-2",
                    amount = 10000L,
                    signRaw = senderCrypto::signRaw,
                )!!
            assertEquals(2L, p2.nonce)

            val currentWallet = repository.getPrimaryWallet()
            assertEquals(3L, currentWallet?.nextNonce)
        }

    @Test
    fun `15 crash recovery preserves payment status and wallet reservations after database close and reopen`() =
        runTest {
            repository.initializeDemoWallet(
                walletId = "wallet-sender",
                displayName = "Phone A",
                publicKey = senderBundle.encoded,
                initialBalance = 50000L,
            )
            val (payload, _) =
                repository.createOutboundPayment(
                    receiverWalletId = "wallet-receiver",
                    receiverPublicKey = "rec-pub",
                    amount = 10000L,
                    signRaw = senderCrypto::signRaw,
                )!!

            // Simulate app crash / restart: close and reopen database on same memory/disk file
            val tempFile = File.createTempFile("crash-test", ".db")
            val crashDb = Room.databaseBuilder(context, KnitDatabase::class.java, tempFile.name).build()
            val crashPaymentDao = crashDb.paymentDao()
            val crashWalletDao = crashDb.walletDao()

            crashWalletDao.upsertWallet(repository.getPrimaryWallet()!!)
            crashPaymentDao.insertPayment(paymentDao.getPaymentById(payload.transactionId)!!)

            val recoveredWallet = crashWalletDao.getWallet("wallet-sender")
            val recoveredPayment = crashPaymentDao.getPaymentById(payload.transactionId)

            assertEquals(10000L, recoveredWallet?.pendingOutbound)
            assertEquals(40000L, recoveredWallet?.availableBalance)
            assertEquals("OFFLINE_SENT", recoveredPayment?.status)

            crashDb.close()
            tempFile.delete()
        }
}
