package com.slippedpenguin.mangolist.data

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.slippedpenguin.mangolist.BuildConfig
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.graphql.GetAnimeByGenreQuery
import com.slippedpenguin.mangolist.graphql.GetMangaReleasesQuery
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/*
 * AniListClient — thin wrapper over Apollo Kotlin 4.x for the AniList GraphQL
 * API.
 *
 * v1.2 surface:
 *   - search(query, type)         — anonymous; default type=ANIME
 *   - getViewer(token)            — bearer auth; per-call client
 *   - getMediaDetails(id, type)   — anonymous rich-detail fetch
 *   - saveEntry(token, entry)     — bearer-auth POST to SaveMediaListEntry
 *   - syncUserList(token, userId, type) — bearer-auth POST to MediaListCollection
 *     (callers iterate type=ANIME then type=MANGA; each becomes one row in Room
 *     stamped with mediaType)
 *   - getPopular / getTrending / getUpcoming / getTopRated (type) — four
 *     Discover carousels, each accepting `type: MediaType = ANIME`
 *   - getByGenre(genre, perPage, type) — Discover genre grid
 *   - getMangaReleases()          — manga discovery carousel
 *   - getNextAiringFor / getAiringSchedule — 7-day airing schedule + per-anime
 *     lookup for the Airing tab; AiringSlot now carries averageScore,
 *     anilistStatus, bannerImage so AiringCards can render those fields.
 *
 *  "type" values accepted by every method that takes one:
 *    - "ANIME" (default)
 *    - "MANGA"
 *  BOTH is a UI-only concept; callers that want both states call the
 *  method twice.
 */
