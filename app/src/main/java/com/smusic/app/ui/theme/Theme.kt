package com.smusic.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Canvas = Color(0xFF090A0C)
val Panel = Color(0xFF141518)
val PanelBorder = Color(0xFF26272B)
val PanelHover = Color(0xFF1D1E22)
val Ink = Color(0xFFF5F5F7)
val Muted = Color(0xFF8E8E93)
val Accent = Color(0xFFD7F26A) // Neon Chartreuse
val AccentDark = Color(0xFF1B220B)
val ErrorRed = Color(0xFFFF453A)
val ErrorContainer = Color(0xFF241414)
val SuccessGreen = Color(0xFF32D74B)

private val SmusicDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = AccentDark,
    background = Canvas,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    surfaceVariant = PanelHover,
    onSurfaceVariant = Muted,
    outline = PanelBorder,
    error = ErrorRed
)

@Composable
fun SmusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmusicDarkColorScheme,
        content = content
    )
}
