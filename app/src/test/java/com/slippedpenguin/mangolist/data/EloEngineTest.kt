package com.slippedpenguin.mangolist.data

import com.slippedpenguin.mangolist.data.local.AnimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EloEngineTest {

    private fun entry(
        id: Int,
        tier: String?,
        elo: Int = EloEngine.INITIAL_ELO,
    ) = AnimeEntry(
        anilistId = id,
        title = "Show $id",
        cover = null,
        coverColor = null,
        format = "TV",
        episodes = 12,
        averageScore = null,
        year = 2024,
        synopsis = null,
        genres = "",
        tier = tier,
        elo = elo,
        currentEp = 0,
        status = "watching",
        notes = "",
        listEntryId = null,
        updatedAt = 0L,
        syncedAt = null,
    )

    // ---- update() ----

    @Test
    fun `equal ratings split the K-factor evenly`() {
        val r = EloEngine.update(1500, 1500)
        assertEquals(1516, r.newWinner)
        assertEquals(1484, r.newLoser)
    }

    @Test
    fun `expected win barely moves the ratings`() {
        val r = EloEngine.update(1900, 1300)
        assertEquals(1901, r.newWinner)
        assertEquals(1299, r.newLoser)
    }

    @Test
    fun `upset win moves the ratings by nearly the full K-factor`() {
        val r = EloEngine.update(1300, 1900)
        assertEquals(1331, r.newWinner)
        assertEquals(1869, r.newLoser)
    }

    @Test
    fun `total elo is conserved after rounding`() {
        val r = EloEngine.update(1723, 1409)
        assertEquals(r.newWinner + r.newLoser, 1723 + 1409)
    }

    @Test
    fun `custom k factor scales the delta`() {
        val equal = EloEngine.update(1500, 1500, k = 16)
        assertEquals(1508, equal.newWinner)
    }

    // ---- bumpElo() ----

    @Test
    fun `bumpElo clamps at the lower bound`() {
        assertEquals(900, EloEngine.bumpElo(905, -50))
    }

    @Test
    fun `bumpElo clamps at the upper bound`() {
        assertEquals(2100, EloEngine.bumpElo(2095, 50))
    }

    @Test
    fun `bumpElo applies normal deltas unchanged`() {
        assertEquals(1550, EloEngine.bumpElo(1500, 50))
    }

    // ---- medianElo / proposeTier ----

    @Test
    fun `medianElo picks middle value for odd counts`() {
        val entries = listOf(entry(1, "S", 1800), entry(2, "S", 2000), entry(3, "S", 1900))
        assertEquals(1900, EloEngine.medianElo("S", entries))
    }

    @Test
    fun `medianElo averages the two middles for even counts`() {
        val entries = listOf(entry(1, "A", 1600), entry(2, "A", 1700))
        assertEquals(1650, EloEngine.medianElo("A", entries))
    }

    @Test
    fun `medianElo returns null for an empty tier`() {
        assertNull(EloEngine.medianElo("D", emptyList()))
    }

    @Test
    fun `proposeTier falls back to B when nothing is ranked`() {
        assertEquals("B", EloEngine.proposeTier(1500, listOf(entry(1, null))))
    }

    @Test
    fun `proposeTier picks the closest median`() {
        val entries = listOf(
            entry(1, "S", 1950),
            entry(2, "C", 1450),
            entry(3, "D", 1100),
        )
        assertEquals("C", EloEngine.proposeTier(1470, entries))
    }

    // ---- tierForScore ----

    @Test
    fun `unrated scores produce no tier`() {
        assertNull(EloEngine.tierForScore(null))
        assertNull(EloEngine.tierForScore(0))
    }

    @Test
    fun `score thresholds map to the right tiers`() {
        assertEquals("D", EloEngine.tierForScore(59))
        assertEquals("C", EloEngine.tierForScore(60))
        assertEquals("B", EloEngine.tierForScore(70))
        assertEquals("A", EloEngine.tierForScore(80))
        assertEquals("S", EloEngine.tierForScore(90))
        assertEquals("S", EloEngine.tierForScore(100))
    }

    // ---- eloForScore ----

    @Test
    fun `midpoint score lands on the initial elo`() {
        assertEquals(EloEngine.INITIAL_ELO, EloEngine.eloForScore(50))
    }

    @Test
    fun `max score clamps to the top of the scale`() {
        assertEquals(2100, EloEngine.eloForScore(100))
        assertEquals(2100, EloEngine.eloForScore(999))
    }

    @Test
    fun `unrated scores fall back to initial elo`() {
        assertEquals(EloEngine.INITIAL_ELO, EloEngine.eloForScore(null))
        assertEquals(EloEngine.INITIAL_ELO, EloEngine.eloForScore(0))
    }
}
