package com.safemesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safemesh.model.DeliveryStatus
import com.safemesh.ui.theme.EmergencyRed
import com.safemesh.ui.theme.EmergencyRedDark
import com.safemesh.ui.theme.MeshBlue
import com.safemesh.ui.theme.MeshGreen
import com.safemesh.ui.theme.MeshOrange
import com.safemesh.ui.theme.MeshYellow
import com.safemesh.ui.theme.NavyBorder
import com.safemesh.ui.theme.NavySurface
import com.safemesh.ui.theme.TextMuted
import com.safemesh.ui.theme.TextPrimary

@Composable
fun SafeMeshCard(
    modifier: Modifier = Modifier,
    borderColor: Color = NavyBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavySurface)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun StatusPill(status: DeliveryStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        DeliveryStatus.SAVED_LOCAL -> Pair(Color(0xFF2C3E50), Color(0xFFBDC3C7))
        DeliveryStatus.SEARCHING -> Pair(Color(0xFF1E3A8A), MeshBlue)
        DeliveryStatus.TRANSFERRING -> Pair(Color(0xFF78350F), MeshOrange)
        DeliveryStatus.RELAYED -> Pair(Color(0xFF14532D), MeshGreen)
        DeliveryStatus.FORWARDED -> Pair(Color(0xFF134E4A), Color(0xFF2DD4BF))
        DeliveryStatus.DELIVERED -> Pair(Color(0xFF065F46), Color(0xFF34D399))
        DeliveryStatus.EXPIRED -> Pair(Color(0xFF374151), Color(0xFF9CA3AF))
        DeliveryStatus.FAILED -> Pair(EmergencyRedDark, EmergencyRed)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.displayLabel,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ConnectivityDot(isActive: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (isActive) MeshGreen else Color(0xFFEF4444))
    )
}

@Composable
fun OfflineWarningBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2D1515))
            .border(1.dp, EmergencyRedDark, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = EmergencyRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "NOTICE: Emergency SOS is stored locally and relayed via nearby phones. It has NOT reached 911/emergency services until confirmed by an active gateway.",
                color = TextPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
