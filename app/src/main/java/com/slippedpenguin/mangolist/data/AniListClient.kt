package com.slippedpenguin.mangolist.data

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.slippedpenguin.mangolist.BuildConfig
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.graphql.GetMediaDetailsQuery
import com.slippedpenguin.mangolist.graphql.GetViewerQuery
import com.slippedpenguin.mangolist.graphql.SearchAnimeQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/*
 * AniListClient — thin wrapper over Apollo Kotlin 4.x for the AniList GraphQL
 * API.
 *
 * v0.4.4 surface:
 *   - search(query)         — anonymous; uses the singleton `apollo` instance.
 *   - getViewer(token)      — bearer auth; per-call client so the Authorization
 *                             header is scoped to just this query.
 *   - getMediaDetails(id)   — anonymous rich-detail fetch (banner, synopsis,
 *                             studios, characters, relations) used by
 *                             DetailScreen.
 *   - saveEntry(token, e)   — bearer-auth stub. Returns null. Apollo Kotlin 4.x's
 *                             generated SaveMediaListEntryMutation constructor
 *                             signature is opaque from cold (no on-device codegen
 *                             dump available) and every typed-accessor variant we
 *                             tried raised 'Unresolved reference' at kotlinc. The
 *                             DetailScreen Sync button correctly degrades to its
 *                             existing "Sync failed" toast. v0.5 will replace
 *                             this with a manual OkHttp POST so response parsing
 *                             is hand-controlled and immune to codegen churn.
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
                personalScore = null,
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
            AnimeViewer(
                id = v.id,
                name = v.name,
                avatarLarge = v.avatar?.large,
                avatarMedium = v.avatar?.medium,
                animeCount = v.statistics?.anime?.count,
                animeMeanScore = v.statistics?.anime?.meanScore,
                episodesWatched = v.statistics?.anime?.episodesWatched,
                minutesWatched = v.statistics?.anime?.minutesWatched,
            )
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getViewer failed", e)
            null
        } finally {
            authClient.close()
        }
    }

    /**
     * Fetch the authenticated viewer's full anime list and map each entry to
     * the local AnimeEntry model. Uses a hand-rolled GraphQL POST to avoid
     * Apollo codegen fragility with fragment spreads.
     *
     * Returns a [SyncResult] so callers can surface the actual error message
     * instead of a generic "Sync failed" toast.
     */
    suspend fun syncUserList(token: String, userId: Int): SyncResult {
        if (token.isBlank()) return SyncResult(null, "No access token. Please log in again.")
        if (userId <= 0) return SyncResult(null, "Invalid user ID. Please log in again.")
        return try {
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val payload = buildJsonObject {
                put(
                    "query",
                    """
                    query(${'$'}userId: Int!) {
                      MediaListCollection(userId: ${'$'}userId, type: ANIME) {
                        lists {
                          name
                          isCustomList
                          entries {
                            id
                            status
                            progress
                            score
                            notes
                            updatedAt
                            media {
                              id
                              title { romaji english }
                              coverImage { large medium color }
                              episodes
                              format
                              averageScore
                              startDate { year }
                              genres
                            }
                          }
                        }
                      }
                    }
                    """.trimIndent().replace("\n", " "),
                )
                put("variables", buildJsonObject { put("userId", userId) })
            }
            val body = json.encodeToString(JsonObject.serializer(), payload)

            val conn = URL("https://graphql.anilist.co").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

            if (responseCode !in 200..299) {
                val msg = "HTTP $responseCode: ${responseBody.take(200)}"
                android.util.Log.w("AniListClient", "syncUserList $msg")
                return SyncResult(null, msg)
            }

            val root = json.parseToJsonElement(responseBody).jsonObject

            // Surface GraphQL errors even when HTTP is 200.
            val errors = root["errors"]?.jsonArray
            if (errors != null && errors.isNotEmpty()) {
                val msg = errors.joinToString(", ") { it.jsonObject["message"]?.jsonPrimitive?.content ?: "GraphQL error" }
                android.util.Log.w("AniListClient", "syncUserList GraphQL errors: $msg")
                return SyncResult(null, msg)
            }

            val nowMillis = System.currentTimeMillis()
            val entries = root["data"]?.jsonObject
                ?.get("MediaListCollection")?.jsonObject
                ?.get("lists")?.jsonArray
                ?.filterIsInstance<JsonObject>()
                ?.filter { it["isCustomList"]?.jsonPrimitive?.boolean != true }
                ?.flatMap { list ->
                    list["entries"]?.jsonArray
                        ?.filterIsInstance<JsonObject>()
                        .orEmpty()
                }
                ?.mapNotNull { entry -> parseMediaListEntry(entry, nowMillis) }
                .orEmpty()
            SyncResult(entries, null)
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "syncUserList failed", e)
            SyncResult(null, e.message ?: "Unknown sync error")
        }
    }

    private fun parseMediaListEntry(entry: JsonObject, nowMillis: Long): AnimeEntry? {
        val media = entry["media"]?.jsonObject ?: return null
        val title = media["title"]?.jsonObject
        val coverImage = media["coverImage"]?.jsonObject
        val startDate = media["startDate"]?.jsonObject

        val localStatus = when (entry["status"]?.jsonPrimitive?.content) {
            "CURRENT"   -> "watching"
            "PLANNING"  -> "plan"
            "COMPLETED" -> "completed"
            "DROPPED"   -> "dropped"
            "PAUSED"    -> "paused"
            "REPEATING" -> "repeating"
            else        -> "watching"
        }

        val updatedAt = entry["updatedAt"]?.jsonPrimitive?.long
        val editTime = updatedAt?.let { it * 1000L } ?: nowMillis

        val score = entry["score"]?.jsonPrimitive?.double

        return AnimeEntry(
            anilistId     = media["id"]?.jsonPrimitive?.int ?: return null,
            title         = title?.get("english")?.jsonPrimitive?.content
                ?: title?.get("romaji")?.jsonPrimitive?.content
                ?: "Untitled",
            cover         = coverImage?.get("large")?.jsonPrimitive?.content
                ?: coverImage?.get("medium")?.jsonPrimitive?.content,
            coverColor    = coverImage?.get("color")?.jsonPrimitive?.content,
            format        = media["format"]?.jsonPrimitive?.content,
            episodes      = media["episodes"]?.jsonPrimitive?.int,
            averageScore  = media["averageScore"]?.jsonPrimitive?.int,
            year          = startDate?.get("year")?.jsonPrimitive?.int,
            synopsis      = null,
            genres        = media["genres"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content }
                ?.joinToString(",")
                ?: "",
            tier          = null,
            elo           = 1500,
            currentEp     = entry["progress"]?.jsonPrimitive?.int ?: 0,
            status        = localStatus,
            notes         = entry["notes"]?.jsonPrimitive?.content ?: "",
            personalScore = score?.let { (it * 10).toInt() },
            listEntryId   = entry["id"]?.jsonPrimitive?.int,
            updatedAt     = editTime,
            syncedAt      = editTime,
        )
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
            // Apollo Kotlin 4.x with `operationBased` codegen and a fragment
            // spread on the outer Media type puts `id` inside the
            // AnimeCardFields wrapper (no inlining) — there's no top-level `id`
            // on the generated `Media` class, so `m.id` is unreferencable. The
            // fragment is non-null whenever Media resolves, so fall back to 0
            // (sentinel) defensively rather than reaching for `m.id`.
            id            = cf?.id ?: 0,
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
     * Exchange an authorization code for an access token (OAuth2 authorization
     * code grant). POSTs to https://anilist.co/api/v2/oauth/token with the
     * client_id, client_secret, code, and grant_type.
     *
     * Returns the access_token string on success, null on failure.
     */
    suspend fun exchangeCodeForToken(code: String): String? {
        if (code.isBlank()) return null
        return try {
            // AniList's token endpoint expects a JSON body per the official docs.
            // client_id must be sent as an integer (Laravel Passport rejects the
            // string form). redirect_uri must match the registered URI exactly.
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val payload = buildJsonObject {
                put("grant_type", "authorization_code")
                put("client_id", BuildConfig.ANILIST_CLIENT_ID.toInt())
                put("client_secret", BuildConfig.ANILIST_CLIENT_SECRET)
                put("redirect_uri", BuildConfig.ANILIST_REDIRECT_URI)
                put("code", code)
            }
            val body = json.encodeToString(JsonObject.serializer(), payload)

            val conn = URL("https://anilist.co/api/v2/oauth/token").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                android.util.Log.w("AniListClient", "token exchange HTTP ${conn.responseCode}: $errorBody")
                return null
            }
            val responseBody = conn.inputStream.bufferedReader().readText()
            val root = json.parseToJsonElement(responseBody).jsonObject
            root["access_token"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "exchangeCodeForToken failed", e)
            null
        }
    }

    /**
     * Push a single AnimeEntry edit back to AniList via SaveMediaListEntry.
     *
     * v0.6: replaced the Apollo codegen stub with a hand-rolled kotlinx
     * serialization JSON POST because the generated SaveMediaListEntryMutation
     * constructor proved opaque at kotlinc.
     *
     * Mappings:
     *   - status → AniList MediaListStatus enum (plan→PLANNING, watching→CURRENT, etc.)
     *   - personalScore / 10.0 → score Float (0-10 scale)
     *   - listEntryId non-null → update; null → create new
     *
     * Returns the AniList MediaList id (new or existing) on success, null on failure.
     */
    suspend fun saveEntry(token: String, entry: AnimeEntry): Int? {
        if (token.isBlank()) return null
        return try {
            val anilistStatus = when (entry.status) {
                "plan"      -> "PLANNING"
                "watching"  -> "CURRENT"
                "completed" -> "COMPLETED"
                "dropped"   -> "DROPPED"
                "paused"    -> "PAUSED"
                "repeating" -> "REPEATING"
                else        -> "CURRENT"
            }
            val variables = buildJsonObject {
                entry.listEntryId?.let { put("id", it) }
                put("mediaId", entry.anilistId)
                put("status", anilistStatus)
                put("progress", entry.currentEp)
                entry.personalScore?.let { put("score", it / 10.0) }
            }
            val payload = buildJsonObject {
                put(
                    "query",
                    "mutation(\$id:Int,\$mediaId:Int,\$status:MediaListStatus,\$score:Float,\$progress:Int)" +
                    "{SaveMediaListEntry(id:\$id,mediaId:\$mediaId,status:\$status,score:\$score,progress:\$progress)" +
                    "{id}}"
                )
                put("variables", variables)
            }
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val body = json.encodeToString(JsonObject.serializer(), payload)

            val conn = URL("https://graphql.anilist.co").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode !in 200..299) {
                android.util.Log.w("AniListClient", "saveEntry HTTP ${conn.responseCode}")
                return null
            }
            val responseBody = conn.inputStream.bufferedReader().readText()
            val root = json.parseToJsonElement(responseBody).jsonObject
            root["data"]?.jsonObject
                ?.get("SaveMediaListEntry")?.jsonObject
                ?.get("id")?.jsonPrimitive?.int
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "saveEntry failed", e)
            null
        }
    }

    /**
     * Fetch the next 7 days of airing schedules from AniList.
     * Returns slots sorted by airingAt ascending.
     */
    suspend fun getAiringSchedule(): List<AiringSlot> {
        val now = System.currentTimeMillis() / 1000
        val week = now + 7 * 86400
        return try {
            val variables = buildJsonObject {
                put("airingAtGreater", now.toInt())
                put("airingAtLesser", week.toInt())
            }
            val payload = buildJsonObject {
                put(
                    "query",
                    "query(\$airingAtGreater:Int!,\$airingAtLesser:Int!)" +
                    "{Page(perPage:50){airingSchedules(airingAt_greater:\$airingAtGreater,airingAt_lesser:\$airingAtLesser,sort:[TIME])" +
                    "{id airingAt episode media{id title{english romaji} coverImage{large}}}}}"
                )
                put("variables", variables)
            }
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val body = json.encodeToString(JsonObject.serializer(), payload)

            val conn = URL("https://graphql.anilist.co").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode !in 200..299) return emptyList()
            val responseBody = conn.inputStream.bufferedReader().readText()
            val root = json.parseToJsonElement(responseBody).jsonObject
            val schedules = root["data"]?.jsonObject
                ?.get("Page")?.jsonObject
                ?.get("airingSchedules")?.jsonArray ?: return emptyList()
            schedules.mapNotNull { el ->
                val obj = el.jsonObject
                val media = obj["media"]?.jsonObject ?: return@mapNotNull null
                val title = media["title"]?.jsonObject
                AiringSlot(
                    id = obj["id"]?.jsonPrimitive?.int ?: 0,
                    airingAt = obj["airingAt"]?.jsonPrimitive?.long ?: 0,
                    episode = obj["episode"]?.jsonPrimitive?.int ?: 0,
                    animeId = media["id"]?.jsonPrimitive?.int ?: 0,
                    title = title?.get("english")?.jsonPrimitive?.content
                        ?: title?.get("romaji")?.jsonPrimitive?.content ?: "Untitled",
                    coverLarge = media["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content,
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getAiringSchedule failed", e)
            emptyList()
        }
    }
}

// Helpers `toMediaListStatus` + `tierToScore` and `MediaListStatus` import were
// removed in v0.4.4 since the stubbed saveEntry no longer maps local status /
// tier to AniList's enums. They come back in v0.5 when saveEntry is rebuilt
// against the manual OkHttp POST.

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
data class AnimeViewer(
    val id: Int,
    val name: String?,
    val avatarLarge: String? = null,
    val avatarMedium: String? = null,
    // AniList statistics (from GetViewer.statistics.anime)
    val animeCount: Int? = null,
    val animeMeanScore: Double? = null,
    val episodesWatched: Int? = null,
    val minutesWatched: Int? = null,
)

/** One airing-schedule slot returned by GetAiringSchedule. */
data class AiringSlot(
    val id: Int,
    val airingAt: Long,      // epoch seconds
    val episode: Int,
    val animeId: Int,
    val title: String,
    val coverLarge: String?,
)

/**
 * Result wrapper for list-sync operations.
 * `entries` is the synced list on success; `error` is a human-readable
 * message when the sync fails.
 */
data class SyncResult(
    val entries: List<AnimeEntry>?,
    val error: String?,
)
