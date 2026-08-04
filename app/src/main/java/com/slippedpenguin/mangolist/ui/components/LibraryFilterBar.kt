package com.slippedpenguin.mangolist.ui.components

import com.slippedpenguin.mangolist.data.local.AnimeEntry

/*
 * Watchlist filter + sort helpers — v1.5.6.
 *
 * The interactive LibraryFilterBar composable is gone: v1.5.6 moves the
 * category selection and the three-line sort button into the LibraryHeader
 * corner (AniHyou style). What remains here is the shared vocabulary the
 * header and screens need:
 *
 *   - FAVORITES_FILTER — sentinel key for the favorites category
 *   - libraryFilterKeys — canonical status order for menus
 *   - statusFilterLabel — manga-aware display labels (Reading/Rereading)
 *   - LibrarySortMode + sortLibraryEntries — the sort menu's options
 */

internal const val FAVORITES_FILTER = "__favorites__"

internal val libraryFilterKeys = listOf<String?>(
    null,
    "watching",
    "completed",
    "plan",
    "paused",
    "repeating",
    "dropped",
    FAVORITES_FILTER,
)

/*
 * v1.5.3: manga-aware status filter labels. AniList stores one status
 * string for both media types, so the display label is derived at render
 * time: anime "watching"/"repeating", manga "reading"/"rereading".
 */
internal fun statusFilterLabel(status: String?, mediaType: String): String = when (status) {
    "watching"  -> if (mediaType == "MANGA") "Reading" else "Watching"
    "repeating" -> if (mediaType == "MANGA") "Rereading" else "Repeating"
    FAVORITES_FILTER -> "Favorites"
    null        -> "All"
    else        -> status.replaceFirstChar { it.uppercase() }
}

/*
 * v1.5.6: sort modes for the watchlist. UPDATED (most recently edited
 * first) is the default — it mirrors the Activity tab's ordering.
 */
enum class LibrarySortMode(val label: String) {
    UPDATED("Recently updated"),
    TITLE("Title A–Z"),
    SCORE("Score"),
    PROGRESS("Progress"),
    TIER("Tier"),
}

/*
 * sortLibraryEntries — applies a LibrarySortMode to a filtered list.
 * Tiers sort S → D with unranked last; scores/progress nulls sink to the
 * bottom so incomplete entries never jump the queue.
 */
internal fun sortLibraryEntries(
    entries: List<AnimeEntry>,
    mode: LibrarySortMode,
): List<AnimeEntry> = when (mode) {
    LibrarySortMode.UPDATED -> entries.sortedByDescending { it.updatedAt }
    LibrarySortMode.TITLE   -> entries.sortedBy { it.title.lowercase() }
    LibrarySortMode.SCORE   -> entries.sortedByDescending { it.personalScore ?: 0 }
    LibrarySortMode.PROGRESS -> entries.sortedByDescending { it.currentEp }
    LibrarySortMode.TIER    -> entries.sortedBy { tierRank(it.tier) }
}

private fun tierRank(tier: String?): Int = when (tier) {
    "S" -> 0
    "A" -> 1
    "B" -> 2
    "C" -> 3
    "D" -> 4
    else -> 5
}
