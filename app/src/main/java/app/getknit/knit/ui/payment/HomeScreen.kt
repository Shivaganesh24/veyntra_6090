package app.getknit.knit.ui.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getknit.knit.data.payment.PaymentEntity
import app.getknit.knit.payment.reconciliation.ReconciliationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHomeScreen(
    viewModel: PaymentViewModel,
    onBackToKnit: () -> Unit,
    onNavigatePay: () -> Unit,
    onNavigateReceive: () -> Unit,
    onNavigateTransactions: () -> Unit,
    onNavigateMesh: () -> Unit,
    onNavigateWallet: () -> Unit,
    onNavigateSettings: () -> Unit,
) {
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val reconState by viewModel.reconciliationState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("OffPay", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Pay offline. Settle when connected.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToKnit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Knit")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
                .padding(padding),
        ) {
            // Top Network Connectivity Status Banner
            NetworkStatusBanner(
                isOnline = isOnline,
                reconState = reconState,
                onReconcileNow = { viewModel.reconcilePaymentsNow() },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    // Balance Card
                    WalletBalanceCard(
                        totalBalancePaise = wallet?.totalBalance ?: 0L,
                        availableBalancePaise = wallet?.availableBalance ?: 0L,
                        pendingOutboundPaise = wallet?.pendingOutbound ?: 0L,
                        pendingInboundPaise = wallet?.pendingInbound ?: 0L,
                    )
                }

                item {
                    // Quick Action Buttons
                    QuickActionsRow(
                        onPay = onNavigatePay,
                        onReceive = onNavigateReceive,
                        onTransactions = onNavigateTransactions,
                        onMesh = onNavigateMesh,
                        onWallet = onNavigateWallet,
                    )
                }

                item {
                    // Demo Mode Banner & Quick Reset Buttons
                    DemoModeCard(
                        onResetPhoneA = { viewModel.resetDemoWallet(isSender = true) },
                        onResetPhoneB = { viewModel.resetDemoWallet(isSender = false) },
                    )
                }

                item {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (payments.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(
                                modifier = Modifier.padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("No payment transactions yet.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    items(payments.take(5)) { payment ->
                        PaymentTransactionItem(payment = payment)
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkStatusBanner(
    isOnline: Boolean,
    reconState: ReconciliationState,
    onReconcileNow: () -> Unit,
) {
    val bgColor = if (isOnline) Color(0xFF2E7D32) else Color(0xFFE65100)
    val text = when {
        !isOnline -> "Offline — Payments can still be sent nearby without Internet"
        reconState == ReconciliationState.SYNCING -> "Online — Auto-settling payments on MST Blockchain..."
        reconState == ReconciliationState.UNCONFIGURED -> "Online — MST Testnet Unconfigured"
        else -> "Online — Connected to MST Testnet"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isOnline) Icons.Default.Router else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (isOnline && reconState != ReconciliationState.SYNCING) {
                IconButton(onClick = onReconcileNow) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun WalletBalanceCard(
    totalBalancePaise: Long,
    availableBalancePaise: Long,
    pendingOutboundPaise: Long,
    pendingInboundPaise: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Available Spendable Balance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Text(
                text = "₹%.2f".format(availableBalancePaise / 100.0),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1B5E20),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Total Represented", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "₹%.2f".format(totalBalancePaise / 100.0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column {
                    Text("Pending Outbound", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "₹%.2f".format(pendingOutboundPaise / 100.0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828),
                    )
                }
                Column {
                    Text("Pending Inbound", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "₹%.2f".format(pendingInboundPaise / 100.0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF6C00),
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionsRow(
    onPay: () -> Unit,
    onReceive: () -> Unit,
    onTransactions: () -> Unit,
    onMesh: () -> Unit,
    onWallet: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Button(
            onClick = onPay,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Send Money")
        }

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedButton(
            onClick = onReceive,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Receive")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onTransactions) {
            Icon(Icons.Default.History, contentDescription = "History")
        }
        IconButton(onClick = onMesh) {
            Icon(Icons.Default.Router, contentDescription = "Mesh")
        }
        IconButton(onClick = onWallet) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet")
        }
    }
}

@Composable
fun DemoModeCard(
    onResetPhoneA: () -> Unit,
    onResetPhoneB: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "OFFPAY DEMO WALLETS",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = "Quick setup for two-phone offline payment testing:",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onResetPhoneA,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Phone A (₹500)", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onResetPhoneB,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Phone B (₹100)", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun PaymentTransactionItem(payment: PaymentEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Tx: ${payment.transactionId.take(12)}...",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = when (payment.status) {
                        "SETTLED" -> "✓ Settled on MST"
                        "OFFLINE_SENT", "PENDING_SETTLEMENT" -> "⏳ Pending Settlement"
                        else -> "Failed"
                    },
                    fontSize = 12.sp,
                    color = when (payment.status) {
                        "SETTLED" -> Color(0xFF2E7D32)
                        "OFFLINE_SENT", "PENDING_SETTLEMENT" -> Color(0xFFEF6C00)
                        else -> Color(0xFFC62828)
                    },
                )
            }
            Text(
                text = "₹%.2f".format(payment.amount / 100.0),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
