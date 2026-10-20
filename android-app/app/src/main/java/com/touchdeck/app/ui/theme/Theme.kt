package com.touchdeck.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TouchDeckColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = BgDark,
    primaryContainer = AccentCyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = AccentCyanDim,
    onSecondary = TextPrimary,
    background = BgDark,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgSurface,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFF333344)
)

@Composable
fun TouchDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TouchDeckColorScheme,
        content = content
    )
}
