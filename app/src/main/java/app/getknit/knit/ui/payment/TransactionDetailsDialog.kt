package app.getknit.knit.ui.payment

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.getknit.knit.data.payment.PaymentEntity
import app.getknit.knit.payment.settlement.MstBlockchainConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VeyntraCardBg = Color(0xFFFFFFFF)
private val VeyntraCardBorder = Color(0xFFE5E7EB)
private val VeyntraBluePrimary = Color(0xFF1E66F5)
private val VeyntraGreenSuccess = Color(0xFF16A34A)
private val VeyntraGreenCardBg = Color(0xFFECFDF5)
private val VeyntraOrangeWarning = Color(0xFFD97706)
private val VeyntraAmberCardBg = Color(0xFFFEF3C7)
private val VeyntraTextDark = Color(0xFF111827)
private val VeyntraTextMuted = Color(0xFF6B7280)

@Suppress("DEPRECATION")
@Composable
fun TransactionDetailsDialog(
    payment: PaymentEntity,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.US) }
    val formattedDate = dateFormat.format(Date(payment.timestamp))

    val isSettled = payment.status == "SETTLED"
    val statusText = when (payment.status) {
        "SETTLED" -> "✓ Successfully Settled on MST Blockchain"
        "PENDING_SETTLEMENT", "RECEIVED", "OFFLINE_SENT" -> "⏳ Pending MST Blockchain Settlement"
        "SUBMITTED" -> "⏳ Settling on MST Blockchain..."
        else -> "Failed / Conflict"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VeyntraCardBorder, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Transaction Details",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = VeyntraTextDark,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = VeyntraTextMuted,
                        )
                    }
                }

                // Status Banner Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSettled) VeyntraGreenCardBg else VeyntraAmberCardBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isSettled) Icons.Default.CheckCircle else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isSettled) VeyntraGreenSuccess else VeyntraOrangeWarning,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSettled) VeyntraGreenSuccess else VeyntraOrangeWarning,
                        )
                    }
                }

                // Large Amount
                Text(
                    text = "₹%.2f".format(payment.amount / 100.0),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = VeyntraTextDark,
                )

                // Details Rows
                DetailItemRow(label = "Transaction ID", value = payment.transactionId, onCopy = {
                    clipboardManager.setText(AnnotatedString(payment.transactionId))
                })

                DetailItemRow(label = "Date & Time", value = formattedDate)

                DetailItemRow(label = "Sender Wallet", value = payment.senderWalletId, onCopy = {
                    clipboardManager.setText(AnnotatedString(payment.senderWalletId))
                })

                DetailItemRow(label = "Receiver Wallet", value = payment.receiverWalletId, onCopy = {
                    clipboardManager.setText(AnnotatedString(payment.receiverWalletId))
                })

                DetailItemRow(label = "Mesh Hop Count", value = "${payment.hopCount} hop(s)")

                if (!payment.blockchainTransactionHash.isNullOrBlank()) {
                    val txHash = payment.blockchainTransactionHash
                    DetailItemRow(label = "MST Tx Hash", value = txHash, onCopy = {
                        clipboardManager.setText(AnnotatedString(txHash))
                    })

                    Button(
                        onClick = {
                            val url = MstBlockchainConfig.getExplorerTxUrl(txHash)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VeyntraBluePrimary),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View on MSTScan Explorer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItemRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null,
) {
    Column {
        Text(text = label, fontSize = 11.sp, color = VeyntraTextMuted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = VeyntraTextDark,
                fontFamily = if (label.contains("Tx") || label.contains("ID")) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.weight(1f),
            )
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = VeyntraBluePrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
