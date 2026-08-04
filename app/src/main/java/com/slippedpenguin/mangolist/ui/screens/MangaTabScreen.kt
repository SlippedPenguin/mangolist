package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * MangaTabScreen — v1.4 dedicated manga tab.
 *
 * Sub-tabs:
 *   - Watchlist — filtered to mediaType=MANGA
 *   - Explore   — manga-only discovery carousels + search
 *
 * v1.5.6: centered pill tabs, per-sub-tab SaveableStateHolder, sort +
 * corner filter controls in the watchlist header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaTabScreen(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()
    val accessToken by app.tokenStore.accessToken.collectAsState(initial = null)
    val userId by app.tokenStore.userId.collectAsState(initial = null)

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    val tabs = listOf("Watchlist", "Explore")
    val tabHolder = rememberSaveableStateHolder()

    Column(modifier = Modifier.fillMaxSize()) {
        OfflineBanner()

        CenteredPillTabs(
            tabs = tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )

        when (selectedTab) {
            0 -> tabHolder.SaveableStateProvider("manga_watchlist") {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        val tok = accessToken
                        val id = userId
                        if (tok.isNullOrBlank() || id.isNullOrBlank()) return@PullToRefreshBox
                        scope.launch {
                            isRefreshing = true
                            try {
                                val result = app.anilistClient.syncUserList(tok, id.toInt(), "MANGA")
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
                    MangaWatchlistContent(navController = navController)
                }
            }
            1 -> tabHolder.SaveableStateProvider("manga_explore") {
                MangaExploreContent(navController)
            }
        }
    }
}

/*
 * Lightweight wrapper that shows manga-only watchlist.
 */
@Composable
private fun MangaWatchlistContent(
    navController: NavController,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val entries by app.database.animeDao().observeAll()
        .collectAsState(initial = emptyList())

    var selectedStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(LibrarySortMode.UPDATED) }
    val mangaEntries = remember(entries) { entries.filter { it.mediaType == "MANGA" } }
    val counts = remember(mangaEntries) {
        buildMap<String?, Int> {
            put(null, mangaEntries.size)
            put(FAVORITES_FILTER, mangaEntries.count { it.favourite })
            mangaEntries.groupingBy { it.status }.eachCount().forEach { (status, count) -> put(status, count) }
        }
    }
    val filtered = remember(mangaEntries, selectedStatus) {
        when (selectedStatus) {
            null -> mangaEntries
            FAVORITES_FILTER -> mangaEntries.filter { it.favourite }
            else -> mangaEntries.filter { it.status == selectedStatus }
        }
    }
    val sorted = remember(filtered, sortMode) { sortLibraryEntries(filtered, sortMode) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                LibraryHeader(
                    title = "Your manga",
                    subtitle = "Pick up your next chapter anytime.",
                    total = mangaEntries.size,
                    active = mangaEntries.count { it.status in listOf("watching", "paused", "repeating") },
                    planned = mangaEntries.count { it.status == "plan" },
                    selectedStatus = selectedStatus,
                    counts = counts,
                    onSelectStatus = { selectedStatus = it },
                    sortMode = sortMode,
                    onSortModeChange = { sortMode = it },
                    mediaType = "MANGA",
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
                                text = if (mangaEntries.isEmpty()) "Your manga library is empty" else "Nothing in this status",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = if (mangaEntries.isEmpty()) "Sync AniList or open Explore to find your next series." else "Try another filter or update a title's status.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            } else {
                items(sorted, key = { it.anilistId }) { entry ->
                    AnimeCard(
                        entry = entry,
                        onClick = { navController.navigate("detail/${entry.mediaType}/${entry.anilistId}") },
                        showSyncPending = true,
                        showFavorite = true,
                    )
                }
            }
        }
    }
}

/*
 * Lightweight wrapper that shows ExploreScreen forced to MANGA.
 */
@Composable
private fun MangaExploreContent(navController: NavController) {
    ExploreScreen(navController, forcedMediaType = "MANGA")
}
