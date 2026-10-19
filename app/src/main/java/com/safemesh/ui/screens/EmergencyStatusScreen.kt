package com.safemesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safemesh.SafeMeshApplication
import com.safemesh.model.DeliveryStatus
import com.safemesh.ui.components.OfflineWarningBanner
import com.safemesh.ui.components.SafeMeshCard
import com.safemesh.ui.components.StatusPill
import com.safemesh.ui.theme.EmergencyRed
import com.safemesh.ui.theme.MeshBlue
import com.safemesh.ui.theme.MeshGreen
import com.safemesh.ui.theme.NavyBackground
import com.safemesh.ui.theme.NavyBorder
import com.safemesh.ui.theme.NavySurface
import com.safemesh.ui.theme.NavySurfaceVariant
import com.safemesh.ui.theme.TextMuted
import com.safemesh.ui.theme.TextPrimary
import com.safemesh.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyStatusScreen(
    app: SafeMeshApplication,
    messageId: String,
    onNavigateBack: () -> Unit
) {
    val messageFlow = app.messageRepository.getMessageByIdFlow(messageId)
    val message by messageFlow.collectAsState(initial = null)
    val transferRecords by app.database.transferRecordDao().getRecordsForMessageFlow(messageId).collectAsState(initial = emptyList())

    val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency SOS Status", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground)
            )
        },
        containerColor = NavyBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (message == null) {
                SafeMeshCard {
                    Text("Loading message status...", color = TextSecondary)
                }
            } else {
                val msg = message!!

                // Status Header Card
                SafeMeshCard(borderColor = if (msg.deliveryAckReceived) MeshGreen else EmergencyRed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LIFECYCLE STATUS",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            StatusPill(status = msg.status)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(NavySurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${msg.relayCount} Relay Transfer(s)",
                                color = MeshBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = msg.plaintextPreview ?: "[Encrypted Emergency Payload]",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Created: ${dateFormat.format(Date(msg.createdAt))}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Delivery Stepper
                SafeMeshCard {
                    Text(
                        text = "Multi-Hop Delivery Progress",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val stepSaved = true
                    val stepRelayed = msg.relayCount > 0 || msg.status == DeliveryStatus.RELAYED || msg.status == DeliveryStatus.DELIVERED
                    val stepDelivered = msg.deliveryAckReceived

                    DeliveryStepItem(
                        title = "Saved Locally",
                        subtitle = "Packet persisted in encrypted database",
                        isCompleted = stepSaved,
                        isCurrent = msg.status == DeliveryStatus.SAVED_LOCAL
                    )
                    DeliveryStepDivider(isCompleted = stepRelayed)
                    DeliveryStepItem(
                        title = "Stored on Relay Nodes",
                        subtitle = if (msg.relayCount > 0) "Transferred to ${msg.relayCount} intermediate phone(s)" else "Searching for compatible SafeMesh nodes",
                        isCompleted = stepRelayed,
                        isCurrent = msg.status == DeliveryStatus.SEARCHING || msg.status == DeliveryStatus.TRANSFERRING
                    )
                    DeliveryStepDivider(isCompleted = stepDelivered)
                    DeliveryStepItem(
                        title = "Delivered to Intended Recipient",
                        subtitle = if (stepDelivered) "Confirmed by recipient delivery acknowledgement" else "Pending recipient / gateway acknowledgement",
                        isCompleted = stepDelivered,
                        isCurrent = msg.status == DeliveryStatus.DELIVERED
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Metadata Details
                SafeMeshCard {
                    Text("Packet Diagnostics", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    DiagnosticRow("Message ID", msg.messageId)
                    DiagnosticRow("Sender ID", msg.senderId)
                    DiagnosticRow("Recipient", msg.recipientId)
                    DiagnosticRow("Hop Count / Limit", "${msg.hopCount} / ${msg.hopLimit}")
                    DiagnosticRow("Security", "AES-256-GCM + HMAC Integrity")
                    DiagnosticRow("Expires At", dateFormat.format(Date(msg.expiresAt)))

                    if (msg.latitude != null && msg.longitude != null) {
                        DiagnosticRow("Coordinates", "%.4f, %.4f (±%.1fm)".format(msg.latitude, msg.longitude, msg.locationAccuracy ?: 0f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transfer records log
                SafeMeshCard {
                    Text("Relay Transfer History (${transferRecords.size})", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (transferRecords.isEmpty()) {
                        Text("No peer transfers recorded yet.", color = TextMuted, fontSize = 13.sp)
                    } else {
                        transferRecords.forEach { record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${record.direction.name} -> Node ${record.peerId.take(8)}",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = dateFormat.format(Date(record.timestamp)),
                                    color = MeshGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                OfflineWarningBanner()
            }
        }
    }
}

@Composable
private fun DeliveryStepItem(
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    isCurrent: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isCompleted) MeshGreen else if (isCurrent) MeshBlue else NavySurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isCompleted || isCurrent) TextPrimary else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DeliveryStepDivider(isCompleted: Boolean) {
    Box(
        modifier = Modifier
            .padding(start = 11.dp, top = 2.dp, bottom = 2.dp)
            .width(2.dp)
            .height(20.dp)
            .background(if (isCompleted) MeshGreen else NavyBorder)
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
