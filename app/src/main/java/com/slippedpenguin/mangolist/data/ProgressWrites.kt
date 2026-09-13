package com.slippedpenguin.mangolist.data

import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.local.AnimeDao
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.work.SyncWorker

/*
 * v1.9 — shared one-tap progress writes.
 *
 * The ManGo-style "update from the list card" feature must behave exactly
 * like the Detail stepper (which has carried these semantics since v0.5):
 *
 *   +1 reaching the cap → auto-complete in the same write
 *   −1 undoing an auto-complete → back to watching
 *   every write stamps updatedAt (sync dirt) and enqueues SyncWorker
 *
 * One source of truth for both surfaces so list-card taps can never drift
 * from Detail behavior.
 */

/** Total for the entry's progress unit, normalized by format. */
fun progressTotalFor(entry: AnimeEntry): Int? = when {
    entry.format == "NOVEL" || entry.format == "LIGHT_NOVEL" -> entry.volumes ?: entry.episodes
    entry.mediaType == "MANGA" -> entry.chapters ?: entry.episodes
    else -> entry.episodes
}

/** Human unit for the entry's progress ("ep" / "ch" / "vol"). */
fun progressUnitFor(entry: AnimeEntry): String = when {
    entry.format == "NOVEL" || entry.format == "LIGHT_NOVEL" -> "vol"
    entry.mediaType == "MANGA" -> "ch"
    else -> "ep"
}

/**
 * Apply a +1/-1 progress delta with Detail-screen semantics.
 * No-ops at the boundaries (below 0; above cap).
 */
suspend fun applyProgressDelta(
    dao: AnimeDao,
    app: AnimeApp,
    entry: AnimeEntry,
    delta: Int,
) {
    if (delta == 0) return
    val cap = progressTotalFor(entry)
    val now = System.currentTimeMillis()

    if (delta > 0) {
        val next = entry.currentEp + 1
        if (cap != null && next > cap) return // already at cap
        dao.update(
            if (cap != null && next >= cap) {
                // Reaching the cap → flip to completed in the same write.
                entry.copy(currentEp = cap, status = "completed", updatedAt = now)
            } else {
                entry.copy(currentEp = next, updatedAt = now)
            }
        )
    } else {
        if (entry.currentEp <= 0) return
        val prev = entry.currentEp - 1
        val wasAutoCompleted =
            cap != null && entry.status == "completed" && entry.currentEp == cap
        dao.update(
            if (wasAutoCompleted && prev > 0) {
                // Undo of an auto-complete → back to watching.
                entry.copy(currentEp = prev, status = "watching", updatedAt = now)
            } else {
                entry.copy(currentEp = prev, updatedAt = now)
            }
        )
    }
    SyncWorker.enqueue(app)
}
