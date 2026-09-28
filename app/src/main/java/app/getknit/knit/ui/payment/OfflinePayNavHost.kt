package app.getknit.knit.ui.payment

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.androidx.compose.koinViewModel

object PaymentRoutes {
    const val HOME = "payment_home"
    const val PAY = "payment_pay"
    const val RECEIVE = "payment_receive"
    const val TRANSACTIONS = "payment_transactions"
    const val MESH = "payment_mesh"
    const val WALLET = "payment_wallet"
    const val SETTINGS = "payment_settings"
}

@Composable
fun OfflinePayNavHost(
    onBackToKnit: () -> Unit,
    viewModel: PaymentViewModel = koinViewModel(),
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = PaymentRoutes.HOME,
    ) {
        composable(PaymentRoutes.HOME) {
            PaymentHomeScreen(
                viewModel = viewModel,
                onBackToKnit = onBackToKnit,
                onNavigatePay = { navController.navigate(PaymentRoutes.PAY) },
                onNavigateReceive = { navController.navigate(PaymentRoutes.RECEIVE) },
                onNavigateTransactions = { navController.navigate(PaymentRoutes.TRANSACTIONS) },
                onNavigateMesh = { navController.navigate(PaymentRoutes.MESH) },
                onNavigateWallet = { navController.navigate(PaymentRoutes.WALLET) },
                onNavigateSettings = { navController.navigate(PaymentRoutes.SETTINGS) },
            )
        }

        composable(PaymentRoutes.PAY) {
            PayScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(PaymentRoutes.RECEIVE) {
            ReceiveScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(PaymentRoutes.TRANSACTIONS) {
            TransactionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(PaymentRoutes.MESH) {
            MeshPaymentScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(PaymentRoutes.WALLET) {
            WalletScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(PaymentRoutes.SETTINGS) {
            OffPaySettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
