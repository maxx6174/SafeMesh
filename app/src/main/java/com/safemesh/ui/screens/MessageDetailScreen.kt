package com.safemesh.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safemesh.SafeMeshApplication
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
fun MessageDetailScreen(
    app: SafeMeshApplication,
    messageId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val message by app.messageRepository.getMessageByIdFlow(messageId).collectAsState(initial = null)
    val transferRecords by app.database.transferRecordDao().getRecordsForMessageFlow(messageId).collectAsState(initial = emptyList())

    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message Details", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
                    Text("Message not found", color = TextSecondary)
                }
            } else {
                val msg = message!!

                // Payload Card
                SafeMeshCard(borderColor = if (msg.isEmergency) EmergencyRed else NavyBorder) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (msg.isEmergency) "EMERGENCY SOS PACKET" else "DECENTRALIZED MESSAGE",
                            fontWeight = FontWeight.Bold,
                            color = if (msg.isEmergency) EmergencyRed else MeshBlue,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        StatusPill(status = msg.status)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = msg.plaintextPreview ?: "[Decrypted on intended recipient device only]",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Created: ${dateFormat.format(Date(msg.createdAt))}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Location Details if attached
                if (msg.latitude != null && msg.longitude != null) {
                    SafeMeshCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Attached Coordinates", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "%.5f, %.5f (Accuracy: ±%.1fm)".format(msg.latitude, msg.longitude, msg.locationAccuracy ?: 0f),
                                    color = MeshGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = {
                                    val geoUri = Uri.parse("geo:${msg.latitude},${msg.longitude}?q=${msg.latitude},${msg.longitude}(Emergency+Location)")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                    try {
                                        context.startActivity(mapIntent)
                                    } catch (e: Exception) {
                                        // Map app not installed
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MeshGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, tint = NavyBackground, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("View Map", color = NavyBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Envelope Security & Cryptography Info
                SafeMeshCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MeshBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cryptographic Envelope", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    DetailItem("Message ID", msg.messageId)
                    DetailItem("Sender ID", msg.senderId)
                    DetailItem("Recipient", msg.recipientId)
                    DetailItem("Encryption Primitive", "AES-256-GCM (Authenticated AEAD)")
                    DetailItem("Key Agreement", if (msg.ephemeralPublicKey != null) "ECDH secp256r1" else "Broadcast Channel")
                    DetailItem("Integrity Auth", "HMAC-SHA256 (${msg.authSignature.take(16)}...)")
                    DetailItem("Initialization Vector", "${msg.iv.take(16)}... (12-byte GCM)")
                    DetailItem("Secret Keys", "Protected in Keystore (Never logged)")
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Routing & Store-Carry-Forward Metrics
                SafeMeshCard {
                    Text("Store-Carry-Forward Metrics", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    DetailItem("Confirmed Relays", "${msg.relayCount} device transfer(s)")
                    DetailItem("Hop Count", "${msg.hopCount} of ${msg.hopLimit} max allowed")
                    DetailItem("Delivery Ack", if (msg.deliveryAckReceived) "CONFIRMED by recipient" else "PENDING")
                    DetailItem("Expires At", dateFormat.format(Date(msg.expiresAt)))
                    DetailItem("Local Storage", "Persistent Room Database")
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Transfer records
                SafeMeshCard {
                    Text("Peer Hop Logs (${transferRecords.size})", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (transferRecords.isEmpty()) {
                        Text("No transfers recorded yet.", color = TextMuted, fontSize = 12.sp)
                    } else {
                        transferRecords.forEach { record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${record.direction.name} Node: ${record.peerId.take(8)}", color = TextSecondary, fontSize = 12.sp)
                                Text(dateFormat.format(Date(record.timestamp)), color = MeshBlue, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
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
