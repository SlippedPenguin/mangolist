package com.slippedpenguin.mangolist.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Direct port of CSS :root variables from `anime-tracker/index.html`.
 * Names chosen to mirror the source so a design change in the HTML
 * prototype can be ported over with a search-and-replace.
 */

val BgDeep        = Color(0xFF0a0a14)
val BgCard        = Color(0xFF14141f)
val BgCardHover   = Color(0xFF1c1c2e)
val BgInput       = Color(0xFF15151f)

val Border        = Color(0xFF25253a)
val BorderStrong  = Color(0xFF353550)

val TextPrimary   = Color(0xFFe8e8f0)
val TextSecondary = Color(0xFF8a8aa3)
val TextMuted     = Color(0xFF5a5a73)

val Accent        = Color(0xFFff3366)
val AccentHover   = Color(0xFFff4577)

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
