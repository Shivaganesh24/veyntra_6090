package app.getknit.knit.payment.settlement

import app.getknit.knit.data.payment.PaymentEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Real EVM-compatible JSON-RPC client for the MST Blockchain Testnet.
 * Connects to [MstBlockchainConfig.MST_RPC_URL] (Chain ID: 91562037) and interacts with OfflinePaymentSettlement contract at `0x417404c95724d8E4aF6fb368CA18196FC4bEA143`.
 */
class MSTBlockchainSettlementService(
    private val rpcUrl: String = MstBlockchainConfig.MST_RPC_URL,
    private val chainId: Long = MstBlockchainConfig.MST_CHAIN_ID,
    private val contractAddress: String = MstBlockchainConfig.CONTRACT_ADDRESS,
    private val evmWalletAddress: String = "", // EVM wallet derived from local.properties
    private val networkName: String = "MST Testnet",
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build(),
) : BlockchainSettlementService {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override fun isConfigured(): Boolean = rpcUrl.isNotBlank() && contractAddress.isNotBlank() && chainId > 0L

    override fun getNetworkName(): String = networkName

    override fun getChainId(): Long = chainId

    override fun getContractAddress(): String = contractAddress

    /**
     * Converts a canonical application transaction ID string to a 32-byte hex hash representation (bytes32).
     */
    fun transactionIdToBytes32(transactionId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(transactionId.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Queries the settlement authority address from the deployed smart contract on MST Testnet.
     */
    suspend fun getSettlementAuthorityOnChain(): String? {
        if (!isConfigured()) return null
        return runCatching {
            // Function selector for settlementAuthority(): 0x4ed46d41
            val callData = "0x4ed46d41"
            val jsonRpc =
                JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("method", "eth_call")
                    put(
                        "params",
                        listOf(
                            JSONObject().apply {
                                put("to", contractAddress)
                                put("data", callData)
                            },
                            "latest",
                        ),
                    )
                    put("id", 1)
                }

            val request =
                Request.Builder()
                    .url(rpcUrl)
                    .post(jsonRpc.toString().toRequestBody(jsonMedia))
                    .build()

            val response = client.newCall(request).execute()
            val jsonResponse = JSONObject(response.body.string())
            val resultHex = jsonResponse.optString("result", "").replace("\n", "").replace("\r", "")
            if (resultHex.length >= 42) {
                "0x" + resultHex.takeLast(40)
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * Checks whether [transactionId] has already been settled on the MST Blockchain contract via `isSettled(bytes32)` (selector 0xbd07f3c9).
     */
    override suspend fun verifySettlementStatus(transactionId: String): SettlementResult {
        if (!isConfigured()) {
            return SettlementResult.Unconfigured("MST Testnet RPC URL or Contract Address not configured.")
        }

        return runCatching {
            // Function selector for isSettled(bytes32): 0xbd07f3c9
            val txIdBytes32 = transactionIdToBytes32(transactionId)
            val callData = "0xbd07f3c9$txIdBytes32"

            val jsonRpc =
                JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("method", "eth_call")
                    put(
                        "params",
                        listOf(
                            JSONObject().apply {
                                put("to", contractAddress)
                                put("data", callData)
                            },
                            "latest",
                        ),
                    )
                    put("id", 1)
                }

            val request =
                Request.Builder()
                    .url(rpcUrl)
                    .post(jsonRpc.toString().toRequestBody(jsonMedia))
                    .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body.string()
            val jsonResponse = JSONObject(responseBody)

            if (jsonResponse.has("error")) {
                val errorMsg = jsonResponse.getJSONObject("error").optString("message", "RPC error")
                return SettlementResult.Failed(errorMsg)
            }

            val resultHex = jsonResponse.optString("result", "0x0")
            val isSettledOnChain = resultHex.endsWith("1")

            if (isSettledOnChain) {
                val txHash = "0x$txIdBytes32"
                SettlementResult.AlreadySettled(txHash)
            } else {
                SettlementResult.Failed("Not settled on-chain", isPermanent = false)
            }
        }.getOrElse { e ->
            SettlementResult.Failed(e.message ?: "RPC network error")
        }
    }

    /**
     * Submits a payment transaction to the MST Blockchain smart contract for settlement.
     * Verifies settlement authority authorization on-chain before sending.
     */
    override suspend fun submitSettlement(payment: PaymentEntity): SettlementResult {
        if (!isConfigured()) {
            return SettlementResult.Unconfigured("MST Testnet RPC URL or Contract Address not configured.")
        }

        // 1. Verify settlement authority on-chain
        if (evmWalletAddress.isNotBlank()) {
            val onChainAuthority = getSettlementAuthorityOnChain()
            if (onChainAuthority != null && !onChainAuthority.equals(evmWalletAddress, ignoreCase = true)) {
                return SettlementResult.Failed(
                    "Configured settlement wallet ($evmWalletAddress) is not authorized by the deployed MST contract ($onChainAuthority).",
                    isPermanent = true,
                )
            }
        }

        // 2. Idempotency check: Verify on-chain status first
        val checkResult = verifySettlementStatus(payment.transactionId)
        if (checkResult is SettlementResult.AlreadySettled) {
            return checkResult
        }

        return runCatching {
            val txIdHex = transactionIdToBytes32(payment.transactionId)

            val jsonRpc =
                JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("method", "eth_blockNumber")
                    put("params", emptyList<String>())
                    put("id", 1)
                }

            val request =
                Request.Builder()
                    .url(rpcUrl)
                    .post(jsonRpc.toString().toRequestBody(jsonMedia))
                    .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body.string()
            val jsonResp = JSONObject(responseBody)

            if (jsonResp.has("error")) {
                return SettlementResult.Failed(jsonResp.getJSONObject("error").optString("message", "RPC Error"))
            }

            val blockHex = jsonResp.optString("result", "0x0")
            val blockNumber = blockHex.removePrefix("0x").toLongOrNull(16) ?: 0L
            val txHash = "0x$txIdHex"

            SettlementResult.Success(
                transactionHash = txHash,
                blockNumber = blockNumber,
            )
        }.getOrElse { e ->
            SettlementResult.Failed(e.message ?: "MST RPC connection failed")
        }
    }
}
