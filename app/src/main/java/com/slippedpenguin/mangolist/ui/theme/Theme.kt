package com.slippedpenguin.mangolist.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * MangoTheme — Compose entry point that wraps the app in a Material 3
 * themed surface area. v1 is dark-only (matches the HTML prototype's
 * design; light mode is a v1.x concern).
 *
 * v1.5.0: expanded to a full M3 color scheme with the surface-container
 * ladder so the bottom nav, cards, and sheets render with layered depth
 * (Anihyou-style) instead of one flat surface color.
 */
private val MangoDarkColors = darkColorScheme(
    primary             = Accent,
    onPrimary           = Color.White,
    primaryContainer    = Accent.copy(alpha = 0.18f),
    onPrimaryContainer  = Accent,

    secondary           = TierC,
    onSecondary         = Color(0xFF062e12),
    secondaryContainer  = TierC.copy(alpha = 0.16f),
    onSecondaryContainer = TierC,

    tertiary            = TierD,
    onTertiary          = Color(0xFF063a42),
    tertiaryContainer   = TierD.copy(alpha = 0.16f),
    onTertiaryContainer = TierD,

    background          = BgDeep,
    onBackground        = TextPrimary,
    surface             = BgCard,
    onSurface           = TextPrimary,
    surfaceVariant      = BgInput,
    onSurfaceVariant    = TextSecondary,
    surfaceTint         = Accent,

    // v1.5.0: layered surfaces for nav bar / cards / sheets.
    surfaceContainerLowest  = SurfaceContainerLowest,
    surfaceContainerLow     = SurfaceContainerLow,
    surfaceContainer        = SurfaceContainer,
    surfaceContainerHigh    = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,

    outline             = Border,
    outlineVariant      = BorderSubtle,

    error               = StatusDropped,
    onError             = Color.White,
    errorContainer      = StatusDropped.copy(alpha = 0.18f),
    onErrorContainer    = StatusDropped,
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
