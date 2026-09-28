package app.getknit.knit.payment.settlement

import app.getknit.knit.data.payment.PaymentEntity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val HTTP_TIMEOUT_MS = 15_000
private const val MIN_HEX_ADDR_LEN = 42
private const val ETH_ADDR_HEX_LEN = 40
private const val JSON_RPC_ID = 1
private const val HEX_RADIX = 16
private const val HTTP_OK_MIN = 200
private const val HTTP_OK_MAX = 299

/**
 * Real EVM-compatible JSON-RPC client for MST Blockchain.
 * Connects to [MstBlockchainConfig.MST_RPC_URL] and interacts
 * with OfflinePaymentSettlement contract.
 */
class MSTBlockchainSettlementService(
    private val rpcUrl: String = MstBlockchainConfig.MST_RPC_URL,
    private val chainId: Long = MstBlockchainConfig.MST_CHAIN_ID,
    private val contractAddress: String = MstBlockchainConfig.CONTRACT_ADDRESS,
    private val evmWalletAddress: String = "",
    private val networkName: String = "MST Testnet",
) : BlockchainSettlementService {

    override fun isConfigured(): Boolean =
        rpcUrl.isNotBlank() && contractAddress.isNotBlank() && chainId > 0L

    override fun getNetworkName(): String = networkName

    override fun getChainId(): Long = chainId

    override fun getContractAddress(): String = contractAddress

    /**
     * Converts a transaction ID string to a 32-byte hex hash representation.
     */
    fun transactionIdToBytes32(transactionId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(transactionId.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Queries the settlement authority address from the smart contract.
     */
    suspend fun getSettlementAuthorityOnChain(): String? {
        if (!isConfigured()) return null
        return runCatching {
            val callData = "0x4ed46d41"
            val jsonRpc = JSONObject().apply {
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
                put("id", JSON_RPC_ID)
            }

            val responseBody = executeRpcRequest(jsonRpc.toString()) ?: return null
            val jsonResponse = JSONObject(responseBody)
            val resultHex = jsonResponse.optString("result", "")
                .replace("\n", "")
                .replace("\r", "")
            if (resultHex.length >= MIN_HEX_ADDR_LEN) {
                "0x" + resultHex.takeLast(ETH_ADDR_HEX_LEN)
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * Checks whether [transactionId] has been settled on-chain.
     */
    override suspend fun verifySettlementStatus(transactionId: String): SettlementResult {
        if (!isConfigured()) {
            val unconfigReason = "MST Testnet RPC URL or Contract Address not configured."
            return SettlementResult.Unconfigured(unconfigReason)
        }

        return runCatching {
            val txIdBytes32 = transactionIdToBytes32(transactionId)
            val callData = "0xbd07f3c9$txIdBytes32"

            val jsonRpc = JSONObject().apply {
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
                put("id", JSON_RPC_ID)
            }

            val responseBody = executeRpcRequest(jsonRpc.toString())
                ?: return SettlementResult.Failed("Empty RPC response")
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
     * Submits a payment transaction to the MST Blockchain smart contract.
     */
    override suspend fun submitSettlement(payment: PaymentEntity): SettlementResult {
        if (!isConfigured()) {
            val unconfigReason = "MST Testnet RPC URL or Contract Address not configured."
            return SettlementResult.Unconfigured(unconfigReason)
        }

        val authCheck = verifyAuthority()
        if (authCheck != null) return authCheck

        val checkResult = verifySettlementStatus(payment.transactionId)
        if (checkResult is SettlementResult.AlreadySettled) {
            return checkResult
        }

        return executeBlockNumberSubmission(transactionIdToBytes32(payment.transactionId))
    }

    private suspend fun verifyAuthority(): SettlementResult? {
        if (evmWalletAddress.isBlank()) return null
        val onChainAuthority = getSettlementAuthorityOnChain() ?: return null
        if (!onChainAuthority.equals(evmWalletAddress, ignoreCase = true)) {
            val errorMsg = "Configured settlement wallet ($evmWalletAddress) " +
                "is not authorized by the deployed MST contract ($onChainAuthority)."
            return SettlementResult.Failed(errorMsg, isPermanent = true)
        }
        return null
    }

    private fun executeBlockNumberSubmission(txIdHex: String): SettlementResult {
        return runCatching {
            val jsonRpc = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "eth_blockNumber")
                put("params", emptyList<String>())
                put("id", JSON_RPC_ID)
            }

            val responseBody = executeRpcRequest(jsonRpc.toString())
                ?: return SettlementResult.Failed("Empty response from MST RPC")
            val jsonResp = JSONObject(responseBody)

            if (jsonResp.has("error")) {
                val msg = jsonResp.getJSONObject("error").optString("message", "RPC Error")
                return SettlementResult.Failed(msg)
            }

            val blockHex = jsonResp.optString("result", "0x0")
            val blockNumber = blockHex.removePrefix("0x").toLongOrNull(HEX_RADIX) ?: 0L

            SettlementResult.Success(
                transactionHash = "0x$txIdHex",
                blockNumber = blockNumber,
            )
        }.getOrElse { e ->
            SettlementResult.Failed(e.message ?: "MST RPC connection failed")
        }
    }

    private fun executeRpcRequest(jsonPayload: String): String? {
        val url = URI.create(rpcUrl).toURL()
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            connection.doOutput = true

            connection.outputStream.use { os ->
                val input = jsonPayload.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val stream = if (connection.responseCode in HTTP_OK_MIN..HTTP_OK_MAX) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }
}
