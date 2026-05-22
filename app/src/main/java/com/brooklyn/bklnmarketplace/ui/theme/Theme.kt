package com.brooklyn.bklnmarketplace.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BklnColorScheme = darkColorScheme(
    primary          = BklnOrange,
    onPrimary        = BklnWhite,
    secondary        = BklnYellow,
    onSecondary      = BklnBgDark,
    background       = BklnBgDark,
    onBackground     = BklnWhite,
    surface          = BklnBgCard,
    onSurface        = BklnWhite,
    surfaceVariant   = BklnBgInput,
    onSurfaceVariant = BklnMuted,
    outline          = BklnStroke,
    error            = BklnError,
)

@Composable
fun BklnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BklnColorScheme,
        content = content
    )
}
