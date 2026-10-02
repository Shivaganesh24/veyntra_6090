package app.getknit.knit.payment.settlement

import android.util.Log
import app.getknit.knit.data.payment.PaymentEntity
import app.getknit.knit.payment.crypto.EvmAddress
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val HTTP_TIMEOUT_MS = 15_000
private const val MIN_HEX_ADDR_LEN = 42
private const val ETH_ADDR_HEX_LEN = 40
private const val HEX_PAD_LEN = 64
private const val JSON_RPC_ID = 1
private const val HEX_RADIX = 16
private const val HTTP_OK_MIN = 200
private const val HTTP_OK_MAX = 299

/**
 * Real EVM-compatible JSON-RPC client for MST Blockchain.
 * Connects to [MstBlockchainConfig.MST_RPC_URL] and interacts
 * with MSTPaymentSettlement contract.
 */
class MSTBlockchainSettlementService(
    private val rpcUrl: String = MstBlockchainConfig.MST_RPC_URL,
    private val chainId: Long = MstBlockchainConfig.MST_CHAIN_ID,
    private val contractAddress: String = MstBlockchainConfig.CONTRACT_ADDRESS,
    private val evmWalletAddress: String = MstBlockchainConfig.RELAYER_ADDRESS,
    private val relayerUrl: String = MstBlockchainConfig.RELAYER_URL,
    private val networkName: String = "MST Testnet",
) : BlockchainSettlementService {
    override fun isConfigured(): Boolean = rpcUrl.isNotBlank() && contractAddress.isNotBlank() && chainId > 0L

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
            val jsonRpc =
                """{"jsonrpc":"2.0","method":"eth_call","params":[{"to":"$contractAddress","data":"$callData"},"latest"],"id":$JSON_RPC_ID}"""

            val responseBody = executeRpcRequest(rpcUrl, jsonRpc) ?: return null
            val resultHex = extractJsonResult(responseBody) ?: return null
            val cleanHex = resultHex.replace("\n", "").replace("\r", "").trim()

            if (cleanHex.length >= MIN_HEX_ADDR_LEN) {
                "0x" + cleanHex.takeLast(ETH_ADDR_HEX_LEN)
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

            val jsonRpc =
                """{"jsonrpc":"2.0","method":"eth_call","params":[{"to":"$contractAddress","data":"$callData"},"latest"],"id":$JSON_RPC_ID}"""

            val responseBody =
                executeRpcRequest(rpcUrl, jsonRpc)
                    ?: return SettlementResult.Failed("Empty RPC response")

            val errorMsg = extractJsonError(responseBody)
            if (errorMsg != null) {
                return SettlementResult.Failed(errorMsg)
            }

            val resultHex = extractJsonResult(responseBody) ?: "0x0"
            val isSettledOnChain = resultHex.trim().endsWith("1")

            if (isSettledOnChain) {
                SettlementResult.AlreadySettled("0x$txIdBytes32")
            } else {
                SettlementResult.Failed("Not settled on-chain", isPermanent = false)
            }
        }.getOrElse { e ->
            val msg = e.message ?: "RPC network error"
            SettlementResult.Failed(msg)
        }
    }

    /**
     * Submits a payment transaction for settlement on the MST Blockchain testnet.
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

        val receiverEvmAddress = payment.receiverEvmAddress
        if (!EvmAddress.isValidEvmAddress(receiverEvmAddress)) {
            return SettlementResult.Failed(
                "Invalid EVM receiver address: '$receiverEvmAddress'. Settlement requires a valid 20-byte EVM address beginning with 0x.",
                isPermanent = true,
            )
        }

        val txIdBytes32 = transactionIdToBytes32(payment.transactionId)
        val callData = encodeSettlePaymentData(txIdBytes32, receiverEvmAddress, payment.amount, payment.nonce)

        val submissionTxHash =
            if (relayerUrl.isNotBlank()) {
                submitViaRelayerService(payment, txIdBytes32, callData, receiverEvmAddress)
            } else {
                submitViaRpc(callData)
            }

        if (submissionTxHash.isNullOrBlank()) {
            return SettlementResult.Failed(
                "Transaction submission failed: Relayer unavailable or transaction rejected",
                isPermanent = false,
            )
        }

        val receiptResult = waitForTransactionReceipt(submissionTxHash)
            ?: return SettlementResult.Failed(
                "Transaction $submissionTxHash unconfirmed on MST Blockchain",
                isPermanent = false,
            )

        val blockNum = receiptResult.blockNumber
            ?: return SettlementResult.Failed(
                "Transaction $submissionTxHash missing block number in receipt",
                isPermanent = false,
            )

        return SettlementResult.Success(
            transactionHash = submissionTxHash,
            blockNumber = blockNum,
        )
    }

    private fun padHex64(hex: String): String = hex.removePrefix("0x").lowercase().padStart(HEX_PAD_LEN, '0')

    private fun encodeSettlePaymentData(
        txIdBytes32: String,
        receiverAddrHex: String,
        amount: Long,
        nonce: Long,
    ): String {
        val selector = "21c28893"
        val p1 = padHex64(txIdBytes32)
        val p2 = padHex64(receiverAddrHex)
        val p3 = padHex64(amount.toString(HEX_RADIX))
        val p4 = padHex64(nonce.toString(HEX_RADIX))
        return "0x$selector$p1$p2$p3$p4"
    }

    private fun submitViaRelayerService(
        payment: PaymentEntity,
        txIdBytes32: String,
        callData: String,
        receiverEvmAddress: String,
    ): String? {
        return runCatching {
            Log.i("OFFPAY", "[OFFPAY][SETTLEMENT] calling relayer url=$relayerUrl")
            val jsonPayload =
                """{
                    "txIdBytes32":"$txIdBytes32",
                    "receiverAddress":"$receiverEvmAddress",
                    "amount":${payment.amount},
                    "nonce":${payment.nonce},
                    "transactionId":"${payment.transactionId}",
                    "senderWalletId":"${payment.senderWalletId}",
                    "receiverWalletId":"${payment.receiverWalletId}",
                    "signature":"${payment.signature}",
                    "contractAddress":"$contractAddress",
                    "callData":"$callData"
                }""".trimIndent().replace("\n", "").replace(" ", "")

            val responseStr = executeRpcRequest(relayerUrl, jsonPayload)
            Log.i("OFFPAY", "[OFFPAY][SETTLEMENT] relayer response = $responseStr")
            if (responseStr.isNullOrBlank()) return null

            val txHash = extractJsonField(responseStr, "txHash")
                ?: extractJsonField(responseStr, "transactionHash")
                ?: extractJsonField(responseStr, "result")
            if (!txHash.isNullOrBlank() && txHash.startsWith("0x")) txHash else null
        }.getOrElse { e ->
            Log.e("OFFPAY", "[OFFPAY][SETTLEMENT] relayer request failed: ${e.message}", e)
            null
        }
    }

    private fun submitViaRpc(callData: String): String? {
        return runCatching {
            val jsonRpc =
                """{"jsonrpc":"2.0","method":"eth_sendRawTransaction","params":["$callData"],"id":$JSON_RPC_ID}"""
            val responseBody = executeRpcRequest(rpcUrl, jsonRpc) ?: return null

            val errorMsg = extractJsonError(responseBody)
            if (errorMsg != null) {
                return null
            }

            val result = extractJsonResult(responseBody)
            if (!result.isNullOrBlank() && result.startsWith("0x") && result.length > 2) result else null
        }.getOrNull()
    }

    private fun waitForTransactionReceipt(txHash: String): ReceiptInfo? {
        return runCatching {
            val jsonRpc =
                """{"jsonrpc":"2.0","method":"eth_getTransactionReceipt","params":["$txHash"],"id":$JSON_RPC_ID}"""
            val responseBody = executeRpcRequest(rpcUrl, jsonRpc) ?: return null

            if (responseBody.contains("\"status\":\"0x1\"") || responseBody.contains("\"status\":\"1\"")) {
                val blockHex = extractJsonField(responseBody, "blockNumber") ?: return null
                val blockNum = blockHex.removePrefix("0x").toLongOrNull(HEX_RADIX) ?: return null
                ReceiptInfo(blockNumber = blockNum)
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun verifyAuthority(): SettlementResult? {
        if (evmWalletAddress.isBlank()) return null
        val onChainAuthority = getSettlementAuthorityOnChain() ?: return null
        if (!onChainAuthority.equals(evmWalletAddress, ignoreCase = true)) {
            val errorMsg =
                "Configured settlement wallet ($evmWalletAddress) " +
                    "is not authorized by the deployed MST contract ($onChainAuthority)."
            return SettlementResult.Failed(errorMsg, isPermanent = true)
        }
        return null
    }

    private fun executeRpcRequest(
        endpointUrl: String,
        jsonPayload: String,
    ): String? {
        return runCatching {
            val url = URI.create(endpointUrl).toURL()
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("User-Agent", "Knit/1.0 (Android)")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = HTTP_TIMEOUT_MS
                connection.readTimeout = HTTP_TIMEOUT_MS
                connection.doOutput = true

                val input = jsonPayload.toByteArray(StandardCharsets.UTF_8)
                connection.setRequestProperty("Content-Length", input.size.toString())

                connection.outputStream.use { os ->
                    os.write(input, 0, input.size)
                    os.flush()
                }

                val stream =
                    if (connection.responseCode in HTTP_OK_MIN..HTTP_OK_MAX) {
                        connection.inputStream
                    } else {
                        connection.errorStream ?: connection.inputStream
                    }

                if (stream == null) return null

                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { e ->
            Log.e("OFFPAY", "[OFFPAY][SETTLEMENT] executeRpcRequest exception endpoint=$endpointUrl msg=${e.message}", e)
            null
        }
    }

    private fun extractJsonResult(json: String): String? {
        val key = "\"result\":"
        val idx = json.indexOf(key)
        if (idx < 0) return null
        val start = idx + key.length
        val end = json.indexOf(",", start).let { if (it < 0) json.indexOf("}", start) else it }
        if (end < 0) return null
        return json.substring(start, end).trim().removeSurrounding("\"")
    }

    private fun extractJsonError(json: String): String? {
        if (!json.contains("\"error\"")) return null
        val key = "\"message\":"
        val idx = json.indexOf(key)
        if (idx < 0) return "RPC Error"
        val start = idx + key.length
        val end = json.indexOf("\"", start + 2)
        if (end < 0) return "RPC Error"
        return json.substring(start, end).trim().removeSurrounding("\"")
    }

    private fun extractJsonField(json: String, fieldName: String): String? {
        val key = "\"$fieldName\":"
        val idx = json.indexOf(key)
        if (idx < 0) return null
        val start = idx + key.length
        val end = json.indexOf(",", start).let { if (it < 0) json.indexOf("}", start) else it }
        if (end < 0) return null
        return json.substring(start, end).trim().removeSurrounding("\"")
    }

    private data class ReceiptInfo(
        val blockNumber: Long?,
    )
}
