package app.getknit.knit.data.payment

import android.util.Log
import androidx.room3.withWriteTransaction
import app.getknit.knit.data.KnitDatabase
import app.getknit.knit.data.crypto.IdentityKeyStore
import app.getknit.knit.identity.NodeId
import app.getknit.knit.mesh.crypto.PublicKeyBundle
import app.getknit.knit.mesh.protocol.RelayEnvelope
import app.getknit.knit.mesh.protocol.WireEnvelope
import app.getknit.knit.notifications.Notifier
import app.getknit.knit.payment.crypto.EvmAddress
import app.getknit.knit.payment.crypto.PaymentSigner
import app.getknit.knit.payment.protocol.PaymentPayload
import app.getknit.knit.payment.protocol.PaymentProtocolValidator
import app.getknit.knit.payment.protocol.PaymentValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.charset.StandardCharsets

/**
 * Observable events emitted when payment states change.
 */
sealed interface PaymentEvent {
    data class Created(
        val payment: PaymentEntity,
    ) : PaymentEvent

    data class Sent(
        val payment: PaymentEntity,
    ) : PaymentEvent

    data class Received(
        val payment: PaymentEntity,
    ) : PaymentEvent

    data class Verified(
        val payment: PaymentEntity,
    ) : PaymentEvent

    data class Settled(
        val payment: PaymentEntity,
        val txHash: String,
    ) : PaymentEvent

    data class Rejected(
        val transactionId: String,
        val reason: PaymentValidationResult,
    ) : PaymentEvent

    data class Duplicate(
        val transactionId: String,
    ) : PaymentEvent
}

