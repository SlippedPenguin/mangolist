package com.slippedpenguin.mangolist.data

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.slippedpenguin.mangolist.BuildConfig
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.graphql.GetMediaDetailsQuery
import com.slippedpenguin.mangolist.graphql.GetPopularAnimeQuery
import com.slippedpenguin.mangolist.graphql.GetTopRatedAnimeQuery
import com.slippedpenguin.mangolist.graphql.GetTrendingAnimeQuery
import com.slippedpenguin.mangolist.graphql.GetUpcomingAnimeQuery
import com.slippedpenguin.mangolist.graphql.GetViewerQuery
import com.slippedpenguin.mangolist.graphql.SearchAnimeQuery
import com.slippedpenguin.mangolist.util.NetworkObserver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
 *   - saveEntry(token, e)   — bearer-auth POST to SaveMediaListEntry that
 *                             pushes status, progress, score, and notes. It
 *                             returns a SaveResult with the server id, timestamp
 *                             (seconds), and stored notes.
 */
class AniListClient(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val networkObserver: NetworkObserver,
) {

    private val apollo: ApolloClient = ApolloClient.Builder()
        .serverUrl("https://graphql.anilist.co")
        .build()

    /**
     * Opens a POST [HttpURLConnection] pre-configured for AniList. The hand-rolled
     * sync paths went silent on v0.8.2 because the default `Dalvik/2.1.0` User-Agent
     * tripped Cloudflare's bot challenge (HTTP 403) and missing timeouts left the
     * connection hanging in captive-portal networks. This helper bakes in:
     *
     *   - explicit connectTimeout / readTimeout so a stalled CDN doesn't pin the
     *     ioScope / SyncWorker forever;
     *   - a custom User-Agent (`MangoList/<version>`), which Cloudflare accepts
     *     while the framework-default Dalvik UA is blocked;
     *   - `Accept: application/json` (was missing on saveEntry / getAiringSchedule);
     *   - `Connection: close` so we never inherit a half-dead pooled connection
     *     from a previous request (the classic EOFException on Android 8-10).
     *
     * Callers still `.use{}` the input/error streams and parse the body; the
     * `.use{}` closure releases the socket on exit, which is sufficient for
     * AniList responses on modern Android.
     */
    private fun openPost(url: String, bearerToken: String? = null): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "MangoList/${BuildConfig.VERSION_NAME}")
        conn.setRequestProperty("Connection", "close")
        if (!bearerToken.isNullOrBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        conn.doOutput = true
        return conn
    }

    /**
     * Fast-fail helper. Runs [block] when online, otherwise returns the
     * supplied [default] sentinel. This avoids long DNS timeouts and gives
     * callers a predictable offline result.
     */
    private inline fun <T> withNetwork(default: T, block: () -> T): T {
        return if (networkObserver.isCurrentlyOnline()) block() else default
    }

    /**
     * Search AniList for anime matching [query]. Returns up to 12 results
     * (the page size declared in `queries.graphql`). Caller upserts any
     * desired hit into Room via AnimeDao.
     */
    suspend fun search(query: String): List<AnimeEntry> {
        if (query.isBlank()) return emptyList()
        return withNetwork(emptyList()) {
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
    }

    /**
     * Build an AnimeEntry from an Apollo `AnimeCardFields` fragment for the
     * Explore (Discover) tab. Fragment type varies per generated query, but
     * the field shape is identical across `...AnimeCardFields` callers, so we
     * pass the seven primitive fields individually and let the helper do the
     * field-to-AnimeEntry mapping. The Explore surfaces never get persisted
     * to Room — favourites and additions happen via the search bar — so the
     * defaults are placeholders (`status = "plan"`, empty `notes`, no
     * `syncedAt`) and never reach the Watchlist unless the user taps a card
     * which routes to DetailScreen and triggers a real Room write there.
     */
    private fun buildDiscoverEntry(
        anilistId: Int,
        english: String?,
        romaji: String?,
        cover: String?,
        coverColor: String?,
        format: String?,
        episodes: Int?,
        averageScore: Int?,
        year: Int?,
        now: Long,
    ): AnimeEntry = AnimeEntry(
        anilistId     = anilistId,
        title         = english ?: romaji ?: "Untitled",
        cover         = cover,
        coverColor    = coverColor,
        format        = format,
        episodes      = episodes,
        averageScore  = averageScore,
        year          = year,
        synopsis      = null,
        genres        = "",
        tier          = null,
        elo           = 1500,
        currentEp     = 0,
        status        = "plan",
        notes         = "",
        personalScore = null,
        listEntryId   = null,
        updatedAt     = now,
        syncedAt      = null,
    )

    /**
     * Fetch ~25 anime ordered by AniList's POPULARITY_DESC. Used as the
     * top horizontal carousel on ExploreScreen. Returns an empty list on
     * any error / offline so the screen renders a graceful placeholder.
     */
    suspend fun getPopular(): List<AnimeEntry> = withNetwork(emptyList()) {
        try {
            val response = apollo.query(GetPopularAnimeQuery()).execute()
            val now = System.currentTimeMillis()
            response.data?.Page?.media.orEmpty().filterNotNull().mapNotNull { entry ->
                val m = entry.animeCardFields ?: return@mapNotNull null
                buildDiscoverEntry(
                    anilistId    = m.id,
                    english      = m.title?.english,
                    romaji       = m.title?.romaji,
                    cover        = m.coverImage?.large ?: m.coverImage?.medium,
                    coverColor   = m.coverImage?.color,
                    format       = m.format?.rawValue,
                    episodes     = m.episodes,
                    averageScore = m.averageScore,
                    year         = m.startDate?.year,
                    now          = now,
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getPopular failed", e)
            emptyList()
        }
    }

    /** TRENDING_DESC carousel. Same shape as `getPopular`. */
    suspend fun getTrending(): List<AnimeEntry> = withNetwork(emptyList()) {
        try {
            val response = apollo.query(GetTrendingAnimeQuery()).execute()
            val now = System.currentTimeMillis()
            response.data?.Page?.media.orEmpty().filterNotNull().mapNotNull { entry ->
                val m = entry.animeCardFields ?: return@mapNotNull null
                buildDiscoverEntry(
                    anilistId    = m.id,
                    english      = m.title?.english,
                    romaji       = m.title?.romaji,
                    cover        = m.coverImage?.large ?: m.coverImage?.medium,
                    coverColor   = m.coverImage?.color,
                    format       = m.format?.rawValue,
                    episodes     = m.episodes,
                    averageScore = m.averageScore,
                    year         = m.startDate?.year,
                    now          = now,
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getTrending failed", e)
            emptyList()
        }
    }

    /**
     * NOT_YET_RELEASED + START_DATE_DESC — the "Coming soon" carousel.
     * Returns episodes = null for most rows (AniList hasn't reported a final
     * count yet); the poster card tolerates this since it never renders
     * progress.
     */
    suspend fun getUpcoming(): List<AnimeEntry> = withNetwork(emptyList()) {
        try {
            val response = apollo.query(GetUpcomingAnimeQuery()).execute()
            val now = System.currentTimeMillis()
            response.data?.Page?.media.orEmpty().filterNotNull().mapNotNull { entry ->
                val m = entry.animeCardFields ?: return@mapNotNull null
                buildDiscoverEntry(
                    anilistId    = m.id,
                    english      = m.title?.english,
                    romaji       = m.title?.romaji,
                    cover        = m.coverImage?.large ?: m.coverImage?.medium,
                    coverColor   = m.coverImage?.color,
                    format       = m.format?.rawValue,
                    episodes     = m.episodes,
                    averageScore = m.averageScore,
                    year         = m.startDate?.year,
                    now          = now,
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getUpcoming failed", e)
            emptyList()
        }
    }

    /** SCORE_DESC carousel — AniList's all-time top rated anime. */
    suspend fun getTopRated(): List<AnimeEntry> = withNetwork(emptyList()) {
        try {
            val response = apollo.query(GetTopRatedAnimeQuery()).execute()
            val now = System.currentTimeMillis()
            response.data?.Page?.media.orEmpty().filterNotNull().mapNotNull { entry ->
                val m = entry.animeCardFields ?: return@mapNotNull null
                buildDiscoverEntry(
                    anilistId    = m.id,
                    english      = m.title?.english,
                    romaji       = m.title?.romaji,
                    cover        = m.coverImage?.large ?: m.coverImage?.medium,
                    coverColor   = m.coverImage?.color,
                    format       = m.format?.rawValue,
                    episodes     = m.episodes,
                    averageScore = m.averageScore,
                    year         = m.startDate?.year,
                    now          = now,
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getTopRated failed", e)
            emptyList()
        }
    }

    /**
     * Fetch the authenticated viewer's profile. Returns null on any error
     * (bad / expired token, network drop, ...) so the caller can decide
     * whether to retain placeholder values or sign the user out.
     */
    suspend fun getViewer(token: String): AnimeViewer? {
        if (token.isBlank()) return null
        return withNetwork(null) {
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
        return withNetwork(SyncResult(null, "No internet connection.")) {
        try {
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
                              isFavourite
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

            val conn = openPost("https://graphql.anilist.co", token)
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

            val root = (json.parseToJsonElement(responseBody) as? JsonObject)
                ?: run {
                    val msg = "Response is not a JSON object: ${responseBody.take(200)}"
                    android.util.Log.w("AniListClient", "syncUserList $msg")
                    return SyncResult(null, msg)
                }

            // Surface GraphQL errors even when HTTP is 200.
            val errors = root["errors"] as? kotlinx.serialization.json.JsonArray
            if (errors != null && errors.isNotEmpty()) {
                val msg = errors.joinToString(", ") { ((it as? JsonObject)?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "GraphQL error" }
                android.util.Log.w("AniListClient", "syncUserList GraphQL errors: $msg")
                return SyncResult(null, msg)
            }

            val nowMillis = System.currentTimeMillis()
            val collection = (root["data"] as? JsonObject)
                ?.get("MediaListCollection")
            // MediaListCollection can be null if the user has no lists.
            if (collection == null || collection is kotlinx.serialization.json.JsonNull) {
                return SyncResult(emptyList(), null)
            }
            val collObj = (collection as? JsonObject)
                ?: run {
                    val msg = "MediaListCollection is not an object: ${collection.toString().take(200)}"
                    android.util.Log.w("AniListClient", "syncUserList $msg")
                    return SyncResult(null, msg)
                }
            val entries = (collObj["lists"] as? kotlinx.serialization.json.JsonArray)
                ?.filterIsInstance<JsonObject>()
                ?.filter { (it["isCustomList"] as? kotlinx.serialization.json.JsonPrimitive)?.boolean != true }
                ?.flatMap { list ->
                    (list["entries"] as? kotlinx.serialization.json.JsonArray)
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
    }

    private fun parseMediaListEntry(entry: JsonObject, nowMillis: Long): AnimeEntry? {
        return try {
            parseMediaListEntrySafe(entry, nowMillis)
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "Skipping one list entry due to parse error", e)
            null
        }
    }

    private fun parseMediaListEntrySafe(entry: JsonObject, nowMillis: Long): AnimeEntry? {
        val media = (entry["media"] as? JsonObject) ?: return null
        val title = media["title"] as? JsonObject
        val coverImage = media["coverImage"] as? JsonObject
        val startDate = media["startDate"] as? JsonObject

        val localStatus = when ((entry["status"] as? kotlinx.serialization.json.JsonPrimitive)?.content) {
            "CURRENT"   -> "watching"
            "PLANNING"  -> "plan"
            "COMPLETED" -> "completed"
            "DROPPED"   -> "dropped"
            "PAUSED"    -> "paused"
            "REPEATING" -> "repeating"
            else        -> "watching"
        }

        val updatedAt = (entry["updatedAt"] as? kotlinx.serialization.json.JsonPrimitive)?.long
        val editTime = updatedAt?.let { it * 1000L } ?: nowMillis

        val score = (entry["score"] as? kotlinx.serialization.json.JsonPrimitive)?.double

        return AnimeEntry(
            anilistId     = (media["id"] as? kotlinx.serialization.json.JsonPrimitive)?.int ?: return null,
            title         = (title?.get("english") as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: (title?.get("romaji") as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: "Untitled",
            cover         = (coverImage?.get("large") as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: (coverImage?.get("medium") as? kotlinx.serialization.json.JsonPrimitive)?.content,
            coverColor    = (coverImage?.get("color") as? kotlinx.serialization.json.JsonPrimitive)?.content,
            format        = (media["format"] as? kotlinx.serialization.json.JsonPrimitive)?.content,
            episodes      = (media["episodes"] as? kotlinx.serialization.json.JsonPrimitive)?.int,
            averageScore  = (media["averageScore"] as? kotlinx.serialization.json.JsonPrimitive)?.int,
            year          = (startDate?.get("year") as? kotlinx.serialization.json.JsonPrimitive)?.int,
            synopsis      = null,
            genres        = (media["genres"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?.joinToString(",")
                ?: "",
            tier          = null,
            elo           = 1500,
            currentEp     = (entry["progress"] as? kotlinx.serialization.json.JsonPrimitive)?.int ?: 0,
            status        = localStatus,
            notes         = (entry["notes"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "",
            personalScore = score?.let { (it * 10).toInt() },
            favourite     = (media["isFavourite"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: false,
            listEntryId   = (entry["id"] as? kotlinx.serialization.json.JsonPrimitive)?.int,
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
        return withNetwork(null) {
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
        return withNetwork(null) {
            try {
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
    
                val conn = openPost("https://anilist.co/api/v2/oauth/token")
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
     *   - notes → String (empty string clears notes on AniList)
     *   - listEntryId non-null → update; null → create new
     *
     * Returns a [SaveResult] with the AniList MediaList id, server updatedAt
     * (seconds), and server notes on success; null on failure.
     */
    suspend fun saveEntry(token: String, entry: AnimeEntry): SaveResult? {
        if (token.isBlank()) return null
        return withNetwork(null) {
            try {
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
                    entry.personalScore?.let { put("scoreRaw", it) }
                    put("notes", entry.notes)
                }
                val payload = buildJsonObject {
                    put(
                        "query",
                    "mutation(\$id:Int,\$mediaId:Int,\$status:MediaListStatus,\$scoreRaw:Int,\$progress:Int,\$notes:String)" +
                    "{SaveMediaListEntry(id:\$id,mediaId:\$mediaId,status:\$status,scoreRaw:\$scoreRaw,progress:\$progress,notes:\$notes)" +
                    "{id updatedAt notes}}"
                    )
                    put("variables", variables)
                }
                val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                val body = json.encodeToString(JsonObject.serializer(), payload)

                val conn = openPost("https://graphql.anilist.co", token)
                conn.outputStream.use { it.write(body.toByteArray()) }

                if (conn.responseCode !in 200..299) {
                    val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    android.util.Log.w("AniListClient", "saveEntry HTTP ${conn.responseCode} (anilistId=${entry.anilistId}, status=${entry.status}, currentEp=${entry.currentEp}, personalScore=${entry.personalScore}, notes.len=${entry.notes.length}): ${errorBody.take(400)}")
                    return null
                }
                val responseBody = conn.inputStream.bufferedReader().readText()
                val root = json.parseToJsonElement(responseBody).jsonObject
                // Surface GraphQL-level errors (validation, schema mismatch) — they typically
                // come back as HTTP 200 with a `errors` array and no `data`.
                (root["errors"] as? kotlinx.serialization.json.JsonArray)?.let { errs ->
                    val msgs = errs.joinToString(", ") { e ->
                        ((e as? JsonObject)?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: e.toString()
                    }
                    android.util.Log.w("AniListClient", "saveEntry GraphQL errors (anilistId=${entry.anilistId}): $msgs")
                    return null
                }
                val saveNode = root["data"]?.jsonObject?.get("SaveMediaListEntry")?.jsonObject ?: run {
                    android.util.Log.w("AniListClient", "saveEntry response has no data.SaveMediaListEntry: ${responseBody.take(400)}")
                    return null
                }
                val id = (saveNode["id"] as? kotlinx.serialization.json.JsonPrimitive)?.int ?: return null
                val updatedAtSeconds = (saveNode["updatedAt"] as? kotlinx.serialization.json.JsonPrimitive)?.long
                val notesElement = saveNode["notes"]
                val returnedNotes = when (notesElement) {
                    is kotlinx.serialization.json.JsonNull -> null
                    is kotlinx.serialization.json.JsonPrimitive -> notesElement.content
                    else -> null
                }
                SaveResult(
                    id = id,
                    updatedAtSeconds = updatedAtSeconds,
                    notes = returnedNotes,
                )
            } catch (e: Exception) {
                android.util.Log.w("AniListClient", "saveEntry failed", e)
                null
        }
        }
    }

    /**
     * Fetch the next 7 days of airing schedules from AniList.
     * Returns slots sorted by airingAt ascending.
     */
/*
     * Toggle the viewer's favourite flag for a single anime.
     *
     * AniList's favourites are media-level (Media.isFavourite), not list-
     * entry-level, so this is intentionally separate from saveEntry.
     * saveEntry mutates a MediaList row; this toggles the user's media-
     * level favourite set on the AniList server. The same AniList
     * openPost(url, bearerToken?) helper used by saveEntry handles the
     * Cloudflare-friendly UA / timeouts / Connection: close concerns.
     *
     * The mutation's response includes the up-to-date
     * User.favourites.anime { nodes { id } } connection. We scan the
     * returned nodes and check whether our animeId is present - that
     * gives us the post-toggle state without a second round-trip. We
     * explicitly request the full page: { perPage: 500 } so power
     * users with >50 favourites don't get silently truncated out of
     * the post-toggle scan.
     *
     * Returns the new favourite state on success; null on any
     * network/parse error or when the response shape doesn't expose the
     * nodes we need. The caller (DetailScreen) is responsible for
     * writing the result back to Room and falling back to optimistic-
     * flip-on-null locally.
     */
    suspend fun toggleFavorite(token: String, animeId: Int): Boolean? {
        if (token.isBlank()) return null
        return withNetwork(null) {
            try {
                val variables = buildJsonObject {
                    put("animeId", animeId)
                }
                val payload = buildJsonObject {
                    put(
                        "query",
                        "mutation(\$animeId:Int)" +
                        "{ToggleFavourite(animeId:\$animeId)" +
                        "{anime{nodes{id}}}}"
                    )
                    put("variables", variables)
                }
                val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                val body = json.encodeToString(JsonObject.serializer(), payload)

                val conn = openPost("https://graphql.anilist.co", token)
                conn.outputStream.use { it.write(body.toByteArray()) }

                if (conn.responseCode !in 200..299) {
                    val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    android.util.Log.w(
                        "AniListClient",
                        "toggleFavorite HTTP ${conn.responseCode} (animeId=$animeId): ${errorBody.take(400)}",
                    )
                    return@withNetwork null
                }
                val responseBody = conn.inputStream.bufferedReader().readText()
                val root = json.parseToJsonElement(responseBody).jsonObject

                // GraphQL-level errors (validation, schema mismatch) - same
                // shape as saveEntry's path; surface and bail.
                (root["errors"] as? kotlinx.serialization.json.JsonArray)?.let { errs ->
                    val msgs = errs.joinToString(", ") { e ->
                        ((e as? JsonObject)?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: e.toString()
                    }
                    android.util.Log.w("AniListClient", "toggleFavorite GraphQL errors (animeId=$animeId): $msgs")
                    return@withNetwork null
                }

                // Walk: data -> ToggleFavourite -> anime -> nodes
                val data = root["data"] as? JsonObject
                val toggleNode = data?.get("ToggleFavourite") as? JsonObject
                val anime = toggleNode?.get("anime") as? JsonObject
                val nodes = anime?.get("nodes") as? JsonArray
                    ?: return@withNetwork null
                nodes.any { node ->
                    val nodeObj = node as? JsonObject ?: return@any false
                    val id = nodeObj["id"] as? JsonPrimitive ?: return@any false
                    id.int == animeId
                }
            } catch (e: Exception) {
                android.util.Log.w("AniListClient", "toggleFavorite failed", e)
                null
            }
        }
    }

    suspend fun getAiringSchedule(): List<AiringSlot> {
        val now = System.currentTimeMillis() / 1000
        val week = now + 7 * 86400
        return withNetwork(emptyList()) {
        try {
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

            val conn = openPost("https://graphql.anilist.co")
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

/*
 * Result wrapper for a successful SaveMediaListEntry mutation.
 * `updatedAtSeconds` is the server timestamp in seconds (multiply by 1000
 * for local millis). `notes` is the server-stored value, which may differ
 * from the local draft if AniList normalizes or truncates it.
 */
data class SaveResult(
    val id: Int,
    val updatedAtSeconds: Long?,
    val notes: String?,
)