// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title MSTPaymentSettlement
 * @notice Minimal, auditable EVM smart contract for settling and attesting offline MST OfflinePay mesh transactions.
 *
 * Enforces single-settlement idempotency per unique transaction ID.
 */
contract MSTPaymentSettlement {
    struct PaymentRecord {
        bytes32 transactionId;
        address sender;
        address receiver;
        uint256 amount;
        uint256 nonce;
        uint256 timestamp;
        uint256 settledAtBlock;
    }

    // Mapping from transactionId (keccak256 hash or bytes32 representation) -> settlement status
    mapping(bytes32 => bool) public isSettled;

    // Mapping from transactionId -> PaymentRecord
    mapping(bytes32 => PaymentRecord) public payments;

    // Event emitted upon successful on-chain settlement
    event PaymentSettled(
        bytes32 indexed transactionId,
        address indexed sender,
        address indexed receiver,
        uint256 amount,
        uint256 nonce,
        uint256 timestamp
    );

    /**
     * @notice Settles an offline payment transaction on-chain.
     * @param transactionId Unique 32-byte hash identifier of the payment transaction.
     * @param receiver The address of the payment recipient.
     * @param amount Payment amount in smallest currency unit (paise/cents).
     * @param nonce Monotonic nonce from the sender's wallet.
     */
    function settlePayment(
        bytes32 transactionId,
        address receiver,
        uint256 amount,
        uint256 nonce
    ) external returns (bool) {
        require(!isSettled[transactionId], "MSTSettlement: Transaction already settled");
        require(amount > 0, "MSTSettlement: Amount must be greater than zero");
        require(receiver != address(0), "MSTSettlement: Invalid receiver address");

        isSettled[transactionId] = true;

        payments[transactionId] = PaymentRecord({
            transactionId: transactionId,
            sender: msg.sender,
            receiver: receiver,
            amount: amount,
            nonce: nonce,
            timestamp: block.timestamp,
            settledAtBlock: block.number
        });

        emit PaymentSettled(
            transactionId,
            msg.sender,
            receiver,
            amount,
            nonce,
            block.timestamp
        );

        return true;
    }

    /**
     * @notice Checks whether a transaction ID has already been settled on-chain.
     */
    function isTransactionSettled(bytes32 transactionId) external view returns (bool) {
        return isSettled[transactionId];
    }

    /**
     * @notice Retrieves full record of a settled transaction.
     */
    function getPaymentRecord(bytes32 transactionId) external view returns (PaymentRecord memory) {
        require(isSettled[transactionId], "MSTSettlement: Transaction not found");
        return payments[transactionId];
    }
}
