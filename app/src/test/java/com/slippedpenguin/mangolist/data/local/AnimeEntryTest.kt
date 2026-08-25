package com.slippedpenguin.mangolist.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeEntryTest {

    private fun entry(
        id: Int = 1,
        title: String = "Frieren",
        status: String = "watching",
        currentEp: Int = 3,
        notes: String = "",
        personalScore: Int? = null,
        favourite: Boolean = false,
        tier: String? = null,
        elo: Int = 1500,
        listEntryId: Int? = null,
        updatedAt: Long = 1_000L,
        syncedAt: Long? = null,
    ) = AnimeEntry(
        anilistId = id,
        title = title,
        cover = null,
        coverColor = null,
        format = "TV",
        episodes = 28,
        averageScore = null,
        year = 2023,
        synopsis = null,
        genres = "Fantasy",
        mediaType = "ANIME",
        tier = tier,
        elo = elo,
        currentEp = currentEp,
        status = status,
        notes = notes,
        personalScore = personalScore,
        favourite = favourite,
        listEntryId = listEntryId,
        updatedAt = updatedAt,
        syncedAt = syncedAt,
    )

    // ---- preserveLocalFields ----

    @Test
    fun `null existing row returns the incoming payload untouched`() {
        val incoming = entry(status = "completed", currentEp = 28)
        val merged = incoming.preserveLocalFields(null)
        assertEquals(incoming, merged)
    }

    @Test
    fun `server-newer merge keeps server tracking but preserves local tierlist`() {
        val incoming = entry( // server snapshot: older updatedAt, fresh metadata
            title = "Frieren (updated cover art)",
            status = "watching",
            currentEp = 7,
            personalScore = 85,
            tier = "S", // stale server-side tier must never overwrite local
            elo = 2100,
            updatedAt = 500L,
            listEntryId = 42,
        )
        val existing = entry(
            status = "paused",
            currentEp = 4,
            notes = "on hold",
            personalScore = 80,
            favourite = true,
            tier = "A",
            elo = 1750,
            updatedAt = 2_000L, // newer than server
            syncedAt = 1_500L,
        )

        val merged = incoming.preserveLocalFields(existing)

        // Local tracking wins
        assertEquals("paused", merged.status)
        assertEquals(4, merged.currentEp)
        assertEquals("on hold", merged.notes)
        assertEquals(80, merged.personalScore)
        assertEquals(true, merged.favourite)
        assertEquals(2_000L, merged.updatedAt)

        // Local-only tierlist data survives every pull
        assertEquals("A", merged.tier)
        assertEquals(1750, merged.elo)

        // Fresh server identity + metadata still come through
        assertEquals(42, merged.listEntryId)
        assertEquals("Frieren (updated cover art)", merged.title)
    }

    @Test
    fun `server-newer merge takes server tracking fields`() {
        val incoming = entry(status = "completed", currentEp = 28, updatedAt = 3_000L)
        val existing = entry(status = "watching", currentEp = 3, updatedAt = 1_000L, tier = "B")

        val merged = incoming.preserveLocalFields(existing)

        assertEquals("completed", merged.status)
        assertEquals(28, merged.currentEp)
        // tier/elo are still local-only, even when the server row is newer
        assertEquals("B", merged.tier)
    }

    @Test
    fun `merge falls back to the incoming syncedAt when the local one is absent`() {
        val incoming = entry(updatedAt = 3_000L, syncedAt = 2_900L)
        val existing = entry(updatedAt = 4_000L, syncedAt = null)

        val merged = incoming.preserveLocalFields(existing)
        assertEquals(2_900L, merged.syncedAt)
    }

    @Test
    fun `never-synced local row adopts the server listEntryId`() {
        val incoming = entry(listEntryId = 77, updatedAt = 3_000L)
        val existing = entry(listEntryId = null, updatedAt = 4_000L)

        val merged = incoming.preserveLocalFields(existing)
        assertEquals(77, merged.listEntryId)
    }

    @Test
    fun `manga rows keep their mediaType through a merge`() {
        val incoming = entry(updatedAt = 3_000L).copy(mediaType = "MANGA")
        val existing = entry(updatedAt = 4_000L).copy(mediaType = "MANGA", tier = "S")

        val merged = incoming.preserveLocalFields(existing)
        assertEquals("MANGA", merged.mediaType)
        assertNull(merged.chapters)
        assertEquals(4_000L, merged.updatedAt)
    }
}
