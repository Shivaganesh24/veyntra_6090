package app.getknit.knit.ui.payment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getknit.knit.ui.image.QrCode
import kotlinx.coroutines.launch

private val VeyntraBg = Color(0xFFF6F8FC)
private val VeyntraCardBg = Color(0xFFFFFFFF)
private val VeyntraCardBorder = Color(0xFFE5E7EB)

private val VeyntraBluePrimary = Color(0xFF1E66F5)

private val VeyntraTextDark = Color(0xFF111827)
private val VeyntraTextMuted = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit,
) {
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val walletId = wallet?.walletId ?: "wallet-unknown"
    val publicKey = wallet?.publicKey ?: ""
    val evmAddress = wallet?.evmAddress ?: ""
    val displayName = wallet?.displayName ?: "Veyntra Wallet"

    var requestedAmountText by remember { mutableStateOf("") }
    val requestedAmountPaise = (requestedAmountText.toLongOrNull() ?: 0L) * 100L

    val qrPayload =
        remember(walletId, publicKey, evmAddress, requestedAmountPaise) {
            OffPayQrPayload.encode(
                walletId = walletId,
                publicKey = publicKey,
                amount = requestedAmountPaise,
                evmAddress = evmAddress,
            )
        }

    val qrBitmap =
        remember(qrPayload) {
            QrCode.render(content = qrPayload, sizePx = 480)
        }

    Scaffold(
        containerColor = VeyntraBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Receive Money",
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
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Card(
                modifier = Modifier.size(260.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, VeyntraCardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "QR Code",
                            modifier = Modifier.size(220.dp),
                        )
                    } else {
                        Text("Generating QR Code...", color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = displayName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = VeyntraTextDark,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = VeyntraCardBg,
                border = BorderStroke(1.dp, VeyntraCardBorder),
            ) {
                Text(
                    text = "Wallet ID: $walletId",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = VeyntraTextMuted,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = requestedAmountText,
                onValueChange = { requestedAmountText = it.filter { char -> char.isDigit() } },
                label = { Text("Request Amount (Optional ₹)", color = VeyntraTextMuted) },
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

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Wallet ID", walletId)
                    clipboard.setPrimaryClip(clip)
                    scope.launch {
                        snackbarHostState.showSnackbar("Wallet ID copied to clipboard!")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = VeyntraBluePrimary,
                        contentColor = Color.White,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Wallet ID", fontWeight = FontWeight.Bold)
            }
        }
    }
}
