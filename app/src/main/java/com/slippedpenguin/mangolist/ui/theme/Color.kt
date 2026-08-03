package com.slippedpenguin.mangolist.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Direct port of CSS :root variables from `anime-tracker/index.html`.
 * Names chosen to mirror the source so a design change in the HTML
 * prototype can be ported over with a search-and-replace.
 */

/*
 * v1.5.3: color tokens retuned to AniHyou's Material-You dark palette
 * (periwinkle primary #BAC3FF on a neutral #1B1B1F base) for the polished
 * look. Tier/status badge colors stay functional and distinct; the app-wide
 * accent, surfaces, and text neutrals now match the reference client.
 */
val BgDeep        = Color(0xFF1B1B1F)  // AniHyou background / surface
val BgCard        = Color(0xFF1B1B1F)  // AniHyou surface
val BgCardHover   = Color(0xFF232329)
val BgInput       = Color(0xFF2B2B31)  // surfaceVariant-ish input field

// Material-3 surface-container ladder retuned to AniHyou's neutral ramp.
val SurfaceContainerLowest  = Color(0xFF131318)
val SurfaceContainerLow     = Color(0xFF1D1D22)
val SurfaceContainer        = Color(0xFF242429)
val SurfaceContainerHigh    = Color(0xFF2B2B31)
val SurfaceContainerHighest = Color(0xFF32323A)

val Border        = Color(0xFF46464F)  // outlineVariant
val BorderStrong  = Color(0xFF90909A)  // outline
val BorderSubtle  = Color(0xFF32323A)

val TextPrimary   = Color(0xFFE4E1E6)  // onSurface
val TextSecondary = Color(0xFFC7C5D0)  // onSurfaceVariant
val TextMuted     = Color(0xFF90909A)  // outline

val Accent        = Color(0xFFBAC3FF)  // AniHyou periwinkle primary
val AccentHover   = Color(0xFFCBD2FF)

// Tier rainbow — used for badges, rows, vs-mode picks.
val TierS         = Color(0xFFff4d6d)
val TierA         = Color(0xFFff8a3d)
val TierB         = Color(0xFFffce4d)
val TierC         = Color(0xFF6dd66d)
val TierD         = Color(0xFF5dc4d6)
val TierUnranked  = Color(0xFF3a3a4e)

// Status badge colors — one per list status so the tracking card + Profile
// breakdown + StatusPill all share the same palette without theme leaks.
val StatusPlan      = Color(0xFF6b6b80)
val StatusWatching  = Color(0xFFff3366)  // same as Accent
val StatusCompleted = Color(0xFF6dd66d)  // same as TierC
val StatusDropped   = Color(0xFFe74c3c)  // distinct from Accent (rose vs crimson)
val StatusPaused    = Color(0xFFf0a040)  // warm amber
val StatusRepeating = Color(0xFF9b59b6)  // soft purple

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
