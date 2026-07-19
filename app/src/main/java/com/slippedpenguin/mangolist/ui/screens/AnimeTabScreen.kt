package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.OfflineBanner
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
 * Pull-to-refresh syncs the AniList anime list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeTabScreen(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()
    val accessToken by app.tokenStore.accessToken.collectAsState(initial = null)
    val userId by app.tokenStore.userId.collectAsState(initial = null)

    var selectedTab by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    val tabs = listOf("Watchlist", "Explore", "Airing")

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

        // Pull-to-refresh wrapper: syncs the user's ANIME list from AniList.
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
                0 -> AnimeWatchlistContent(navController)
                1 -> AnimeExploreContent(navController)
                2 -> AiringScreen(
                    onNavigateDetail = { id -> navController.navigate("detail/ANIME/$id") },
                )
            }
        }
    }
}

/*
 * Lightweight wrapper that shows the WatchlistScreen content filtered
 * to mediaType=ANIME. We reuse the existing WatchlistScreen composable
 * but force `selectedMediaType` to "ANIME" and hide the All/Anime/Manga
 * chip strip so the user can't switch to manga from the Anime tab.
 */
@Composable
private fun AnimeWatchlistContent(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val entries by app.database.animeDao().observeAll()
        .collectAsState(initial = emptyList())

    // Force ANIME filter; hide the media-type chip strip.
    val filtered = remember(entries) { entries.filter { it.mediaType == "ANIME" } }

    if (filtered.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (entries.isEmpty()) "No anime in your list" else "No anime in this view",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Pull to sync from AniList or switch to the Explore tab to find anime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    } else {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            androidx.compose.foundation.lazy.items(filtered, key = { it.anilistId }) { entry ->
                com.slippedpenguin.mangolist.ui.components.AnimeCard(
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
 * Lightweight wrapper that shows the ExploreScreen content forced to
 * mediaType=ANIME. Uses the same carousel logic but with a locked
 * segmented-control value set to "ANIME".
 */
@Composable
private fun AnimeExploreContent(navController: NavController) {
    // Just call ExploreScreen — it already handles type selection.
    // We use the default mediaType which is "ANIME".
    ExploreScreen(navController, forcedMediaType = "ANIME")
}
