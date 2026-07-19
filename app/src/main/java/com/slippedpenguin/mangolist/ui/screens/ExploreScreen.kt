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
 * Explore — v1.1: discovery surface replacing the v0.x "Add" search-only
 * tab. Three layered modes under the search bar:
 *
 *   1. **Search bar query** wins outright: any time the user has typed
 *      ≥ 2 chars, the screen swaps to a vertical list of AniList search
 *      hits. The genre chip strip stays visible (so the user can pivot
 *      back) but its selection is ignored while searching.
 *
 *   2. **Genre chip selected** (search bar empty): the four carousels are
 *      hidden and a 2-column grid of `AnimePosterCard` renders for the
 *      chosen genre, fetched via AniList's `Page.media(genre_in: [...],
 *      sort: POPULARITY_DESC)`. Tap a tile routes to DetailScreen.
 *
 *   3. **No search, no genre chip** (the "All" chip is the implicit default):
 *      the four carousels render exactly as v1.0 shipped them.
 *
 * Each carousel fetches independently — failures in one don't block the
 * others. Pull-to-refresh re-runs all four carousel queries (and the
 * currently-selected genre query, if any) in parallel.
 *
 * The chip strip mirrors AniHyou: one `LazyRow` of `FilterChip`s, sticky
 * under the search bar. "All" clears `selectedGenre` so the carousels come
 * back. Unknown / blank chip strings short-circuit in `AniListClient.getByGenre`
 * rather than surface a sync error toast.
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
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var genreResults by remember { mutableStateOf<List<AnimeEntry>?>(null) }
    var genreLoading by remember { mutableStateOf(false) }
    var requestSeq by remember { mutableStateOf(0) }
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
            // Reload carousels AND re-fetch the current genre so a pull on
            // a genre-filtered grid replaces stale data. The `finally` keeps
            // the PullToRefreshBox spinner visible until both network calls
            // resolve — without it, `scope.launch { ... }` returns
            // synchronously and we'd flip `isRefreshing` off before the work
            // finishes, making the spinner disappear prematurely.
            scope.launch {
                try {
                    reloadCarousels()
                    selectedGenre?.let { genreResults = app.anilistClient.getByGenre(it) }
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    // Update the in-list set so "+ Add" -> "Open" state on search hits is
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

    // Genre chip -> fetch. Every chip change fires a fresh `getByGenre`
    // call. We deliberately don't cache results across chip selections —
    // the user expects a fresh list per chip, and the AniList request is
    // ~200 ms so request echo between chips feels instant.
    //
    // We key on both `selectedGenre` AND `isSearching`: while the search
    // bar owns the screen, chip taps are visible-but-inert (genre mode is
    // hidden in favour of SearchResultsList), so we short-circuit and
    // avoid launching a backend fetch whose result would be discarded.
    LaunchedEffect(selectedGenre, isSearching) {
        if (isSearching) return@LaunchedEffect
        val g = selectedGenre ?: run {
            genreResults = null
            genreLoading = false
            return@LaunchedEffect
        }
        // Token-guard against rapid chip taps: each fetch is stamped with the
        // current requestSeq, and only the most recent fetch may commit its
        // results. Without this, a slow earlier fetch (e.g. "Action") could
        // complete after a fast later one (e.g. "Romance") and overwrite the
        // newer results, leaving the grid out of sync with the chip state.
        val mySeq = ++requestSeq
        genreLoading = true
        val res = app.anilistClient.getByGenre(g)
        if (mySeq == requestSeq) {
            genreResults = res
            genreLoading = false
        }
    }

    val isSearching = query.trim().length >= 2
    val isGenreSelected = selectedGenre != null && !isSearching

    Column(modifier = Modifier.fillMaxSize()) {
        OfflineBanner()

        // Search bar — sticky at top so the chips / carousels / grid scroll under it.
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

        // Genre chip strip. Sticky under the search bar. "All" deselects.
        // While searching, the chips stay visible but their choice is
        // ignored (search bar takes precedence) and they dim to 40% alpha
        // so the inertness is visually obvious.
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

/*
 * Curated genre list — mirrors AniHyou's Discover chip strip. Each
 * label is an exact AniList genre string (case-insensitive on AniList's
 * side, but `AniListClient.getByGenre` canonicalises to Title Case).
 * Order matches AniHyou so muscle memory transfers.
 */
private val GENRES: List<String> = listOf(
    "Action",
    "Adventure",
    "Comedy",
    "Drama",
    "Ecchi",
    "Fantasy",
    "Horror",
    "Mahou Shoujo",
    "Mecha",
    "Music",
    "Mystery",
    "Psychological",
    "Romance",
    "Sci-Fi",
    "Slice of Life",
    "Sports",
    "Supernatural",
    "Thriller",
)

@Composable
private fun GenreChips(
    selected: String?,
    onSelect: (String?) -> Unit,
    searchActive: Boolean,
    modifier: Modifier = Modifier,
) {
    // While the search bar owns the screen, dim the chip strip so taps
    // visibly feel inert (the chip LaunchedEffect no-ops in `isSearching`).
    // The alpha animates over ~150ms so the transition feels deliberate
    // rather than snapping when the user types or clears the search bar.
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
        // Shimmer-while-loading mirrors v1.0's carousel loading state so the
        // genre chip doesn't feel slower than the carousels below it.
        loading && items == null -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        items.isNullOrEmpty() -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    !isOnline -> "Couldn't reach AniList.\nPull down to retry."
                    else -> "No $genre anime found on AniList.\nTry another genre."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        else -> LazyVerticalGrid(
            // Adaptive cells so the grid scales from a 320dp narrow phone
            // (1 or 2 columns) to a tablet (3+ columns) without horizontal
            // overflow. The hardcoded 140.dp poster card stays centred in
            // each cell so the visual density matches the LazyRow carousels
            // above.
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Each cell is sized by `GridCells.Adaptive(minSize = 160.dp)`,
            // so we pass `Modifier.fillMaxWidth()` to the card and it scales
            // 1:1 to the cell. The Card composable inside AnimePosterCard
            // already fills its parent's width, so the poster block grows on
            // tablets and shrinks on narrow phones without manual math.
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
