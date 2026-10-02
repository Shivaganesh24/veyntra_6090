package app.getknit.knit.payment.settlement

/**
 * Dynamic network configuration for MST Blockchain Testnet.
 * All properties are softcoded and can be dynamically overridden at runtime.
 */
data class MstNetworkConfig(
    val rpcUrl: String = MstBlockchainConfig.MST_RPC_URL,
    val chainId: Long = MstBlockchainConfig.MST_CHAIN_ID,
    val contractAddress: String = MstBlockchainConfig.CONTRACT_ADDRESS,
    val relayerAddress: String = MstBlockchainConfig.RELAYER_ADDRESS,
    val relayerUrl: String = MstBlockchainConfig.RELAYER_URL,
    val explorerBaseUrl: String = MstBlockchainConfig.EXPLORER_BASE_URL,
    val nativeCurrencySymbol: String = MstBlockchainConfig.NATIVE_CURRENCY_SYMBOL,
    val networkName: String = "MST Testnet",
) {
    fun getExplorerTxUrl(txHash: String): String = "$explorerBaseUrl/tx/$txHash"
}

/**
 * MST Blockchain Testnet Default Configuration Fallbacks.
 * Centralized default settings for RPC connection, chain parameters, and smart contract integration.
 */
object MstBlockchainConfig {
    const val MST_RPC_URL = "https://testnetrpc.mstblockchain.com"
    const val MST_CHAIN_ID = 91562037L
    const val CONTRACT_ADDRESS = "0x417404c95724d8E4aF6fb368CA18196FC4bEA143"
    const val RELAYER_ADDRESS = "0x29e55e6691803ab357b6777e6a94ce5b086fbbed"
    const val RELAYER_URL = "http://10.0.2.2:3000/relayer"
    const val EXPLORER_BASE_URL = "https://testnet.mstscan.com"
    const val NATIVE_CURRENCY_SYMBOL = "tMSTC"
    const val REQUIRED_CONFIRMATIONS = 1

    val defaultConfig: MstNetworkConfig = MstNetworkConfig()

    fun getExplorerTxUrl(txHash: String): String = "$EXPLORER_BASE_URL/tx/$txHash"
}
