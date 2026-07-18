package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.AnimePosterCard
import com.slippedpenguin.mangolist.ui.components.OfflineBanner
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.TierUnranked
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Explore — v1.0: discovery surface replacing the v0.x "Add" search-only
 * tab. Three AniList carousels stack under the search bar:
 *
 *   1. Popular (POPULARITY_DESC)
 *   2. Trending (TRENDING_DESC)
 *   3. Coming Soon (NOT_YET_RELEASED + START_DATE_DESC)
 *   4. Top Rated (SCORE_DESC)
 *
 * Each carousel fetches independently — failures in one don't block the
 * others. Pull-to-refresh re-runs all four queries in parallel via
 * coroutineScope { async { ... } } / awaitAll, which is cheaper than
 * sequential await — the AniList introspection suggests ~200ms per query,
 * so a 4-up parallel fetch lands in ~300ms rather than ~800ms.
 *
 * When the user types into the search bar (≥ 2 chars), the carousels are
 * hidden entirely and the screen swaps to a vertical list of search hits.
 * This mirrors AniHyou's explore surface — one column for two distinct
 * modes rather than two parallel columns, which would compete for vertical
 * space on a phone.
 *
 * Tapping a search hit upserts the AnimeEntry into Room and routes to
 * DetailScreen — same as v0.x's "Add" flow. Tapping a carousel card
 * routes directly to DetailScreen without writing to Room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var popular by remember { mutableStateOf<List<AnimeEntry>?>(null) }
    var trending by remember { mutableStateOf<List<AnimeEntry>?>(null) }
    var upcoming by remember { mutableStateOf<List<AnimeEntry>?>(null) }
    var topRated by remember { mutableStateOf<List<AnimeEntry>?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<AnimeEntry>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var inListIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val isOnline by app.networkObserver.isOnline.collectAsState(initial = true)

    suspend fun reloadCarousels() {
        coroutineScope {
            val (pop, trd, upc, top) = awaitAll(
                async { app.anilistClient.getPopular() },
                async { app.anilistClient.getTrending() },
                async { app.anilistClient.getUpcoming() },
                async { app.anilistClient.getTopRated() },
            )
            popular = pop as? List<AnimeEntry> ?: emptyList()
            trending = trd as? List<AnimeEntry> ?: emptyList()
            upcoming = upc as? List<AnimeEntry> ?: emptyList()
            topRated = top as? List<AnimeEntry> ?: emptyList()
        }
    }

    LaunchedEffect(Unit) {
        reloadCarousels()
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            reloadCarousels()
            isRefreshing = false
        }
    }

    // Update the in-list set so "Add" → "Open" state on search hits is
    // accurate without the user having to back out of Explore.
    LaunchedEffect(Unit) {
        app.database.animeDao().observeAll().collect { all ->
            inListIds = all.map { it.anilistId }.toSet()
        }
    }

    // Debounced search. When query is < 2 chars, clear results. When ≥ 2,
    // wait 350ms after the last keystroke then fire `app.anilistClient.search`.
    LaunchedEffect(query) {
        val cleaned = query.trim()
        if (cleaned.length < 2) {
            searchResults = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        searchResults = app.anilistClient.search(cleaned)
        searching = false
    }

    val isSearching = query.trim().length >= 2

    Column(modifier = Modifier.fillMaxSize()) {
        OfflineBanner()

        // Search bar — sticky at top so the carousels / results scroll under it.
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search AniList…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = TierUnranked.copy(alpha = 0.25f),
            ),
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isRefreshing) {
                    isRefreshing = true
                    // Also re-fire search if the user is currently searching,
                    // so the search results refresh along with the carousels.
                    if (isSearching) {
                        scope.launch {
                            searchResults = app.anilistClient.search(query.trim())
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isSearching) {
                SearchResultsList(
                    results = searchResults,
                    loading = searching,
                    inListIds = inListIds,
                    onAdd = { entry ->
                        scope.launch {
                            if (entry.anilistId !in inListIds) {
                                app.database.animeDao().upsert(entry)
                            }
                            navController.navigate("detail/${entry.anilistId}")
                        }
                    },
                )
            } else {
                CarouselColumn(
                    popular = popular,
                    trending = trending,
                    upcoming = upcoming,
                    topRated = topRated,
                    isOnline = isOnline,
                    onCardClick = { entry ->
                        navController.navigate("detail/${entry.anilistId}")
                    },
                )
            }
        }
    }
}

@Composable
private fun CarouselColumn(
    popular: List<AnimeEntry>?,
    trending: List<AnimeEntry>?,
    upcoming: List<AnimeEntry>?,
    topRated: List<AnimeEntry>?,
    isOnline: Boolean,
    onCardClick: (AnimeEntry) -> Unit,
) {
    val allLoaded = popular != null && trending != null && upcoming != null && topRated != null
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (!allLoaded) {
            item(key = "loading_initial") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }

        item(key = "popular") {
            CarouselRow(
                title = "Popular this season",
                items = popular.orEmpty(),
                emptyText = if (isOnline) "No popular anime right now." else "Couldn't reach AniList.",
                onCardClick = onCardClick,
            )
        }
        item(key = "trending") {
            CarouselRow(
                title = "Trending now",
                items = trending.orEmpty(),
                emptyText = "No trending anime right now.",
                onCardClick = onCardClick,
            )
        }
        item(key = "upcoming") {
            CarouselRow(
                title = "Coming soon",
                items = upcoming.orEmpty(),
                emptyText = "No upcoming releases.",
                onCardClick = onCardClick,
            )
        }
        item(key = "top_rated") {
            CarouselRow(
                title = "Top rated of all time",
                items = topRated.orEmpty(),
                emptyText = "No top-rated anime right now.",
                onCardClick = onCardClick,
            )
        }
        item(key = "footer_spacer") {
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CarouselRow(
    title: String,
    items: List<AnimeEntry>,
    emptyText: String,
    onCardClick: (AnimeEntry) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = Accent,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
        )
        if (items.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(items, key = { it.anilistId }) { entry ->
                    AnimePosterCard(
                        entry = entry,
                        onClick = { onCardClick(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<AnimeEntry>,
    loading: Boolean,
    inListIds: Set<Int>,
    onAdd: (AnimeEntry) -> Unit,
) {
    when {
        loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        results.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No matches.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results, key = { it.anilistId }) { result ->
                SearchResultRow(
                    entry = result,
                    inList = result.anilistId in inListIds,
                    onAdd = { onAdd(result) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(entry: AnimeEntry, inList: Boolean, onAdd: () -> Unit) {
    val year = entry.year?.toString().orEmpty()
    val epLabel = entry.episodes?.let { "  $it ep" } ?: ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = entry.cover,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 56.dp, height = 80.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (year.isNotEmpty() || epLabel.isNotEmpty()) {
                Text(
                    text = (year + epLabel).trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = onAdd) {
            Text(if (inList) "Open" else "+ Add")
        }
    }
}

