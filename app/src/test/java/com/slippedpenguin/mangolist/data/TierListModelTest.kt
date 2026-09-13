package com.slippedpenguin.mangolist.data

import com.slippedpenguin.mangolist.data.local.AnimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TierListModelTest {

    private fun entry(
        id: Int,
        tier: String? = null,
        elo: Int = 1500,
        personalScore: Int? = null,
        tierRank: Int? = null,
        updatedAt: Long = 1_000L,
    ) = AnimeEntry(
        anilistId = id,
        title = "Entry $id",
        cover = null,
        coverColor = null,
        format = "TV",
        episodes = 12,
        averageScore = null,
        year = 2024,
        synopsis = null,
        genres = "",
        mediaType = "ANIME",
        tier = tier,
        elo = elo,
        tierRank = tierRank,
        currentEp = 0,
        status = "watching",
        notes = "",
        personalScore = personalScore,
        listEntryId = null,
        updatedAt = updatedAt,
        syncedAt = null,
    )

    private fun sections(vararg pairs: Pair<String?, List<Int>>) =
        pairs.map { (tier, ids) ->
            TierSection(
                tier,
                ids.map { entry(it, tier = tier, elo = 1500, personalScore = 50, tierRank = null) },
            )
        }

    // ---- move ----

    @Test
    fun `move reorders within the same tier`() {
        val s = sections("S" to listOf(1, 2, 3))
        val moved = TierListModel.move(s, entryId = 3, destTier = "S", destIndex = 0)
        assertEquals(listOf(3, 1, 2), moved.first { it.tier == "S" }.entries.map { it.anilistId })
    }

    @Test
    fun `move transfers an entry between tiers`() {
        val s = sections("S" to listOf(1, 2), "B" to listOf(5))
        val moved = TierListModel.move(s, entryId = 1, destTier = "B", destIndex = 1)
        assertEquals(listOf(2), moved.first { it.tier == "S" }.entries.map { it.anilistId })
        assertEquals(listOf(5, 1), moved.first { it.tier == "B" }.entries.map { it.anilistId })
    }

    @Test
    fun `move clamps an out-of-range destination index`() {
        val s = sections("S" to listOf(1), "A" to emptyList())
        val moved = TierListModel.move(s, entryId = 1, destTier = "A", destIndex = 99)
        assertEquals(listOf(1), moved.first { it.tier == "A" }.entries.map { it.anilistId })
    }

    @Test
    fun `move with an unknown entry id is a no-op`() {
        val s = sections("S" to listOf(1))
        assertEquals(s, TierListModel.move(s, entryId = 999, destTier = "A", destIndex = 0))
    }

    @Test
    fun `move into the unranked bucket removes the tier`() {
        val s = sections("S" to listOf(1), null to listOf(9))
        val moved = TierListModel.move(s, entryId = 1, destTier = null, destIndex = 0)
        assertEquals(listOf(1, 9), moved.first { it.tier == null }.entries.map { it.anilistId })
        assertTrue(moved.first { it.tier == "S" }.entries.isEmpty())
    }

    // ---- commitRows ----

    @Test
    fun `commit assigns the landing tier and densifies ranks in the destination`() {
        val s = sections("S" to listOf(1), "A" to listOf(2, 3))
        val moved = TierListModel.move(s, entryId = 1, destTier = "A", destIndex = 0)
        val rows = TierListModel.commitRows(moved, entryId = 1)

        val dragged = rows.first { it.anilistId == 1 }
        assertEquals("A", dragged.tier)
        assertEquals(0, dragged.tierRank)
        // Destination neighbors get dense ranks too
        assertEquals(1, rows.first { it.anilistId == 2 }.tierRank)
        assertEquals(2, rows.first { it.anilistId == 3 }.tierRank)
    }

    @Test
    fun `commit landing elo equals the destination tier median`() {
        // Tier B holds entries at elo 1600 and 1800 → median 1700.
        val s = listOf(
            TierSection("B", listOf(entry(2, tier = "B", elo = 1600), entry(3, tier = "B", elo = 1800))),
            TierSection(null, listOf(entry(1))),
        )
        val moved = TierListModel.move(s, entryId = 1, destTier = "B", destIndex = 1)
        val dragged = TierListModel.commitRows(moved, entryId = 1).first { it.anilistId == 1 }
        assertEquals(1700, dragged.elo)
    }

    @Test
    fun `commit into an empty tier resets elo to initial`() {
        val s = sections("S" to listOf(1), "D" to emptyList())
        val moved = TierListModel.move(s, entryId = 1, destTier = "D", destIndex = 0)
        val dragged = TierListModel.commitRows(moved, entryId = 1).first { it.anilistId == 1 }
        assertEquals(EloEngine.INITIAL_ELO, dragged.elo)
        assertEquals("D", dragged.tier)
    }

    @Test
    fun `commit into unranked clears tier tierRank and resets elo`() {
        val s = sections("S" to listOf(1), null to emptyList())
        val moved = TierListModel.move(s, entryId = 1, destTier = null, destIndex = 0)
        val unranked = TierListModel.commitRows(moved, entryId = 1).first { it.anilistId == 1 }
        assertNull(unranked.tier)
        assertNull(unranked.tierRank)
        assertEquals(EloEngine.INITIAL_ELO, unranked.elo)
    }

    @Test
    fun `commit leaves updatedAt untouched so drags never flag sync`() {
        val s = sections("S" to listOf(1), "A" to listOf(2))
        val moved = TierListModel.move(s, entryId = 1, destTier = "A", destIndex = 0)
        val rows = TierListModel.commitRows(moved, entryId = 1)
        rows.forEach { row ->
            assertEquals(1_000L, row.updatedAt)
            assertNull(row.syncedAt) // unchanged → SyncWorker sees no pending push
        }
    }

    @Test
    fun `sectionsFrom builds tiers plus an unranked tail`() {
        val byTier = mapOf("S" to listOf(entry(1, tier = "S")))
        val s = TierListModel.sectionsFrom(EloEngine.TIERS, byTier, listOf(entry(9)))
        assertEquals(EloEngine.TIERS.size + 1, s.size)
        assertEquals("S", s.first().tier)
        assertNull(s.last().tier)
        assertEquals(listOf(9), s.last().entries.map { it.anilistId })
    }
}
