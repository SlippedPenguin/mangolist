package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * Explore — v1.2: discovery surface for both anime and manga.
 *
 *   - **Media-type segmented control** at the very top: Anime / Manga.
 *     Toggling it re-fetches all four carousels for the chosen type.
 *     Both modes render the same carousels with the same query surface;
 *     only the `type` parameter passed to the carousel fetches changes.
 *
 *   - **Search bar** still wins outright when active (≥ 2 chars). The
 *     segmented control dims to 40% alpha while searching so the
 *     precedence rule is visually obvious.
 *
 *   - **Genre chip strip** sits below the search bar and respects the
 *     active media-type for chip-driven queries.
 *
 *   - **Three modes in clear precedence**: search bar > selected genre >
 *     no selection (carousels).
 *
 * Each carousel fetches independently — failures in one don't block the
 * others. Pull-to-refresh re-runs all four carousel queries (and the
 * currently-selected genre query, if any) in parallel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf("ANIME") }  // v1.2: "ANIME" or "MANGA"
    var popular by remember { mutableStateOf<List<AnimeEntry>?>(null) }
    var trending by remember { mutableStateOf<List<AnimeEntry>?>(null) }
    var upcoming by remember { mutableStateOf<List<AnimeEntry>?>(null) }
    var topRated by remember { mutableStateOf<List<AnimeEntry>?>(null) }
    var mangaReleases by remember { mutableStateOf<List<AnimeEntry>?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<AnimeEntry>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var inListIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var genreResults by remember { mutableStateOf<List<AnimeEntry>?>(null) }
    var genreLoading by remember { mutableStateOf(false) }
    var requestSeq by remember { mutableStateOf(0) }
    var firstLoad by remember { mutableStateOf(true) }
    val isOnline by app.networkObserver.isOnline.collectAsState(initial = true)

    suspend fun reloadCarousels() {
        coroutineScope {
            if (mediaType == "ANIME") {
                val (pop, trd, upc, top) = awaitAll(
                    async { app.anilistClient.getPopular("ANIME") },
                    async { app.anilistClient.getTrending("ANIME") },
                    async { app.anilistClient.getUpcoming("ANIME") },
                    async { app.anilistClient.getTopRated("ANIME") },
                )
                popular = pop as? List<AnimeEntry> ?: emptyList()
                trending = trd as? List<AnimeEntry> ?: emptyList()
                upcoming = upc as? List<AnimeEntry> ?: emptyList()
                topRated = top as? List<AnimeEntry> ?: emptyList()
            } else {
                val (pop, trd, upc, top) = awaitAll(
                    async { app.anilistClient.getPopular("MANGA") },
                    async { app.anilistClient.getTrending("MANGA") },
                    async { app.anilistClient.getUpcoming("MANGA") },
                    async { app.anilistClient.getTopRated("MANGA") },
                )
                popular = pop as? List<AnimeEntry> ?: emptyList()
                trending = trd as? List<AnimeEntry> ?: emptyList()
                upcoming = upc as? List<AnimeEntry> ?: emptyList()
                topRated = top as? List<AnimeEntry> ?: emptyList()
                mangaReleases = app.anilistClient.getMangaReleases()
            }
        }
    }

    // v1.2.1: single LaunchedEffect with first-load fastpath avoids
    // the double-fire from v1.2 where LaunchedEffect(Unit) + 
    // LaunchedEffect(mediaType) both fired on cold composition.
    LaunchedEffect(mediaType) {
        if (firstLoad) {
            firstLoad = false
            reloadCarousels()
            return@LaunchedEffect
        }
        delay(500)
        reloadCarousels()
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            scope.launch {
                try {
                    reloadCarousels()
                    selectedGenre?.let { genreResults = app.anilistClient.getByGenre(it, type = mediaType) }
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        app.database.animeDao().observeAll().collect { all ->
            inListIds = all.map { it.anilistId }.toSet()
        }
    }

    LaunchedEffect(query) {
        val cleaned = query.trim()
        if (cleaned.length < 2) {
            searchResults = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        searchResults = app.anilistClient.search(cleaned, mediaType)
        searching = false
    }

    // Compute search-active BEFORE the LaunchedEffects below read it —
    // forward references to a later-declared `val` are a Kotlin compile
    // error even though the value would be in scope at composition time.
    val isSearching = query.trim().length >= 2

    LaunchedEffect(selectedGenre, isSearching) {
        if (isSearching) return@LaunchedEffect
        val g = selectedGenre ?: run {
            genreResults = null
            genreLoading = false
            return@LaunchedEffect
        }
        val mySeq = ++requestSeq
        genreLoading = true
        val res = app.anilistClient.getByGenre(g, type = mediaType)
        if (mySeq == requestSeq) {
            genreResults = res
            genreLoading = false
        }
    }
    val isGenreSelected = selectedGenre != null && !isSearching

    Column(modifier = Modifier.fillMaxSize()) {
        OfflineBanner()

        // v1.2: media-type segmented control. Anime / Manga. Toggling
        // switches every carousel and the genre chip strip to manga.
        MediaTypeSegmentedControl(
            selected = mediaType,
            onSelect = { mediaType = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text(if (mediaType == "MANGA") "Search AniList manga…" else "Search AniList…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = TierUnranked.copy(alpha = 0.25f),
            ),
        )

        GenreChips(
            selected = selectedGenre,
            onSelect = { selectedGenre = it },
            searchActive = isSearching,
            modifier = Modifier.fillMaxWidth(),
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isRefreshing) {
                    isRefreshing = true
                    if (isSearching) {
                        scope.launch {
                            searchResults = app.anilistClient.search(query.trim(), mediaType)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                isSearching -> SearchResultsList(
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
                isGenreSelected -> GenreGrid(
                    genre = selectedGenre!!,
                    items = genreResults,
                    loading = genreLoading,
                    isOnline = isOnline,
                    onCardClick = { entry ->
                        navController.navigate("detail/${entry.anilistId}")
                    },
                )
                else -> CarouselColumn(
                    mediaType = mediaType,
                    popular = popular,
                    trending = trending,
                    upcoming = upcoming,
                    topRated = topRated,
                    mangaReleases = mangaReleases,
                    isOnline = isOnline,
                    onCardClick = { entry ->
                        navController.navigate("detail/${entry.anilistId}")
                    },
                )
            }
        }
    }
}

/*
 * v1.2: Anime / Manga segmented control. Single line of Material3
 * `SingleChoiceSegmentedButtonRow` because that's the canonical
 * M3 idiom — three-tier toggle pill, accent-coloured selection,
 * stays above the search bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaTypeSegmentedControl(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf("ANIME", "MANGA")
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, opt ->
            SegmentedButton(
                selected = selected == opt,
                onClick = { onSelect(opt) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Accent.copy(alpha = 0.25f),
                ),
            ) {
                Text(if (opt == "ANIME") "Anime" else "Manga")
            }
        }
    }
}

private val GENRES: List<String> = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
    "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery",
    "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports",
    "Supernatural", "Thriller",
)

@Composable
private fun GenreChips(
    selected: String?,
    onSelect: (String?) -> Unit,
    searchActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val chipAlpha by animateFloatAsState(
        targetValue = if (searchActive) 0.4f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "genreChipStripAlpha",
    )
    LazyRow(
        modifier = modifier
            .padding(vertical = 4.dp)
            .graphicsLayer { this.alpha = chipAlpha },
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent.copy(alpha = 0.25f),
                ),
            )
        }
        items(GENRES, key = { it }) { genre ->
            FilterChip(
                selected = selected == genre,
                onClick = { onSelect(if (selected == genre) null else genre) },
                label = { Text(genre) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent.copy(alpha = 0.25f),
                ),
            )
        }
    }
}

@Composable
private fun GenreGrid(
    genre: String,
    items: List<AnimeEntry>?,
    loading: Boolean,
    isOnline: Boolean,
    onCardClick: (AnimeEntry) -> Unit,
) {
    when {
        loading && items == null -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        items.isNullOrEmpty() -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    !isOnline -> "Couldn't reach AniList.\nPull down to retry."
                    else -> "No $genre matches.\nTry another genre."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            gridItems(items, key = { it.anilistId }) { entry ->
                AnimePosterCard(
                    entry = entry,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCardClick(entry) },
                )
            }
        }
    }
}

@Composable
private fun CarouselColumn(
    mediaType: String,
    popular: List<AnimeEntry>?,
    trending: List<AnimeEntry>?,
    upcoming: List<AnimeEntry>?,
    topRated: List<AnimeEntry>?,
    mangaReleases: List<AnimeEntry>?,
    isOnline: Boolean,
    onCardClick: (AnimeEntry) -> Unit,
) {
    val allLoaded = popular != null && trending != null && upcoming != null && topRated != null
    val emptyAccent = if (mediaType == "MANGA") "manga" else "anime"
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (!allLoaded) {
            item(key = "loading_initial") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }

        // Manga-only: "Releasing manga" carousel sits at the very top.
        if (mediaType == "MANGA") {
            item(key = "manga_releases") {
                CarouselRow(
                    title = "Releasing manga",
                    items = mangaReleases.orEmpty(),
                    emptyText = "No manga releasing this season.",
                    onCardClick = onCardClick,
                )
            }
        }

        item(key = "popular") {
            CarouselRow(
                title = if (mediaType == "MANGA") "Popular this season" else "Popular this season",
                items = popular.orEmpty(),
                emptyText = if (isOnline) "No popular $emptyAccent right now." else "Couldn't reach AniList.",
                onCardClick = onCardClick,
            )
        }
        item(key = "trending") {
            CarouselRow(
                title = "Trending now",
                items = trending.orEmpty(),
                emptyText = "No trending $emptyAccent right now.",
                onCardClick = onCardClick,
            )
        }
        item(key = "upcoming") {
            CarouselRow(
                title = if (mediaType == "MANGA") "Coming soon" else "Coming soon",
                items = upcoming.orEmpty(),
                emptyText = "No upcoming releases.",
                onCardClick = onCardClick,
            )
        }
        item(key = "top_rated") {
            CarouselRow(
                title = "Top rated of all time",
                items = topRated.orEmpty(),
                emptyText = "No top-rated $emptyAccent right now.",
                onCardClick = onCardClick,
            )
        }
        item(key = "footer_spacer") { Spacer(Modifier.height(24.dp)) }
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
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
                        modifier = Modifier.width(140.dp),
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
        ) { CircularProgressIndicator() }
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
    val epLabel = if (entry.mediaType == "MANGA") {
        entry.episodes?.let { "  $it ch" } ?: ""
    } else {
        entry.episodes?.let { "  $it ep" } ?: ""
    }
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
