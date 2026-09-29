package app.getknit.knit.ui.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getknit.knit.data.payment.PaymentEntity
import app.getknit.knit.payment.reconciliation.ReconciliationState
import app.getknit.knit.ui.scan.QrScanner
import kotlinx.coroutines.launch

// Modern Veyntra Color Palette (Matching Reference Screenshots)
private val VeyntraBg = Color(0xFFF6F8FC)
private val VeyntraCardBg = Color(0xFFFFFFFF)
private val VeyntraCardBorder = Color(0xFFE5E7EB)

private val VeyntraBluePrimary = Color(0xFF1E66F5)
private val VeyntraBlueLight = Color(0xFFEFF6FF)

private val VeyntraGreenSuccess = Color(0xFF16A34A)
private val VeyntraGreenCardBg = Color(0xFFECFDF5)
private val VeyntraGreenCardText = Color(0xFF047857)

private val VeyntraOrangeWarning = Color(0xFFD97706)
private val VeyntraAmberCardBg = Color(0xFFFEF3C7)
private val VeyntraAmberPill = Color(0xFFFFFBEB)

private val VeyntraTextDark = Color(0xFF111827)
private val VeyntraTextMuted = Color(0xFF6B7280)

@Suppress("UNUSED_PARAMETER")
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
    val neighbors by viewModel.neighbors.collectAsStateWithLifecycle()

    var isScanning by remember { mutableStateOf(false) }
    var selectedPaymentForDetails by remember { mutableStateOf<PaymentEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val myWalletId = wallet?.walletId ?: ""

    if (selectedPaymentForDetails != null) {
        TransactionDetailsDialog(
            payment = selectedPaymentForDetails!!,
            onDismiss = { selectedPaymentForDetails = null },
        )
    }

    if (isScanning) {
        QrScanner(
            onResult = { qrText ->
                isScanning = false
                val parsed = OffPayQrPayload.decode(qrText)
                if (parsed != null && parsed.walletId.isNotBlank()) {
                    if (myWalletId.isNotBlank() && parsed.walletId == myWalletId) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Cannot send payment to your own wallet!")
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("✓ Scanned Veyntra QR: ${parsed.walletId.take(14)}...")
                        }
                        onNavigatePay()
                    }
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Invalid or unsupported Veyntra QR Code")
                    }
                }
            },
            onCancel = { isScanning = false },
        )
        return
    }

    Scaffold(
        containerColor = VeyntraBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Veyntra",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = VeyntraBluePrimary,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(VeyntraGreenSuccess),
                            )
                            Text(
                                text = "Connected to ${neighbors.size} mesh nodes",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VeyntraTextDark,
                            )
                        }
                        Text(
                            text = "Pay offline. Settle when connected.",
                            fontSize = 11.sp,
                            color = VeyntraTextMuted,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToKnit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Mesh",
                            tint = VeyntraTextDark,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isScanning = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = VeyntraBluePrimary,
                        )
                    }
                    IconButton(onClick = onNavigateTransactions) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = VeyntraTextMuted,
                        )
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = VeyntraTextMuted,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = VeyntraBg,
                    ),
            )
        },
        bottomBar = {
            VeyntraBottomNavigationBar(
                activeTab = 0,
                onHomeClick = { /* Already on Home */ },
                onNearbyClick = onBackToKnit,
                onTransactionsClick = onNavigateTransactions,
                onSettingsClick = onNavigateSettings,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    // 1. MST Blockchain Online Status Card
                    MstStatusCard(
                        isOnline = isOnline,
                        reconState = reconState,
                        onSyncClick = { viewModel.reconcilePaymentsNow() },
                    )
                }

                item {
                    // 2. Veyntra Wallet Card
                    VeyntraWalletCard(
                        totalBalancePaise = wallet?.totalBalance ?: 0L,
                        availableBalancePaise = wallet?.availableBalance ?: 0L,
                        pendingOutboundPaise = wallet?.pendingOutbound ?: 0L,
                        pendingInboundPaise = wallet?.pendingInbound ?: 0L,
                    )
                }

                item {
                    // 3. Action Buttons Row (Send Money & Receive)
                    VeyntraActionRow(
                        onSendClick = onNavigatePay,
                        onReceiveClick = onNavigateReceive,
                    )
                }

                item {
                    // 4. Mesh Network Card
                    VeyntraMeshNetworkCard(
                        neighborsCount = neighbors.size,
                        onMeshClick = onNavigateMesh,
                    )
                }

                item {
                    // 5. Veyntra Demo Wallets Card
                    VeyntraDemoWalletsCard(
                        onResetPhoneA = { viewModel.resetDemoWallet(isSender = true) },
                        onResetPhoneB = { viewModel.resetDemoWallet(isSender = false) },
                    )
                }

                item {
                    // 6. Recent Transactions Section Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Recent Transactions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VeyntraTextDark,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onNavigateTransactions() },
                        ) {
                            Text(
                                text = "View all",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VeyntraBluePrimary,
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = VeyntraBluePrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                if (payments.isEmpty()) {
                    item {
                        VeyntraEmptyTransactionsCard()
                    }
                } else {
                    items(payments.take(5)) { payment ->
                        VeyntraTransactionItem(
                            payment = payment,
                            myWalletId = myWalletId,
                            onClick = { selectedPaymentForDetails = payment },
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun MstStatusCard(
    isOnline: Boolean,
    reconState: ReconciliationState,
    onSyncClick: () -> Unit,
) {
    val cardBg = if (isOnline) VeyntraGreenCardBg else VeyntraAmberCardBg
    val iconColor = if (isOnline) VeyntraGreenSuccess else VeyntraOrangeWarning
    val titleText =
        when {
            !isOnline -> "Offline — Mesh active"
            reconState == ReconciliationState.SYNCING -> "Online — Auto-settling on MST Blockchain..."
            reconState == ReconciliationState.UNCONFIGURED -> "Online — MST Testnet Unconfigured"
            else -> "Online — Connected to MST Testnet"
        }
    val subtitleText = if (isOnline) "Your wallet is synced and ready" else "Payments send nearby & settle when online"

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, iconColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.Router else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Column {
                    Text(
                        text = titleText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = VeyntraGreenCardText,
                    )
                    Text(
                        text = subtitleText,
                        fontSize = 11.sp,
                        color = VeyntraTextMuted,
                    )
                }
            }

            if (isOnline && reconState != ReconciliationState.SYNCING) {
                IconButton(onClick = onSyncClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync",
                        tint = VeyntraGreenCardText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun VeyntraWalletCard(
    totalBalancePaise: Long,
    availableBalancePaise: Long,
    pendingOutboundPaise: Long,
    pendingInboundPaise: Long,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, VeyntraCardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VeyntraBlueLight,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = VeyntraBluePrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column {
                    Text(
                        text = "Veyntra Wallet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = VeyntraTextDark,
                    )
                    Text(
                        text = "Available to spend",
                        fontSize = 11.sp,
                        color = VeyntraBluePrimary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                text = "₹%.2f".format(availableBalancePaise / 100.0),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = VeyntraTextDark,
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Total / Settled", fontSize = 11.sp, color = VeyntraTextMuted)
                    Text(
                        "₹%.2f".format(totalBalancePaise / 100.0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = VeyntraGreenSuccess,
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .height(30.dp)
                            .width(1.dp)
                            .background(VeyntraCardBorder),
                )

                Column {
                    Text("Pending Outbound", fontSize = 11.sp, color = VeyntraTextMuted)
                    Text(
                        "₹%.2f".format(pendingOutboundPaise / 100.0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = VeyntraOrangeWarning,
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .height(30.dp)
                            .width(1.dp)
                            .background(VeyntraCardBorder),
                )

                Column {
                    Text("Pending Inbound", fontSize = 11.sp, color = VeyntraTextMuted)
                    Text(
                        "₹%.2f".format(pendingInboundPaise / 100.0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = VeyntraBluePrimary,
                    )
                }
            }
        }
    }
}

@Composable
fun VeyntraActionRow(
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Send Money Button (Solid Blue)
        Surface(
            modifier =
                Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSendClick() },
            shape = RoundedCornerShape(20.dp),
            color = VeyntraBluePrimary,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Column {
                    Text(
                        text = "Send Money",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "Pay nearby or offline",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // Receive Button (Light Blue)
        Surface(
            modifier =
                Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onReceiveClick() },
            shape = RoundedCornerShape(20.dp),
            color = VeyntraBlueLight,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = VeyntraBluePrimary.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = VeyntraBluePrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Column {
                    Text(
                        text = "Receive",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = VeyntraTextDark,
                    )
                    Text(
                        text = "Show QR or share link",
                        fontSize = 11.sp,
                        color = VeyntraTextMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun VeyntraMeshNetworkCard(
    neighborsCount: Int,
    onMeshClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, VeyntraCardBorder, RoundedCornerShape(16.dp))
                .clickable { onMeshClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = VeyntraGreenCardBg,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = VeyntraGreenSuccess,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Mesh Network",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = VeyntraTextDark,
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(VeyntraGreenSuccess),
                            )
                            Text(
                                text = "Connected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VeyntraGreenSuccess,
                            )
                        }
                        Text(
                            text = "$neighborsCount nearby nodes · Your phone is part of the mesh.",
                            fontSize = 11.sp,
                            color = VeyntraTextMuted,
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = VeyntraTextMuted,
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Bluetooth
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = VeyntraBluePrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Column {
                        Text("Bluetooth", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VeyntraTextDark)
                        Text("$neighborsCount linked", fontSize = 10.sp, color = VeyntraTextMuted)
                    }
                }

                // Wi-Fi Aware
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = VeyntraBluePrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Column {
                        Text("Wi-Fi Aware", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VeyntraTextDark)
                        Text("Ready", fontSize = 10.sp, color = VeyntraTextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun VeyntraDemoWalletsCard(
    onResetPhoneA: () -> Unit,
    onResetPhoneB: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, VeyntraOrangeWarning.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VeyntraAmberCardBg),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = VeyntraOrangeWarning.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = VeyntraOrangeWarning,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Text(
                    text = "Veyntra Demo Wallets",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = VeyntraTextDark,
                )
            }

            Text(
                text = "Quick setup for two-phone offline payment testing:",
                fontSize = 11.sp,
                color = VeyntraTextMuted,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onResetPhoneA() },
                    shape = RoundedCornerShape(20.dp),
                    color = VeyntraAmberPill,
                    border = BorderStroke(1.dp, VeyntraOrangeWarning.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Phone A (₹500)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VeyntraOrangeWarning,
                        )
                    }
                }

                Surface(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onResetPhoneB() },
                    shape = RoundedCornerShape(20.dp),
                    color = VeyntraAmberPill,
                    border = BorderStroke(1.dp, VeyntraOrangeWarning.copy(alpha = 0.3f)),
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Phone B (₹100)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VeyntraOrangeWarning,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VeyntraEmptyTransactionsCard() {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, VeyntraCardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = VeyntraTextMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(38.dp),
            )
            Text(
                text = "No payment transactions yet.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = VeyntraTextDark,
            )
            Text(
                text = "Your offline payments will appear here.",
                fontSize = 12.sp,
                color = VeyntraTextMuted,
            )
        }
    }
}

@Composable
fun VeyntraTransactionItem(
    payment: PaymentEntity,
    myWalletId: String = "",
    onClick: () -> Unit = {},
) {
    val rawMyNodeId = myWalletId.removePrefix("wallet-")
    val isReceived =
        payment.receiverWalletId == myWalletId ||
            (rawMyNodeId.isNotBlank() && payment.receiverWalletId.contains(rawMyNodeId)) ||
            payment.status == "PENDING_SETTLEMENT" ||
            payment.status == "RECEIVED"

    val amountPrefix = if (isReceived) "+ " else "- "
    val amountColor = if (isReceived) VeyntraGreenSuccess else VeyntraTextDark

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, VeyntraCardBorder, RoundedCornerShape(12.dp))
                .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Tx: ${payment.transactionId.take(14)}...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = VeyntraTextDark,
                )
                val statusText =
                    when (payment.status) {
                        "SETTLED" -> if (isReceived) "Received — Successfully Settled ✓" else "Sent — Successfully Settled ✓"
                        "PENDING_SETTLEMENT", "RECEIVED" -> "Received Offline — Pending Settlement"
                        "OFFLINE_SENT" -> "Sent Offline — Pending Settlement"
                        "SUBMITTED" -> "Settling on MST..."
                        else -> payment.status
                    }
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        when (payment.status) {
                            "SETTLED" -> VeyntraGreenSuccess
                            "OFFLINE_SENT", "PENDING_SETTLEMENT", "RECEIVED" -> VeyntraOrangeWarning
                            else -> Color(0xFFEF4444)
                        },
                )
                if (!payment.blockchainTransactionHash.isNullOrBlank()) {
                    Text(
                        text = "MST Tx: ${payment.blockchainTransactionHash.take(16)}...",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = VeyntraBluePrimary,
                    )
                }
            }
            Text(
                text = "$amountPrefix₹%.2f".format(payment.amount / 100.0),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = amountColor,
            )
        }
    }
}

@Composable
fun VeyntraBottomNavigationBar(
    activeTab: Int,
    onHomeClick: () -> Unit,
    onNearbyClick: () -> Unit,
    onTransactionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    NavigationBar(
        containerColor = VeyntraCardBg,
        tonalElevation = 6.dp,
    ) {
        NavigationBarItem(
            selected = activeTab == 0,
            onClick = onHomeClick,
            icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Home") },
            label = { Text("Home", fontSize = 11.sp, fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium) },
            colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor = VeyntraBluePrimary,
                    selectedTextColor = VeyntraBluePrimary,
                    unselectedIconColor = VeyntraTextMuted,
                    unselectedTextColor = VeyntraTextMuted,
                    indicatorColor = VeyntraBlueLight,
                ),
        )
        NavigationBarItem(
            selected = activeTab == 1,
            onClick = onNearbyClick,
            icon = { Icon(Icons.Default.Share, contentDescription = "Nearby") },
            label = { Text("Nearby", fontSize = 11.sp, fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium) },
            colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor = VeyntraBluePrimary,
                    selectedTextColor = VeyntraBluePrimary,
                    unselectedIconColor = VeyntraTextMuted,
                    unselectedTextColor = VeyntraTextMuted,
                    indicatorColor = VeyntraBlueLight,
                ),
        )
        NavigationBarItem(
            selected = activeTab == 2,
            onClick = onTransactionsClick,
            icon = { Icon(Icons.Default.History, contentDescription = "Transactions") },
            label = { Text("Transactions", fontSize = 11.sp, fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Medium) },
            colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor = VeyntraBluePrimary,
                    selectedTextColor = VeyntraBluePrimary,
                    unselectedIconColor = VeyntraTextMuted,
                    unselectedTextColor = VeyntraTextMuted,
                    indicatorColor = VeyntraBlueLight,
                ),
        )
        NavigationBarItem(
            selected = activeTab == 3,
            onClick = onSettingsClick,
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings", fontSize = 11.sp, fontWeight = if (activeTab == 3) FontWeight.Bold else FontWeight.Medium) },
            colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor = VeyntraBluePrimary,
                    selectedTextColor = VeyntraBluePrimary,
                    unselectedIconColor = VeyntraTextMuted,
                    unselectedTextColor = VeyntraTextMuted,
                    indicatorColor = VeyntraBlueLight,
                ),
        )
    }
}
