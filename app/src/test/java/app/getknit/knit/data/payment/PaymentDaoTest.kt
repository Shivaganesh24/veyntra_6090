package app.getknit.knit.data.payment

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.getknit.knit.data.KnitDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentDaoTest {
    private lateinit var db: KnitDatabase
    private lateinit var paymentDao: PaymentDao
    private lateinit var walletDao: WalletDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, KnitDatabase::class.java).build()
        paymentDao = db.paymentDao()
        walletDao = db.walletDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `payment insertion and querying by id and nonce works`() =
        runTest {
            val payment =
                PaymentEntity(
                    transactionId = "tx-100",
                    senderPublicKey = "sender-pub-1",
                    receiverPublicKey = "receiver-pub-1",
                    senderWalletId = "wallet-sender",
                    receiverWalletId = "wallet-receiver",
                    amount = 10000L, // ₹100.00
                    currency = "INR",
                    timestamp = 1000L,
                    nonce = 1L,
                    status = "CREATED",
                    signature = "sig-123",
                    createdOffline = true,
                    expiryTime = 2000L,
                )

            paymentDao.insertPayment(payment)

            val fetchedById = paymentDao.getPaymentById("tx-100")
            assertNotNull(fetchedById)
            assertEquals("tx-100", fetchedById?.transactionId)
            assertEquals(10000L, fetchedById?.amount)

            val fetchedByNonce = paymentDao.getPaymentByNonce("sender-pub-1", 1L)
            assertNotNull(fetchedByNonce)
            assertEquals("tx-100", fetchedByNonce?.transactionId)
        }

    @Test
    fun `wallet fund reservation succeeds when sufficient balance exists and fails when insufficient`() =
        runTest {
            val wallet =
                WalletEntity(
                    walletId = "wallet-1",
                    publicKey = "pub-1",
                    displayName = "Demo Wallet",
                    settledBalance = 50000L, // ₹500.00
                    pendingOutbound = 0L,
                    isDemo = true,
                )
            walletDao.upsertWallet(wallet)

            // Reserve ₹100 (10000 paise) -> Available: ₹400
            val reserved1 = walletDao.reserveOutboundAmount("wallet-1", 10000L)
            assertEquals(1, reserved1)

            val walletAfterRes1 = walletDao.getWallet("wallet-1")
            assertEquals(50000L, walletAfterRes1?.settledBalance)
            assertEquals(10000L, walletAfterRes1?.pendingOutbound)
            assertEquals(40000L, walletAfterRes1?.availableBalance)

            // Reserve ₹400 more -> Available: ₹0
            val reserved2 = walletDao.reserveOutboundAmount("wallet-1", 40000L)
            assertEquals(1, reserved2)

            val walletAfterRes2 = walletDao.getWallet("wallet-1")
            assertEquals(0L, walletAfterRes2?.availableBalance)

            // Attempt to reserve ₹100 more -> Should FAIL (0 rows updated) because available balance is 0
            val reserved3 = walletDao.reserveOutboundAmount("wallet-1", 10000L)
            assertEquals(0, reserved3)

            // Release ₹100 reserved funds
            walletDao.releaseOutboundAmount("wallet-1", 10000L)
            val walletAfterRelease = walletDao.getWallet("wallet-1")
            assertEquals(10000L, walletAfterRelease?.availableBalance)
        }

    @Test
    fun `confirm settled payment deducts both total and pending outbound balance`() =
        runTest {
            val wallet =
                WalletEntity(
                    walletId = "wallet-1",
                    publicKey = "pub-1",
                    displayName = "Demo Wallet",
                    settledBalance = 50000L, // ₹500.00
                    pendingOutbound = 10000L, // ₹100.00 pending
                    isDemo = true,
                )
            walletDao.upsertWallet(wallet)

            // Confirm settlement of ₹100 outbound payment
            walletDao.confirmOutboundSettled("wallet-1", 10000L)

            val settledWallet = walletDao.getWallet("wallet-1")
            assertEquals(40000L, settledWallet?.totalBalance) // ₹400.00
            assertEquals(0L, settledWallet?.pendingOutbound) // ₹0 pending
            assertEquals(40000L, settledWallet?.availableBalance)
        }
}
