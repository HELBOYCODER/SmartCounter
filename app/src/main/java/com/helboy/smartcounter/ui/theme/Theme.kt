package com.helboy.smartcounter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldCyber,
    secondary = AmberAccent,
    tertiary = CyanNeon,
    background = BlackObsidian,
    surface = SurfaceDark,
    surfaceVariant = SurfaceCard,
    onPrimary = BlackObsidian,
    onSecondary = BlackObsidian,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun SmartCounterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
