package com.slippedpenguin.mangolist.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * MangoTheme — v1.8 OLED redesign.
 *
 * Dark-only (this app is a night-tracking app; light mode remains a
 * non-goal). The scheme maps the OLED ladder from Color.kt onto Material 3
 * and adds an app-wide Shapes scale so every card/pill/sheet shares one
 * geometry language instead of one-off RoundedCornerShape values.
 */

private val MangoDarkColors = darkColorScheme(
    primary             = Accent,
    onPrimary           = Color(0xFF0A0A14),
    primaryContainer    = Accent.copy(alpha = 0.16f),
    onPrimaryContainer  = Accent,

    secondary           = Accent2,
    onSecondary         = Color(0xFF0A0A14),
    secondaryContainer  = Accent2.copy(alpha = 0.16f),
    onSecondaryContainer = Accent2,

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

    // Layered surfaces for nav bar / cards / sheets.
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

/** App-wide shape scale — the redesign's shared geometry language. */
private val MangoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The brand gradient — periwinkle → violet. Every glow/banner uses this. */
fun brandGradient(): Brush = Brush.linearGradient(listOf(AccentDeep, Accent2Deep))

/** Softer horizontal wash for chips and nav accents. */
fun brandGradientSoft(): Brush = Brush.horizontalGradient(
    listOf(Accent.copy(alpha = 0.22f), Accent2.copy(alpha = 0.22f)),
)

@Composable
fun MangoTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MangoDarkColors,
        typography = mangoTypography(),
        shapes = MangoShapes,
        content = content,
    )
}
