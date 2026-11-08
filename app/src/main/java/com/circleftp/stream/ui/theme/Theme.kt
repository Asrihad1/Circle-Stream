package com.circleftp.stream.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CircleRed,
    onPrimary = TextPrimary,
    primaryContainer = CircleAccent,
    background = CircleDarkBg,
    onBackground = TextPrimary,
    surface = CircleSurface,
    onSurface = TextPrimary,
    surfaceVariant = CircleCard,
    onSurfaceVariant = TextSecondary
)

@Composable
fun CircleStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

