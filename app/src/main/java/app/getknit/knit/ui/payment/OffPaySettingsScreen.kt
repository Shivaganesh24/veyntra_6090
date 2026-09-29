package app.getknit.knit.ui.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getknit.knit.payment.settlement.MstBlockchainConfig

private val VeyntraBg = Color(0xFFF6F8FC)
private val VeyntraCardBg = Color(0xFFFFFFFF)
private val VeyntraCardBorder = Color(0xFFE5E7EB)

private val VeyntraBluePrimary = Color(0xFF1E66F5)

private val VeyntraTextDark = Color(0xFF111827)
private val VeyntraTextMuted = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OffPaySettingsScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit,
) {
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = VeyntraBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Veyntra Settings",
                        fontWeight = FontWeight.Bold,
                        color = VeyntraTextDark,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VeyntraTextDark,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = VeyntraBg,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
                border = BorderStroke(1.dp, VeyntraCardBorder),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Account Information", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VeyntraTextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Account Name: ${wallet?.displayName ?: "Veyntra Wallet"}", color = VeyntraTextDark)
                    Text("Wallet ID: ${wallet?.walletId ?: "wallet-unknown"}", fontSize = 12.sp, color = VeyntraTextMuted)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
                border = BorderStroke(1.dp, VeyntraCardBorder),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Network & Settlement Status", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VeyntraTextDark)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Internet Connection:", color = VeyntraTextDark)
                        Text(
                            text = if (isOnline) "🟢 Online" else "🟠 Offline",
                            fontWeight = FontWeight.Bold,
                            color = VeyntraTextDark,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    val networkLabel =
                        "Network: ${MstBlockchainConfig.NATIVE_CURRENCY_SYMBOL} " +
                            "(${MstBlockchainConfig.MST_CHAIN_ID})"
                    Text(networkLabel, color = VeyntraTextDark)
                    val shortContract =
                        MstBlockchainConfig.CONTRACT_ADDRESS.take(12) + "..." +
                            MstBlockchainConfig.CONTRACT_ADDRESS.takeLast(6)
                    Text("Contract: $shortContract", fontSize = 11.sp, color = VeyntraTextMuted)

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.reconcilePaymentsNow() },
                        enabled = isOnline,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = VeyntraBluePrimary,
                                contentColor = Color.White,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Sync Pending Payments Now", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
                border = BorderStroke(1.dp, VeyntraCardBorder),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About Veyntra", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VeyntraTextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Veyntra — Pay offline. Settle when connected.",
                        fontWeight = FontWeight.Bold,
                        color = VeyntraBluePrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val descText =
                        "Send offline payments over nearby devices when the " +
                            "Internet isn't available, and settle securely on MST " +
                            "Blockchain when connectivity returns."
                    Text(
                        text = descText,
                        fontSize = 12.sp,
                        color = VeyntraTextMuted,
                    )
                }
            }
        }
    }
}
