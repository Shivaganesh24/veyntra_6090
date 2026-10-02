package app.getknit.knit.payment.crypto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val EVM_ADDR_HEX_LEN = 40
private const val EVM_ADDR_TOTAL_LEN = 42
private const val ZERO_EVM_ADDR = "0x0000000000000000000000000000000000000000"

/**
 * EVM Address validation and generation utilities for MST Blockchain.
 */
object EvmAddress {

    /**
     * Strict validation for 20-byte EVM/MST hex addresses:
     * - Must start with "0x"
     * - Must have exactly 40 hex characters after "0x"
     * - Must not be the zero address (0x0000...0000)
     */
    fun isValidEvmAddress(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val clean = address.trim()
        if (!clean.startsWith("0x", ignoreCase = true)) return false
        if (clean.length != EVM_ADDR_TOTAL_LEN) return false
        if (clean.equals(ZERO_EVM_ADDR, ignoreCase = true)) return false

        val hexPart = clean.substring(2)
        return hexPart.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }

    /**
     * Derives a real, valid 20-byte EVM address deterministically from [seed].
     */
    fun deriveEvmAddress(seed: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("mst/evm/address/v1".toByteArray(StandardCharsets.UTF_8))
        val hash = digest.digest(seed)
        val last20Hex = hash.takeLast(20).joinToString("") { "%02x".format(it) }
        val candidate = "0x$last20Hex"
        check(isValidEvmAddress(candidate)) { "Derived EVM address is invalid: $candidate" }
        return candidate
    }
}
