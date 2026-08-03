package com.slippedpenguin.mangolist.data

import com.slippedpenguin.mangolist.data.local.AnimeEntry
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Adaptive Elo ranking engine — ported from `anime-tracker/index.html`.
 *
 * Defaults:
 *   - K-factor constant at 32 (matches HTML prototype)
 *   - Initial Elo = 1500
 *   - vs-mode rounds = 3
 *   - Tier reset on tier reassignment (Elo resets to INITIAL_ELO)
 *
 * Implementation notes:
 *   - Uses integer Elo throughout — half-points from average are rounded.
 *   - Clamps Elo bumps to [900, 2100] so a manipulative nudge button mashing
 *     doesn't break the scale.
 */
object EloEngine {
    const val INITIAL_ELO = 1500
    const val K_FACTOR    = 32
    const val VS_ROUNDS   = 3
    val TIERS = listOf("S", "A", "B", "C", "D")

    data class EloUpdate(val newWinner: Int, val newLoser: Int)

    /** One-vs-one Elo update. */
    fun update(winnerElo: Int, loserElo: Int, k: Int = K_FACTOR): EloUpdate {
        val expected = 1.0 / (1.0 + 10.0.pow((loserElo - winnerElo) / 400.0))
        val delta = k * (1.0 - expected)
        return EloUpdate(
            newWinner = (winnerElo + delta).roundToInt(),
            newLoser  = (loserElo  - delta).roundToInt(),
        )
    }

    /** Median Elo of all entries currently in the given tier. */
    fun medianElo(tier: String, entries: List<AnimeEntry>): Int? {
        val elos = entries.filter { it.tier == tier }.map { it.elo }.sorted()
        if (elos.isEmpty()) return null
        val mid = elos.size / 2
        return if (elos.size % 2 == 1) elos[mid]
        else ((elos[mid - 1] + elos[mid]) / 2.0).roundToInt()
    }

    /** Pick the tier whose median is closest to `rootElo`. Falls back to "B". */
    fun proposeTier(rootElo: Int, entries: List<AnimeEntry>): String {
        var bestTier = "B"
        var bestDist = Double.MAX_VALUE
        for (t in TIERS) {
            val med = medianElo(t, entries) ?: continue
            val dist = abs(med - rootElo).toDouble()
            if (dist <= bestDist) { bestDist = dist; bestTier = t }
        }
        return bestTier
    }

    /** Vs-mode: pick the ranked entry closest to `rootElo`, excluding `excludeIds`. */
    fun pickOpponent(
        rootElo: Int,
        entries: List<AnimeEntry>,
        excludeIds: Set<Int>,
    ): AnimeEntry? {
        var best: AnimeEntry? = null
        var bestDist = Int.MAX_VALUE
        for (e in entries) {
            if (e.anilistId in excludeIds) continue
            if (e.tier == null) continue
            val dist = abs(e.elo - rootElo)
            if (dist < bestDist) { bestDist = dist; best = e }
        }
        return best
    }

    /** Bump Elo by ±delta, clamped to [900, 2100]. */
    fun bumpElo(current: Int, delta: Int): Int =
        (current + delta).coerceIn(900, 2100)

    /**
     * Map a 0-100 personal score to a tier letter, or null when unrated.
     *
     * v1.5.1: the "Rank from my ratings" one-tap flow lets the tierlist be
     * seeded from scores the user has ALREADY given on AniList/Detail — no
     * long-press ceremony required. Thresholds are intentionally generous
     * so S stays aspirational (90+) and D catches anything rated below 60.
     */
    fun tierForScore(score: Int?): String? {
        if (score == null || score <= 0) return null
        return when {
            score >= 90 -> "S"
            score >= 80 -> "A"
            score >= 70 -> "B"
            score >= 60 -> "C"
            else        -> "D"
        }
    }

    /**
     * Derive a starting Elo from a 0-100 personal score so auto-ranked
     * entries sit in a sensible spot inside their tier (linear map from
     * 900 at score 0 to 2100 at score 100 — score 50 lands exactly on
     * INITIAL_ELO). Unrated scores fall back to INITIAL_ELO.
     */
    fun eloForScore(score: Int?): Int {
        if (score == null || score <= 0) return INITIAL_ELO
        return (900 + score * 12).coerceIn(900, 2100)
    }
}

/*
 * Mirror of the JS prototype's `safeNum(x, fallback)`: turns anything
 * non-finite into a deterministic fallback. Required because Room can
 * hold nullable columns and older DB rows may carry ghost values.
 */
fun safeNum(x: Any?, fallback: Int = 0): Int = (x as? Number)?.toInt() ?: fallback
