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
 *     `progress` field when `syncedAt` is present.
 *   - `listEntryId` is the AniList MediaList id — null means "never synced."
 *   - `updatedAt` is the high-water mark for last-write-wins sync conflicts.
 *   - `genres` is a comma-separated string to avoid a join table for v1
 *     (a normalised Genres column is a v1.x concern).
 */
@Entity(tableName = "anime_entries")
data class AnimeEntry(
    @PrimaryKey
    val anilistId: Int,

    // AniList-sourced immutable metadata (refreshed on next search/details fetch)
    val title: String,
    val cover: String?,
    val coverColor: String?,
    val format: String?,           // "TV", "MOVIE", "OVA", "MANGA" → ANIME here
    val episodes: Int?,            // null if AniList reports "currently airing"
    val averageScore: Int?,        // 0-100; we divide by 10 for display
    val year: Int?,
    val synopsis: String?,
    val genres: String,            // comma-separated, v1 simplicity

    // Tierlist / Elo (local-only)
    val tier: String?,             // "S" | "A" | "B" | "C" | "D" | null = unranked
    val elo: Int,                  // default 1500

    // Tracking
    val currentEp: Int,            // 0..episodes
    val status: String,            // "plan" | "watching" | "completed" | "dropped"
    val notes: String,
    val personalScore: Int? = null,  // null = unset; 0-100 (maps to 0-10 with one decimal)

    // Sync metadata
    val listEntryId: Int?,         // AniList MediaList id (null = never synced)
    val updatedAt: Long,           // last local edit (epoch millis)
    val syncedAt: Long?,           // last successful push (epoch millis)
)
