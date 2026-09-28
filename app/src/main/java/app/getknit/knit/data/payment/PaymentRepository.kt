package app.getknit.knit.data.payment

import android.util.Log
import app.getknit.knit.data.crypto.IdentityKeyStore
import app.getknit.knit.mesh.protocol.RelayEnvelope
import app.getknit.knit.mesh.protocol.WireEnvelope
import app.getknit.knit.payment.crypto.PaymentSigner
import app.getknit.knit.payment.protocol.PaymentPayload
import app.getknit.knit.payment.protocol.PaymentProtocolValidator
import app.getknit.knit.payment.protocol.PaymentValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Observable events emitted when payment states change.
 */
sealed interface PaymentEvent {
    data class Created(val payment: PaymentEntity) : PaymentEvent
    data class Sent(val payment: PaymentEntity) : PaymentEvent
    data class Received(val payment: PaymentEntity) : PaymentEvent
    data class Verified(val payment: PaymentEntity) : PaymentEvent
    data class Settled(val payment: PaymentEntity, val txHash: String) : PaymentEvent
    data class Rejected(val transactionId: String, val reason: PaymentValidationResult) : PaymentEvent
    data class Duplicate(val transactionId: String) : PaymentEvent
}

class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val walletDao: WalletDao,
    private val identityKeyStore: IdentityKeyStore,
) {
    private val _events = MutableSharedFlow<PaymentEvent>(extraBufferCapacity = 64)
    val events: Flow<PaymentEvent> = _events.asSharedFlow()

    suspend fun getPrimaryWallet(): WalletEntity? = walletDao.getPrimaryWallet()

    fun observePrimaryWallet(): Flow<WalletEntity?> = walletDao.observePrimaryWallet()

    fun observeAllPayments(): Flow<List<PaymentEntity>> = paymentDao.observeAllPayments()

    suspend fun getAllPayments(): List<PaymentEntity> = paymentDao.getAllPayments()

    suspend fun getPaymentById(transactionId: String): PaymentEntity? = paymentDao.getPaymentById(transactionId)

    suspend fun getPendingSettlementPayments(): List<PaymentEntity> = paymentDao.getPendingSettlementPayments()

    /**
     * Initializes a primary demo wallet if none exists yet.
     */
    suspend fun initializeDemoWallet(
        walletId: String,
        displayName: String,
        publicKey: String? = null,
        initialBalance: Long = 50000L, // ₹500.00
    ): WalletEntity {
        val existing = walletDao.getPrimaryWallet()
        if (existing != null) return existing

        val walletPublicKey = publicKey ?: runCatching { identityKeyStore.keys().publicBundle.encoded }.getOrElse { "fallback-pub-key" }
        val wallet =
            WalletEntity(
                walletId = walletId,
                publicKey = walletPublicKey,
                displayName = displayName,
                currency = "INR",
                settledBalance = initialBalance,
                pendingInbound = 0L,
                pendingOutbound = 0L,
                nextNonce = 1L,
                isDemo = true,
            )
        walletDao.upsertWallet(wallet)
        return wallet
    }

    suspend fun resetDemoWallet(
        walletId: String,
        displayName: String,
        publicKey: String,
        initialBalance: Long,
    ): WalletEntity {
        val existingPrimary = walletDao.getPrimaryWallet()
        val targetId = existingPrimary?.walletId ?: walletId
        val wallet =
            WalletEntity(
                walletId = targetId,
                publicKey = publicKey,
                displayName = displayName,
                currency = "INR",
                settledBalance = initialBalance,
                pendingInbound = 0L,
                pendingOutbound = 0L,
                nextNonce = existingPrimary?.nextNonce ?: 1L,
                isDemo = true,
            )
        walletDao.upsertWallet(wallet)
        return wallet
    }

    /**
     * Creates and signs an outbound payment transaction, reserving funds atomically and incrementing nonce.
     */
    suspend fun createOutboundPayment(
        receiverWalletId: String,
        receiverPublicKey: String,
        amount: Long,
        signRaw: (ByteArray) -> ByteArray,
    ): Pair<PaymentPayload, PaymentEntity>? {
        val wallet = walletDao.getPrimaryWallet() ?: return null

        // 1. Atomic reservation check & update
        val reservedRows = walletDao.reserveOutboundAmount(wallet.walletId, amount)
        if (reservedRows <= 0) {
            _events.tryEmit(PaymentEvent.Rejected("insufficient-funds", PaymentValidationResult.INVALID_AMOUNT))
            return null
        }

        // 2. Fetch updated wallet state for nonce
        val currentWallet = walletDao.getWallet(wallet.walletId) ?: wallet
        val currentNonce = currentWallet.nextNonce
        walletDao.incrementNonce(wallet.walletId)

        // 3. Build PaymentPayload
        val now = System.currentTimeMillis()
        val txId = "tx-${now}-${(1000..9999).random()}"

        val payload =
            PaymentPayload(
                transactionId = txId,
                senderPublicKey = currentWallet.publicKey,
                receiverPublicKey = receiverPublicKey,
                senderWalletId = currentWallet.walletId,
                receiverWalletId = receiverWalletId,
                amount = amount,
                currency = "INR",
                timestamp = now,
                nonce = currentNonce,
                createdOffline = true,
                expiryTime = now + (24 * 60 * 60 * 1000L), // 24h
            )

        // 4. Cryptographically sign payload
        val sig = PaymentSigner.sign(payload, signRaw)

        // 5. Save PaymentEntity locally in OFFLINE_SENT state
        val entity =
            PaymentEntity(
                transactionId = txId,
                senderPublicKey = currentWallet.publicKey,
                receiverPublicKey = receiverPublicKey,
                senderWalletId = currentWallet.walletId,
                receiverWalletId = receiverWalletId,
                amount = amount,
                currency = "INR",
                timestamp = now,
                nonce = currentNonce,
                status = "OFFLINE_SENT",
                signature = sig,
                createdOffline = true,
                settlementStatus = "PENDING",
                expiryTime = payload.expiryTime,
            )
        paymentDao.insertPayment(entity)

        _events.tryEmit(PaymentEvent.Created(entity))
        _events.tryEmit(PaymentEvent.Sent(entity))

        return payload to entity
    }

    /**
     * Processes an incoming payment frame received from the mesh network.
     * Offline received payments increase [pendingInbound] ONLY and are NOT spendable until settled.
     */
    @Suppress("UnusedParameter")
    suspend fun processInboundPayment(
        payload: PaymentPayload,
        signature: String,
        env: RelayEnvelope,
        wire: WireEnvelope,
        fromNodeId: String,
    ): PaymentValidationResult {
        val myWallet = walletDao.getPrimaryWallet()

        // 1. Receiver Identity Check
        val myWalletId = myWallet?.walletId ?: identityKeyStore.keys().publicBundle.encoded
        val myPublicKey = myWallet?.publicKey ?: identityKeyStore.keys().publicBundle.encoded

        if (payload.receiverWalletId != myWalletId && payload.receiverPublicKey != myPublicKey) {
            return PaymentValidationResult.VALID
        }

        // 2. Anti-replay & Duplicate Check
        val existing = paymentDao.getPaymentById(payload.transactionId)
        if (existing != null) {
            _events.tryEmit(PaymentEvent.Duplicate(payload.transactionId))
            return PaymentValidationResult.DUPLICATE_TRANSACTION
        }

        // 3. Protocol & Cryptographic Signature Validation
        val validationResult = PaymentProtocolValidator.validate(payload, signature, paymentDao)
        if (validationResult != PaymentValidationResult.VALID) {
            _events.tryEmit(PaymentEvent.Rejected(payload.transactionId, validationResult))
            return validationResult
        }

        // 4. Record Payment as PENDING_SETTLEMENT
        Log.d("PaymentRepository", "Processing inbound payment ${env.id} from node $fromNodeId")
        val entity =
            PaymentEntity(
                transactionId = payload.transactionId,
                senderPublicKey = payload.senderPublicKey,
                receiverPublicKey = payload.receiverPublicKey,
                senderWalletId = payload.senderWalletId,
                receiverWalletId = payload.receiverWalletId,
                amount = payload.amount,
                currency = payload.currency,
                timestamp = payload.timestamp,
                nonce = payload.nonce,
                previousTransactionReference = payload.previousTransactionReference,
                status = "PENDING_SETTLEMENT",
                signature = signature,
                createdOffline = payload.createdOffline,
                settlementStatus = "PENDING",
                hopCount = wire.hops,
                expiryTime = payload.expiryTime,
            )
        paymentDao.insertPayment(entity)

        // 5. Update local receiver pending inbound balance (NOT settled/spendable balance)
        if (myWallet != null) {
            walletDao.receiveInboundPending(myWallet.walletId, payload.amount)
        }

        _events.tryEmit(PaymentEvent.Received(entity))
        _events.tryEmit(PaymentEvent.Verified(entity))

        return PaymentValidationResult.VALID
    }

    /**
     * Releases an outbound reservation if a payment expires or is rejected.
     */
    suspend fun releaseOutboundReservation(transactionId: String, amount: Long, reason: PaymentValidationResult) {
        val payment = paymentDao.getPaymentById(transactionId) ?: return
        if (payment.status == "SETTLED" || payment.status == "REJECTED" || payment.status == "EXPIRED") return

        paymentDao.updateStatus(transactionId, "REJECTED", "FAILED", txHash = null)
        walletDao.releaseOutboundAmount(payment.senderWalletId, amount)
        _events.tryEmit(PaymentEvent.Rejected(transactionId, reason))
    }

    /**
     * Confirms on-chain settlement for an outbound payment (Step 5 callback).
     */
    suspend fun confirmOutboundSettlement(transactionId: String, amount: Long, txHash: String) {
        val payment = paymentDao.getPaymentById(transactionId) ?: return
        if (payment.status == "SETTLED") return // Idempotent

        paymentDao.updateStatus(transactionId, "SETTLED", "CONFIRMED", txHash = txHash)
        walletDao.confirmOutboundSettled(payment.senderWalletId, amount)

        val updated = paymentDao.getPaymentById(transactionId) ?: payment
        _events.tryEmit(PaymentEvent.Settled(updated, txHash))
    }

    /**
     * Confirms on-chain settlement for an inbound payment (Step 5 callback).
     * Shifts funds from pendingInbound to settledBalance (making them spendable).
     */
    suspend fun confirmInboundSettlement(transactionId: String, amount: Long, txHash: String) {
        val payment = paymentDao.getPaymentById(transactionId) ?: return
        if (payment.status == "SETTLED") return // Idempotent

        paymentDao.updateStatus(transactionId, "SETTLED", "CONFIRMED", txHash = txHash)
        walletDao.confirmInboundSettled(payment.receiverWalletId, amount)

        val updated = paymentDao.getPaymentById(transactionId) ?: payment
        _events.tryEmit(PaymentEvent.Settled(updated, txHash))
    }

    /**
     * Rolls back an unconfirmed inbound payment if on-chain settlement fails or conflicts.
     */
    suspend fun rollbackInboundPending(transactionId: String, amount: Long, reason: PaymentValidationResult) {
        val payment = paymentDao.getPaymentById(transactionId) ?: return
        if (payment.status == "REJECTED" || payment.status == "CONFLICT") return

        paymentDao.updateStatus(transactionId, "CONFLICT", "FAILED", txHash = null)
        walletDao.rollbackInboundPending(payment.receiverWalletId, amount)
        _events.tryEmit(PaymentEvent.Rejected(transactionId, reason))
    }
}
