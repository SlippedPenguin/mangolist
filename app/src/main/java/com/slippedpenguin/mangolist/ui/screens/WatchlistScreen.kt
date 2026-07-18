package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.AnimeCard
import kotlinx.coroutines.launch

/*
 * Watchlist — observes the full anime_entries table; tapping a card opens
 * Detail.
 *
 * v0.8.5 (Anihyou parity): horizontal status-segmented tabs sit above the
 * list. Each tab shows its current count ("Watching 5") so users can see
 * distribution at a glance without scrolling. `selectedStatus == null` is
 * the All sentinel.
 *
 * The `observeAll` Flow still drives updates — filtering is local to the
 * scope, so a sync push that lands in Room re-runs the filter automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()
    val entries by app.database.animeDao().observeAll()
        .collectAsState(initial = emptyList())
    val accessToken by app.tokenStore.accessToken.collectAsState(initial = null)
    val userId      by app.tokenStore.userId.collectAsState(initial = null)

    var isRefreshing by remember { mutableStateOf(false) }
    // null = All. The detected statuses in HANDOFF.md are
    // watching / completed / plan / dropped / paused / repeating.
    var selectedStatus by remember { mutableStateOf<String?>(null) }

    val counts = remember(entries) { statusCounts(entries) }
    val filtered = remember(entries, selectedStatus) {
        if (selectedStatus == null) entries
        else entries.filter { it.status == selectedStatus }
    }
    val selectedIndex = remember(selectedStatus) {
        statusTabs.indexOfFirst { it.key == selectedStatus }.coerceAtLeast(0)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            val tok = accessToken
            val id  = userId
            if (tok.isNullOrBlank() || id.isNullOrBlank()) return@PullToRefreshBox
            scope.launch {
                isRefreshing = true
                try {
                    val result = app.anilistClient.syncUserList(tok, id.toInt())
                    if (result.entries != null) {
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
        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.LibraryAdd,
                title = "Your watchlist is empty",
                subtitle = "Head to the Add tab to find your first anime. Pull to sync when you're signed in.",
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 16.dp,
                ) {
                    statusTabs.forEachIndexed { idx, tab ->
                        Tab(
                            selected = idx == selectedIndex,
                            onClick = { selectedStatus = tab.key },
                            text = {
                                Text(
                                    text = "${tab.label} ${counts[tab.key] ?: 0}",
                                )
                            },
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No anime in this list",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Switch tabs to see other anime. Use the Status dialog on the Detail screen to assign a status.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
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
                                onClick = { navController.navigate("detail/${entry.anilistId}") },
                                showSyncPending = true,
                                showRelativeTimestamp = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

/*
 * Status tabs. `key == null` is the All sentinel — picks the filter
 * branch that shows every entry. The order mirrors the Anihyou /
 * MyAnimeList convention: All first, then active, then completed,
 * then dropped, then paused, then repeating.
 */
private data class StatusTab(val key: String?, val label: String)
private val statusTabs = listOf(
    StatusTab(key = null,        label = "All"),
    StatusTab(key = "watching",  label = "Watching"),
    StatusTab(key = "completed", label = "Completed"),
    StatusTab(key = "plan",      label = "Planning"),
    StatusTab(key = "dropped",   label = "Dropped"),
    StatusTab(key = "paused",    label = "Paused"),
    StatusTab(key = "repeating", label = "Repeating"),
)

/*
 * statusCounts — count per status + total at the `null` key. Lets the
 * tab labels show their bucket size without each tab recomputing the
 * whole list. Grouped on the entries list once per dataset change
 * (wrapped in `remember(entries)` at the call site).
 */
private fun statusCounts(entries: List<AnimeEntry>): Map<String?, Int> {
    val map = HashMap<String?, Int>()
    map[null] = entries.size
    for (e in entries) {
        map[e.status] = (map[e.status] ?: 0) + 1
    }
    return map
}

/*
 * EmptyState — centred icon + title + subtitle, used when the user's
 * local list has zero rows (not synced yet, or genuinely empty).
 * v0.8.5: re-added because the status-segmented rewrite accidentally
 * dropped the helper while keeping the call site.
 */
@Composable
internal fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
