package com.dtb.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TerminalCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = Color(0xFF80F0FF),

    secondary = TerminalGreen,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004D28),
    onSecondaryContainer = Color(0xFF80FFAE),

    tertiary = TerminalOrange,
    onTertiary = Color.Black,

    background = DarkBackground,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,

    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    error = TerminalRed,
    onError = Color.Black
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
