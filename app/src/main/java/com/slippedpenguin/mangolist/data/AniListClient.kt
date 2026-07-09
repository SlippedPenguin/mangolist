package com.slippedpenguin.mangolist.data

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.graphql.SearchAnimeQuery

/*
 * AniListClient — thin wrapper over Apollo Kotlin 4.x for the AniList GraphQL
 * API. v0.2.2 exposes only `search()` (the AniList SearchAnime query is
 * public — no auth required). Phase C will add:
 *   - getViewer(token)        — read after OAuth PIN exchange
 *   - getMediaListCollection  — sync-pull
 *   - saveEntry(token, ...)   — sync-push via the SaveMediaListEntry mutation
 *
 * The ApolloClient instance is built per-AnimeApp and reuses a single ENetwork
 * for the lifetime of the process. We attach the OAuth bearer token only when
 * one is available, otherwise the search endpoint is reachable anonymously.
 */
class AniListClient(@Suppress("UNUSED_PARAMETER") context: Context) {

    private val apollo: ApolloClient = ApolloClient.Builder()
        .serverUrl("https://graphql.anilist.co")
        .build()

    /**
     * Search AniList for anime matching [query]. Returns up to 12 results
     * (the page size declared in `queries.graphql`). Caller upserts any
     * desired hit into Room via AnimeDao.
     *
     * Returns an empty list on any error so the UI can render "no matches"
     * rather than crash. Detailed errors are logged but not surfaced (Phase
     * C exposes them through TokenStore events if needed).
     */
    suspend fun search(query: String): List<AnimeEntry> {
        if (query.isBlank()) return emptyList()
        val response = try {
            apollo.query(SearchAnimeQuery(search = query)).execute()
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "search failed", e)
            return emptyList()
        }

        val now = System.currentTimeMillis()
        // Apollo Kotlin 4.x codegen surfaces the bottom-level `media(...) { ...AnimeCardFields }`
        // selection as a `SearchAnimeQuery.Media` whose only direct property is the
        // fragment-spread wrapper `animeCardFields: AnimeCardFields?` — fragment
        // fields (`id`, `title`, `coverImage`, ...) are NOT inlined onto Media. Pull
        // the fragment out first, then map onto AnimeEntry. `mapNotNull` drops any
        // entries where the fragment is somehow absent.
        return response.data?.Page?.media.orEmpty().filterNotNull().mapNotNull { entry ->
            val m = entry.animeCardFields ?: return@mapNotNull null
            AnimeEntry(
                anilistId    = m.id,
                // `m.title` is the wrapped-fragment selection-set (AnimeCardFields.Title?),
                // itself nullable. Use safe-calls here so the elvis chain still resolves.
                title        = m.title?.english ?: m.title?.romaji ?: "Untitled",
                cover        = m.coverImage?.large ?: m.coverImage?.medium,
                coverColor   = m.coverImage?.color,
                format       = m.format?.rawValue,
                episodes     = m.episodes,
                averageScore = m.averageScore,
                year         = m.startDate?.year,
                synopsis     = null,
                genres       = "",
                tier         = null,
                elo          = 1500,
                currentEp    = 0,
                status       = "plan",
                notes        = "",
                listEntryId  = null,
                updatedAt    = now,
                syncedAt     = null,
            )
        }
    }
}
