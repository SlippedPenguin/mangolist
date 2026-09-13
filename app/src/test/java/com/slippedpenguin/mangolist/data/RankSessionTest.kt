package com.slippedpenguin.mangolist.data

import com.slippedpenguin.mangolist.data.local.AnimeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankSessionTest {

    private fun entry(
        id: Int,
        tier: String? = null,
        elo: Int = 1500,
        personalScore: Int? = null,
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
        currentEp = 0,
        status = "watching",
        notes = "",
        personalScore = personalScore,
        listEntryId = null,
        updatedAt = 1_000L,
        syncedAt = null,
    )

    // ---- session start ----

    @Test
    fun `start pairs the first unranked candidate with the closest-elo ranked title`() {
        val ranked = listOf(
            entry(1, tier = "S", elo = 2000),
            entry(2, tier = "B", elo = 1500),
        )
        val session = RankSession.start(ranked, unranked = listOf(entry(3, elo = 1550)))

        assertEquals(3, session.candidate?.anilistId)
        assertEquals(2, session.opponent?.anilistId) // |1550-1500| < |1550-2000|
        assertEquals(1, session.remainingCount)
        assertEquals(0, session.judgedCount)
        assertFalse(session.isDone)
    }

    @Test
    fun `start with nothing ranked lands the candidate immediately at initial elo`() {
        val session = RankSession.start(ranked = emptyList(), unranked = listOf(entry(3)))

        assertTrue(session.isDone)
        val landed = session.committedRows.single()
        assertEquals(3, landed.anilistId)
        assertEquals("B", landed.tier) // proposeTier fallback
        assertEquals(EloEngine.INITIAL_ELO, landed.elo)
    }

    @Test
    fun `start with an empty pool is immediately done`() {
        val session = RankSession.start(ranked = listOf(entry(1, tier = "S")), unranked = emptyList())
        assertTrue(session.isDone)
        assertTrue(session.committedRows.isEmpty())
    }

    // ---- choosing a winner ----

    @Test
    fun `candidate win applies the real elo update and proposes a tier`() {
        val ranked = listOf(entry(1, tier = "B", elo = 1500))
        val session = RankSession.start(ranked, unranked = listOf(entry(3, elo = 1500)))
        val next = session.choose(candidateWins = true)

        // K=32, equal elos → delta 16 exactly (16.0 rounds to 16)
        val expected = EloEngine.update(winnerElo = 1500, loserElo = 1500)
        val landed = next.committedRows.first { it.anilistId == 3 }
        assertEquals(expected.newWinner, landed.elo)
        // proposeTier only considers tiers that HAVE entries: with only B
        // populated, the landed candidate joins B (the fallback is unused).
        assertEquals("B", landed.tier)
        assertEquals(1, next.judgedCount)
        assertTrue(next.isDone) // pool exhausted
    }

    @Test
    fun `candidate loss applies the loser-side elo`() {
        val ranked = listOf(entry(1, tier = "B", elo = 1500))
        val session = RankSession.start(ranked, unranked = listOf(entry(3, elo = 1500)))
        val next = session.choose(candidateWins = false)

        val expected = EloEngine.update(winnerElo = 1500, loserElo = 1500)
        val landed = next.committedRows.first { it.anilistId == 3 }
        assertEquals(expected.newLoser, landed.elo)
    }

    @Test
    fun `opponent elo also moves in the committed row`() {
        val ranked = listOf(entry(1, tier = "B", elo = 1500))
        val session = RankSession.start(ranked, unranked = listOf(entry(3, elo = 1500)))
        val next = session.choose(candidateWins = true)

        val expected = EloEngine.update(winnerElo = 1500, loserElo = 1500)
        val opp = next.committedRows.first { it.anilistId == 1 }
        assertEquals(expected.newLoser, opp.elo)
        assertEquals("B", opp.tier) // opponent keeps its tier; only elo shifts
    }

    @Test
    fun `h2h lands never bump updatedAt`() {
        val ranked = listOf(entry(1, tier = "B", elo = 1500))
        val session = RankSession.start(ranked, unranked = listOf(entry(3)))
        val next = session.choose(candidateWins = true)

        next.committedRows.forEach { row ->
            assertEquals(1_000L, row.updatedAt)
            assertNull(row.syncedAt)
        }
    }

    @Test
    fun `choose and skip are no-ops on a finished session`() {
        val done = RankSession.start(emptyList(), emptyList())
        assertEquals(done, done.choose(candidateWins = true))
        assertEquals(done, done.skip())
    }

    // ---- skip / pool behavior ----

    @Test
    fun `skip defers the candidate and advances to the next one`() {
        val ranked = listOf(entry(1, tier = "B", elo = 1500))
        val session = RankSession.start(ranked, unranked = listOf(entry(3), entry(4)))
        val skipped = session.skip()

        assertEquals(4, skipped.candidate?.anilistId)
        assertFalse(skipped.isDone)
        assertEquals(0, skipped.judgedCount)
    }

    @Test
    fun `a session with multiple candidates keeps committing through the pool`() {
        val ranked = listOf(entry(1, tier = "B", elo = 1500))
        val session = RankSession.start(ranked, unranked = listOf(entry(3), entry(4)))

        val afterFirst = session.choose(candidateWins = true)
        assertEquals(4, afterFirst.candidate?.anilistId)
        assertFalse(afterFirst.isDone)

        val afterSecond = afterFirst.choose(candidateWins = false)
        assertTrue(afterSecond.isDone)
        assertEquals(2, afterSecond.judgedCount)
        // Three ids present: two landed candidates + the shifted opponent
        assertEquals(setOf(1, 3, 4), afterSecond.committedRows.map { it.anilistId }.toSet())
    }

    // ---- proposeTier sanity (engine contract the session relies on) ----

    @Test
    fun `proposeTier picks the tier whose median is closest`() {
        val ranked = listOf(
            entry(1, tier = "S", elo = 1900),
            entry(2, tier = "A", elo = 1600),
            entry(3, tier = "C", elo = 1300),
        )
        assertEquals("A", EloEngine.proposeTier(1620, ranked))
        assertEquals("S", EloEngine.proposeTier(1850, ranked))
        assertEquals("C", EloEngine.proposeTier(1350, ranked))
    }
}
