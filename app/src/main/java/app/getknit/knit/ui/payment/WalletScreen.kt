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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val VeyntraBg = Color(0xFFF6F8FC)
private val VeyntraCardBg = Color(0xFFFFFFFF)
private val VeyntraCardBorder = Color(0xFFE5E7EB)

private val VeyntraBluePrimary = Color(0xFF1E66F5)
private val VeyntraBlueLight = Color(0xFFEFF6FF)

private val VeyntraGreenSuccess = Color(0xFF16A34A)
private val VeyntraOrangeWarning = Color(0xFFD97706)

private val VeyntraTextDark = Color(0xFF111827)
private val VeyntraTextMuted = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit,
) {
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = VeyntraBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Wallet Details",
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
                    Text(
                        text = wallet?.displayName ?: "Veyntra Wallet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = VeyntraTextDark,
                    )
                    Text(
                        text = "Wallet ID: ${wallet?.walletId ?: ""}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = VeyntraTextMuted,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Settled On-Chain Balance: ₹%.2f".format((wallet?.settledBalance ?: 0L) / 100.0),
                        fontWeight = FontWeight.SemiBold,
                        color = VeyntraGreenSuccess,
                    )
                    Text(
                        text = "Pending Outbound Reserved: ₹%.2f".format((wallet?.pendingOutbound ?: 0L) / 100.0),
                        color = VeyntraOrangeWarning,
                    )
                    Text(
                        text = "Pending Inbound Unconfirmed: ₹%.2f".format((wallet?.pendingInbound ?: 0L) / 100.0),
                        color = VeyntraOrangeWarning,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VeyntraBlueLight,
                        border = BorderStroke(1.dp, VeyntraBluePrimary.copy(alpha = 0.2f)),
                    ) {
                        Text(
                            text = "Spendable Balance: ₹%.2f".format((wallet?.availableBalance ?: 0L) / 100.0),
                            modifier = Modifier.padding(12.dp),
                            fontWeight = FontWeight.Bold,
                            color = VeyntraBluePrimary,
                        )
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
                    Text("Monotonic Nonce Counter", fontWeight = FontWeight.Bold, color = VeyntraTextDark)
                    Text("Next Nonce: ${wallet?.nextNonce ?: 1L}", fontSize = 14.sp, color = VeyntraTextMuted)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Ed25519 Public Key:", fontWeight = FontWeight.Bold, color = VeyntraTextDark)
                    Text(
                        text = wallet?.publicKey ?: "",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = VeyntraTextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Demo Wallet Switch:", fontWeight = FontWeight.Bold, color = VeyntraTextDark)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.resetDemoWallet(isSender = true) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = VeyntraBluePrimary,
                                contentColor = Color.White,
                            ),
                    ) {
                        Text("Phone A (₹500)")
                    }
                    OutlinedButton(
                        onClick = { viewModel.resetDemoWallet(isSender = false) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, VeyntraCardBorder),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = VeyntraTextDark,
                            ),
                    ) {
                        Text("Phone B (₹100)")
                    }
                }
            }
        }
    }
}
