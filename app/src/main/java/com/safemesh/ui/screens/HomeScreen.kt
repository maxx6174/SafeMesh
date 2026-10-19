package com.safemesh.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safemesh.SafeMeshApplication
import com.safemesh.ui.components.ConnectivityDot
import com.safemesh.ui.components.EmergencySosButton
import com.safemesh.ui.components.OfflineWarningBanner
import com.safemesh.ui.components.SafeMeshCard
import com.safemesh.ui.theme.EmergencyRed
import com.safemesh.ui.theme.EmergencyRedDark
import com.safemesh.ui.theme.MeshBlue
import com.safemesh.ui.theme.MeshGreen
import com.safemesh.ui.theme.MeshOrange
import com.safemesh.ui.theme.NavyBackground
import com.safemesh.ui.theme.NavyBorder
import com.safemesh.ui.theme.NavySurface
import com.safemesh.ui.theme.NavySurfaceVariant
import com.safemesh.ui.theme.TextMuted
import com.safemesh.ui.theme.TextPrimary
import com.safemesh.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: SafeMeshApplication,
    onNavigateToSos: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToPeers: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onEmergencyAlertClick: (String) -> Unit
) {
    val meshManager = app.bluetoothMeshManager
    val isMeshActive by meshManager.isMeshActive.collectAsState()
    val isBluetoothEnabled by meshManager.isBluetoothEnabled.collectAsState()
    val statusMessage by meshManager.statusMessage.collectAsState()

    val peers by app.peerRepository.allPeersFlow.collectAsState(initial = emptyList())
    val queueCount by app.storeCarryForwardEngine.pendingQueueCount.collectAsState()
    val latestEmergency by app.latestEmergencyPacket.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(EmergencyRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("SafeMesh", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                },
                actions = {
                    // Mesh status indicator pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isMeshActive) Color(0xFF064E3B) else Color(0xFF374151))
                            .border(1.dp, if (isMeshActive) MeshGreen else Color.Gray, RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ConnectivityDot(isActive = isMeshActive)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMeshActive) "MESH ON" else "OFFLINE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMeshActive) MeshGreen else Color.LightGray
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // High priority banner if emergency alert received
            if (latestEmergency != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEmergencyAlertClick(latestEmergency!!.messageId) },
                    colors = CardDefaults.cardColors(containerColor = EmergencyRedDark),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "EMERGENCY SOS RECEIVED!",
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "From Node ${latestEmergency!!.senderId.take(8)} • Tap to view location",
                                color = TextPrimary.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Mesh Connectivity Card
            SafeMeshCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isBluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                contentDescription = null,
                                tint = if (isBluetoothEnabled) MeshBlue else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBluetoothEnabled) "Bluetooth Ready" else "Bluetooth Disabled",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Node ID: ${app.messageRepository.localNodeId.take(8)}",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (isMeshActive) meshManager.stopMesh() else meshManager.startMesh()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMeshActive) Color(0xFF374151) else MeshGreen
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isMeshActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isMeshActive) "Stop" else "Start",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusMessage,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${peers.size} nearby device(s)",
                        color = MeshBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Prominent Emergency SOS Hold Button
            EmergencySosButton(
                onSosActivated = onNavigateToSos
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionCard(
                    title = "Send Message",
                    subtitle = "Encrypted peer chat",
                    icon = Icons.Default.Email,
                    iconTint = MeshBlue,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMessages
                )
                ActionCard(
                    title = "My Location",
                    subtitle = "GPS coordinates",
                    icon = Icons.Default.LocationOn,
                    iconTint = MeshGreen,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToLocation
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActionCard(
                    title = "Nearby Devices",
                    subtitle = "${peers.size} nodes found",
                    icon = Icons.Default.People,
                    iconTint = Color(0xFFA78BFA),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToPeers
                )
                ActionCard(
                    title = "Queue & Relays",
                    subtitle = "$queueCount pending",
                    icon = Icons.Default.Refresh,
                    iconTint = MeshOrange,
                    badge = if (queueCount > 0) "$queueCount" else null,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMessages
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Decentralized warning banner
            OfflineWarningBanner()
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NavySurface)
            .border(1.dp, NavyBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(EmergencyRed)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(badge, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
    }
}
