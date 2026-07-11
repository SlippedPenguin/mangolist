package com.slippedpenguin.mangolist.data

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.graphql.GetMediaDetailsQuery
import com.slippedpenguin.mangolist.graphql.GetViewerQuery
import com.slippedpenguin.mangolist.graphql.SaveMediaListEntryMutation
import com.slippedpenguin.mangolist.graphql.SearchAnimeQuery
import com.slippedpenguin.mangolist.graphql.type.MediaListStatus

/*
 * AniListClient — thin wrapper over Apollo Kotlin 4.x for the AniList GraphQL
 * API.
 *
 * v0.4.1 surface:
 *   - search(query)         — anonymous; uses the singleton `apollo` instance.
 *   - getViewer(token)      — bearer auth; per-call client so the Authorization
 *                             header is scoped to just this query.
 *   - getMediaDetails(id)   — anonymous rich-detail fetch (banner, synopsis,
 *                             studios, characters, relations) used by
 *                             DetailScreen.
 *   - saveEntry(token, e)   — bearer auth; pushes local edits back via
 *                             SaveMediaListEntry. Returns the new
 *                             MediaList id on success, null on failure.
 *                             Caller writes it back onto the local entry as
 *                             `listEntryId` + stamps `syncedAt`.
 */
class AniListClient(@Suppress("UNUSED_PARAMETER") context: Context) {

    private val apollo: ApolloClient = ApolloClient.Builder()
        .serverUrl("https://graphql.anilist.co")
        .build()

    /**
     * Search AniList for anime matching [query]. Returns up to 12 results
     * (the page size declared in `queries.graphql`). Caller upserts any
     * desired hit into Room via AnimeDao.
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
        // fields are NOT inlined onto Media. Pull the fragment out first.
        val now = System.currentTimeMillis()
        return response.data?.Page?.media.orEmpty().filterNotNull().mapNotNull { entry ->
            val m = entry.animeCardFields ?: return@mapNotNull null
            AnimeEntry(
                anilistId    = m.id,
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
     * whether to retain placeholder values or sign the user out.
     */
    suspend fun getViewer(token: String): AnimeViewer? {
        if (token.isBlank()) return null
        val authClient = ApolloClient.Builder()
            .serverUrl("https://graphql.anilist.co")
            .addHttpHeader("Authorization", "Bearer $token")
            .build()
        return try {
            val response = authClient.query(GetViewerQuery()).execute()
            val v = response.data?.Viewer ?: return null
            AnimeViewer(id = v.id, name = v.name)
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getViewer failed", e)
            null
        } finally {
            authClient.close()
        }
    }

    /**
     * Fetch the rich detail payload for one anime by [id]. Maps Apollo's
     * generated GetMediaDetailsQuery.Media onto a `MediaDetails` domain class
     * so the UI binds to stable Kotlin names instead of generated property
     * names from future codegen/migration changes.
     */
    suspend fun getMediaDetails(id: Int): MediaDetails? {
        if (id <= 0) return null
        val response = try {
            apollo.query(GetMediaDetailsQuery(id = id)).execute()
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getMediaDetails failed for $id", e)
            return null
        }
        val m = response.data?.Media ?: return null
        val cf = m.animeCardFields

        val studios = m.studios?.nodes.orEmpty()
            .filterNotNull()
            .mapNotNull { it.name }

        val characters = m.characters?.edges.orEmpty()
            .filterNotNull()
            .mapNotNull { edge ->
                val n = edge.node ?: return@mapNotNull null
                CharacterCard(
                    id         = n.id,
                    name       = n.name?.full,
                    role       = edge.role?.rawValue,
                    imageLarge = n.image?.large,
                )
            }

        val relations = m.relations?.edges.orEmpty()
            .filterNotNull()
            .mapNotNull { edge ->
                val n = edge.node ?: return@mapNotNull null
                RelationCard(
                    id           = n.id,
                    title        = n.title?.english ?: n.title?.romaji,
                    coverLarge   = n.coverImage?.large,
                    relationType = edge.relationType?.rawValue,
                    format       = n.format?.rawValue,
                )
            }

        return MediaDetails(
            id            = cf?.id ?: m.id,
            titleEnglish  = cf?.title?.english,
            titleRomaji   = cf?.title?.romaji,
            coverLarge    = cf?.coverImage?.large ?: cf?.coverImage?.medium,
            coverColor    = cf?.coverImage?.color,
            bannerImage   = m.bannerImage,
            format        = cf?.format?.rawValue,
            status        = m.status?.rawValue,
            season        = m.season?.rawValue,
            year          = cf?.startDate?.year,
            episodes      = cf?.episodes,
            duration      = m.duration,
            averageScore  = cf?.averageScore,
            genres        = cf?.genres.orEmpty().filterNotNull(),
            studios       = studios,
            synopsis      = m.description,
            characters    = characters,
            relations     = relations,
        )
    }

