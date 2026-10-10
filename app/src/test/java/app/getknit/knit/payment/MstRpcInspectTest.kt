package app.getknit.knit.payment

import app.getknit.knit.mesh.crypto.MessageCrypto
import app.getknit.knit.mesh.crypto.PublicKeyBundle
import app.getknit.knit.mesh.crypto.TinkInit
import app.getknit.knit.payment.crypto.EvmAddress
import app.getknit.knit.payment.crypto.PaymentSigner
import app.getknit.knit.payment.protocol.PaymentPayload
import app.getknit.knit.payment.settlement.MSTBlockchainSettlementService
import app.getknit.knit.payment.settlement.MstBlockchainConfig
import app.getknit.knit.payment.settlement.SettlementResult
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
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
    fun inspectNetworkAndRpc() =
        runBlocking {
            val service = MSTBlockchainSettlementService()
            val authOnChain = service.getSettlementAuthorityOnChain()
            assertNotNull(authOnChain)
            assertEquals("0x29e55e6691803ab357b6777e6a94ce5b086fbbed", authOnChain?.lowercase())

            val status = service.verifySettlementStatus("tx-test-nonexistent-123")
            assertTrue(status is SettlementResult.Failed)
        }

    @Test
    fun generateLivePaymentAndMstTokenId() =
        runBlocking {
            TinkInit.ensure()
            val keyTemplate = KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW")
            val hybridHandle = KeysetHandle.generateNew(keyTemplate)
            val sigHandle = KeysetHandle.generateNew(KeyTemplates.get("ED25519_RAW"))
            val bundle = PublicKeyBundle.fromPrivate(hybridHandle, sigHandle)
            val crypto = MessageCrypto(hybridHandle, sigHandle)

            val receiverHybrid = KeysetHandle.generateNew(keyTemplate)
            val receiverSig = KeysetHandle.generateNew(KeyTemplates.get("ED25519_RAW"))
            val receiverBundle = PublicKeyBundle.fromPrivate(receiverHybrid, receiverSig)

            val now = System.currentTimeMillis()
            val txId = "tx-$now-${(1000..9999).random()}"
            val receiverEvm = EvmAddress.deriveEvmAddress(receiverBundle.encoded.toByteArray(Charsets.UTF_8))

            val payload =
                PaymentPayload(
                    transactionId = txId,
                    senderPublicKey = bundle.encoded,
                    receiverPublicKey = receiverBundle.encoded,
                    senderWalletId = "wallet-sender-${bundle.encoded.take(8)}",
                    receiverWalletId = "wallet-receiver-${receiverBundle.encoded.take(8)}",
                    amount = 50000L, // ₹500.00
                    currency = "INR",
                    timestamp = now,
                    nonce = 1L,
                    createdOffline = true,
                    expiryTime = now + (24 * 60 * 60 * 1000L),
                    receiverEvmAddress = receiverEvm,
                )

            val signature = PaymentSigner.sign(payload, crypto::signRaw)
            val signedPayload = payload.copy(signature = signature)

            val settlementService = MSTBlockchainSettlementService()
            val bytes32TokenId = settlementService.transactionIdToBytes32(txId)

            println("=== MST OFFLINEPAY GENERATED TRANSACTION ===")
            println("Transaction ID: $txId")
            println("MST Bytes32 Token ID: 0x$bytes32TokenId")
            println("Sender Wallet ID: ${signedPayload.senderWalletId}")
            println("Receiver Wallet ID: ${signedPayload.receiverWalletId}")
            println("Receiver EVM Address: $receiverEvm")
            println("Amount: ₹${signedPayload.amount / 100.0} (50000 paise)")
            println("Currency: ${signedPayload.currency}")
            println("Signature: ${signedPayload.signature}")
            println("============================================")

            assertNotNull(txId)
            assertEquals(64, bytes32TokenId.length)
            assertTrue(EvmAddress.isValidEvmAddress(receiverEvm))
        }
}
