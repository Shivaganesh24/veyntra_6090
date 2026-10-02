require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { ethers } = require('ethers');

const PORT = process.env.PORT || 3000;
const MST_RPC_URL = process.env.MST_RPC_URL || 'https://testnetrpc.mstblockchain.com';
const MST_CHAIN_ID = parseInt(process.env.MST_CHAIN_ID || '91562037', 10);
const MST_CONTRACT_ADDRESS = process.env.MST_CONTRACT_ADDRESS || '0x417404c95724d8E4aF6fb368CA18196FC4bEA143';
const EXPECTED_AUTHORITY = (process.env.EXPECTED_AUTHORITY || '0x29e55e6691803ab357b6777e6a94ce5b086fbbed').toLowerCase();
const RELAYER_PRIVATE_KEY = process.env.RELAYER_PRIVATE_KEY || '';
const RELAYER_AUTH_SECRET = process.env.RELAYER_AUTH_SECRET || '';

const ABI = [
  'function settlePayment(bytes32 transactionId, address receiver, uint256 amount, uint256 nonce) external returns (bool)',
  'function isSettled(bytes32 transactionId) external view returns (bool)'
];

const app = express();
app.use(cors());
app.use(express.json());

const provider = new ethers.JsonRpcProvider(MST_RPC_URL, {
  chainId: MST_CHAIN_ID,
  name: 'mst-testnet'
});

let wallet = null;
let contract = null;

function initWalletAndContract() {
  if (RELAYER_PRIVATE_KEY && RELAYER_PRIVATE_KEY.trim().length > 0) {
    try {
      const cleanKey = RELAYER_PRIVATE_KEY.trim().startsWith('0x')
        ? RELAYER_PRIVATE_KEY.trim()
        : '0x' + RELAYER_PRIVATE_KEY.trim();
      wallet = new ethers.Wallet(cleanKey, provider);
      const derivedAuthority = wallet.address.toLowerCase();

      if (derivedAuthority !== EXPECTED_AUTHORITY) {
        console.error(`CRITICAL ERROR: Derived wallet address (${derivedAuthority}) DOES NOT MATCH expected settlement authority (${EXPECTED_AUTHORITY}).`);
        console.error('Refusing to run with mismatched authority key.');
        process.exit(1);
      }

      console.log(`[Relayer Init] Authority Wallet Verified: ${wallet.address}`);
      contract = new ethers.Contract(MST_CONTRACT_ADDRESS, ABI, wallet);
    } catch (err) {
      console.error(`[Relayer Init] Failed to initialize private key wallet: ${err.message}`);
      process.exit(1);
    }
  } else {
    console.log('[Relayer Init] RELAYER_PRIVATE_KEY not supplied in environment. Relayer running in READ-ONLY mode.');
    contract = new ethers.Contract(MST_CONTRACT_ADDRESS, ABI, provider);
  }
}

initWalletAndContract();

// Helper: Strict 32-byte hex string validation (must be exactly 64 hex characters, optional 0x prefix)
function validateAndFormatBytes32(raw) {
  if (!raw || typeof raw !== 'string') return null;
  let clean = raw.trim().toLowerCase();
  if (clean.startsWith('0x')) {
    clean = clean.slice(2);
  }
  if (clean.length !== 64 || /[^0-9a-f]/.test(clean)) {
    return null;
  }
  return '0x' + clean;
}

// GET /health
app.get('/health', async (req, res) => {
  try {
    const blockNum = await provider.getBlockNumber();
    res.json({
      status: 'ok',
      chainId: MST_CHAIN_ID,
      authority: EXPECTED_AUTHORITY,
      signerConfigured: wallet !== null,
      rpcConnected: blockNum > 0,
      latestBlock: blockNum
    });
  } catch (err) {
    res.status(500).json({
      status: 'error',
      error: err.message
    });
  }
});

// GET /diagnostics
app.get('/diagnostics', async (req, res) => {
  try {
    const blockNum = await provider.getBlockNumber();
    const network = await provider.getNetwork();
    const code = await provider.getCode(MST_CONTRACT_ADDRESS);

    let balance = '0';
    if (wallet) {
      const balWei = await provider.getBalance(wallet.address);
      balance = ethers.formatEther(balWei) + ' tMSTC';
    } else {
      const balWei = await provider.getBalance(EXPECTED_AUTHORITY);
      balance = ethers.formatEther(balWei) + ' tMSTC';
    }

    res.json({
      rpcUrl: MST_RPC_URL,
      expectedChainId: MST_CHAIN_ID,
      actualChainId: Number(network.chainId),
      contractAddress: MST_CONTRACT_ADDRESS,
      contractCodeExists: code && code !== '0x' && code.length > 2,
      authorityAddress: EXPECTED_AUTHORITY,
      authorityBalance: balance,
      signerConfigured: wallet !== null,
      latestBlock: blockNum
    });
  } catch (err) {
    res.status(500).json({
      status: 'error',
      error: err.message
    });
  }
});

