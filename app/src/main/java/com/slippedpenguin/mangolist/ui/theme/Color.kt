package com.slippedpenguin.mangolist.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * v1.8 "OLED" redesign — full palette retune.
 *
 * Design direction (from the ui-ux-pro-max design-system run for this
 * product): true-black OLED surfaces with a vibrant indigo→violet accent
 * gradient. The public token names from v1.5.3 are kept so every existing
 * call site keeps compiling; the *values* change underneath.
 *
 * Key change vs v1.5.3: backgrounds were neutral gray (#1B1B1F). Now the
 * base is true black and every surface level is a translucent indigo
 * wash — the app reads as one continuous OLED canvas with cards that
 * barely lift off it, instead of gray boxes on gray.
 */

// ---- Background ladder (true OLED) ----------------------------------------
val BgDeep        = Color(0xFF000000)                    // screen canvas
val BgCard        = Color(0xFF000000)                    // cards sit on black
val BgCardHover   = Color(0xFF15151E)                    // secondary cards
val BgInput       = Color(0xFF101018)                    // text fields

// Surface-container ladder: black → indigo-tinted lifts.
val SurfaceContainerLowest  = Color(0xFF000000)
val SurfaceContainerLow     = Color(0xFF0B0B12)
val SurfaceContainer        = Color(0xFF111119)
val SurfaceContainerHigh    = Color(0xFF181823)
val SurfaceContainerHighest = Color(0xFF1F1F2D)

// ---- Hairlines -------------------------------------------------------------
val Border        = Color(0xFF2A2A3A)                    // outlineVariant
val BorderStrong  = Color(0xFF4A4A5E)                    // outline
val BorderSubtle  = Color(0xFF1D1D2A)                    // card hairline

// ---- Text (OLED needs cooler, brighter whites for AA contrast) -------------
val TextPrimary   = Color(0xFFF4F3F8)
val TextSecondary = Color(0xFFB8B6C9)
val TextMuted     = Color(0xFF77758A)

// ---- Accent system: TWO hues so gradients have a from/to -------------------
// Primary stays periwinkle (AniHyou heritage, ~2:1 contrast on black);
// Accent2 (violet) is its gradient partner. Every "glow" surface in the
// app composes Accent.copy(alpha) → Accent2.copy(alpha).
val Accent        = Color(0xFFBAC3FF)                    // periwinkle
val AccentHover   = Color(0xFFCBD2FF)
val Accent2       = Color(0xFFA78BFA)                    // violet partner

// Deep gradient endpoints for hero/banner washes (full-saturation forms).
val AccentDeep    = Color(0xFF4F46E5)                    // indigo-600
val Accent2Deep   = Color(0xFF7C3AED)                    // violet-600

// ---- Tier rainbow (kept: functional colors) --------------------------------
val TierS         = Color(0xFFff4d6d)
val TierA         = Color(0xFFff8a3d)
val TierB         = Color(0xFFffce4d)
val TierC         = Color(0xFF6dd66d)
val TierD         = Color(0xFF5dc4d6)
val TierUnranked  = Color(0xFF2A2A3A)

// ---- Status badge colors (kept) --------------------------------------------
val StatusPlan      = Color(0xFF6b6b80)
val StatusWatching  = Color(0xFFff3366)
val StatusCompleted = Color(0xFF6dd66d)
val StatusDropped   = Color(0xFFe74c3c)
val StatusPaused    = Color(0xFFf0a040)
val StatusRepeating = Color(0xFF9b59b6)

/** Lookup mapping a tier letter to its color. Returns TierUnranked for null. */
fun tierColor(tier: String?): Color = when (tier) {
    "S" -> TierS
    "A" -> TierA
    "B" -> TierB
    "C" -> TierC
    "D" -> TierD
    else -> TierUnranked
}

/** Lookup mapping a list status to its badge color. */
fun statusColor(status: String): Color = when (status) {
    "plan"      -> StatusPlan
    "watching"  -> StatusWatching
    "completed" -> StatusCompleted
    "dropped"   -> StatusDropped
    "paused"    -> StatusPaused
    "repeating" -> StatusRepeating
    else        -> StatusPlan
}
