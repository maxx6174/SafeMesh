package com.safemesh.ui.screens

import android.content.Context
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.safemesh.service.SafeMeshForegroundService
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: SafeMeshApplication,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("safemesh_prefs", Context.MODE_PRIVATE) }

    var autoForward by remember { mutableStateOf(prefs.getBoolean("auto_forward", true)) }
    var locationSharing by remember { mutableStateOf(prefs.getBoolean("location_sharing", true)) }
    var lowPowerDiscovery by remember { mutableStateOf(prefs.getBoolean("low_power_discovery", false)) }
    var backgroundServiceEnabled by remember { mutableStateOf(prefs.getBoolean("background_service", true)) }
    var expiryHours by remember { mutableIntStateOf(prefs.getInt("expiry_hours", 24)) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val isMeshActive by app.bluetoothMeshManager.isMeshActive.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Privacy", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
            // Node Identity Card
            SafeMeshCard {
                Text("Device Cryptographic Identity", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Ephemeral Node ID: ${app.messageRepository.localNodeId}", color = MeshBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Public Key: secp256r1 EC curve (P-256)\nDecentralized, self-sovereign identity without online registration.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mesh Routing Preferences
            SafeMeshCard {
                Text("Mesh & Routing Preferences", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(10.dp))

                SettingToggleRow(
                    title = "Automatic Store-Carry-Forward",
                    subtitle = "Relay verified emergency SOS packets when encountering other phones",
                    checked = autoForward,
                    onCheckedChange = {
                        autoForward = it
                        prefs.edit().putBoolean("auto_forward", it).apply()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingToggleRow(
                    title = "Location Sharing in SOS",
                    subtitle = "Include GPS coordinates in emergency messages when acquired",
                    checked = locationSharing,
                    onCheckedChange = {
                        locationSharing = it
                        prefs.edit().putBoolean("location_sharing", it).apply()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingToggleRow(
                    title = "Battery-Conscious Discovery",
                    subtitle = "Lower BLE advertising & scan duty cycle to conserve battery life",
                    checked = lowPowerDiscovery,
                    onCheckedChange = {
                        lowPowerDiscovery = it
                        prefs.edit().putBoolean("low_power_discovery", it).apply()
                        if (isMeshActive) {
                            app.bluetoothMeshManager.startMesh(lowPowerMode = it)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingToggleRow(
                    title = "Background Mesh Relay Service",
                    subtitle = "Keep Bluetooth mesh alive in Android foreground service",
                    checked = backgroundServiceEnabled,
                    onCheckedChange = {
                        backgroundServiceEnabled = it
                        prefs.edit().putBoolean("background_service", it).apply()
                        if (it) {
                            SafeMeshForegroundService.startService(context)
                        } else {
                            SafeMeshForegroundService.stopService(context)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Background Restrictions Explanation Card
            SafeMeshCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = MeshBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Android Background Restrictions", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Android strictly limits background Bluetooth scanning when the screen is locked to save battery. SafeMesh uses a foreground notification service ('connectedDevice' type) to maximize relay reliability. To prevent Android from terminating mesh relaying, disable Battery Optimization for SafeMesh in system settings.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Security & Encryption Model Documentation
            SafeMeshCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MeshGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Security Architecture & Encryption", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Transport Envelope: Clear metadata (Version, Message ID, Expiry, Hop Count) validated before relaying.\n• Directed Messages: End-to-end encrypted with ECDH (secp256r1) + AES-256-GCM. Intermediary relays cannot read plaintext.\n• Broadcast Emergency SOS: Transport payload encrypted with SafeMesh Universal Broadcast AEAD key so any authorized responder node can decrypt and render location while eavesdroppers without protocol parsing are prevented.\n• Envelope Integrity: HMAC-SHA256 tampering prevention.\n• Zero Central Dependencies: No accounts, no backend, no cloud.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // App Version Info
            SafeMeshCard {
                Text("SafeMesh Prototype Info", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Version: 1.0.0 (Offline Mesh Edition)\nProtocol: SafeMesh Binary Framer v1\nArchitecture: MVVM + Room + Coroutines + BLE GATT", color = TextMuted, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Clear Data Button
            Button(
                onClick = { showDeleteConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRedDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = TextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear All Local Messages & Cache", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Clear All Local Data?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will delete all stored emergency messages, relay records, and peer history from this device's Room database.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            app.messageRepository.clearAllData()
                            app.peerRepository.clearAll()
                            showDeleteConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("Delete", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = NavySurface
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = MeshBlue)
        )
    }
}
