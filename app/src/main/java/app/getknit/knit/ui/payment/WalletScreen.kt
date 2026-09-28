package app.getknit.knit.ui.payment

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit,
) {
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallet Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = wallet?.displayName ?: "MST Wallet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Wallet ID: ${wallet?.walletId ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Settled On-Chain Balance: ₹%.2f".format((wallet?.settledBalance ?: 0L) / 100.0))
                    Text("Pending Outbound Reserved: ₹%.2f".format((wallet?.pendingOutbound ?: 0L) / 100.0), color = Color(0xFFC62828))
                    Text("Pending Inbound Unconfirmed: ₹%.2f".format((wallet?.pendingInbound ?: 0L) / 100.0), color = Color(0xFFEF6C00))

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "Spendable Balance: ₹%.2f".format((wallet?.availableBalance ?: 0L) / 100.0),
                            modifier = Modifier.padding(12.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Monotonic Nonce Counter", fontWeight = FontWeight.Bold)
                    Text("Next Nonce: ${wallet?.nextNonce ?: 1L}", fontSize = 14.sp)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Ed25519 Public Key:", fontWeight = FontWeight.Bold)
                    Text(
                        text = wallet?.publicKey ?: "",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Demo Reset Options:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.resetDemoWallet(isSender = true) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Reset Phone A (₹500)")
                    }
                    Button(
                        onClick = { viewModel.resetDemoWallet(isSender = false) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Reset Phone B (₹100)")
                    }
                }
            }
        }
    }
}
