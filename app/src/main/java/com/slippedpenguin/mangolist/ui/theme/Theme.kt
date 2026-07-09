package com.slippedpenguin.mangolist.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/*
 * MangoTheme — Compose entry point that wraps the app in a Material 3
 * themed surface area. v1 is dark-only (matches the HTML prototype's
 * design; light mode is a v1.x concern).
 */
private val MangoDarkColors = darkColorScheme(
    primary             = Accent,
    onPrimary           = TextPrimary,
    primaryContainer    = Accent.copy(alpha = 0.18f),
    onPrimaryContainer  = Accent,
    secondary           = TierC,
    onSecondary         = TextPrimary,
    background          = BgDeep,
    onBackground        = TextPrimary,
    surface             = BgCard,
    onSurface           = TextPrimary,
    surfaceVariant      = BgInput,
    onSurfaceVariant    = TextSecondary,
    outline             = Border,
    outlineVariant      = Border,
    error               = Accent,
    onError             = TextPrimary,
)

@Composable
fun MangoTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MangoDarkColors,
        typography = Typography,
        content = content,
    )
}
