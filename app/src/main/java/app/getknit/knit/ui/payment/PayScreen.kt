package app.getknit.knit.ui.payment

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getknit.knit.mesh.Peer
import app.getknit.knit.ui.scan.QrScanner
import kotlinx.coroutines.launch

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
                        snackbarHostState.showSnackbar("Invalid or unsupported OffPay QR Code")
                    }
                }
            },
            onCancel = { isScanning = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Money (OffPay)", fontWeight = FontWeight.Bold) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Spendable Available:")
                    Text(
                        "₹%.2f".format(availableBalance / 100.0),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20),
                    )
                }
            }

            // Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { char -> char.isDigit() } },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Recipient Wallet ID or Selected Peer
            OutlinedTextField(
                value = receiverWalletId,
                onValueChange = {
                    receiverWalletId = it
                    if (receiverPublicKey.isEmpty()) receiverPublicKey = it
                },
                label = { Text("Recipient OffPay ID / Public Key") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { isScanning = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                    }
                },
            )

            if (receiverName.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                ) {
                    Text(
                        text = "Selected Recipient: $receiverName",
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text("Nearby Discovered OffPay Devices:", fontWeight = FontWeight.Bold)

            if (neighbors.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        "Searching for nearby OffPay devices... Or tap QR icon / enter ID above.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
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
                        scope.launch { snackbarHostState.showSnackbar("Select or enter a recipient OffPay ID") }
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
                        onSuccess = {
                            isSubmitting = false
                            scope.launch {
                                val msg = "✓ Payment Created — Sent through nearby device network. " +
                                    "Waiting for Internet to settle."
                                snackbarHostState.showSnackbar(msg)
                            }
                            onBack()
                        },
                        onError = { err ->
                            isSubmitting = false
                            scope.launch { snackbarHostState.showSnackbar(err) }
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isSubmitting) "Processing..." else "↑ Send Offline (₹${amountText.ifEmpty { "0" }})")
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
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
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
                    text = "Nearby Device: ${peer.nodeId.take(12)}...",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "OffPay ID: wallet-${peer.nodeId.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (isSelected) {
                Text("SELECTED", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
