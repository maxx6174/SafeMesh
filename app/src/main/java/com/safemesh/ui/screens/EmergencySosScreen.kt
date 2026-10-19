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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safemesh.SafeMeshApplication
import com.safemesh.protocol.SafeMeshPacket
import com.safemesh.ui.components.OfflineWarningBanner
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySosScreen(
    app: SafeMeshApplication,
    onNavigateBack: () -> Unit,
    onSosDispatched: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val locationProvider = app.locationProvider
    val locInfo by locationProvider.currentLocation.collectAsState()

    var selectedPreset by remember { mutableStateOf("Medical Emergency") }
    var detailsText by remember { mutableStateOf("") }
    var attachLocation by remember { mutableStateOf(true) }
    var recipientOption by remember { mutableStateOf(SafeMeshPacket.BROADCAST_RECIPIENT) }
    var isSending by remember { mutableStateOf(false) }

    // 3-second cancellation countdown window
    var isCountingDown by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(3) }

    val presets = listOf(
        "Medical Emergency",
        "Trapped / Search & Rescue",
        "Fire / Hazardous Situation",
        "Natural Disaster / Flood",
        "Evacuation Assistance"
    )

    LaunchedEffect(Unit) {
        locationProvider.requestSingleUpdate { }
    }

    LaunchedEffect(isCountingDown) {
        if (isCountingDown) {
            countdownSeconds = 3
            while (countdownSeconds > 0) {
                delay(1000)
                countdownSeconds--
            }
            if (isCountingDown) {
                isCountingDown = false
                isSending = true
                scope.launch {
                    val fullMessage = if (detailsText.isBlank()) {
                        selectedPreset
                    } else {
                        "$selectedPreset: $detailsText"
                    }

                    val lat = if (attachLocation) locInfo?.latitude else null
                    val lon = if (attachLocation) locInfo?.longitude else null
                    val acc = if (attachLocation) locInfo?.accuracyMeters else null
                    val ts = if (attachLocation) locInfo?.timestamp else null

                    val packet = app.messageRepository.createEmergencySos(
                        text = fullMessage,
                        recipientId = recipientOption,
                        latitude = lat,
                        longitude = lon,
                        accuracy = acc,
                        locationTimestamp = ts
                    )

                    // Ensure mesh is active so advertising & scanning transmit the packet
                    app.bluetoothMeshManager.startMesh()

                    onSosDispatched(packet.messageId)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activate Emergency SOS", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
            // Warning Banner
            OfflineWarningBanner()

            Spacer(modifier = Modifier.height(16.dp))

            // Preset Selection
            Text(
                text = "Select Emergency Category",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    val isSelected = selectedPreset == preset
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) EmergencyRedDark.copy(alpha = 0.4f) else NavySurface)
                            .border(
                                1.dp,
                                if (isSelected) EmergencyRed else NavyBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedPreset = preset }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) EmergencyRed else NavySurfaceVariant)
                                .border(1.dp, if (isSelected) EmergencyRed else NavyBorder, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = preset,
                            color = TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Explanatory Details Field
            Text(
                text = "Additional Details (Optional)",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = detailsText,
                onValueChange = { detailsText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("E.g., 2 adults injured, need shelter, near milestone 42", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeshBlue,
                    unfocusedBorderColor = NavyBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Location Attachment Card
            SafeMeshCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = attachLocation,
                        onCheckedChange = { attachLocation = it },
                        colors = CheckboxDefaults.colors(checkedColor = MeshGreen, uncheckedColor = TextMuted)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Attach GPS Coordinates",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        val locDescription = if (locInfo != null) {
                            val typeLabel = if (locInfo!!.isLastKnown) "Last Known" else "Current Fix"
                            "%.4f, %.4f (±%.1fm, %s)".format(
                                locInfo!!.latitude,
                                locInfo!!.longitude,
                                locInfo!!.accuracyMeters ?: 0f,
                                typeLabel
                            )
                        } else {
                            "GPS coordinates unavailable (will send without GPS)"
                        }
                        Text(text = locDescription, color = if (locInfo != null) MeshGreen else MeshBlue, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recipient Destination
            SafeMeshCard {
                Text(
                    text = "Transmission Destination",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Broadcast to all reachable SafeMesh relay nodes and emergency responders within multi-hop mesh range.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Cancellation countdown or dispatch button
            if (isCountingDown) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(EmergencyRedDark)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TRANSMITTING IN $countdownSeconds SECONDS...",
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { isCountingDown = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NavySurface),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("CANCEL TRANSMISSION", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = { isCountingDown = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TRANSMIT EMERGENCY SOS",
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