@Suppress("LongMethod", "MaxLineLength", "UnusedParameter")
class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val walletDao: WalletDao,
    private val identityKeyStore: IdentityKeyStore,
    private val notifier: Notifier? = null,
    private val db: KnitDatabase? = null,
) {
    private val _events = MutableSharedFlow<PaymentEvent>(extraBufferCapacity = 64)
    val events: Flow<PaymentEvent> = _events.asSharedFlow()

    suspend fun getPrimaryWallet(): WalletEntity? = walletDao.getPrimaryWallet()

    fun observePrimaryWallet(): Flow<WalletEntity?> = walletDao.observePrimaryWallet()

    fun observeAllPayments(): Flow<List<PaymentEntity>> = paymentDao.observeAllPayments()

    suspend fun getAllPayments(): List<PaymentEntity> = paymentDao.getAllPayments()

    suspend fun getPaymentById(transactionId: String): PaymentEntity? = paymentDao.getPaymentById(transactionId)

    suspend fun getPendingSettlementPayments(): List<PaymentEntity> = paymentDao.getPendingSettlementPayments()

    private fun isSameWallet(
        id1: String,
        id2: String,
    ): Boolean {
        if (id1.equals(id2, ignoreCase = true)) return true
        val clean1 = id1.removePrefix("wallet-").trim()
        val clean2 = id2.removePrefix("wallet-").trim()
        return clean1.isNotBlank() && clean1.equals(clean2, ignoreCase = true)
    }

    /**
     * Derives a deterministic EVM address for a wallet using the device's key bytes.
     */
    private fun deriveEvmAddressForWallet(walletId: String, publicKey: String): String {
        val seed = (walletId + "|" + publicKey).toByteArray(StandardCharsets.UTF_8)
        return EvmAddress.deriveEvmAddress(seed)
    }

    /**
     * Initializes a primary demo wallet if none exists yet.
     */
    suspend fun initializeDemoWallet(
        walletId: String,
        displayName: String,
        publicKey: String? = null,
        initialBalance: Long = 50000L, // ₹500.00
        evmAddress: String? = null,
    ): WalletEntity {
        val walletPublicKey =
            publicKey ?: runCatching {
                identityKeyStore.keys().publicBundle.encoded
            }.getOrElse { "fallback-pub-key" }

        val derivedEvmAddress =
            if (EvmAddress.isValidEvmAddress(evmAddress)) {
                evmAddress!!
            } else {
                deriveEvmAddressForWallet(walletId, walletPublicKey)
            }

        val existing = walletDao.getPrimaryWallet()
        if (existing != null) {
            if (existing.walletId != walletId || existing.evmAddress != derivedEvmAddress) {
                walletDao.deleteWallet(existing.walletId)
                val updated =
                    existing.copy(
                        walletId = walletId,
                        publicKey = walletPublicKey,
                        evmAddress = derivedEvmAddress,
                        updatedAt = System.currentTimeMillis(),
                    )
                walletDao.upsertWallet(updated)
                return updated
            }
            return existing
        }

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
                evmAddress = derivedEvmAddress,
            )
        walletDao.upsertWallet(wallet)
        return wallet
    }

    suspend fun resetDemoWallet(
        walletId: String,
        displayName: String,
        publicKey: String,
        initialBalance: Long,
        evmAddress: String? = null,
    ): WalletEntity {
        val existingPrimary = walletDao.getPrimaryWallet()
        if (existingPrimary != null && existingPrimary.walletId != walletId) {
            walletDao.deleteWallet(existingPrimary.walletId)
        }
        val derivedEvmAddress =
            if (EvmAddress.isValidEvmAddress(evmAddress)) {
                evmAddress!!
            } else {
                deriveEvmAddressForWallet(walletId, publicKey)
            }

        val wallet =
            WalletEntity(
                walletId = walletId,
                publicKey = publicKey,
                displayName = displayName,
                currency = "INR",
                settledBalance = initialBalance,
                pendingInbound = 0L,
                pendingOutbound = 0L,
                nextNonce = existingPrimary?.nextNonce ?: 1L,
                isDemo = true,
                evmAddress = derivedEvmAddress,
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
        receiverEvmAddress: String? = null,
    ): Pair<PaymentPayload, PaymentEntity>? {
        val wallet = walletDao.getPrimaryWallet() ?: return null

        // 1. Atomic reservation check & update
        val reservedRows = walletDao.reserveOutboundAmount(wallet.walletId, amount)
        if (reservedRows <= 0) {
            Log.w("OFFPAY", "OFFPAY_SEND_FAILED reason=insufficient-funds available=${wallet.availableBalance}")
            _events.tryEmit(PaymentEvent.Rejected("insufficient-funds", PaymentValidationResult.INVALID_AMOUNT))
            return null
        }

        // 2. Fetch updated wallet state for nonce
        val currentWallet = walletDao.getWallet(wallet.walletId) ?: wallet
        val currentNonce = currentWallet.nextNonce
        walletDao.incrementNonce(wallet.walletId)

        // 3. Determine verified receiver EVM address
        val finalReceiverEvmAddress =
            if (EvmAddress.isValidEvmAddress(receiverEvmAddress)) {
                receiverEvmAddress!!
            } else {
                deriveEvmAddressForWallet(receiverWalletId, receiverPublicKey)
            }

        // 4. Build PaymentPayload
        val now = System.currentTimeMillis()
        val txId = "tx-$now-${(1000..9999).random()}"

        val initialPayload =
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
                receiverEvmAddress = finalReceiverEvmAddress,
            )

        // 5. Cryptographically sign payload (includes receiverEvmAddress)
        val sig = PaymentSigner.sign(initialPayload, signRaw)
        val payload = initialPayload.copy(signature = sig)

        // 6. Save PaymentEntity locally in OFFLINE_SENT state
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
                receiverEvmAddress = finalReceiverEvmAddress,
            )
        paymentDao.insertPayment(entity)

        Log.d("OFFPAY", "OFFPAY_SEND_CREATED txId=$txId sender=${currentWallet.walletId} receiver=$receiverWalletId evm=$finalReceiverEvmAddress")
        Log.d("OFFPAY", "OFFPAY_SEND_RESERVED transactionId=$txId reservedAmount=$amount")

        _events.tryEmit(PaymentEvent.Created(entity))
        _events.tryEmit(PaymentEvent.Sent(entity))

        return payload to entity
    }

    suspend fun ensurePrimaryWallet(): WalletEntity {
        val existing = walletDao.getPrimaryWallet()
        if (existing != null) return existing

        val pubKey = runCatching { identityKeyStore.keys().publicBundle.encoded }.getOrElse { "fallback-pub-key" }
        val nodeId =
            runCatching {
                val bundle = PublicKeyBundle.decode(pubKey)
                if (bundle != null) NodeId.fromPublicKeyBundle(pubKey) else pubKey
            }.getOrElse { pubKey }

        val walletId = "wallet-$nodeId"
        val derivedEvmAddress = deriveEvmAddressForWallet(walletId, pubKey)

        val wallet =
            WalletEntity(
                walletId = walletId,
                publicKey = pubKey,
                displayName = "OffPay Wallet (${nodeId.take(6)})",
                currency = "INR",
                settledBalance = 50000L,
                pendingInbound = 0L,
                pendingOutbound = 0L,
                nextNonce = 1L,
                isDemo = true,
                evmAddress = derivedEvmAddress,
            )
        walletDao.upsertWallet(wallet)
        return wallet
    }

    /**
     * Processes an incoming payment frame received from the mesh network.
     * Offline received payments increase [pendingInbound] ONLY and are NOT spendable until settled.
     */
    @Suppress("UnusedParameter", "CyclomaticComplexMethod")
    suspend fun processInboundPayment(
        payload: PaymentPayload,
        signature: String,
        env: RelayEnvelope,
        wire: WireEnvelope,
        fromNodeId: String,
    ): PaymentValidationResult {
        val myWallet = walletDao.getPrimaryWallet() ?: ensurePrimaryWallet()

        Log.d("OFFPAY", "OFFPAY_PACKET_RECEIVED transactionId=${payload.transactionId} amount=${payload.amount}")

        // 1. Receiver Identity Check
        val myWalletId = myWallet.walletId
        val myPublicKey = myWallet.publicKey
        val rawNodeId = myWalletId.removePrefix("wallet-").trim()

        val isForMe =
            isSameWallet(payload.receiverWalletId, myWalletId) ||
                payload.receiverPublicKey.equals(myPublicKey, ignoreCase = true) ||
                (rawNodeId.isNotBlank() && payload.receiverWalletId.contains(rawNodeId, ignoreCase = true)) ||
                (rawNodeId.isNotBlank() && payload.receiverPublicKey.contains(rawNodeId, ignoreCase = true)) ||
                (myPublicKey.isNotBlank() && payload.receiverPublicKey.contains(rawNodeId, ignoreCase = true))

        if (!isForMe) {
            Log.d("OFFPAY", "Payment ${payload.transactionId} is for another recipient, ignoring locally")
            return PaymentValidationResult.VALID
        }

        // 2. Anti-replay & Duplicate Check
        val existing = paymentDao.getPaymentById(payload.transactionId)
        if (existing != null) {
            Log.d("OFFPAY", "OFFPAY_RECEIVE_DUPLICATE transactionId=${payload.transactionId}")
            _events.tryEmit(PaymentEvent.Duplicate(payload.transactionId))
            return PaymentValidationResult.DUPLICATE_TRANSACTION
        }

        // 3. Protocol & Cryptographic Signature Validation
        val validationResult = PaymentProtocolValidator.validate(payload, signature, paymentDao)
        if (validationResult != PaymentValidationResult.VALID) {
            Log.w("OFFPAY", "OFFPAY_VALIDATION_FAILED transactionId=${payload.transactionId} reason=$validationResult")
            _events.tryEmit(PaymentEvent.Rejected(payload.transactionId, validationResult))
            return validationResult
        }

        Log.d("OFFPAY", "OFFPAY_SIGNATURE_VALID transactionId=${payload.transactionId} signatureValid=true")

        // 4. Record Payment as PENDING_SETTLEMENT, carrying receiverEvmAddress
        val finalReceiverEvmAddress =
            if (EvmAddress.isValidEvmAddress(payload.receiverEvmAddress)) {
                payload.receiverEvmAddress
            } else {
                myWallet.evmAddress
            }

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
                receiverEvmAddress = finalReceiverEvmAddress,
            )

        // 5. Atomic DB insertion & balance update
        if (db != null) {
            db.withWriteTransaction {
                paymentDao.insertPayment(entity)
                walletDao.receiveInboundPending(myWallet.walletId, payload.amount)
            }
        } else {
            paymentDao.insertPayment(entity)
            walletDao.receiveInboundPending(myWallet.walletId, payload.amount)
        }

        Log.d("OFFPAY", "OFFPAY_PAYMENT_INSERTED transactionId=${payload.transactionId} databaseInsert=true evm=$finalReceiverEvmAddress")
        Log.d("OFFPAY", "OFFPAY_PENDING_INBOUND_UPDATED transactionId=${payload.transactionId} amount=${payload.amount}")

        var notifTriggered = false
        if (notifier != null) {
            notifier.notifyPaymentReceived(
                senderWalletId = payload.senderWalletId,
                amountPaise = payload.amount,
                transactionId = payload.transactionId,
            )
            notifTriggered = true
            Log.d("OFFPAY", "OFFPAY_NOTIFICATION_SENT transactionId=${payload.transactionId}")
        }

        Log.d(
            "OFFPAY",
            "OFFPAY_RECEIVE: transactionId=${payload.transactionId} amount=${payload.amount} notificationTriggered=$notifTriggered",
        )

        _events.tryEmit(PaymentEvent.Received(entity))
        _events.tryEmit(PaymentEvent.Verified(entity))

        return PaymentValidationResult.VALID
    }

    /**
     * Marks a payment status as SUBMITTED while on-chain settlement is in progress.
     */
    suspend fun markPaymentSubmitted(transactionId: String) {
        val payment = paymentDao.getPaymentById(transactionId) ?: return
        if (payment.status == "SETTLED") return
        paymentDao.updateStatus(transactionId, "SUBMITTED", "PENDING", txHash = null)
    }

    /**
     * Resets a payment status back to PENDING_SETTLEMENT or OFFLINE_SENT if settlement failed transiently.
     */
    suspend fun resetPaymentPending(transactionId: String) {
        val payment = paymentDao.getPaymentById(transactionId) ?: return
        if (payment.status == "SETTLED") return
        val myWalletId = walletDao.getPrimaryWallet()?.walletId ?: ""
        val originalStatus =
            if (payment.createdOffline && isSameWallet(payment.senderWalletId, myWalletId)) {
                "OFFLINE_SENT"
            } else {
                "PENDING_SETTLEMENT"
            }
        paymentDao.updateStatus(transactionId, originalStatus, "PENDING", txHash = null)
    }

    /**
     * Releases an outbound reservation if a payment expires or is rejected.
     */
    suspend fun releaseOutboundReservation(
        transactionId: String,
        amount: Long,
        reason: PaymentValidationResult,
    ) {
        val payment = paymentDao.getPaymentById(transactionId) ?: return
        if (payment.status == "SETTLED" || payment.status == "REJECTED" || payment.status == "EXPIRED") return

        val targetWalletId = payment.senderWalletId
        if (db != null) {
            db.withWriteTransaction {
                paymentDao.updateStatus(transactionId, "REJECTED", "FAILED", txHash = null)
                walletDao.releaseOutboundAmount(targetWalletId, amount)
            }
        } else {
            paymentDao.updateStatus(transactionId, "REJECTED", "FAILED", txHash = null)
            walletDao.releaseOutboundAmount(targetWalletId, amount)
        }
        _events.tryEmit(PaymentEvent.Rejected(transactionId, reason))
    }

    /**
     * Confirms on-chain settlement for an outbound payment (Step 5 callback).
     * Deducts amount from settled balance and clears pending outbound reservation.
     */
    suspend fun confirmOutboundSettlement(
        transactionId: String,
        amount: Long,
        txHash: String,
    ) {
        val payment = paymentDao.getPaymentById(transactionId) ?: return
        if (payment.status == "SETTLED") return // Idempotent

        val primaryWallet = walletDao.getPrimaryWallet()
        val targetWalletId = primaryWallet?.walletId ?: payment.senderWalletId
        if (db != null) {
            db.withWriteTransaction {
                paymentDao.updateStatus(transactionId, "SETTLED", "CONFIRMED", txHash = txHash)
                walletDao.confirmOutboundSettled(targetWalletId, amount)
            }
        } else {
            paymentDao.updateStatus(transactionId, "SETTLED", "CONFIRMED", txHash = txHash)
            walletDao.confirmOutboundSettled(targetWalletId, amount)
        }

        val updated = paymentDao.getPaymentById(transactionId) ?: payment
        _events.tryEmit(PaymentEvent.Settled(updated, txHash))
        notifier?.notifyPaymentSettled(amount, transactionId, txHash)
    }

    /**
     * Confirms on-chain settlement for an inbound payment (Step 5 callback).
     * Adds amount to settled balance and clears pending inbound (making funds spendable).
     */
    suspend fun confirmInboundSettlement(
        transactionId: String,
        amount: Long,
        txHash: String,
    ) {
        val payment = paymentDao.getPaymentById(transactionId) ?: return
        if (payment.status == "SETTLED") return // Idempotent

        val primaryWallet = walletDao.getPrimaryWallet()
        val targetWalletId = primaryWallet?.walletId ?: payment.receiverWalletId
        if (db != null) {
            db.withWriteTransaction {
                paymentDao.updateStatus(transactionId, "SETTLED", "CONFIRMED", txHash = txHash)
                walletDao.confirmInboundSettled(targetWalletId, amount)
            }
        } else {
            paymentDao.updateStatus(transactionId, "SETTLED", "CONFIRMED", txHash = txHash)
            walletDao.confirmInboundSettled(targetWalletId, amount)
        }

        val updated = paymentDao.getPaymentById(transactionId) ?: payment
        _events.tryEmit(PaymentEvent.Settled(updated, txHash))
        notifier?.notifyPaymentSettled(amount, transactionId, txHash)
    }

    /**
     * Rolls back an unconfirmed inbound payment if on-chain settlement fails or conflicts.
     */
    suspend fun rollbackInboundPending(
        transactionId: String,
        amount: Long,
        reason: PaymentValidationResult,
    ) {
        val payment = paymentDao.getPaymentById(transactionId) ?: return
        if (payment.status == "REJECTED" || payment.status == "CONFLICT") return

        val targetWalletId = payment.receiverWalletId
        if (db != null) {
            db.withWriteTransaction {
                paymentDao.updateStatus(transactionId, "CONFLICT", "FAILED", txHash = null)
                walletDao.rollbackInboundPending(targetWalletId, amount)
            }
        } else {
            paymentDao.updateStatus(transactionId, "CONFLICT", "FAILED", txHash = null)
            walletDao.rollbackInboundPending(targetWalletId, amount)
        }
        _events.tryEmit(PaymentEvent.Rejected(transactionId, reason))
    }
}
