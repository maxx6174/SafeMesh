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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safemesh.SafeMeshApplication
import com.safemesh.model.MessageType
import com.safemesh.protocol.SafeMeshPacket
import com.safemesh.ui.components.SafeMeshCard
import com.safemesh.ui.theme.EmergencyRed
import com.safemesh.ui.theme.EmergencyRedDark
import com.safemesh.ui.theme.MeshBlue
import com.safemesh.ui.theme.MeshGreen
import com.safemesh.ui.theme.NavyBackground
import com.safemesh.ui.theme.NavyBorder
import com.safemesh.ui.theme.NavySurface
import com.safemesh.ui.theme.NavySurfaceVariant
import com.safemesh.ui.theme.TextMuted
import com.safemesh.ui.theme.TextPrimary
import com.safemesh.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivedEmergencyScreen(
    app: SafeMeshApplication,
    messageId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val message by app.messageRepository.getMessageByIdFlow(messageId).collectAsState(initial = null)
    var ackSent by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Alert", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EmergencyRedDark)
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
                    Text("Emergency message not found", color = TextSecondary)
                }
            } else {
                val msg = message!!

                // High-Contrast Red Alert Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(EmergencyRedDark)
                        .border(2.dp, EmergencyRed, RoundedCornerShape(16.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(EmergencyRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "EMERGENCY SOS BROADCAST",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "From Node ${msg.senderId}",
                                    color = TextPrimary.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = msg.plaintextPreview ?: "[Encrypted Emergency Information]",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Received: ${dateFormat.format(Date(msg.createdAt))}",
                            color = TextPrimary.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // GPS Location Details Card
                SafeMeshCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MeshGreen, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Emergency Location Coordinates", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (msg.latitude != null && msg.longitude != null) {
                        Text(
                            text = "Latitude: %.5f\nLongitude: %.5f".format(msg.latitude, msg.longitude),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 22.sp
                        )
                        if (msg.locationAccuracy != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Estimated GPS Accuracy: ±%.1f meters".format(msg.locationAccuracy),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Explicit button to view location - does not auto-launch external apps
                        Button(
                            onClick = {
                                val uri = Uri.parse("geo:${msg.latitude},${msg.longitude}?q=${msg.latitude},${msg.longitude}(SOS+Sender+Location)")
                                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                try {
                                    context.startActivity(mapIntent)
                                } catch (e: Exception) {
                                    // Map viewer not installed
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MeshGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = NavyBackground)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open in Map Application", color = NavyBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        Text(
                            text = "No GPS coordinates were included by the sender for this SOS.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Acknowledge Receipt Button
                Button(
                    onClick = {
                        scope.launch {
                            // Creates and queues a DELIVERY_ACK packet to return through the mesh
                            ackSent = true
                            app.messageRepository.recordDeliveredAck(msg.messageId, msg.senderId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ackSent) Color(0xFF065F46) else MeshBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !ackSent
                ) {
                    Icon(
                        imageVector = if (ackSent) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = if (ackSent) TextPrimary else NavyBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (ackSent) "DELIVERY RECEIPT ACKNOWLEDGED" else "ACKNOWLEDGE RECEIPT OF SOS",
                        color = if (ackSent) TextPrimary else NavyBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
