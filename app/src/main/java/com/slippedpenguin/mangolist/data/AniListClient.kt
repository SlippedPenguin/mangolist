package com.slippedpenguin.mangolist.data

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.graphql.GetViewerQuery
import com.slippedpenguin.mangolist.graphql.SearchAnimeQuery

/*
 * AniListClient — thin wrapper over Apollo Kotlin 4.x for the AniList GraphQL
 * API.
 *
 * v0.3.0 surface:
 *   - search(query)        — anonymous; uses the singleton `apollo` instance.
 *   - getViewer(token)     — bearer auth; builds a fresh ApolloClient per
 *                            call so the Authorization header is scoped to
 *                            just this query (the singleton stays anonymous
 *                            for search()).
 *
 * v0.4 / later:
 *   - getMediaListCollection(token, userId)
 *   - saveEntry(token, ...) via SaveMediaListEntry mutation
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
     * rather than crash. Detailed errors are logged but not surfaced.
     */
    suspend fun search(query: String): List<AnimeEntry> {
        if (query.isBlank()) return emptyList()
        val response = try {
            apollo.query(SearchAnimeQuery(search = query)).execute()
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "search failed", e)
            return emptyList()
        }

        // Apollo Kotlin 4.x codegen surfaces the bottom-level `media(...) { ...AnimeCardFields }`
        // selection as a `SearchAnimeQuery.Media` whose only direct property is the
        // fragment-spread wrapper `animeCardFields: AnimeCardFields?` — fragment
        // fields (`id`, `title`, `coverImage`, ...) are NOT inlined onto Media. Pull
        // the fragment out first, then map onto AnimeEntry. `mapNotNull` drops any
        // entries where the fragment is somehow absent.
        val now = System.currentTimeMillis()
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

    /**
     * Fetch the authenticated viewer's profile. Returns null on any error
     * (bad / expired token, network drop, ...) so the caller can decide
     * whether to retain placeholder userId=0 / userName=null or sign the
     * user out.
     *
     * Apollo's `addHttpHeader("Authorization", "Bearer $token")` attaches
     * the bearer to this single client; we dispose it after the call so
     * subsequent anonymous `search()` calls don't carry stale tokens.
     */
    suspend fun getViewer(token: String): AnimeViewer? {
        if (token.isBlank()) return null
        val authClient = ApolloClient.Builder()
            .serverUrl("https://graphql.anilist.co")
            .addHttpHeader("Authorization", "Bearer $token")
            .build()
        return try {
            val response = authClient.query(GetViewerQuery()).execute()
            // AniList's root Query field is `Viewer` (capital). Apollo Kotlin 4.x
            // operationBased codegen preserves the GraphQL field name verbatim,
            // so the generated property is `Viewer`, not `viewer`.
            val v = response.data?.Viewer ?: return null
            AnimeViewer(id = v.id, name = v.name)
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getViewer failed", e)
            null
        } finally {
            authClient.close()
        }
    }
}

/*
 * Lightweight view of the AniList viewer that we cache in TokenStore. Only
 * id + name are surfaced in the UI today; the AniList `Viewer` query returns
 * more (avatar, statistics) — pull those in v0.4 once they're needed.
 */
data class AnimeViewer(val id: Int, val name: String?)
