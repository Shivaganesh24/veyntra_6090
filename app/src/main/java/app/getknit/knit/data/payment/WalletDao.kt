package app.getknit.knit.data.payment

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWallet(wallet: WalletEntity)

    @Query("DELETE FROM wallets WHERE walletId = :walletId")
    suspend fun deleteWallet(walletId: String)

    @Query("SELECT * FROM wallets WHERE walletId = :walletId")
    suspend fun getWallet(walletId: String): WalletEntity?

    @Query("SELECT * FROM wallets WHERE walletId = :walletId")
    fun observeWallet(walletId: String): Flow<WalletEntity?>

    @Query("SELECT * FROM wallets LIMIT 1")
    fun observePrimaryWallet(): Flow<WalletEntity?>

    @Query("SELECT * FROM wallets LIMIT 1")
    suspend fun getPrimaryWallet(): WalletEntity?

    /**
     * Atomically reserves [amount] for an outbound offline payment.
     * Fails (returns 0 updated rows) if available balance `(settledBalance - pendingOutbound)` is less than [amount].
     */
    @Query(
        "UPDATE wallets SET pendingOutbound = pendingOutbound + :amount, updatedAt = :now " +
            "WHERE walletId = :walletId AND (settledBalance - pendingOutbound) >= :amount",
    )
    suspend fun reserveOutboundAmount(
        walletId: String,
        amount: Long,
        now: Long = System.currentTimeMillis(),
    ): Int

    /**
     * Releases a previously reserved outbound amount if payment is rejected, canceled, or expired.
     */
    @Query(
        "UPDATE wallets SET pendingOutbound = MAX(0, pendingOutbound - :amount), updatedAt = :now " +
            "WHERE walletId = :walletId",
    )
    suspend fun releaseOutboundAmount(
        walletId: String,
        amount: Long,
        now: Long = System.currentTimeMillis(),
    )

    /**
     * Confirms settlement of an outbound payment on-chain: deducts [amount] from [settledBalance] and [pendingOutbound].
     */
    @Query(
        "UPDATE wallets SET settledBalance = MAX(0, settledBalance - :amount), " +
            "pendingOutbound = MAX(0, pendingOutbound - :amount), updatedAt = :now " +
            "WHERE walletId = :walletId",
    )
    suspend fun confirmOutboundSettled(
        walletId: String,
        amount: Long,
        now: Long = System.currentTimeMillis(),
    )

    /**
     * Receives an unconfirmed inbound offline payment via mesh: increases [pendingInbound].
     * Does NOT increase spendable settled balance!
     */
    @Query(
        "UPDATE wallets SET pendingInbound = pendingInbound + :amount, updatedAt = :now " +
            "WHERE walletId = :walletId",
    )
    suspend fun receiveInboundPending(
        walletId: String,
        amount: Long,
        now: Long = System.currentTimeMillis(),
    )

    /**
     * Confirms on-chain settlement of an inbound payment: decreases [pendingInbound] and increases [settledBalance].
     */
    @Query(
        "UPDATE wallets SET pendingInbound = MAX(0, pendingInbound - :amount), " +
            "settledBalance = settledBalance + :amount, updatedAt = :now " +
            "WHERE walletId = :walletId",
    )
    suspend fun confirmInboundSettled(
        walletId: String,
        amount: Long,
        now: Long = System.currentTimeMillis(),
    )

    /**
     * Rolls back an unconfirmed inbound payment if settlement fails or is rejected on-chain.
     */
    @Query(
        "UPDATE wallets SET pendingInbound = MAX(0, pendingInbound - :amount), updatedAt = :now " +
            "WHERE walletId = :walletId",
    )
    suspend fun rollbackInboundPending(
        walletId: String,
        amount: Long,
        now: Long = System.currentTimeMillis(),
    )

    /**
     * Increments the wallet's monotonic nonce counter and returns the new nonce value.
     */
    @Query("UPDATE wallets SET nextNonce = nextNonce + 1, updatedAt = :now WHERE walletId = :walletId")
    suspend fun incrementNonce(
        walletId: String,
        now: Long = System.currentTimeMillis(),
    )
}