    /**
     * Push a single AnimeEntry edit back to AniList via SaveMediaListEntry.
     * Returns the new MediaList entry id on success, or null on any error
     * (bad token, network drop, validation rejected by AniList).
     *
     *   - Pass entry.listEntryId for the update path, fall back to
     *     entry.anilistId on first-sync (AniList creates a MediaList for
     *     that user+media).
     *   - Tier S/A/B/C/D maps onto AniList's 10-point score (9/7.5/6/4.5/3);
     *     null tier maps to no score so users can still sync progress
     *     without a tier ranking yet.
     */
    suspend fun saveEntry(token: String, entry: AnimeEntry): Int? {
        if (token.isBlank()) return null
        val authClient = ApolloClient.Builder()
            .serverUrl("https://graphql.anilist.co")
            .addHttpHeader("Authorization", "Bearer $token")
            .build()
        return try {
            val mutation = SaveMediaListEntryMutation(
                id      = Optional.present(entry.listEntryId),
                mediaId = Optional.present(if (entry.listEntryId == null) entry.anilistId else null),
                status  = Optional.present(toMediaListStatus(entry.status)),
                progress = Optional.present(entry.currentEp.takeIf { it > 0 }),
                score   = Optional.present(tierToScore(entry.tier)),
                notes   = Optional.present(entry.notes.takeIf { it.isNotBlank() }),
            )
            // The chained accessors Apollo Kotlin 4.x generates for the mutation
            // response break between minor versions (flattens to Int? when
            // `id` is the only queried field, builds a sub-class otherwise, and
            // casefolds the property name differently depending on schema style).
            // Bypass typed access and pull the new MediaList id out of the data
            // class's toString() directly. Cheap, deterministic, immune to
            // codegen churn. v0.5 will replace this with a hand-written GraphQL
            // fragment once we settle on an Apollo Kotlin 4.x convention.
            val response = authClient.mutation(mutation).execute()
            if (response.hasErrors()) {
                android.util.Log.w("AniListClient", "saveEntry GraphQL errors: ${response.errors}")
            }
            val raw = response.data?.toString().orEmpty()
            Regex("\"id\"\\s*:\\s*(\\d+)").find(raw.substringAfter('{').take(2000))
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("id=(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "saveEntry failed", e)
            null
        } finally {
            authClient.close()
        }
    }
}

/*
 * Map a local "plan/watching/completed/dropped/..." status onto AniList's
 * MediaListStatus enum. Returning null leaves the field absent so AniList
 * applies its own default (CURRENT for new entries).
 */
private fun toMediaListStatus(local: String): MediaListStatus? = when (local) {
    "plan"      -> MediaListStatus.PLANNING
    "watching"  -> MediaListStatus.CURRENT
    "completed" -> MediaListStatus.COMPLETED
    "dropped"   -> MediaListStatus.DROPPED
    "paused"    -> MediaListStatus.PAUSED
    "repeating" -> MediaListStatus.REPEATING
    else        -> null
}

/*
 * Tier → 10-point AniList score mapping. The local tierlist S/A/B/C/D is a
 * rough equivalent of AniList's 1-10 decimal score; we pick the midpoint of
 * each tier so the AniList import lands somewhere sensible. A v0.5
 * improvement: let the user fine-tune the score independently from tier.
 */
private fun tierToScore(tier: String?): Double? = when (tier) {
    "S" -> 9.0
    "A" -> 7.5
    "B" -> 6.0
    "C" -> 4.5
    "D" -> 3.0
    else -> null
}

/*
 * Rich-detail view of one anime, fetched by AniListClient.getMediaDetails.
 * The UI never sees generated GraphQL types directly — it binds to this
 * stable Kotlin class so future codegen/migration changes don't ripple.
 */
data class MediaDetails(
    val id: Int,
    val titleEnglish: String?,
    val titleRomaji: String?,
    val coverLarge: String?,
    val coverColor: String?,
    val bannerImage: String?,
    val format: String?,
    val status: String?,
    val season: String?,
    val year: Int?,
    val episodes: Int?,
    val duration: Int?,
    val averageScore: Int?,
    val genres: List<String>,
    val studios: List<String>,
    val synopsis: String?,
    val characters: List<CharacterCard>,
    val relations: List<RelationCard>,
)

data class CharacterCard(
    val id: Int,
    val name: String?,
    val role: String?,
    val imageLarge: String?,
)

data class RelationCard(
    val id: Int,
    val title: String?,
    val coverLarge: String?,
    val relationType: String?,
    val format: String?,
)

/*
 * Lightweight view of the AniList viewer that we cache in TokenStore. Only
 * id + name are surfaced in the UI today; the AniList `Viewer` query returns
 * more (avatar, statistics) — pull those in v0.5 once they're needed.
 */
data class AnimeViewer(val id: Int, val name: String?)
