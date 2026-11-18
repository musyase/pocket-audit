package dev.musyase.pocketaudit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val light = lightColorScheme(
    primary = Color(0xFF135B53),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EAE4),
    onPrimaryContainer = Color(0xFF0B3D38),
    secondary = Color(0xFF596963),
    secondaryContainer = Color(0xFFE3ECE7),
    background = Color(0xFFF8F7F2),
    onBackground = Color(0xFF202A27),
    surface = Color(0xFFF8F7F2),
    onSurface = Color(0xFF202A27),
    surfaceContainerLow = Color(0xFFF0F2EC),
    surfaceContainer = Color(0xFFE9EEE8),
    surfaceContainerHigh = Color(0xFFE2E8E0),
    surfaceVariant = Color(0xFFECEEE8),
    onSurfaceVariant = Color(0xFF46534E),
    outline = Color(0xFF727E77),
    error = Color(0xFFB3261E),
)

private val dark = darkColorScheme(
    primary = Color(0xFFA2D8CA),
    onPrimary = Color(0xFF003D36),
    primaryContainer = Color(0xFF0D5149),
    onPrimaryContainer = Color(0xFFD3EAE4),
    secondary = Color(0xFFB6C9BF),
    secondaryContainer = Color(0xFF394B43),
    background = Color(0xFF151D1A),
    onBackground = Color(0xFFE3EAE5),
    surface = Color(0xFF151D1A),
    onSurface = Color(0xFFE3EAE5),
    surfaceContainerLow = Color(0xFF1E2923),
    surfaceContainer = Color(0xFF26332B),
    surfaceContainerHigh = Color(0xFF304037),
    surfaceVariant = Color(0xFF2B3530),
    onSurfaceVariant = Color(0xFFC3CEC6),
    outline = Color(0xFF8B9A90),
    error = Color(0xFFFFB4AB),
)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) dark else light,
        typography = Typography(),
        content = content,
    )
}
