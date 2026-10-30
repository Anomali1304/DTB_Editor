package com.dtb.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = IdePrimaryCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = IdePrimaryCyan,

    secondary = IdeSecondaryGreen,
    onSecondary = Color.Black,

    background = IdeBackground,
    onBackground = TextPrimary,

    surface = IdeSurface,
    onSurface = TextPrimary,

    surfaceVariant = IdeSurfaceHeader,
    onSurfaceVariant = TextSecondary,

    outline = IdeBorder,

    error = IdeErrorRed,
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
