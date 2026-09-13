package com.slippedpenguin.mangolist.data

import com.slippedpenguin.mangolist.data.local.AnimeEntry

/*
 * v1.7: pure model behind the drag & drop tierlist.
 *
 * The TiersScreen composable holds a `List<TierSection>` while a drag is in
 * flight and mutates it via [TierListModel.move] as the finger crosses tier
 * boundaries; on drop it asks [TierListModel.commitRows] for the exact Room
 * rows to persist. Keeping that logic here (no Android/Compose imports)
 * means the reorder semantics are unit-testable in CI — the gesture shell
 * stays a thin layer over tested behavior.
 *
 * Ordering contract (matches the v1.7 DAO query):
 *   - A manually dragged row carries a dense 0-based `tierRank` inside its
 *     tier and sorts ahead of never-dragged rows.
 *   - Never-dragged rows keep the v1.5.7 default: personalScore DESC, elo DESC.
 *   - Any drop densifies ranks across both affected sections so the visible
 *     order and the stored order can't drift apart.
 *
 * updatedAt is NEVER touched here. tier/elo/tierRank are local-only fields
 * (never pushed to AniList); bumping updatedAt would flag every drag as a
 * pending sync and drain no-op pushes to AniList (same reason the v1.5.1
 * auto-rank path avoids it).
 */
data class TierSection(val tier: String?, val entries: List<AnimeEntry>)

object TierListModel {

    /** Elo assigned on landing in `tier`: its median, or the initial value when empty. */
    fun eloForLanding(tier: String?, sectionEntries: List<AnimeEntry>): Int {
        if (tier == null) return EloEngine.INITIAL_ELO
        return EloEngine.medianElo(tier, sectionEntries) ?: EloEngine.INITIAL_ELO
    }

    /**
     * Move `entryId` into section `destTier` at `destIndex` (index within that
     * section's entry list, clamped). Returns the new section list. Pure —
     * callers own the state lifecycle (drag start/end/cancel).
     */
    fun move(
        sections: List<TierSection>,
        entryId: Int,
        destTier: String?,
        destIndex: Int,
    ): List<TierSection> {
        var dragged: AnimeEntry? = null
        val removed = sections.map { section ->
            val hit = section.entries.firstOrNull { it.anilistId == entryId }
            if (hit != null) {
                dragged = hit
                section.copy(entries = section.entries.filterNot { it.anilistId == entryId })
            } else {
                section
            }
        }
        val entry = dragged ?: return sections
        val destIdx = destIndex.coerceIn(0, (removed.firstOrNull { it.tier == destTier }?.entries?.size ?: 0))
        return removed.map { section ->
            if (section.tier == destTier) {
                val mutable = section.entries.toMutableList()
                mutable.add(destIdx, entry)
                section.copy(entries = mutable)
            } else {
                section
            }
        }
    }

    /**
     * Rows to persist after a drop of `entryId`: the dragged row (new tier +
     * median-derived elo) plus dense tierRank re-indexing of every row in the
     * affected sections. Unranked landings clear tier and tierRank.
     */
    fun commitRows(sections: List<TierSection>, entryId: Int): List<AnimeEntry> {
        val destSection = sections.firstOrNull { s -> s.entries.any { it.anilistId == entryId } }
            ?: return emptyList()
        val rows = mutableListOf<AnimeEntry>()

        destSection.entries.forEachIndexed { index, entry ->
            if (entry.anilistId == entryId) {
                rows += entry.copy(
                    tier = destSection.tier,
                    elo = eloForLanding(destSection.tier, destSection.entries),
                    tierRank = if (destSection.tier == null) null else index,
                )
            } else if (destSection.tier != null) {
                rows += entry.copy(tierRank = index)
            }
        }
        return rows
    }

    /** Section list snapshot built from DAO flows — the drag model's starting shape. */
    fun sectionsFrom(
        tiers: List<String>,
        byTier: Map<String, List<AnimeEntry>>,
        unranked: List<AnimeEntry>,
    ): List<TierSection> =
        tiers.map { TierSection(it, byTier[it].orEmpty()) } + TierSection(null, unranked)
}