class AniListClient(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val networkObserver: NetworkObserver,
) {

    private val apollo: ApolloClient = ApolloClient.Builder()
        .serverUrl("https://graphql.anilist.co")
        .build()

    /**
     * v1.2.1: Simple rate-limit gate for anonymous (no bearer token)
     * HTTP POSTs. AniList's public API allows ~90 req/min; spacing
     * anonymous calls by at least 350ms stops the HTTP 429 cascade
     * seen on the Airing and Explore tabs. Authenticated requests
     * (which carry user-specific rate limits) don't gate here.
     */
    @Volatile
    private var lastAnonRequestMs: Long = 0L

    private suspend fun rateLimitGate() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastAnonRequestMs
        val minGapMs = 350L
        if (elapsed in 1..<minGapMs) {
            kotlinx.coroutines.delay(minGapMs - elapsed)
        }
        lastAnonRequestMs = System.currentTimeMillis()
    }

    /**
     * Opens a POST [HttpURLConnection] pre-configured for AniList. The
     * hand-rolled sync paths went silent on v0.8.2 because the default
     * `Dalvik/2.1.0` User-Agent tripped Cloudflare's bot challenge (HTTP
     * 403) and missing timeouts left the connection hanging in
     * captive-portal networks. Bakes in:
     *
     *   - explicit connectTimeout / readTimeout
     *   - a custom User-Agent (`MangoList/<version>`), which Cloudflare
     *     accepts while the framework-default Dalvik UA is blocked
     *   - `Accept: application/json`
     *   - `Connection: close` so we never inherit a half-dead pooled
     *     connection from a previous request (the classic
     *     `EOFException` on Android 8-10)
     *
     * Callers still `.use{}` the input/error streams and parse the body;
     * the `.use{}` closure releases the socket on exit.
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
     * supplied [default] sentinel. Avoids long DNS timeouts and gives
     * callers a predictable offline result.
     */
    private inline fun <T> withNetwork(default: T, block: () -> T): T {
        return if (networkObserver.isCurrentlyOnline()) block() else default
    }

    /**
     * Translate a string mediaType to the AniList MediaType enum Apollo
     * Kotlin 4.x generates. Accepts "ANIME" / "MANGA". Anything else
     * falls back to ANIME (log + return default) so a future caller
     * passing "BOTH" or a typo doesn't crash codegen-bound call sites.
     */
    private fun toAniListType(type: String): com.apollographql.apollo.api.Optional<com.slippedpenguin.mangolist.graphql.type.MediaType> {
        val enum = com.slippedpenguin.mangolist.graphql.type.MediaType.entries.firstOrNull { it.rawValue == type }
            ?: com.slippedpenguin.mangolist.graphql.type.MediaType.ANIME
        return com.apollographql.apollo.api.Optional.present(enum)
    }

    /**
     * Search AniList for media matching [query]. Returns up to 12 results.
     * The optional [type] parameter (default "ANIME") lets the search bar
     * surface manga too when the user toggles the Explore media-type
     * segmented control.
     */
    suspend fun search(query: String, type: String = "ANIME"): List<AnimeEntry> {
        if (query.isBlank()) return emptyList()
        return withNetwork(emptyList()) {
            withContext(Dispatchers.IO) {
                val response = try {
                    apollo.query(SearchAnimeQuery(search = query, type = toAniListType(type))).execute()
                } catch (e: Exception) {
                    android.util.Log.w("AniListClient", "search failed", e)
                    return@withContext emptyList()
                }
                val now = System.currentTimeMillis()
                response.data?.Page?.media.orEmpty().filterNotNull().mapNotNull { entry ->
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
                        mediaType    = m.type?.rawValue ?: "ANIME",
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
            }  // withContext
        }
    }

    /**
     * Build an AnimeEntry from an Apollo `AnimeCardFields` fragment for the
     * Explore (Discover) tab. The Explore surfaces never get persisted to
     * Room — favourites and additions happen via the search bar — so the
     * defaults are placeholders (`status = "plan"`, empty `notes`, no
     * `syncedAt`) and never reach the Watchlist unless the user taps a
     * card.
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
        mediaType: String,
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
        mediaType     = mediaType,
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
     * Fetch ~25 anime (or manga) ordered by AniList's POPULARITY_DESC.
     * Used as the top horizontal carousel on ExploreScreen. v1.2:
     * `type` parameter — default ANIME keeps every existing call site
     * back-compatible; pass "MANGA" for the manga carousel mode.
     */
    suspend fun getPopular(type: String = "ANIME"): List<AnimeEntry> = withNetwork(emptyList()) {
        try {
            withContext(Dispatchers.IO) {
                val response = apollo.query(GetPopularAnimeQuery(type = toAniListType(type))).execute()
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
                        mediaType    = m.type?.rawValue ?: "ANIME",
                        now          = now,
                    )
                }
            }  // withContext
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getPopular failed", e)
            emptyList()
        }
    }

    /** TRENDING_DESC carousel. Same shape as `getPopular`. */
    suspend fun getTrending(type: String = "ANIME"): List<AnimeEntry> = withNetwork(emptyList()) {
        try {
            withContext(Dispatchers.IO) {
                val response = apollo.query(GetTrendingAnimeQuery(type = toAniListType(type))).execute()
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
                        mediaType    = m.type?.rawValue ?: "ANIME",
                        now          = now,
                    )
                }
            }  // withContext
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getTrending failed", e)
            emptyList()
        }
    }

    /**
     * NOT_YET_RELEASED + START_DATE_DESC — the "Coming soon" carousel.
     * v1.2: pulls manga when `type == "MANGA"`; the query is the same
     * shape, just with a different media type filter.
     */
    suspend fun getUpcoming(type: String = "ANIME"): List<AnimeEntry> = withNetwork(emptyList()) {
        try {
            withContext(Dispatchers.IO) {
                val response = apollo.query(GetUpcomingAnimeQuery(type = toAniListType(type))).execute()
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
                        mediaType    = m.type?.rawValue ?: "ANIME",
                        now          = now,
                    )
                }
            }  // withContext
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getUpcoming failed", e)
            emptyList()
        }
    }

    /** SCORE_DESC carousel — AniList's all-time top rated. */
    suspend fun getTopRated(type: String = "ANIME"): List<AnimeEntry> = withNetwork(emptyList()) {
        try {
            withContext(Dispatchers.IO) {
                val response = apollo.query(GetTopRatedAnimeQuery(type = toAniListType(type))).execute()
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
                        mediaType    = m.type?.rawValue ?: "ANIME",
                        now          = now,
                    )
                }
            }  // withContext
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getTopRated failed", e)
            emptyList()
        }
    }

    /**
     * Top [perPage] media in a single [genre] on AniList. v1.2: takes a
     * `type` parameter so the genre grid can serve manga too.
     * Existing call sites that omit `type` get the v1.1 ANIME behaviour.
     */
    suspend fun getByGenre(genre: String, perPage: Int = 25, type: String = "ANIME"): List<AnimeEntry> {
        if (genre.isBlank()) return emptyList()
        val canonical = genre.trim().replaceFirstChar { it.uppercase() }
        return withNetwork(emptyList()) {
            try {
                withContext(Dispatchers.IO) {
                    val response = apollo.query(
                        GetAnimeByGenreQuery(
                            genre = canonical,
                            // Apollo Kotlin 4.x: any GraphQL variable with a
                            // schema-default becomes Optional<T?> in the
                            // generated constructor. Wrap so the caller's
                            // explicit value lands instead of falling back
                            // to the schema default (25).
                            perPage = com.apollographql.apollo.api.Optional.present(perPage),
                            type = toAniListType(type),
                        ),
                    ).execute()
                    val now = System.currentTimeMillis()
                    response.data?.Page?.media.orEmpty()
                        .filterNotNull()
                        .mapNotNull { entry ->
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
                                mediaType    = m.type?.rawValue ?: "ANIME",
                                now          = now,
                            )
                        }
                }  // withContext
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Re-throw cooperative cancellation explicitly so the outer
                // LaunchedEffect's key change (rapid chip taps) propagates as
                // a real cancellation rather than being swallowed as a sync
                // error that returns an empty list.
                throw e
            } catch (e: Exception) {
                android.util.Log.w("AniListClient", "getByGenre failed for $canonical", e)
                emptyList()
            }
        }
    }

    /**
     * v1.2: "Releasing manga" — manga equivalent of getAiringSchedule.
     * Manga has no `airingSchedules` field, so we hit `Page.media` with
     * `status: RELEASING` and `START_DATE_DESC` sort. Same AnimeCardFields
     * shape as the carousels, so it slots into the existing Explore
     * "Discover" carousels when the user toggles the segmented media-type
     * control to "MANGA" or "BOTH" with the upcoming carousel promoted.
     */
    suspend fun getMangaReleases(): List<AnimeEntry> = withNetwork(emptyList()) {
        try {
            withContext(Dispatchers.IO) {
                // GetMangaReleasesQuery has no GraphQL variables — the
                // .graphql hardcodes perPage:50, so Apollo Kotlin generates
                // a parameterless constructor. The wrapper intentionally
                // exposes no perPage arg either; the page size is owned by
                // the schema literal.
                val response = apollo.query(GetMangaReleasesQuery()).execute()
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
                        mediaType    = "MANGA",
                        now          = now,
                    )
                }
            }  // withContext
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "getMangaReleases failed", e)
            emptyList()
        }
    }

    /**
     * Fetch the authenticated viewer's profile.
     */
    suspend fun getViewer(token: String): AnimeViewer? {
        if (token.isBlank()) return null
        return withNetwork(null) {
            val authClient = ApolloClient.Builder()
                .serverUrl("https://graphql.anilist.co")
                .addHttpHeader("Authorization", "Bearer $token")
                .build()
            return try {
                withContext(Dispatchers.IO) {
                    val response = authClient.query(GetViewerQuery()).execute()
                    val v = response.data?.Viewer ?: return@withContext null
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
                }
            } catch (e: Exception) {
                android.util.Log.w("AniListClient", "getViewer failed", e)
                null
            } finally {
                authClient.close()
            }
        }
    }

    /**
     * Fetch the authenticated viewer's full [type] list (ANIME or MANGA)
     * and map each entry to the local AnimeEntry model. Uses a hand-rolled
     * GraphQL POST to avoid Apollo codegen fragility with fragment spreads.
     *
     * Returns a [SyncResult] so callers can surface the actual error
     * message instead of a generic "Sync failed" toast. Callers iterate
     * twice (ANIME then MANGA) to populate a single Room table.
     */
    suspend fun syncUserList(token: String, userId: Int, type: String = "ANIME"): SyncResult {
        if (token.isBlank()) return SyncResult(null, "No access token. Please log in again.")
        if (userId <= 0) return SyncResult(null, "Invalid user ID. Please log in again.")
        if (type !in listOf("ANIME", "MANGA")) return SyncResult(null, "Unknown media type: $type")
        return withNetwork(SyncResult(null, "No internet connection.")) {
            try {
                withContext(Dispatchers.IO) {
                    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                    val payload = buildJsonObject {
                        put(
                            "query",
                            """
                            query(${'$'}userId: Int!) {
                              MediaListCollection(userId: ${'$'}userId, type: $type) {
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
                                      type
                                      title { romaji english }
                                      coverImage { large medium color }
                                      episodes
                                      chapters
                                      volumes
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
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }

                    if (responseCode !in 200..299) {
                        val msg = "HTTP $responseCode: ${responseBody.take(200)}"
                        android.util.Log.w("AniListClient", "syncUserList($type) $msg")
                        return@withContext SyncResult(null, msg)
                    }

                    val root = (json.parseToJsonElement(responseBody) as? JsonObject)
                        ?: run {
                            val msg = "Response is not a JSON object: ${responseBody.take(200)}"
                            android.util.Log.w("AniListClient", "syncUserList $msg")
                            return@withContext SyncResult(null, msg)
                        }

                    val errors = root["errors"] as? kotlinx.serialization.json.JsonArray
                    if (errors != null && errors.isNotEmpty()) {
                        val msg = errors.joinToString(", ") {
                            ((it as? JsonObject)?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "GraphQL error"
                        }
                        android.util.Log.w("AniListClient", "syncUserList($type) GraphQL errors: $msg")
                        return@withContext SyncResult(null, msg)
                    }

                    val nowMillis = System.currentTimeMillis()
                    val collection = (root["data"] as? JsonObject)
                        ?.get("MediaListCollection")
                    if (collection == null || collection is kotlinx.serialization.json.JsonNull) {
                        return@withContext SyncResult(emptyList(), null)
                    }
                    val collObj = (collection as? JsonObject)
                        ?: run {
                            val msg = "MediaListCollection is not an object: ${collection.toString().take(200)}"
                            android.util.Log.w("AniListClient", "syncUserList $msg")
                            return@withContext SyncResult(null, msg)
                        }
                    val entries = (collObj["lists"] as? kotlinx.serialization.json.JsonArray)
                        ?.filterIsInstance<JsonObject>()
                        ?.filter { (it["isCustomList"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull != true }
                        ?.flatMap { list ->
                            (list["entries"] as? kotlinx.serialization.json.JsonArray)
                                ?.filterIsInstance<JsonObject>()
                                .orEmpty()
                        }
                        ?.mapNotNull { entry -> parseMediaListEntry(entry, type, nowMillis) }
                        .orEmpty()
                    SyncResult(entries, null)
                }  // withContext
            } catch (e: Exception) {
                android.util.Log.e("AniListClient", "syncUserList($type) failed: ${e.javaClass.simpleName}", e)
                SyncResult(null, e.message ?: "Unknown sync error (${e.javaClass.simpleName})")
            }
        }
    }

    private fun parseMediaListEntry(entry: JsonObject, mediaType: String, nowMillis: Long): AnimeEntry? {
        return try {
            parseMediaListEntrySafe(entry, mediaType, nowMillis)
        } catch (e: Exception) {
            android.util.Log.w("AniListClient", "Skipping one list entry due to parse error", e)
            null
        }
    }

    private fun parseMediaListEntrySafe(
        entry: JsonObject,
        mediaType: String,
        nowMillis: Long,
    ): AnimeEntry? {
        val media = (entry["media"] as? JsonObject) ?: return null
        val title = media["title"] as? JsonObject
        val coverImage = media["coverImage"] as? JsonObject
        val startDate = media["startDate"] as? JsonObject

        // Prefer the type AniList returned on the response; fall back to
        // the caller's requested mediaType parameter.
        val serverType = (media["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val resolvedType = serverType ?: mediaType

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

        // Manga has `chapters` instead of `episodes`. For manga we read
        // `chapters`; for anime, `episodes`.
        val totalUnits = if (resolvedType == "MANGA") {
            (media["chapters"] as? kotlinx.serialization.json.JsonPrimitive)?.int
                ?: (media["volumes"] as? kotlinx.serialization.json.JsonPrimitive)?.int
        } else {
            (media["episodes"] as? kotlinx.serialization.json.JsonPrimitive)?.int
        }

        return AnimeEntry(
            anilistId     = (media["id"] as? kotlinx.serialization.json.JsonPrimitive)?.int ?: return null,
            title         = (title?.get("english") as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: (title?.get("romaji") as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: "Untitled",
            cover         = (coverImage?.get("large") as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: (coverImage?.get("medium") as? kotlinx.serialization.json.JsonPrimitive)?.content,
            coverColor    = (coverImage?.get("color") as? kotlinx.serialization.json.JsonPrimitive)?.content,
            format        = (media["format"] as? kotlinx.serialization.json.JsonPrimitive)?.content,
            episodes      = totalUnits,
            averageScore  = (media["averageScore"] as? kotlinx.serialization.json.JsonPrimitive)?.int,
            year          = (startDate?.get("year") as? kotlinx.serialization.json.JsonPrimitive)?.int,
            synopsis      = null,
            genres        = (media["genres"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?.joinToString(",")
                ?: "",
            mediaType     = resolvedType,
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
     * Fetch the rich detail payload for one anime by [id]. v1.2: takes an
     * optional [type] so manga entries load correctly (AniList IDs are
     * globally unique, but the type guides the GraphQL fetcher).
     */
    suspend fun getMediaDetails(id: Int, type: String = "ANIME"): MediaDetails? {
        if (id <= 0) return null
        return withNetwork(null) {
            val response = try {
                withContext(Dispatchers.IO) {
                    apollo.query(
                        GetMediaDetailsQuery(id = id, type = toAniListType(type)),
                    ).execute()
                }
            } catch (e: Exception) {
                android.util.Log.w("AniListClient", "getMediaDetails failed for $id", e)
                return@withNetwork null
            }
            val m = response.data?.Media ?: return@withNetwork null
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

            return@withNetwork MediaDetails(
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
                chapters      = cf?.chapters,
                volumes       = cf?.volumes,
                duration      = m.duration,
                averageScore  = cf?.averageScore,
                genres        = cf?.genres.orEmpty().filterNotNull(),
                studios       = studios,
                synopsis      = m.description,
                characters    = characters,
                relations     = relations,
                mediaType     = cf?.type?.rawValue ?: "ANIME",
            )
        }
    }

    /**
     * Exchange an authorization code for an access token (OAuth2 authorization
     * code grant). POSTs to https://anilist.co/api/v2/oauth/token with the
     * client_id, client_secret, code, and grant_type.
     */
    suspend fun exchangeCodeForToken(code: String): String? {
        if (code.isBlank()) return null
        return withNetwork(null) {
            try {
                withContext(Dispatchers.IO) {
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
                        val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        android.util.Log.w("AniListClient", "token exchange HTTP ${conn.responseCode}: $errorBody")
                        return@withContext null
                    }
                    val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = json.parseToJsonElement(responseBody).jsonObject
                    root["access_token"]?.jsonPrimitive?.content
                }  // withContext
            } catch (e: Exception) {
                android.util.Log.w("AniListClient", "exchangeCodeForToken failed", e)
                null
            }
        }
    }

    /**
     * Push a single AnimeEntry edit back to AniList via SaveMediaListEntry.
     * Hand-rolled kotlinx serialization JSON POST (the generated
     * SaveMediaListEntryMutation constructor proved opaque at kotlinc).
     *
     * Mappings:
     *   - status → AniList MediaListStatus enum
     *   - personalScore / 10.0 → score Float (0-10 scale; AniList expects
     *     Float, not Int)
     *   - notes → String (empty string clears notes on AniList)
     *   - listEntryId non-null → update; null → create new
     *
     * v1.2.1: Fixed the score variable name from `scoreRaw` → `score`
     * (AniList's SaveMediaListEntry mutation declares `score: Float`,
     * not `scoreRaw: Int`). Also converts the app's 0-100 integer to
     * AniList's 0.0-10.0 Float before sending.
     */
    suspend fun saveEntry(token: String, entry: AnimeEntry): SaveResult? {
        if (token.isBlank()) return null
        return withNetwork(null) {
            try {
                withContext(Dispatchers.IO) {
                    val anilistStatus = when (entry.status) {
                        "plan"      -> "PLANNING"
                        "watching"  -> "CURRENT"
                        "completed" -> "COMPLETED"
                        "dropped"   -> "DROPPED"
                        "paused"    -> "PAUSED"
                        "repeating" -> "REPEATING"
                        else        -> "CURRENT"
                    }
                    // AniList expects `score: Float` in 0.0-10.0 range.
                    // The app stores personalScore as 0-100 integer,
                    // so divide by 10.0 and format to one decimal place.
                    val scoreFloat = entry.personalScore?.let { it / 10.0 }
                    val variables = buildJsonObject {
                        entry.listEntryId?.let { put("id", it) }
                        put("mediaId", entry.anilistId)
                        put("status", anilistStatus)
                        put("progress", entry.currentEp)
                        scoreFloat?.let { put("score", it) }
                        put("notes", entry.notes)
                    }
                    val payload = buildJsonObject {
                        put(
                            "query",
                            "mutation(\$id:Int,\$mediaId:Int,\$status:MediaListStatus,\$score:Float,\$progress:Int,\$notes:String)" +
                            "{SaveMediaListEntry(id:\$id,mediaId:\$mediaId,status:\$status,score:\$score,progress:\$progress,notes:\$notes)" +
                            "{id updatedAt notes score}}",
                        )
                        put("variables", variables)
                    }
                    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                    val body = json.encodeToString(JsonObject.serializer(), payload)

                    val conn = openPost("https://graphql.anilist.co", token)
                    conn.outputStream.use { it.write(body.toByteArray()) }

                    if (conn.responseCode !in 200..299) {
                        val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        android.util.Log.w("AniListClient", "saveEntry HTTP ${conn.responseCode} (anilistId=${entry.anilistId}, status=${entry.status}, currentEp=${entry.currentEp}, personalScore=${entry.personalScore}, notes.len=${entry.notes.length}): ${errorBody.take(400)}")
                        return@withContext null
                    }
                    val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = json.parseToJsonElement(responseBody).jsonObject
                    (root["errors"] as? kotlinx.serialization.json.JsonArray)?.let { errs ->
                        val msgs = errs.joinToString(", ") { e ->
                            ((e as? JsonObject)?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: e.toString()
                        }
                        android.util.Log.w("AniListClient", "saveEntry GraphQL errors (anilistId=${entry.anilistId}): $msgs")
                        return@withContext null
                    }
                    val saveNode = root["data"]?.jsonObject?.get("SaveMediaListEntry")?.jsonObject ?: run {
                        android.util.Log.w("AniListClient", "saveEntry response has no data.SaveMediaListEntry: ${responseBody.take(400)}")
                        return@withContext null
                    }
                    val id = (saveNode["id"] as? kotlinx.serialization.json.JsonPrimitive)?.int ?: return@withContext null
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
                }  // withContext
            } catch (e: Exception) {
                android.util.Log.w("AniListClient", "saveEntry failed", e)
                null
            }
        }
    }

    /**
     * Toggle the viewer's favourite flag on a single anime.
     * AniList's favourites are media-level (Media.isFavourite), not list-
     * entry-level. The mutation's response carries the up-to-date
     * User.favourites.anime { nodes { id } } connection; we scan it for
     * the post-toggle state so the caller doesn't need a second
     * round-trip.
     */
    suspend fun toggleFavorite(token: String, animeId: Int): Boolean? {
        if (token.isBlank()) return null
        return withNetwork(null) {
            try {
                withContext(Dispatchers.IO) {
                    val variables = buildJsonObject {
                        put("animeId", animeId)
                    }
                    val payload = buildJsonObject {
                        put(
                            "query",
                            "mutation(\$animeId:Int)" +
                            "{ToggleFavourite(animeId:\$animeId)" +
                            "{anime{nodes{id}}}}",
                        )
                        put("variables", variables)
                    }
                    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                    val body = json.encodeToString(JsonObject.serializer(), payload)

                    val conn = openPost("https://graphql.anilist.co", token)
                    conn.outputStream.use { it.write(body.toByteArray()) }

                    if (conn.responseCode !in 200..299) {
                        val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        android.util.Log.w(
                            "AniListClient",
                            "toggleFavorite HTTP ${conn.responseCode} (animeId=$animeId): ${errorBody.take(400)}",
                        )
                        return@withContext null
                    }
                    val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = json.parseToJsonElement(responseBody).jsonObject

                    (root["errors"] as? kotlinx.serialization.json.JsonArray)?.let { errs ->
                        val msgs = errs.joinToString(", ") { e ->
                            ((e as? JsonObject)?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: e.toString()
                        }
                        android.util.Log.w("AniListClient", "toggleFavorite GraphQL errors (animeId=$animeId): $msgs")
                        return@withContext null
                    }

                    val data = root["data"] as? JsonObject
                    val toggleNode = data?.get("ToggleFavourite") as? JsonObject
                    val anime = toggleNode?.get("anime") as? JsonObject
                    val nodes = anime?.get("nodes") as? JsonArray
                        ?: return@withContext null
                    nodes.any { node ->
                        val nodeObj = node as? JsonObject ?: return@any false
                        val id = nodeObj["id"] as? JsonPrimitive ?: return@any false
                        id.int == animeId
                    }
                }  // withContext
            } catch (e: Exception) {
                android.util.Log.w("AniListClient", "toggleFavorite failed", e)
                null
            }
        }
    }

    /**
     * Fetch `Media.nextAiringEpisode` for a specific list of media IDs.
     * Used by AiringScreen's `On my list` tab so the user's tracking
     * shows always appear even when their global schedule slot falls
     * off the AniList `Page(perPage:50)` first page.
     *
     * v1.2: also fetches `averageScore`, `status`, `bannerImage` on the
     * media node so AiringCards can render those fields without a
     * second round-trip per row. The fields are nullable on the wire;
     * the resulting `AiringSlot` carries nullable averages/status/banner
     * and the UI uses them as fallback styling.
     *
     * Returns a list sorted ascending by `airingAt` so day-grouping in
     * the UI can render top-to-bottom without re-sorting.
     */
    suspend fun getNextAiringFor(mediaIds: List<Int>): List<AiringSlot> {
        if (mediaIds.isEmpty()) return emptyList()
        return withNetwork(emptyList()) {
            try {
                rateLimitGate()
                withContext(Dispatchers.IO) {
                    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                    val result = mediaIds.chunked(50).flatMap { batch ->
                        val payload = buildJsonObject {
                            put(
                                "query",
                                "query(\$ids:[Int])" +
                                "{Page(perPage:50)" +
                                "{media(id_in:\$ids,type:ANIME)" +
                                "{id title{english romaji} coverImage{large} " +
                                "averageScore status bannerImage " +
                                "nextAiringEpisode{id airingAt episode}}}}",
                            )
                            put("variables", buildJsonObject {
                                put("ids", JsonArray(batch.map { JsonPrimitive(it) }))
                            })
                        }
                        val body = json.encodeToString(JsonObject.serializer(), payload)

                        val conn = openPost("https://graphql.anilist.co")
                        conn.outputStream.use { it.write(body.toByteArray()) }

                        if (conn.responseCode !in 200..299) {
                            android.util.Log.w(
                                "AniListClient",
                                "getNextAiringFor HTTP ${conn.responseCode} on batch of ${batch.size}",
                            )
                            return@flatMap emptyList<AiringSlot>()
                        }
                        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                        val root = json.parseToJsonElement(responseBody).jsonObject
                        val mediaArray = root["data"]?.jsonObject
                            ?.get("Page")?.jsonObject
                            ?.get("media")?.jsonArray ?: return@flatMap emptyList()

                        mediaArray.mapNotNull { el ->
                            val m = el.jsonObject
                            val next = m["nextAiringEpisode"] as? JsonObject ?: return@mapNotNull null
                            val title = m["title"] as? JsonObject
                            val cover = m["coverImage"] as? JsonObject
                            val avgScore = (m["averageScore"] as? JsonPrimitive)?.int
                            val status = (m["status"] as? JsonPrimitive)?.content
                            val banner = (m["bannerImage"] as? JsonPrimitive)?.content
                            AiringSlot(
                                id = (next["id"] as? JsonPrimitive)?.int ?: return@mapNotNull null,
                                airingAt = (next["airingAt"] as? JsonPrimitive)?.long ?: return@mapNotNull null,
                                episode = (next["episode"] as? JsonPrimitive)?.int ?: 0,
                                animeId = (m["id"] as? JsonPrimitive)?.int ?: return@mapNotNull null,
                                title = (title?.get("english") as? JsonPrimitive)?.content
                                    ?: (title?.get("romaji") as? JsonPrimitive)?.content
                                    ?: "Untitled",
                                coverLarge = (cover?.get("large") as? JsonPrimitive)?.content,
                                averageScore = avgScore,
                                anilistStatus = status,
                                bannerImage = banner,
                            )
                        }
                    }
                    result
                }  // withContext
            } catch (e: Exception) {
                android.util.Log.w("AniListClient", "getNextAiringFor failed", e)
                emptyList()
            }
        }.sortedBy { it.airingAt }
    }

    /**
     * Fetch the next 7 days of airing schedules from AniList.
     *
     * v1.2: extends the JSON query with `averageScore status bannerImage`
     * on the `media` node so the `All airing` and `On my list` tabs both
     * have rich metadata on each slot.
     *
     * Returns slots sorted by airingAt ascending.
     */
    suspend fun getAiringSchedule(): List<AiringSlot> {
        val now = System.currentTimeMillis() / 1000
        val week = now + 7 * 86400
        return withNetwork(emptyList()) {
            try {
                rateLimitGate()
                withContext(Dispatchers.IO) {
                    val variables = buildJsonObject {
                        put("airingAtGreater", now.toInt())
                        put("airingAtLesser", week.toInt())
                    }
                    val payload = buildJsonObject {
                        put(
                            "query",
                            "query(\$airingAtGreater:Int!,\$airingAtLesser:Int!)" +
                            "{Page(perPage:50){airingSchedules(airingAt_greater:\$airingAtGreater,airingAt_lesser:\$airingAtLesser,sort:[TIME])" +
                            "{id airingAt episode media{id title{english romaji} coverImage{large} " +
                            "averageScore status bannerImage}}}}",
                        )
                        put("variables", variables)
                    }
                    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                    val body = json.encodeToString(JsonObject.serializer(), payload)

                    val conn = openPost("https://graphql.anilist.co")
                    conn.outputStream.use { it.write(body.toByteArray()) }

                    if (conn.responseCode !in 200..299) return@withContext emptyList()
                    val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = json.parseToJsonElement(responseBody).jsonObject
                    val schedules = root["data"]?.jsonObject
                        ?.get("Page")?.jsonObject
                        ?.get("airingSchedules")?.jsonArray ?: return@withContext emptyList()
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
                            averageScore = (media["averageScore"] as? JsonPrimitive)?.int,
                            anilistStatus = (media["status"] as? JsonPrimitive)?.content,
                            bannerImage = (media["bannerImage"] as? JsonPrimitive)?.content,
                        )
                    }
                }  // withContext
            } catch (e: Exception) {
                android.util.Log.w("AniListClient", "getAiringSchedule failed", e)
                emptyList()
            }
        }
    }
}

/*
 * Rich-detail view of one anime/manga, fetched by AniListClient.getMediaDetails.
 * The UI never sees generated GraphQL types directly — it binds to this
 * stable Kotlin class so future codegen/migration changes don't ripple.
 *
 * v1.2: adds `chapters`, `volumes`, `mediaType` so DetailScreen can switch
 * between "Episodes X", "Chapters X", and "Volumes X" labels based on
 * mediaType + format.
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
    val chapters: Int? = null,
    val volumes: Int? = null,
    val duration: Int?,
    val averageScore: Int?,
    val genres: List<String>,
    val studios: List<String>,
    val synopsis: String?,
    val characters: List<CharacterCard>,
    val relations: List<RelationCard>,
    val mediaType: String = "ANIME",
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
 * Lightweight view of the AniList viewer that we cache in TokenStore.
 */
data class AnimeViewer(
    val id: Int,
    val name: String?,
    val avatarLarge: String? = null,
    val avatarMedium: String? = null,
    val animeCount: Int? = null,
    val animeMeanScore: Double? = null,
    val episodesWatched: Int? = null,
    val minutesWatched: Int? = null,
)

/**
 * One airing-schedule slot returned by GetAiringSchedule /
 * GetNextAiringFor. v1.2: carries `averageScore`, `anilistStatus`, and
 * `bannerImage` so AiringCard can render score badge + status pill +
 * 4dp-tall banner accent above the cover fallback. All three are
 * nullable on the wire and nullable here.
 */
data class AiringSlot(
    val id: Int,
    val airingAt: Long,
    val episode: Int,
    val animeId: Int,
    val title: String,
    val coverLarge: String?,
    val averageScore: Int? = null,
    val anilistStatus: String? = null,
    val bannerImage: String? = null,
)

/**
 * Result wrapper for list-sync operations.
 */
data class SyncResult(
    val entries: List<AnimeEntry>?,
    val error: String?,
)

/*
 * Result wrapper for a successful SaveMediaListEntry mutation.
 */
data class SaveResult(
    val id: Int,
    val updatedAtSeconds: Long?,
    val notes: String?,
)
