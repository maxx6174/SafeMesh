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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    app: SafeMeshApplication,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val provider = app.locationProvider
    val locationInfo by provider.currentLocation.collectAsState()
    val isGpsEnabled by provider.isGpsEnabled.collectAsState()
    val hasPermission by provider.hasPermission.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline GPS & Coordinates", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isRefreshing = true
                        provider.requestSingleUpdate {
                            isRefreshing = false
                        }
                    }) {
                        if (isRefreshing) {
                            CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh GPS", tint = TextPrimary)
                        }
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
            // Sensor Status Card
            SafeMeshCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isGpsEnabled) Icons.Default.LocationOn else Icons.Default.LocationDisabled,
                            contentDescription = null,
                            tint = if (isGpsEnabled) MeshGreen else EmergencyRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Location Hardware Status", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                            Text(
                                text = if (isGpsEnabled) "GPS Provider Active" else "GPS Disabled in System Settings",
                                color = if (isGpsEnabled) MeshGreen else EmergencyRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Coordinates Display
            SafeMeshCard {
                Text(
                    text = "ACQUIRED COORDINATES",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (locationInfo != null) {
                    val loc = locationInfo!!
                    Text(
                        text = "%.6f° N, %.6f° E".format(loc.latitude, loc.longitude),
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (loc.isLastKnown) Color(0xFF1E3A8A) else Color(0xFF065F46))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (loc.isLastKnown) "LAST KNOWN FIX" else "LIVE GPS FIX",
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (loc.accuracyMeters != null) {
                            Text(
                                text = "Accuracy: ±%.1f meters".format(loc.accuracyMeters),
                                color = MeshGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Timestamp: ${dateFormat.format(Date(loc.timestamp))}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val uri = Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(Current+GPS+Location)")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            try {
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                // Map application not installed
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MeshGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = NavyBackground)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Show in Map Application", color = NavyBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MeshBlue, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No GPS fix available currently", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SafeMesh never invents GPS coordinates. Emergency SOS can still be created and transmitted via Bluetooth without GPS.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Informational Card
            SafeMeshCard {
                Text("Offline Location Transparency", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Location is accessed strictly on-device using Android LocationManager.\n• Coordinates are never transmitted over the Internet.\n• When Emergency SOS is sent, coordinates are encrypted within the payload or attached only if authorized.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
