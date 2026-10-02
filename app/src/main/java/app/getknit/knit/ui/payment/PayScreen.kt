package app.getknit.knit.ui.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getknit.knit.mesh.Peer
import app.getknit.knit.ui.scan.QrScanner
import kotlinx.coroutines.launch

private val VeyntraBg = Color(0xFFF6F8FC)
private val VeyntraCardBg = Color(0xFFFFFFFF)
private val VeyntraCardBorder = Color(0xFFE5E7EB)

private val VeyntraBluePrimary = Color(0xFF1E66F5)
private val VeyntraBlueLight = Color(0xFFEFF6FF)

private val VeyntraGreenSuccess = Color(0xFF16A34A)
private val VeyntraGreenCardBg = Color(0xFFECFDF5)

private val VeyntraTextDark = Color(0xFF111827)
private val VeyntraTextMuted = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit,
) {
    val neighbors by viewModel.neighbors.collectAsStateWithLifecycle()
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()

    var receiverWalletId by remember { mutableStateOf("") }
    var receiverPublicKey by remember { mutableStateOf("") }
    var receiverEvmAddress by remember { mutableStateOf("") }
    var receiverName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("100") }
    var isScanning by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val availableBalance = wallet?.availableBalance ?: 0L
    val myWalletId = wallet?.walletId ?: ""

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
                        receiverWalletId = parsed.walletId
                        receiverPublicKey = parsed.publicKey
                        receiverEvmAddress = parsed.evmAddress
                        receiverName = parsed.walletId
                        if (parsed.amount > 0L) {
                            amountText = (parsed.amount / 100L).toString()
                        }
                        scope.launch {
                            snackbarHostState.showSnackbar("✓ Recipient QR Scanned: ${parsed.walletId}")
                        }
                    }
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Invalid or unsupported QR Code")
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
                    Text(
                        text = "Send Money",
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Balance Banner Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VeyntraGreenCardBg),
                border = BorderStroke(1.dp, VeyntraGreenSuccess.copy(alpha = 0.2f)),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Spendable Balance",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VeyntraTextDark,
                    )
                    Text(
                        text = "₹%.2f".format(availableBalance / 100.0),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VeyntraGreenSuccess,
                    )
                }
            }

            // Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { char -> char.isDigit() } },
                label = { Text("Amount (₹)", color = VeyntraTextMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VeyntraBluePrimary,
                        unfocusedBorderColor = VeyntraCardBorder,
                        focusedTextColor = VeyntraTextDark,
                        unfocusedTextColor = VeyntraTextDark,
                    ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Recipient Input
            OutlinedTextField(
                value = receiverWalletId,
                onValueChange = {
                    receiverWalletId = it
                    if (receiverPublicKey.isEmpty()) receiverPublicKey = it
                },
                label = { Text("Recipient Wallet ID / Public Key", color = VeyntraTextMuted) },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VeyntraBluePrimary,
                        unfocusedBorderColor = VeyntraCardBorder,
                        focusedTextColor = VeyntraTextDark,
                        unfocusedTextColor = VeyntraTextDark,
                    ),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { isScanning = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = VeyntraBluePrimary,
                        )
                    }
                },
            )

            if (receiverName.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VeyntraBlueLight,
                    border = BorderStroke(1.dp, VeyntraBluePrimary.copy(alpha = 0.2f)),
                ) {
                    Text(
                        text = "Selected: $receiverName",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = VeyntraBluePrimary,
                    )
                }
            }

            Text(
                text = "Nearby Discovered Mesh Devices:",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = VeyntraTextDark,
            )

            if (neighbors.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
                    border = BorderStroke(1.dp, VeyntraCardBorder),
                ) {
                    Text(
                        text = "Searching for nearby mesh devices... Or scan QR / enter ID above.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = VeyntraTextMuted,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(neighbors.toList()) { peer ->
                        PeerSelectionCard(
                            peer = peer,
                            isSelected = receiverWalletId == "wallet-" + peer.nodeId.take(8),
                            onSelect = {
                                receiverWalletId = "wallet-" + peer.nodeId.take(8)
                                receiverPublicKey = peer.nodeId
                                receiverName = peer.nodeId.take(12)
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                enabled = !isSubmitting,
                onClick = {
                    val amountPaise = (amountText.toLongOrNull() ?: 0L) * 100L
                    if (amountPaise <= 0L) {
                        scope.launch { snackbarHostState.showSnackbar("Enter a valid amount > 0") }
                        return@Button
                    }
                    if (amountPaise > availableBalance) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Insufficient funds! Available: ₹%.2f".format(availableBalance / 100.0),
                            )
                        }
                        return@Button
                    }
                    if (receiverWalletId.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("Select or enter a recipient ID") }
                        return@Button
                    }
                    if (myWalletId.isNotBlank() && receiverWalletId == myWalletId) {
                        scope.launch { snackbarHostState.showSnackbar("Cannot send payment to your own wallet!") }
                        return@Button
                    }

                    isSubmitting = true
                    viewModel.sendPayment(
                        receiverWalletId = receiverWalletId,
                        receiverPublicKey = if (receiverPublicKey.isNotBlank()) receiverPublicKey else receiverWalletId,
                        amount = amountPaise,
                        receiverEvmAddress = receiverEvmAddress,
                        onSuccess = {
                            isSubmitting = false
                            scope.launch {
                                snackbarHostState.showSnackbar("✓ Payment Sent over mesh network!")
                            }
                            onBack()
                        },
                        onError = { err ->
                            isSubmitting = false
                            scope.launch { snackbarHostState.showSnackbar(err) }
                        },
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = VeyntraBluePrimary,
                        contentColor = Color.White,
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSubmitting) "Processing..." else "Send ₹${amountText.ifEmpty { "0" }} Offline",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
fun PeerSelectionCard(
    peer: Peer,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isSelected) VeyntraBlueLight else VeyntraCardBg,
            ),
        border = BorderStroke(1.dp, if (isSelected) VeyntraBluePrimary else VeyntraCardBorder),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(VeyntraBluePrimary),
                    )
                    Text(
                        text = "Device: ${peer.nodeId.take(12)}...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = VeyntraTextDark,
                    )
                }
                Text(
                    text = "Wallet ID: wallet-${peer.nodeId.take(8)}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = VeyntraTextMuted,
                )
            }
            if (isSelected) {
                Text(
                    text = "SELECTED",
                    color = VeyntraBluePrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
