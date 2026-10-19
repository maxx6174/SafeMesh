package com.safemesh.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EmergencyRed,
    onPrimary = TextPrimary,
    primaryContainer = EmergencyRedDark,
    onPrimaryContainer = TextPrimary,
    secondary = MeshBlue,
    onSecondary = NavyBackground,
    secondaryContainer = NavySurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = MeshGreen,
    onTertiary = NavyBackground,
    background = NavyBackground,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = NavyBorder
)

@Composable
fun SafeMeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
