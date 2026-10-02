package app.getknit.knit.payment

import app.getknit.knit.payment.settlement.MSTBlockchainSettlementService
import app.getknit.knit.payment.settlement.MstBlockchainConfig
import app.getknit.knit.payment.settlement.SettlementResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MstRpcInspectTest {
    @Test
    fun testRpcConfig() {
        assertTrue(MstBlockchainConfig.MST_RPC_URL.isNotBlank())
        assertEquals(91562037L, MstBlockchainConfig.MST_CHAIN_ID)
        assertEquals("0x417404c95724d8E4aF6fb368CA18196FC4bEA143", MstBlockchainConfig.CONTRACT_ADDRESS)
        assertEquals("0x29e55e6691803ab357b6777e6a94ce5b086fbbed", MstBlockchainConfig.RELAYER_ADDRESS)
    }

    @Test
    fun testSettlementServiceInitialization() {
        val service = MSTBlockchainSettlementService()
        assertTrue(service.isConfigured())
        assertEquals("MST Testnet", service.getNetworkName())
        assertEquals(91562037L, service.getChainId())
        assertEquals("0x417404c95724d8E4aF6fb368CA18196FC4bEA143", service.getContractAddress())
    }

    @Test
    fun testTransactionIdToBytes32() {
        val service = MSTBlockchainSettlementService()
        val bytes32 = service.transactionIdToBytes32("tx-123456")
        assertEquals(64, bytes32.length)
    }

    @Test
    fun inspectNetworkAndRpc() = runBlocking {
        val service = MSTBlockchainSettlementService()
        val authOnChain = service.getSettlementAuthorityOnChain()
        assertNotNull(authOnChain)
        assertEquals("0x29e55e6691803ab357b6777e6a94ce5b086fbbed", authOnChain?.lowercase())

        val status = service.verifySettlementStatus("tx-test-nonexistent-123")
        assertTrue(status is SettlementResult.Failed)
    }
}
