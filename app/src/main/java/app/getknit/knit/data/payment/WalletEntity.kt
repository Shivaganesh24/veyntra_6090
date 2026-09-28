package app.getknit.knit.data.payment

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Local wallet state for MST OfflinePay.
 *
 * Balance accounting model:
 * - [settledBalance]: Confirmed on-chain / initial funds (e.g. 50000 = ₹500.00).
 * - [pendingInbound]: Unsettled incoming offline payments (e.g. 10000 = ₹100.00).
 * - [pendingOutbound]: Reserved outbound offline payments (e.g. 10000 = ₹100.00).
 * - [availableBalance]: Spendable balance = `(settledBalance - pendingOutbound)`. Inbound pending funds are NOT spendable.
 * - [totalBalance]: Represented total = `settledBalance + pendingInbound`.
 * - [nextNonce]: Monotonically increasing nonce counter per wallet.
 * - [isDemo]: Clearly distinguishes demo hackathon wallet balances from actual on-chain funds.
 */
@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val walletId: String,
    val publicKey: String,
    val displayName: String,
    val currency: String = "INR",
    val settledBalance: Long,
    val pendingInbound: Long = 0L,
    val pendingOutbound: Long = 0L,
    val nextNonce: Long = 1L,
    val isDemo: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val availableBalance: Long
        get() = (settledBalance - pendingOutbound).coerceAtLeast(0L)

    val totalBalance: Long
        get() = settledBalance + pendingInbound
}
