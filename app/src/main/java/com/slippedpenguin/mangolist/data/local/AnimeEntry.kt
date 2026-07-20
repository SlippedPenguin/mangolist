package com.slippedpenguin.mangolist.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/*
 * Single-table schema for the user's local list.
 *
 * Design notes (from the JS prototype at anime-tracker/index.html):
 *   - `tier` + `elo` are local-only tierlist data. Never pushed to AniList
 *     (AniList has no tierlist field, and the user wants this kept private).
 *   - `currentEp` is the local progress counter; sync pushes it to AniList's
 *     `progress` field when `syncedAt` is present. For manga the same field
 *     stores chapter progress (AniList's `progress` for MANGA covers either
 *     chapters or volumes by your scoring-system preference; we treat it
 *     as chapters since that's the dominant convention).
 *   - `listEntryId` is the AniList MediaList id — null means "never synced."
 *   - `updatedAt` is the high-water mark for last-write-wins sync conflicts.
 *   - `genres` is a comma-separated string to avoid a join table for v1
 *     (a normalised Genres column is a v1.x concern).
 *
 * v1.2: added `mediaType` discriminator (ROOM v4) so a single table holds
 * both anime and manga. Defaults to "ANIME" so v3 rows migrate untouched.
 * AniList assigns globally-unique ids across ANIME+MANGA, so `(anilistId)`
 * remains a safe PK — `(anilistId, mediaType)` would be overkill here and
 * would complicate every existing Room query.
 *
 * v1.3: added `chapters` and `volumes` columns so manga entries can track
 * both chapter and volume progress independently of anime episode counts.
 * Existing rows default to NULL for these columns; `currentEp` continues
 * to store the active progress value (episodes for anime, chapters for manga).
 */
@Entity(tableName = "anime_entries")
data class AnimeEntry(
    @PrimaryKey
    val anilistId: Int,

    // AniList-sourced immutable metadata (refreshed on next search/details fetch)
    val title: String,
    val cover: String?,
    val coverColor: String?,
    val format: String?,
    val episodes: Int?,               // anime episode count
    val chapters: Int? = null,        // manga chapter count (v1.3)
    val volumes: Int? = null,           // manga volume count (v1.3)
    val averageScore: Int?,
    val year: Int?,
    val synopsis: String?,
    val genres: String,
    val mediaType: String = "ANIME",  // "ANIME" or "MANGA"; set from graphql `type` on every pull

    // Tierlist / Elo (local-only)
    val tier: String?,
    val elo: Int,

    // Tracking
    val currentEp: Int,
    val status: String,
    val notes: String,
    val personalScore: Int? = null,
    val favourite: Boolean = false,

    // Sync metadata
    val listEntryId: Int?,
    val updatedAt: Long,
    val syncedAt: Long?,
) {
    /**
     * Merges an incoming synced entry with an existing local row.
     *
     * Local-only tierlist data (`tier`, `elo`) is always preserved. If the
     * local row has been edited more recently than the server row
     * (`existing.updatedAt > this.updatedAt`), the user's tracking fields
     * (`status`, `currentEp`, `notes`, `personalScore`, `favourite`) and the
     * local `updatedAt`/`syncedAt` are kept. Otherwise the server values win.
     *
     * v1.2: also preserves `mediaType` — a manga entry upserted with ANIME
     * defaults is corrected to the incoming mediaType on the same row, but
     * `tier`/`elo` always come from the existing local row.
     */
    fun preserveLocalFields(existing: AnimeEntry?): AnimeEntry {
        if (existing == null) return this
        val localIsNewer = existing.updatedAt > this.updatedAt
        return if (localIsNewer) {
            // Keep the user's local edits but refresh server IDs and keep
            // immutable metadata (title, cover, etc.) from the server payload.
            this.copy(
                status = existing.status,
                currentEp = existing.currentEp,
                notes = existing.notes,
                personalScore = existing.personalScore,
                favourite = existing.favourite,
                updatedAt = existing.updatedAt,
                syncedAt = existing.syncedAt ?: this.syncedAt,
                tier = existing.tier ?: this.tier,
                elo = existing.elo ?: this.elo,
                listEntryId = this.listEntryId ?: existing.listEntryId,
            )
        } else {
            this.copy(
                tier = existing.tier ?: this.tier,
                elo = existing.elo ?: this.elo,
            )
        }
    }
}
