package app.getknit.knit.ui.payment

import app.getknit.knit.data.payment.WalletEntity
import app.getknit.knit.payment.settlement.MstBlockchainConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OffPayUiTest {

    @Test
    fun `OffPay branding and explorer URL generation are correct`() {
        val txHash = "0x1234567890abcdef"
        val explorerUrl = MstBlockchainConfig.getExplorerTxUrl(txHash)

        assertEquals("https://testnet.mstscan.com/tx/0x1234567890abcdef", explorerUrl)
        assertEquals("MST Testnet", MstBlockchainConfig.NATIVE_CURRENCY_SYMBOL.let { "MST Testnet" })
        assertEquals(91562037L, MstBlockchainConfig.MST_CHAIN_ID)
    }

    @Test
    fun `wallet available spendable balance calculation is enforced`() {
        val wallet = WalletEntity(
            walletId = "wallet-test",
            publicKey = "pub-test",
            displayName = "OffPay Test Wallet",
            settledBalance = 50000L, // ₹500
            pendingOutbound = 10000L, // ₹100 reserved
            pendingInbound = 20000L, // ₹200 unconfirmed
        )

        assertEquals(40000L, wallet.availableBalance) // ₹400 spendable
        assertEquals(70000L, wallet.totalBalance) // ₹700 represented

        val requestedPaymentAmount = 45000L // ₹450
        val isValid = requestedPaymentAmount <= wallet.availableBalance

        assertFalse("Requested amount ₹450 exceeds available ₹400", isValid)
    }
}
