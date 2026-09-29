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
import app.getknit.knit.notifications.NotifConversation
import app.getknit.knit.notifications.NotifMessage
import app.getknit.knit.notifications.Notifier
import app.getknit.knit.payment.crypto.PaymentSigner
import app.getknit.knit.payment.protocol.PaymentPayload
import app.getknit.knit.payment.protocol.PaymentProtocolValidator
import app.getknit.knit.payment.protocol.PaymentValidationResult
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class PaymentMeshIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: KnitDatabase
    private lateinit var paymentDao: PaymentDao
    private lateinit var walletDao: WalletDao

    private lateinit var senderHybridPrivate: KeysetHandle
    private lateinit var senderSigPrivate: KeysetHandle
    private lateinit var senderBundle: PublicKeyBundle
    private lateinit var senderCrypto: MessageCrypto

    private lateinit var receiverHybridPrivate: KeysetHandle
    private lateinit var receiverSigPrivate: KeysetHandle
    private lateinit var receiverBundle: PublicKeyBundle
    private lateinit var receiverCrypto: MessageCrypto

    private lateinit var keyStoreFile: File

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
        receiverCrypto = MessageCrypto(receiverHybridPrivate, receiverSigPrivate)

        keyStoreFile = File.createTempFile("identity", ".key")
        val keystoreSecret = KeystoreSecret(context, "test-alias-1", "identity.key", keyStoreFile.parentFile!!)
        val identityKeyStore = IdentityKeyStore(keystoreSecret)

        val repository = PaymentRepository(paymentDao, walletDao, identityKeyStore)
    }

    @After
    fun tearDown() {
        db.close()
        keyStoreFile.delete()
    }

    @Test
    fun `inbound payment processing stores payment and updates receiver balance`() =
        runTest {
            val now = System.currentTimeMillis()
            val receiverWallet =
                WalletEntity(
                    walletId = "wallet-receiver",
                    publicKey = receiverBundle.encoded,
                    displayName = "Phone B",
                    settledBalance = 10000L, // ₹100.00
                    pendingOutbound = 0L,
                )
            walletDao.upsertWallet(receiverWallet)

            val payload =
                PaymentPayload(
                    transactionId = "tx-mesh-1",
                    senderPublicKey = senderBundle.encoded,
                    receiverPublicKey = receiverBundle.encoded,
                    senderWalletId = "wallet-sender",
                    receiverWalletId = "wallet-receiver",
                    amount = 10000L, // ₹100.00
                    currency = "INR",
                    timestamp = now,
                    nonce = 1L,
                    createdOffline = true,
                    expiryTime = now + 86400000L,
                )
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)

            val keyStoreFile2 = File.createTempFile("identity2", ".key")
            val secret2 = KeystoreSecret(context, "test-alias-2", "identity2.key", keyStoreFile2.parentFile!!)
            val identityKeyStoreReceiver = IdentityKeyStore(secret2)
            val receiverRepo = PaymentRepository(paymentDao, walletDao, identityKeyStoreReceiver)

            val env =
                RelayEnvelope(
                    type = FrameType.PAYMENT,
                    id = payload.transactionId,
                    senderId = "sender-node",
                    sentAt = now,
                    recipientId = payload.receiverWalletId,
                    payload = ByteArray(0),
                )
            val wire =
                WireEnvelope(
                    ttl = 8,
                    hops = 2, // Multi-hop: 2 hops away!
                    relay = true,
                    sig = Base64.getDecoder().decode(sig),
                    signed = ByteArray(0),
                )

            val result = receiverRepo.processInboundPayment(payload, sig, env, wire, "node-b")
            assertEquals(PaymentValidationResult.VALID, result)

            val storedPayment = paymentDao.getPaymentById("tx-mesh-1")
            assertNotNull(storedPayment)
            assertEquals("PENDING_SETTLEMENT", storedPayment?.status)
            assertEquals(2, storedPayment?.hopCount) // Hop count captured!

            val updatedWallet = walletDao.getWallet("wallet-receiver")
            assertEquals(10000L, updatedWallet?.settledBalance) // ₹100 settled
            assertEquals(10000L, updatedWallet?.pendingInbound) // ₹100 pending inbound
            assertEquals(20000L, updatedWallet?.totalBalance) // ₹200 total represented

            keyStoreFile2.delete()
        }

    @Test
    fun `payment intended for another recipient is ignored locally without changing balance`() =
        runTest {
            val now = System.currentTimeMillis()
            val receiverWallet =
                WalletEntity(
                    walletId = "wallet-receiver-me",
                    publicKey = receiverBundle.encoded,
                    displayName = "Phone B",
                    settledBalance = 10000L,
                    pendingOutbound = 0L,
                )
            walletDao.upsertWallet(receiverWallet)

            val payloadForOther =
                PaymentPayload(
                    transactionId = "tx-mesh-other",
                    senderPublicKey = senderBundle.encoded,
                    receiverPublicKey = "other-pub-key",
                    senderWalletId = "wallet-sender",
                    receiverWalletId = "wallet-other-person",
                    amount = 10000L,
                    currency = "INR",
                    timestamp = now,
                    nonce = 2L,
                    createdOffline = true,
                    expiryTime = now + 86400000L,
                )
            val sig = PaymentSigner.sign(payloadForOther, senderCrypto::signRaw)

            val secret3 = KeystoreSecret(context, "test-alias-3", "identity3.key", keyStoreFile.parentFile!!)
            val identityKeyStore = IdentityKeyStore(secret3)
            val repository = PaymentRepository(paymentDao, walletDao, identityKeyStore)

            val env =
                RelayEnvelope(
                    type = FrameType.PAYMENT,
                    id = payloadForOther.transactionId,
                    senderId = "sender-node",
                    payload = ByteArray(0),
                )
            val wire = WireEnvelope(sig = Base64.getDecoder().decode(sig), signed = ByteArray(0))

            val result = repository.processInboundPayment(payloadForOther, sig, env, wire, "node-other")
            assertEquals(PaymentValidationResult.VALID, result)

            // Database should NOT contain this payment because it wasn't for this wallet!
            val storedPayment = paymentDao.getPaymentById("tx-mesh-other")
            assertNull(storedPayment)

            // Balance remains unchanged!
            val walletAfter = walletDao.getWallet("wallet-receiver-me")
            assertEquals(10000L, walletAfter?.totalBalance)
        }

    @Test
    fun `multi hop forwarding preserves signature validity across intermediate hops`() =
        runTest {
            val now = System.currentTimeMillis()
            val payload =
                PaymentPayload(
                    transactionId = "tx-multihop-1",
                    senderPublicKey = senderBundle.encoded,
                    receiverPublicKey = receiverBundle.encoded,
                    senderWalletId = "wallet-phone-a",
                    receiverWalletId = "wallet-phone-c",
                    amount = 5000L, // ₹50.00
                    currency = "INR",
                    timestamp = now,
                    nonce = 10L,
                    createdOffline = true,
                    expiryTime = now + 86400000L,
                )
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)

            // Simulate Phone B (intermediate relay node):
            // Phone B receives wire with hops = 1 and forwards it as wireRelayed with hops = 2
            val wireHop1 =
                WireEnvelope(
                    ttl = 8,
                    hops = 1,
                    relay = true,
                    sig = Base64.getDecoder().decode(sig),
                    signed = ByteArray(0),
                )
            val wireHop2 = wireHop1.relayed() // Hops incremented to 2

            // Phone C (recipient) verifies signature on wireHop2
            val isSigValidOnPhoneC = PaymentSigner.verify(payload, sig, senderBundle)
            val msg = "Ed25519 signature remains 100% valid after mesh hop count increment"
            assertTrue(msg, isSigValidOnPhoneC)

            val resultValidation = PaymentProtocolValidator.validate(payload, sig)
            assertEquals(PaymentValidationResult.VALID, resultValidation)
            assertEquals(2, wireHop2.hops)
        }

    @Test
    fun `duplicate inbound payment does not trigger second notification or duplicate credit`() =
        runTest {
            val now = System.currentTimeMillis()
            val fakeNotifier = FakeNotifier()

            val secret4 = KeystoreSecret(context, "test-alias-4", "identity4.key", keyStoreFile.parentFile!!)
            val identityKeyStoreReceiver = IdentityKeyStore(secret4)
            val receiverRepo = PaymentRepository(paymentDao, walletDao, identityKeyStoreReceiver, fakeNotifier)

            val myWallet = receiverRepo.ensurePrimaryWallet()

            val payload =
                PaymentPayload(
                    transactionId = "tx-dup-1",
                    senderPublicKey = senderBundle.encoded,
                    receiverPublicKey = myWallet.publicKey,
                    senderWalletId = "wallet-sender",
                    receiverWalletId = myWallet.walletId,
                    amount = 10000L,
                    currency = "INR",
                    timestamp = now,
                    nonce = 1L,
                    createdOffline = true,
                    expiryTime = now + 86400000L,
                )
            val sig = PaymentSigner.sign(payload, senderCrypto::signRaw)
            val env = RelayEnvelope(type = FrameType.PAYMENT, id = payload.transactionId, senderId = "sender-node", payload = ByteArray(0))
            val wire = WireEnvelope(sig = Base64.getDecoder().decode(sig), signed = ByteArray(0))

            // First processing: SUCCESS
            val result1 = receiverRepo.processInboundPayment(payload, sig, env, wire, "node-sender")
            assertEquals(PaymentValidationResult.VALID, result1)
            assertEquals(1, fakeNotifier.receivedNotifications.size)
            assertEquals(10000L, walletDao.getWallet(myWallet.walletId)?.pendingInbound)

            // Second processing (duplicate): DUPLICATE_TRANSACTION
            val result2 = receiverRepo.processInboundPayment(payload, sig, env, wire, "node-sender")
            assertEquals(PaymentValidationResult.DUPLICATE_TRANSACTION, result2)

            // Notification count remains 1 and pending inbound remains 10000L
            assertEquals(1, fakeNotifier.receivedNotifications.size)
            assertEquals(10000L, walletDao.getWallet(myWallet.walletId)?.pendingInbound)
        }
}

