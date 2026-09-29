package app.getknit.knit.ui.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
private val VeyntraGreenSuccess = Color(0xFF16A34A)
private val VeyntraGreenCardBg = Color(0xFFECFDF5)

private val VeyntraTextDark = Color(0xFF111827)
private val VeyntraTextMuted = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshPaymentScreen(
    viewModel: PaymentViewModel,
    onBack: () -> Unit,
) {
    val neighbors by viewModel.neighbors.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = VeyntraBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mesh Network Diagnostics",
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Veyntra Mesh Transport Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = VeyntraTextDark,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = VeyntraBluePrimary,
                            )
                            Text("Bluetooth LE Mesh", color = VeyntraTextDark, fontSize = 14.sp)
                        }
                        Surface(
                            shape = CircleShape,
                            color = VeyntraGreenCardBg,
                        ) {
                            Text(
                                text = "Active",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = VeyntraGreenSuccess,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = VeyntraBluePrimary,
                            )
                            Text("Wi-Fi Aware NAN", color = VeyntraTextDark, fontSize = 14.sp)
                        }
                        Surface(
                            shape = CircleShape,
                            color = VeyntraGreenCardBg,
                        ) {
                            Text(
                                text = "Ready",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = VeyntraGreenSuccess,
                            )
                        }
                    }
                }
            }

            Text(
                text = "Discovered Mesh Peers (${neighbors.size}):",
                fontWeight = FontWeight.Bold,
                color = VeyntraTextDark,
                fontSize = 14.sp,
            )

            if (neighbors.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
                    border = BorderStroke(1.dp, VeyntraCardBorder),
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Searching for nearby mesh devices...",
                            color = VeyntraTextMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(neighbors.toList()) { peer ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = VeyntraCardBg),
                            border = BorderStroke(1.dp, VeyntraCardBorder),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(VeyntraBluePrimary),
                                    )
                                    Column {
                                        Text(
                                            text = "Peer: ${peer.nodeId.take(12)}...",
                                            fontWeight = FontWeight.Bold,
                                            color = VeyntraTextDark,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            text = "Wallet ID: wallet-${peer.nodeId.take(8)}",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = VeyntraTextMuted,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.Router,
                                    contentDescription = null,
                                    tint = VeyntraBluePrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
