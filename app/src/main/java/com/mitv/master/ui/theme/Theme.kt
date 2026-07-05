package com.mitv.master.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// MITV brand palette — dark background with gold accents (consistent with
// existing MiTV Network / MICH branding)
val MitvBlack = Color(0xFF0A0A0A)
val MitvSurface = Color(0xFF161616)
val MitvGold = Color(0xFFD4AF37)
val MitvGoldLight = Color(0xFFF2D571)
val MitvRed = Color(0xFFE50914) // Netflix-style accent for live/rec indicators
val MitvTextSecondary = Color(0xFFB3B3B3)

private val MitvDarkColorScheme = darkColorScheme(
    primary = MitvGold,
    secondary = MitvGoldLight,
    background = MitvBlack,
    surface = MitvSurface,
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    error = MitvRed
)

@Composable
fun MITVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MitvDarkColorScheme,
        typography = MitvTypography,
        content = content
    )
}
