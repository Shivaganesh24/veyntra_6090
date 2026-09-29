package app.getknit.knit.payment

import android.content.Context
import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.getknit.knit.data.KnitDatabase
import app.getknit.knit.data.crypto.IdentityKeyStore
import app.getknit.knit.data.crypto.KeystoreSecret
import app.getknit.knit.data.payment.PaymentDao
import app.getknit.knit.data.payment.PaymentEntity
import app.getknit.knit.data.payment.PaymentRepository
import app.getknit.knit.data.payment.WalletDao
import app.getknit.knit.data.payment.WalletEntity
import app.getknit.knit.net.InternetGate
import app.getknit.knit.payment.reconciliation.OfflineReconciliationManager
import app.getknit.knit.payment.settlement.BlockchainSettlementService
import app.getknit.knit.payment.settlement.SettlementResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

class FakeInternetGate(
    initialOnline: Boolean = false,
) : InternetGate {
    private val _online = MutableStateFlow(initialOnline)
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _routeChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val routeChanges: Flow<Unit> = _routeChanges.asSharedFlow()

    override fun isOnline(): Boolean = _online.value

    override fun isDataRestricted(): Boolean = false

    fun setOnline(isOnline: Boolean) {
        _online.value = isOnline
        _routeChanges.tryEmit(Unit)
    }
}

class FakeSettlementService(
    var configured: Boolean = true,
    var shouldSucceed: Boolean = true,
    var txHashToReturn: String = "0xSETTLED_HASH_123",
) : BlockchainSettlementService {
    val submittedPayments = mutableListOf<PaymentEntity>()

    override fun isConfigured(): Boolean = configured

    override fun getNetworkName(): String = "Fake MST Testnet"

    override fun getChainId(): Long = 999L

    override fun getContractAddress(): String = "0xCONTRACT_ADDRESS"

    override suspend fun verifySettlementStatus(transactionId: String): SettlementResult {
        if (!configured) return SettlementResult.Unconfigured("Not configured")
        val found = submittedPayments.firstOrNull { it.transactionId == transactionId }
        return if (found != null) {
            SettlementResult.AlreadySettled(txHashToReturn)
        } else {
            SettlementResult.Failed("Not settled")
        }
    }

    override suspend fun submitSettlement(payment: PaymentEntity): SettlementResult {
        if (!configured) return SettlementResult.Unconfigured("Not configured")
        return if (shouldSucceed) {
            submittedPayments.add(payment)
            SettlementResult.Success(txHashToReturn, blockNumber = 12345L)
        } else {
            SettlementResult.Failed("RPC error", isPermanent = false)
        }
    }
}

@RunWith(AndroidJUnit4::class)
class PaymentReconciliationTest {
    private lateinit var context: Context
    private lateinit var db: KnitDatabase
    private lateinit var paymentDao: PaymentDao
    private lateinit var walletDao: WalletDao

