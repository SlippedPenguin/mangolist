package com.slippedpenguin.mangolist.data

import com.slippedpenguin.mangolist.data.local.AnimeEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * v1.7.1: the h2h eligibility gate. User feedback that drove this: h2h was
 * offering unfinished / not-started titles (impossible to judge) and mixing
 * anime with manga (meaningless comparison). Medium separation is enforced
 * by the screen's per-medium seeding; these tests pin the finished/rated
 * gate itself.
 */
class RankSessionEligibilityTest {

    private fun entry(
        id: Int = 1,
        mediaType: String = "ANIME",
        status: String = "watching",
        currentEp: Int = 0,
        episodes: Int? = 12,
        chapters: Int? = null,
        volumes: Int? = null,
        format: String? = "TV",
        personalScore: Int? = null,
        tier: String? = null,
    ) = AnimeEntry(
        anilistId = id,
        title = "Entry $id",
        cover = null,
        coverColor = null,
        format = format,
        episodes = episodes,
        chapters = chapters,
        volumes = volumes,
        averageScore = null,
        year = 2024,
        synopsis = null,
        genres = "",
        mediaType = mediaType,
        tier = tier,
        elo = 1500,
        currentEp = currentEp,
        status = status,
        notes = "",
        personalScore = personalScore,
        listEntryId = null,
        updatedAt = 1_000L,
        syncedAt = null,
    )

    @Test
    fun `not-started unrated title is not eligible`() {
        val e = entry(status = "plan", currentEp = 0)
        assertFalse(RankSession.isEligible(e))
    }

    @Test
    fun `partially watched unrated title is not eligible`() {
        val e = entry(status = "watching", currentEp = 5, episodes = 12)
        assertFalse(RankSession.isEligible(e))
    }

    @Test
    fun `completed title is eligible regardless of stored progress`() {
        val e = entry(status = "completed", currentEp = 0, episodes = null)
        assertTrue(RankSession.isEligible(e))
    }

    @Test
    fun `reaching the known episode cap is eligible`() {
        val e = entry(status = "watching", currentEp = 12, episodes = 12)
        assertTrue(RankSession.isEligible(e))
    }

    @Test
    fun `manga eligibility uses chapters`() {
        val mid = entry(mediaType = "MANGA", currentEp = 40, chapters = 120, episodes = null, format = "MANGA")
        assertFalse(RankSession.isEligible(mid))
        val done = entry(mediaType = "MANGA", currentEp = 120, chapters = 120, episodes = null, format = "MANGA")
        assertTrue(RankSession.isEligible(done))
    }

    @Test
    fun `a rating makes even an unfinished title eligible`() {
        val e = entry(status = "watching", currentEp = 3, personalScore = 85)
        assertTrue(RankSession.isEligible(e))
    }

    @Test
    fun `already-ranked titles never enter the pool`() {
        val e = entry(tier = "S", status = "completed")
        assertFalse(RankSession.isEligible(e))
    }
}
