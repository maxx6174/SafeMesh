package com.safemesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safemesh.SafeMeshApplication
import com.safemesh.data.database.MessageEntity
import com.safemesh.model.DeliveryStatus
import com.safemesh.model.MessageType
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MessageFilter(val label: String) {
    ALL("All"),
    SENT("Sent"),
    RECEIVED("Received"),
    EMERGENCY("Emergency"),
    PENDING("Pending"),
    DELIVERED("Delivered")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    app: SafeMeshApplication,
    onNavigateBack: () -> Unit,
    onMessageClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val allMessages by app.messageRepository.allMessagesFlow.collectAsState(initial = emptyList())
    var selectedFilter by remember { mutableStateOf(MessageFilter.ALL) }
    var composeText by remember { mutableStateOf("") }
    var targetRecipient by remember { mutableStateOf("BROADCAST") }

    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    val filteredMessages = when (selectedFilter) {
        MessageFilter.ALL -> allMessages
        MessageFilter.SENT -> allMessages.filter { it.senderId == app.messageRepository.localNodeId }
        MessageFilter.RECEIVED -> allMessages.filter { it.senderId != app.messageRepository.localNodeId }
        MessageFilter.EMERGENCY -> allMessages.filter { it.isEmergency }
        MessageFilter.PENDING -> allMessages.filter {
            it.status == DeliveryStatus.SAVED_LOCAL || it.status == DeliveryStatus.SEARCHING || it.status == DeliveryStatus.TRANSFERRING
        }
        MessageFilter.DELIVERED -> allMessages.filter { it.status == DeliveryStatus.DELIVERED }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages & Queues", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
                .padding(16.dp)
        ) {
            // Horizontal Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MessageFilter.entries.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) MeshBlue else NavySurface)
                            .border(1.dp, if (isSelected) MeshBlue else NavyBorder, RoundedCornerShape(16.dp))
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter.label,
                            color = if (isSelected) NavyBackground else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Compose Box for Standard Decentralized Message
            SafeMeshCard {
                Text("Compose Encrypted Message", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = composeText,
                        onValueChange = { composeText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type message...", color = TextMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeshBlue,
                            unfocusedBorderColor = NavyBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (composeText.isNotBlank()) {
                                scope.launch {
                                    app.messageRepository.createStandardMessage(
                                        text = composeText.trim(),
                                        recipientId = targetRecipient,
                                        recipientPublicKeyBase64 = null
                                    )
                                    composeText = ""
                                    app.bluetoothMeshManager.startMesh()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeshBlue),
                        shape = RoundedCornerShape(10.dp),
                        enabled = composeText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = NavyBackground, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Messages List
            if (filteredMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No messages in '${selectedFilter.label}' filter", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMessages, key = { it.messageId }) { msg ->
                        MessageItemCard(
                            message = msg,
                            isLocal = msg.senderId == app.messageRepository.localNodeId,
                            timeFormatted = dateFormat.format(Date(msg.createdAt)),
                            onClick = { onMessageClick(msg.messageId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItemCard(
    message: MessageEntity,
    isLocal: Boolean,
    timeFormatted: String,
    onClick: () -> Unit
) {
    SafeMeshCard(
        borderColor = if (message.isEmergency) EmergencyRed.copy(alpha = 0.6f) else NavyBorder,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (message.isEmergency) EmergencyRed else MeshBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (message.isEmergency) Icons.Default.Warning else Icons.Default.Lock,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isLocal) "Outbox: To ${message.recipientId}" else "Inbox: From ${message.senderId.take(8)}",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = timeFormatted,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            StatusPill(status = message.status)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = message.plaintextPreview ?: "[Encrypted Payload - AES-256-GCM]",
            color = if (message.isEmergency) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (message.isEmergency) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Relays: ${message.relayCount} | Hops: ${message.hopCount}/${message.hopLimit}",
                color = TextMuted,
                fontSize = 11.sp
            )
            if (message.latitude != null && message.longitude != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MeshGreen, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("GPS Attached", color = MeshGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
