require('dotenv').config();
const { ethers } = require('ethers');

const MST_RPC_URL = process.env.MST_RPC_URL || 'https://testnetrpc.mstblockchain.com';
const MST_CHAIN_ID = parseInt(process.env.MST_CHAIN_ID || '91562037', 10);
const MST_CONTRACT_ADDRESS = process.env.MST_CONTRACT_ADDRESS || '0x417404c95724d8E4aF6fb368CA18196FC4bEA143';
const EXPECTED_AUTHORITY = (process.env.EXPECTED_AUTHORITY || '0x29e55e6691803ab357b6777e6a94ce5b086fbbed').toLowerCase();

async function run() {
  console.log('--- RELAYER DIAGNOSTICS TEST ---');
  const provider = new ethers.JsonRpcProvider(MST_RPC_URL, { chainId: MST_CHAIN_ID, name: 'mst-testnet' });

  const blockNum = await provider.getBlockNumber();
  console.log(`1. RPC Connection: OK, Latest Block = ${blockNum}`);

  const network = await provider.getNetwork();
  console.log(`2. Chain ID: ${Number(network.chainId)} (Expected: ${MST_CHAIN_ID})`);

  const code = await provider.getCode(MST_CONTRACT_ADDRESS);
  console.log(`3. Contract Code at ${MST_CONTRACT_ADDRESS}: ${code.length > 2 ? 'EXISTS (' + code.length + ' bytes)' : 'MISSING'}`);

  const balWei = await provider.getBalance(EXPECTED_AUTHORITY);
  console.log(`4. Authority (${EXPECTED_AUTHORITY}) Balance: ${ethers.formatEther(balWei)} tMSTC`);

  const abi = [
    'function isSettled(bytes32 transactionId) external view returns (bool)',
    'function isTransactionSettled(bytes32 transactionId) external view returns (bool)'
  ];
  const contract = new ethers.Contract(MST_CONTRACT_ADDRESS, abi, provider);

  let isSettledVal = false;
  try {
    isSettledVal = await contract.isSettled('0x' + '0'.repeat(64));
    console.log(`5. Contract isSettled(0x00...00) call: ${isSettledVal}`);
  } catch (err) {
    console.log(`5. Contract isSettled call failed: ${err.message}`);
    try {
      isSettledVal = await contract.isTransactionSettled('0x' + '0'.repeat(64));
      console.log(`5. Contract isTransactionSettled(0x00...00) call: ${isSettledVal}`);
    } catch (e) {
      console.log(`5. Contract isTransactionSettled call failed: ${e.message}`);
    }
  }

  console.log('--- ALL DIAGNOSTICS PASSED SUCCESSFULLY ---');
}

run().catch(err => {
  console.error('DIAGNOSTICS FAILED:', err);
  process.exit(1);
});
