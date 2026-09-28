package app.getknit.knit.payment.settlement

/**
 * MST Blockchain Testnet Configuration.
 * Centralized settings for RPC connection, chain parameters, and smart contract integration.
 */
object MstBlockchainConfig {
    const val MST_RPC_URL = "https://testnetrpc.mstblockchain.com"
    const val MST_CHAIN_ID = 91562037L
    const val CONTRACT_ADDRESS = "0x417404c95724d8E4aF6fb368CA18196FC4bEA143"
    const val EXPLORER_BASE_URL = "https://testnet.mstscan.com"
    const val NATIVE_CURRENCY_SYMBOL = "tMSTC"
    const val REQUIRED_CONFIRMATIONS = 1

    fun getExplorerTxUrl(txHash: String): String = "$EXPLORER_BASE_URL/tx/$txHash"
}