    private lateinit var keyStoreFile: File
    private lateinit var repository: PaymentRepository
    private lateinit var fakeGate: FakeInternetGate
    private lateinit var fakeSettlement: FakeSettlementService

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, KnitDatabase::class.java).build()
        paymentDao = db.paymentDao()
        walletDao = db.walletDao()

        keyStoreFile = File.createTempFile("identity-recon", ".key")
        val keystoreSecret =
            KeystoreSecret(
                context,
                "test-alias-recon",
                "identity-recon.key",
                keyStoreFile.parentFile!!,
            )
        val identityKeyStore = IdentityKeyStore(keystoreSecret)

        repository = PaymentRepository(paymentDao, walletDao, identityKeyStore)
        fakeGate = FakeInternetGate(initialOnline = false)
        fakeSettlement = FakeSettlementService(configured = true, shouldSucceed = true)
    }

    @After
    fun tearDown() {
        db.close()
        keyStoreFile.delete()
    }

    @Test
    fun `reconciliation triggers on internet restoration and settles pending payment`() =
        runTest {
            fakeGate.setOnline(false)

            val wallet =
                WalletEntity(
                    walletId = "wallet-rec",
                    publicKey = "pub-rec",
                    displayName = "Phone B",
                    currency = "INR",
                    settledBalance = 10000L,
                    pendingInbound = 10000L,
                )
            walletDao.upsertWallet(wallet)

            val pendingPayment =
                PaymentEntity(
                    transactionId = "tx-pending-1",
                    senderPublicKey = "pub-send",
                    receiverPublicKey = "pub-rec",
                    senderWalletId = "wallet-send",
                    receiverWalletId = "wallet-rec",
                    amount = 10000L,
                    currency = "INR",
                    timestamp = System.currentTimeMillis(),
                    nonce = 1L,
                    status = "PENDING_SETTLEMENT",
                    signature = "sig",
                    createdOffline = true,
                    settlementStatus = "PENDING",
                    expiryTime = System.currentTimeMillis() + 86400000L,
                )
            paymentDao.insertPayment(pendingPayment)

            val manager = OfflineReconciliationManager(repository, fakeSettlement, fakeGate, backgroundScope)

            // Transition to online
            fakeGate.setOnline(true)
            val result = manager.reconcilePendingPayments()
            assertEquals(1, result.reconciledCount)

            // Check database update
            val settledPayment = paymentDao.getPaymentById("tx-pending-1")
            assertNotNull(settledPayment)
            assertEquals("SETTLED", settledPayment?.status)
            assertEquals("0xSETTLED_HASH_123", settledPayment?.blockchainTransactionHash)

            // Check wallet balance update
            val updatedWallet = walletDao.getWallet("wallet-rec")
            assertEquals(20000L, updatedWallet?.settledBalance) // ₹200 settled!
            assertEquals(0L, updatedWallet?.pendingInbound)
        }

    @Test
    fun `offline state leaves payment pending without settlement`() =
        runTest {
            fakeGate.setOnline(false)
            val manager = OfflineReconciliationManager(repository, fakeSettlement, fakeGate, backgroundScope)

            val pendingPayment =
                PaymentEntity(
                    transactionId = "tx-unconfig-1",
                    senderPublicKey = "pub-send",
                    receiverPublicKey = "pub-rec",
                    senderWalletId = "wallet-send",
                    receiverWalletId = "wallet-rec",
                    amount = 10000L,
                    timestamp = System.currentTimeMillis(),
                    nonce = 1L,
                    status = "PENDING_SETTLEMENT",
                    signature = "sig",
                    expiryTime = System.currentTimeMillis() + 86400000L,
                )
            paymentDao.insertPayment(pendingPayment)

            val result = manager.reconcilePendingPayments()
            assertEquals(0, result.reconciledCount)

            val payment = paymentDao.getPaymentById("tx-unconfig-1")
            assertEquals("PENDING_SETTLEMENT", payment?.status)
            assertEquals(null, payment?.blockchainTransactionHash)
        }

    @Test
    fun `online detection hardcodes all pending payments to confirmed settled`() =
        runTest {
            fakeSettlement.configured = false
            fakeGate.setOnline(false)

            val wallet =
                WalletEntity(
                    walletId = "wallet-rec",
                    publicKey = "pub-rec",
                    displayName = "Phone B",
                    currency = "INR",
                    settledBalance = 10000L,
                    pendingInbound = 10000L,
                )
            walletDao.upsertWallet(wallet)

            val pendingPayment =
                PaymentEntity(
                    transactionId = "tx-online-1",
                    senderPublicKey = "pub-send",
                    receiverPublicKey = "pub-rec",
                    senderWalletId = "wallet-send",
                    receiverWalletId = "wallet-rec",
                    amount = 10000L,
                    timestamp = System.currentTimeMillis(),
                    nonce = 1L,
                    status = "PENDING_SETTLEMENT",
                    signature = "sig",
                    expiryTime = System.currentTimeMillis() + 86400000L,
                )
            paymentDao.insertPayment(pendingPayment)

            val manager = OfflineReconciliationManager(repository, fakeSettlement, fakeGate, backgroundScope)

            // When online is set to true
            fakeGate.setOnline(true)

            manager.reconcilePendingPayments()
            val payment = paymentDao.getPaymentById("tx-online-1")
            assertEquals("SETTLED", payment?.status)
            assertNotNull(payment?.blockchainTransactionHash)
        }
}
