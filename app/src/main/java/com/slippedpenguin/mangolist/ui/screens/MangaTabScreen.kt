package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.ui.components.AnimeCard
import com.slippedpenguin.mangolist.ui.components.OfflineBanner
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/*
 * MangaTabScreen — v1.4 dedicated manga tab.
 *
 * Sub-tabs:
 *   - Watchlist — filtered to mediaType=MANGA
 *   - Explore   — manga-only discovery carousels + search
 *
 * Pull-to-refresh syncs the AniList manga list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaTabScreen(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()
    val accessToken by app.tokenStore.accessToken.collectAsState(initial = null)
    val userId by app.tokenStore.userId.collectAsState(initial = null)

    var selectedTab by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    val tabs = listOf("Watchlist", "Explore")

    Column(modifier = Modifier.fillMaxSize()) {
        OfflineBanner()

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                val tok = accessToken
                val id = userId
                if (tok.isNullOrBlank() || id.isNullOrBlank()) {
                    isRefreshing = false
                    return@PullToRefreshBox
                }
                scope.launch {
                    isRefreshing = true
                    try {
                        val result = app.anilistClient.syncUserList(tok, id.toInt(), "MANGA")
                        if (result.entries != null && result.entries.isNotEmpty()) {
                            val existing = app.database.animeDao().getAll().associateBy { it.anilistId }
                            val merged = result.entries.map { it.preserveLocalFields(existing[it.anilistId]) }
                            app.database.animeDao().upsertAll(merged)
                        }
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            when (selectedTab) {
                0 -> MangaWatchlistContent(navController)
                1 -> MangaExploreContent(navController)
            }
        }
    }
}

/*
 * Lightweight wrapper that shows manga-only watchlist.
 */
@Composable
private fun MangaWatchlistContent(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val entries by app.database.animeDao().observeAll()
        .collectAsState(initial = emptyList())

    val filtered = remember(entries) { entries.filter { it.mediaType == "MANGA" } }

    if (filtered.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (entries.isEmpty()) "No manga in your list" else "No manga in this view",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Pull to sync from AniList or switch to the Explore tab to find manga.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(filtered, key = { it.anilistId }) { entry ->
                AnimeCard(
                    entry = entry,
                    onClick = { navController.navigate("detail/${entry.mediaType}/${entry.anilistId}") },
                    showSyncPending = true,
                    showRelativeTimestamp = true,
                    showFavorite = true,
                )
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
