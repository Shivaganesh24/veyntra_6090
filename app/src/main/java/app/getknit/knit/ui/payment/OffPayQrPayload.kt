package app.getknit.knit.ui.payment

import android.util.Log

/**
 * Encodes and decodes QR payment requests for MST OfflinePay.
 */
object OffPayQrPayload {
    private const val TAG = "OffPayQrPayload"
    private const val MIN_WALLET_ID_LEN = 8

    data class ParsedRequest(
        val walletId: String,
        val publicKey: String,
        val amount: Long, // in paise
        val requestId: String?,
        val evmAddress: String = "",
    )

    fun encode(
        walletId: String,
        publicKey: String,
        amount: Long = 0L,
        evmAddress: String = "",
    ): String {
        val reqId = "req-${System.currentTimeMillis()}-${(1000..9999).random()}"
        return "offpay:v1?w=$walletId&pk=$publicKey&evm=$evmAddress&amt=$amount&req=$reqId"
    }

    @Suppress("CyclomaticComplexMethod")
    fun decode(input: String): ParsedRequest? {
        val str = input.trim()
        if (str.isBlank()) return null

        return runCatching {
            if (str.startsWith("offpay:v1?")) {
                val query = str.substringAfter("offpay:v1?")
                val params =
                    query.split("&").associate { param ->
                        val parts = param.split("=", limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                    }
                val walletId = params["w"]
                if (walletId.isNullOrBlank()) return null
                val publicKey = params["pk"]?.takeIf { it.isNotBlank() } ?: walletId
                val evmAddress = params["evm"].orEmpty()
                val amount = params["amt"]?.toLongOrNull() ?: 0L
                val reqId = params["req"]
                ParsedRequest(
                    walletId = walletId,
                    publicKey = publicKey,
                    amount = amount,
                    requestId = reqId,
                    evmAddress = evmAddress,
                )
            } else if (str.contains("|")) {
                val parts = str.split("|")
                if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                    val wId = parts[0]
                    val pk = if (parts.size >= 2 && parts[1].isNotBlank()) parts[1] else wId
                    val evm = if (parts.size >= 3 && parts[2].startsWith("0x")) parts[2] else ""
                    ParsedRequest(walletId = wId, publicKey = pk, amount = 0L, requestId = null, evmAddress = evm)
                } else {
                    null
                }
            } else if (str.startsWith("wallet-") || str.length >= MIN_WALLET_ID_LEN) {
                ParsedRequest(walletId = str, publicKey = str, amount = 0L, requestId = null, evmAddress = "")
            } else {
                null
            }
        }.getOrElse { e ->
            Log.e(TAG, "Failed to decode QR payload: $input", e)
            null
        }
    }
}
