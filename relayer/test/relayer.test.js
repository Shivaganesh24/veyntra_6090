const test = require('node:test');
const assert = require('node:assert');
const crypto = require('crypto');
const {
  canonicalSigningBytes,
  verifyEd25519Signature,
  validateAndFormatBytes32,
  transactionIdToBytes32
} = require('../server');

test('validateAndFormatBytes32 correctly validates 64-char hex strings', () => {
  assert.strictEqual(validateAndFormatBytes32('0x' + 'a'.repeat(64)), '0x' + 'a'.repeat(64));
  assert.strictEqual(validateAndFormatBytes32('b'.repeat(64)), '0x' + 'b'.repeat(64));
  assert.strictEqual(validateAndFormatBytes32('invalid-hex'), null);
  assert.strictEqual(validateAndFormatBytes32('123'), null);
  assert.strictEqual(validateAndFormatBytes32(null), null);
});

test('transactionIdToBytes32 computes deterministic SHA-256 hex bytes32 string', () => {
  const hash1 = transactionIdToBytes32('tx-123456');
  const hash2 = transactionIdToBytes32('tx-123456');
  assert.strictEqual(hash1, hash2);
  assert.strictEqual(hash1.length, 66); // '0x' + 64 hex chars
  assert.strictEqual(hash1.startsWith('0x'), true);
});

test('Ed25519 signature verification works for valid signed payloads', () => {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ed25519');
  const rawPubKey = publicKey.export({ format: 'der', type: 'spki' }).slice(12);
  const senderPkBase64 = rawPubKey.toString('base64');

  const now = Date.now();
  const payload = {
    transactionId: 'tx-test-100',
    senderPublicKey: senderPkBase64,
    receiverPublicKey: senderPkBase64,
    senderWalletId: 'wallet-sender',
    receiverWalletId: 'wallet-receiver',
    amount: 10000,
    currency: 'INR',
    timestamp: now,
    nonce: 1,
    previousTransactionReference: null,
    createdOffline: true,
    expiryTime: now + 86400000,
    receiverEvmAddress: '0x29e55e6691803ab357b6777e6a94ce5b086fbbed'
  };

  const canonicalBytes = canonicalSigningBytes(payload);
  const rawSig = crypto.sign(null, canonicalBytes, privateKey);
  const sigBase64 = rawSig.toString('base64');

  const isValid = verifyEd25519Signature(payload, sigBase64, senderPkBase64);
  assert.strictEqual(isValid, true);
});

test('Ed25519 signature verification fails when payload is tampered', () => {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ed25519');
  const rawPubKey = publicKey.export({ format: 'der', type: 'spki' }).slice(12);
  const senderPkBase64 = rawPubKey.toString('base64');

  const now = Date.now();
  const payload = {
    transactionId: 'tx-test-100',
    senderPublicKey: senderPkBase64,
    receiverPublicKey: senderPkBase64,
    senderWalletId: 'wallet-sender',
    receiverWalletId: 'wallet-receiver',
    amount: 10000,
    currency: 'INR',
    timestamp: now,
    nonce: 1,
    previousTransactionReference: null,
    createdOffline: true,
    expiryTime: now + 86400000,
    receiverEvmAddress: '0x29e55e6691803ab357b6777e6a94ce5b086fbbed'
  };

  const canonicalBytes = canonicalSigningBytes(payload);
  const rawSig = crypto.sign(null, canonicalBytes, privateKey);
  const sigBase64 = rawSig.toString('base64');

  // Tamper with amount
  const tamperedPayload = { ...payload, amount: 999999 };
  const isValid = verifyEd25519Signature(tamperedPayload, sigBase64, senderPkBase64);
  assert.strictEqual(isValid, false);
});