// POST /relayer
app.post('/relayer', async (req, res) => {
  try {
    // Relayer Security Guard Check (if secret configured)
    if (RELAYER_AUTH_SECRET) {
      const reqSecret = req.headers['x-relayer-secret'] || req.headers['authorization'];
      if (!reqSecret || reqSecret !== RELAYER_AUTH_SECRET) {
        return res.status(401).json({ status: 'FAILED', error: 'Unauthorized relayer request' });
      }
    }
    // TODO: In production, verify Ed25519 signature over PaymentPayload from Android client before broadcasting gas-spending transactions.

    const body = req.body || {};

    const rawTxId = body.txIdBytes32 || body.transactionId || (body.payment && body.payment.transactionId);
    const rawReceiver = body.receiverAddress || (body.payment && body.payment.receiverAddress);
    const rawAmount = body.amount !== undefined ? body.amount : (body.payment && body.payment.amount);
    const rawNonce = body.nonce !== undefined ? body.nonce : (body.payment && body.payment.nonce);

    // 1. Strict txIdBytes32 Validation: MUST be exactly 32 bytes / 64 hex characters
    if (!rawTxId) {
      return res.status(400).json({ status: 'FAILED', error: 'Missing txIdBytes32' });
    }
    const txIdBytes32 = validateAndFormatBytes32(rawTxId);
    if (!txIdBytes32) {
      return res.status(400).json({
        status: 'FAILED',
        error: `Invalid txIdBytes32 (${rawTxId}): Must be exactly 32 bytes (64 hexadecimal characters)`
      });
    }

    // 2. Strict receiverAddress Validation: MUST be a genuine 20-byte EVM address starting with 0x
    if (!rawReceiver || typeof rawReceiver !== 'string' || !rawReceiver.startsWith('0x') || !ethers.isAddress(rawReceiver)) {
      return res.status(400).json({
        status: 'FAILED',
        error: `Invalid EVM receiverAddress (${rawReceiver}): Receiver address must be a genuine 20-byte EVM hex address beginning with 0x`
      });
    }
    const receiverAddress = ethers.getAddress(rawReceiver);

    // 3. Validate amount > 0
    let amountBig;
    try {
      amountBig = BigInt(rawAmount);
      if (amountBig <= 0n) throw new Error('Amount must be greater than zero');
    } catch (e) {
      return res.status(400).json({ status: 'FAILED', error: `Invalid amount (${rawAmount}): ${e.message}` });
    }

    // 4. Validate nonce >= 0
    let nonceBig;
    try {
      nonceBig = BigInt(rawNonce);
      if (nonceBig < 0n) throw new Error('Nonce cannot be negative');
    } catch (e) {
      return res.status(400).json({ status: 'FAILED', error: `Invalid nonce (${rawNonce}): ${e.message}` });
    }

    console.log(`[Relayer Request] txId=${txIdBytes32} receiver=${receiverAddress} amount=${amountBig.toString()} nonce=${nonceBig.toString()}`);

    // 5. Idempotency Check: Verify if transaction was already settled on-chain using contract getter isSettled(bytes32)
    let isSettledOnChain = false;
    try {
      isSettledOnChain = await contract.isSettled(txIdBytes32);
    } catch (e) {
      console.warn(`[Relayer Check] isSettled query error: ${e.message}`);
    }

    if (isSettledOnChain) {
      console.log(`[Relayer Check] Transaction ${txIdBytes32} is ALREADY_SETTLED on-chain.`);
      return res.json({
        status: 'ALREADY_SETTLED',
        message: 'Transaction already settled on MST Blockchain'
      });
    }

    // 6. Check wallet readiness
    if (!wallet) {
      return res.status(503).json({
        status: 'FAILED',
        error: 'RELAYER_PRIVATE_KEY is not configured on the relayer backend server.'
      });
    }

    // 7. Submit signed transaction via contract
    console.log(`[Relayer Submitting] Sending settlePayment transaction to MST Testnet...`);
    const tx = await contract.settlePayment(txIdBytes32, receiverAddress, amountBig, nonceBig);
    console.log(`[Relayer Broadcast] Transaction hash: ${tx.hash}. Waiting for confirmation...`);

    // 8. Wait for REAL transaction receipt
    const receipt = await tx.wait(1);

    if (!receipt || receipt.status !== 1) {
      console.error(`[Relayer Failed] Transaction reverted or status != 1 on-chain. Hash: ${tx.hash}`);
      return res.status(500).json({
        status: 'FAILED',
        error: `Transaction execution reverted on-chain (txHash: ${tx.hash})`
      });
    }

    console.log(`[Relayer Confirmed] Real settlement confirmed on-chain! Hash: ${receipt.hash} Block: ${receipt.blockNumber}`);

    return res.json({
      status: 'SUCCESS',
      txHash: receipt.hash,
      blockNumber: receipt.blockNumber
    });

  } catch (err) {
    console.error(`[Relayer Error] Settlement error: ${err.message}`, err);
    return res.status(500).json({
      status: 'FAILED',
      error: err.message || String(err)
    });
  }
});

app.listen(PORT, () => {
  console.log(`====================================================`);
  console.log(` MST Blockchain Testnet Relayer Backend Running`);
  console.log(` Port: ${PORT}`);
  console.log(` RPC: ${MST_RPC_URL}`);
  console.log(` Chain ID: ${MST_CHAIN_ID}`);
  console.log(` Contract: ${MST_CONTRACT_ADDRESS}`);
  console.log(` Authority: ${EXPECTED_AUTHORITY}`);
  console.log(` Signer: ${wallet ? 'Configured (' + wallet.address + ')' : 'Not Configured (Read-Only)'}`);
  console.log(`====================================================`);
});