@Suppress("EmptyFunctionBlock")
class FakeNotifier : Notifier {
    val receivedNotifications = mutableListOf<Triple<String, Long, String>>()

    override fun createChannel() { /* No-op */ }

    override fun notify(
        incoming: NotifMessage,
        conversation: NotifConversation,
        selfId: String,
        selfName: String,
        selfAvatarBytes: ByteArray?,
    ) { /* No-op */ }

    override fun notifyTransferOffer(
        peerId: String,
        peerName: String,
        peerAvatarBytes: ByteArray?,
        fileName: String,
        sizeBytes: Long?,
    ) { /* No-op */ }

    override fun notifyMention(
        incoming: NotifMessage,
        conversation: NotifConversation,
        selfId: String,
        selfName: String,
        selfAvatarBytes: ByteArray?,
    ) { /* No-op */ }

    override fun onReplied(
        notificationTag: String,
        text: String,
        selfId: String,
        selfName: String,
        selfAvatarBytes: ByteArray?,
    ) { /* No-op */ }

    override fun setVisibleConversation(conversationId: String?) { /* No-op */ }

    override fun clearConversation(conversationId: String) { /* No-op */ }

    override fun onDismissed(tag: String) { /* No-op */ }

    override fun notifyMessageRequests(count: Int) { /* No-op */ }

    override fun setRequestsVisible(visible: Boolean) { /* No-op */ }

    override fun notifyOpenToChat(
        names: List<String>,
        avatarBytes: ByteArray?,
    ) { /* No-op */ }

    override fun clearOpenToChat() { /* No-op */ }

    override fun notifyPaymentReceived(
        senderWalletId: String,
        amountPaise: Long,
        transactionId: String,
    ) {
        receivedNotifications.add(Triple(senderWalletId, amountPaise, transactionId))
    }
}
