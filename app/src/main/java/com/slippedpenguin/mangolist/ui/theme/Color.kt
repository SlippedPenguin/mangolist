package com.slippedpenguin.mangolist.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * MangoList design tokens — v2.0 polish pass.
 * Keeps the dark cinematic base but raises contrast, adds subtle
 * surface tints, and introduces a cohesive accent family.
 */

val BgDeep        = Color(0xFF0B0B14)
val BgCard        = Color(0xFF13131F)
val BgCardHover   = Color(0xFF1A1A2B)
val BgInput       = Color(0xFF15151F)
val BgElevated    = Color(0xFF1B1B29)

val Border        = Color(0xFF2A2A40)
val BorderStrong  = Color(0xFF3A3A55)

val TextPrimary   = Color(0xFFF0F0F7)
val TextSecondary = Color(0xFF9CA0B0)
val TextMuted     = Color(0xFF64667A)

val Accent        = Color(0xFFFF4D7A)
val AccentHover   = Color(0xFFFF6B8F)
val AccentSoft    = Color(0xFFFF4D7A)

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
