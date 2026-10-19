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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safemesh.SafeMeshApplication
import com.safemesh.data.database.PeerEntity
import com.safemesh.model.PeerConnectionState
import com.safemesh.ui.components.ConnectivityDot
import com.safemesh.ui.components.SafeMeshCard
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyDevicesScreen(
    app: SafeMeshApplication,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val peers by app.peerRepository.allPeersFlow.collectAsState(initial = emptyList())
    val isScanning by app.bluetoothMeshManager.isMeshActive.collectAsState()

    var isDemoMode by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby Participating Devices", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            app.bluetoothMeshManager.startMesh()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = TextPrimary)
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
                .padding(16.dp)
        ) {
            // Demo Mode Switch Card (Explicitly labeled as required by prompt)
            SafeMeshCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Demonstration Mode",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Simulate virtual nodes for multi-hop UI testing without physical second phone.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = isDemoMode,
                        onCheckedChange = { enabled ->
                            isDemoMode = enabled
                            scope.launch {
                                if (enabled) {
                                    app.peerRepository.insertDemoPeers()
                                } else {
                                    app.peerRepository.clearAll()
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = MeshBlue
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Discovery Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NavySurface)
                    .border(1.dp, NavyBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        tint = MeshBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isScanning) "Active BLE Mesh Discovery" else "Mesh Idle",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "${peers.size} Found",
                    color = MeshGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (peers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No SafeMesh devices detected nearby",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ensure another phone is running SafeMesh with Bluetooth enabled, or turn on Demonstration Mode above.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(peers, key = { it.peerId }) { peer ->
                        PeerItemCard(peer = peer, timeFormatted = dateFormat.format(Date(peer.lastSeen)))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerItemCard(peer: PeerEntity, timeFormatted: String) {
    SafeMeshCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (peer.isDemonstration) Color(0xFF4C1D95) else Color(0xFF1E3A8A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = peer.peerId.take(2).uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = peer.deviceName ?: "SafeMesh Node",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        if (peer.isDemonstration) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF7C3AED))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text("DEMO", color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = "ID: ${peer.peerId} • ${peer.bluetoothAddress ?: "BLE Direct"}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Connection state badge
            val (badgeBg, badgeText, badgeLabel) = when (peer.connectionState) {
                PeerConnectionState.CONNECTED -> Triple(Color(0xFF065F46), MeshGreen, "Connected")
                PeerConnectionState.CONNECTING -> Triple(Color(0xFF78350F), Color(0xFFFBBF24), "Connecting")
                PeerConnectionState.DISCONNECTED -> Triple(NavySurfaceVariant, TextSecondary, "In Range")
                PeerConnectionState.FAILED -> Triple(Color(0xFF7F1D1D), EmergencyRed, "Failed")
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(badgeLabel, color = badgeText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "RSSI: ${peer.rssi} dBm",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Text(
                text = "Transfers: ${peer.transferCount}",
                color = MeshBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Last seen: $timeFormatted",
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        if (peer.lastTransferStatus != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Status: ${peer.lastTransferStatus}",
                color = MeshGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
