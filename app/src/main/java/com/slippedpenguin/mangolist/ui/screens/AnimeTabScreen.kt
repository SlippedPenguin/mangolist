package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.slippedpenguin.mangolist.data.applyProgressDelta
import com.slippedpenguin.mangolist.ui.components.AnimePosterCard
import com.slippedpenguin.mangolist.ui.components.LibraryViewMode
import com.slippedpenguin.mangolist.ui.components.SwipeableRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.ui.components.AnimeCard
import com.slippedpenguin.mangolist.ui.components.CenteredPillTabs
import com.slippedpenguin.mangolist.ui.components.FAVORITES_FILTER
import com.slippedpenguin.mangolist.ui.components.LibraryHeader
import com.slippedpenguin.mangolist.ui.components.LibrarySortMode
import com.slippedpenguin.mangolist.ui.components.OfflineBanner
import com.slippedpenguin.mangolist.ui.components.sortLibraryEntries
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/*
 * AnimeTabScreen — v1.4 dedicated anime tab.
 *
 * Sub-tabs:
 *   - Watchlist — filtered to mediaType=ANIME
 *   - Explore   — anime-only discovery carousels + search
 *   - Airing    — 7-day schedule (anime-specific)
 *
 * v1.5.6: the sub-tab row is now a centered pill row (AniHyou style), each
 * sub-tab keeps its own scroll/filter state via a SaveableStateHolder, and
 * the watchlist gained sort + corner filter controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeTabScreen(
    navController: NavController,
    initialTab: Int = 0,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()
    val accessToken by app.tokenStore.accessToken.collectAsState(initial = null)
    val userId by app.tokenStore.userId.collectAsState(initial = null)

    var selectedTab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, 2)) }
    var isRefreshing by remember { mutableStateOf(false) }

    val tabs = listOf("Watchlist", "Explore", "Airing")
    val tabHolder = rememberSaveableStateHolder()

    Column(modifier = Modifier.fillMaxSize()) {
        OfflineBanner()

        CenteredPillTabs(
            tabs = tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )

        when (selectedTab) {
            0 -> tabHolder.SaveableStateProvider("anime_watchlist") {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        val tok = accessToken
                        val id = userId
                        if (tok.isNullOrBlank() || id.isNullOrBlank()) return@PullToRefreshBox
                        scope.launch {
                            isRefreshing = true
                            try {
                                val result = app.anilistClient.syncUserList(tok, id.toInt(), "ANIME")
                                if (result.entries != null && result.entries.isNotEmpty()) {
                                    app.database.animeDao().mergePullResults(result.entries)
                                }
                            } finally {
                                isRefreshing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AnimeWatchlistContent(navController = navController)
                }
            }
            1 -> tabHolder.SaveableStateProvider("anime_explore") {
                AnimeExploreContent(navController)
            }
            2 -> tabHolder.SaveableStateProvider("anime_airing") {
                AiringScreen(
                    onNavigateDetail = { id -> navController.navigate("detail/ANIME/$id") },
                )
            }
        }
    }
}

/*
 * Lightweight wrapper that shows the anime-only watchlist.
 */
@Composable
private fun AnimeWatchlistContent(
    navController: NavController,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val dao = app.database.animeDao()  // v1.9: for one-tap progress writes
    val scope = rememberCoroutineScope()  // v1.9: swipe/quick-increment writes
    val entries by app.database.animeDao().observeAll()
        .collectAsState(initial = emptyList())
    var selectedStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(LibrarySortMode.UPDATED) }
    // v1.9: ManGo-style poster-grid view toggle (list is the default).
    var viewMode by rememberSaveable { mutableStateOf(LibraryViewMode.LIST) }

    val animeEntries = remember(entries) { entries.filter { it.mediaType == "ANIME" } }
    val counts = remember(animeEntries) {
        buildMap<String?, Int> {
            put(null, animeEntries.size)
            put(FAVORITES_FILTER, animeEntries.count { it.favourite })
            animeEntries.groupingBy { it.status }.eachCount().forEach { (status, count) -> put(status, count) }
        }
    }
    val filtered = remember(animeEntries, selectedStatus) {
        when (selectedStatus) {
            null -> animeEntries
            FAVORITES_FILTER -> animeEntries.filter { it.favourite }
            else -> animeEntries.filter { it.status == selectedStatus }
        }
    }
    val sorted = remember(filtered, sortMode) { sortLibraryEntries(filtered, sortMode) }
    val gridRows = remember(sorted) { sorted.chunked(3) }  // v1.9 grid rows

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),  // v1.8: clear the floating dock
        ) {
            item {
                LibraryHeader(
                    title = "Your anime",
                    subtitle = "Keep your next watch in reach.",
                    total = animeEntries.size,
                    active = animeEntries.count { it.status in listOf("watching", "paused", "repeating") },
                    planned = animeEntries.count { it.status == "plan" },
                    selectedStatus = selectedStatus,
                    counts = counts,
                    onSelectStatus = { selectedStatus = it },
                    sortMode = sortMode,
                    onSortModeChange = { sortMode = it },
                    viewMode = viewMode,
                    onViewModeChange = { viewMode = it },
                    mediaType = "ANIME",
                )
            }
            if (sorted.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (animeEntries.isEmpty()) "Your anime library is empty" else "Nothing in this status",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = if (animeEntries.isEmpty()) "Sync AniList or open Explore to find your next series." else "Try another filter or update a title's status.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            } else if (viewMode == LibraryViewMode.GRID) {
                // v1.9: ManGo-style poster grid, rendered as 3-up rows
                // inside the one scroll container (keeps the header, the
                // empty state, and dock clearance in every mode). Posters
                // route to Detail; swipe/quick-increment are list-mode
                // features — grid cells are pure browse.
                items(gridRows.size, key = { rowIdx -> "grid_row_$rowIdx" }) { rowIdx ->
                    val rowEntries = gridRows[rowIdx]
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowEntries.forEach { entry ->
                            AnimePosterCard(
                                entry = entry,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate("detail/${entry.mediaType}/${entry.anilistId}") },
                            )
                        }
                        repeat(3 - rowEntries.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                items(sorted, key = { it.anilistId }) { entry ->
                    // v1.9: swipe right +1 / left −1 + tap-to-+1 pill, both
                    // sharing Detail's write semantics via applyProgressDelta.
                    SwipeableRow(
                        onSwipeRight = {
                            scope.launch { applyProgressDelta(dao, app, entry, +1) }
                        },
                        onSwipeLeft = {
                            scope.launch { applyProgressDelta(dao, app, entry, -1) }
                        },
                    ) {
                        AnimeCard(
                            entry = entry,
                            onClick = { navController.navigate("detail/${entry.mediaType}/${entry.anilistId}") },
                            showSyncPending = true,
                            showFavorite = true,
                            onQuickIncrement = {
                                scope.launch { applyProgressDelta(dao, app, entry, +1) }
                            },
                        )
                    }
                }
            }
        }
    }
}

/*
 * Lightweight wrapper that shows ExploreScreen forced to ANIME.
 */
@Composable
private fun AnimeExploreContent(navController: NavController) {
    ExploreScreen(navController, forcedMediaType = "ANIME")
}
