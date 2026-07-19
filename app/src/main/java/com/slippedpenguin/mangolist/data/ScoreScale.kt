package com.slippedpenguin.mangolist.data

/*
 * Score scale — user-facing toggle that switches between two display
 * styles for the same underlying AnimeEntry.personalScore value (which is
 * always an Int in 0..100, mapped to AniList's 0..10 / 0.5-step API).
 *
 * The toggle is DISPLAY ONLY: AniList still receives personalScore / 10.0
 * regardless of which scale the user picked. So no migration is required
 * when switching scales and there is no data loss.
 *
 * v1.2: Default flipped from OUT_OF_10 to OUT_OF_100 per user feedback —
 * most modern trackers show 0-100 and that's what the user requested.
 * Existing users who explicitly picked OUT_OF_10 keep that choice
 * because TokenStore persists the scale tag; only fresh-app installs and
 * users who never opened Profile's chip row inherit the new default.
 */
enum class ScoreScale {
    OUT_OF_10,
    OUT_OF_100;

    companion object {
        val Default: ScoreScale = OUT_OF_100

        /** Parse the DataStore-stored tag; falls back to [Default] on missing/invalid. */
        fun fromTag(tag: String?): ScoreScale = when (tag) {
            "OUT_OF_10"  -> OUT_OF_10
            "OUT_OF_100" -> OUT_OF_100
            else          -> Default
        }

        /** Stable tag for DataStore persistence. */
        fun tagOf(scale: ScoreScale): String = scale.name
    }
}

/*
 * Score formatting helpers — single source of truth for how a
 * `personalScore: Int?` (0..100) renders in the UI. Centralised here
 * so the dialogue, score button label, profile stats, and any future
 * surface all agree.
 */
object ScoreDisplay {

    /** Display label for a stored score (or "Not rated" when null/0). */
    fun label(score: Int?, scale: ScoreScale): String {
        if (score == null || score <= 0) return "Not rated"
        return when (scale) {
            ScoreScale.OUT_OF_10  -> "%.1f / 10".format(score / 10.0)
            ScoreScale.OUT_OF_100 -> "$score / 100"
        }
    }

    /** Step label for a 1..10 slot. */
    fun stepLabel(stepIndex: Int, scale: ScoreScale): String = when (scale) {
        ScoreScale.OUT_OF_10  -> "%.1f".format(stepIndex.toDouble())
        ScoreScale.OUT_OF_100 -> "${stepIndex * 10}"
    }

    /** Score column header for the picker dialog. */
    fun axisLabel(scale: ScoreScale): String = when (scale) {
        ScoreScale.OUT_OF_10  -> "/ 10"
        ScoreScale.OUT_OF_100 -> "/ 100"
    }
}
