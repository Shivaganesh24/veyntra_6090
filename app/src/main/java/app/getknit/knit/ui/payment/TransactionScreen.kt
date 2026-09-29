package app.getknit.knit.ui.payment

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.getknit.knit.data.payment.PaymentEntity
import app.getknit.knit.payment.settlement.MstBlockchainConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VeyntraBg = Color(0xFFF6F8FC)
private val VeyntraCardBg = Color(0xFFFFFFFF)
private val VeyntraCardBorder = Color(0xFFE5E7EB)

private val VeyntraBluePrimary = Color(0xFF1E66F5)
private val VeyntraGreenSuccess = Color(0xFF16A34A)
private val VeyntraOrangeWarning = Color(0xFFD97706)

private val VeyntraTextDark = Color(0xFF111827)
private val VeyntraTextMuted = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit,
) {
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    var selectedPaymentForDetails by remember { mutableStateOf<PaymentEntity?>(null) }

    if (selectedPaymentForDetails != null) {
        TransactionDetailsDialog(
            payment = selectedPaymentForDetails!!,
            onDismiss = { selectedPaymentForDetails = null },
        )
    }

    Scaffold(
        containerColor = VeyntraBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Payment Transactions",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VeyntraBg,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (payments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No transactions found.",
                        color = VeyntraTextMuted,
                        fontSize = 15.sp,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(payments) { payment ->
                        FullTransactionCard(
                            payment = payment,
                            onClick = { selectedPaymentForDetails = payment },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullTransactionCard(
    payment: PaymentEntity,
    onClick: () -> Unit = {},
) {
    val dateFormat = rememberDateFormat()
    val formattedDate = dateFormat.format(Date(payment.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
        border = BorderStroke(1.dp, VeyntraCardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tx: ${payment.transactionId.take(16)}...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = VeyntraTextDark,
                )
                Text(
                    text = "₹%.2f".format(payment.amount / 100.0),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = VeyntraBluePrimary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("From: ${payment.senderWalletId}", fontSize = 12.sp, color = VeyntraTextDark)
            Text("To: ${payment.receiverWalletId}", fontSize = 12.sp, color = VeyntraTextDark)
            Text("Date: $formattedDate", fontSize = 12.sp, color = VeyntraTextMuted)
            Text("Mesh Hops: ${payment.hopCount} hop(s)", fontSize = 12.sp, color = VeyntraTextMuted)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (payment.status) {
                        "SETTLED" -> VeyntraGreenSuccess.copy(alpha = 0.15f)
                        "OFFLINE_SENT", "PENDING_SETTLEMENT" -> VeyntraOrangeWarning.copy(alpha = 0.15f)
                        else -> Color(0xFFFEE2E2)
                    },
                ) {
                    Text(
                        text = when (payment.status) {
                            "SETTLED" -> "✓ Successfully Settled on MST"
                            "PENDING_SETTLEMENT", "RECEIVED" -> "⏳ Pending Settlement"
                            "OFFLINE_SENT" -> "⏳ Sent Offline — Pending Settlement"
                            else -> payment.status
                        },
                        color = when (payment.status) {
                            "SETTLED" -> VeyntraGreenSuccess
                            "OFFLINE_SENT", "PENDING_SETTLEMENT" -> VeyntraOrangeWarning
                            else -> Color(0xFFDC2626)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                if (!payment.blockchainTransactionHash.isNullOrBlank()) {
                    val context = LocalContext.current
                    Surface(
                        onClick = {
                            val url = MstBlockchainConfig.getExplorerTxUrl(payment.blockchainTransactionHash)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = VeyntraBluePrimary,
                    ) {
                        Text(
                            text = "View on MSTScan (0x${payment.blockchainTransactionHash.take(8)}...)",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberDateFormat(): SimpleDateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US) }
