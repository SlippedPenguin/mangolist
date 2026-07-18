package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.slippedpenguin.mangolist.ui.components.AnimeCard
import kotlinx.coroutines.launch

/*
 * Watchlist — observes the full anime_entries table; tapping a card opens
 * Detail. v1 wires the DataSource through AnimeApp → AnimeDatabase. ViewModels
 * land in v1.1.
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(entries, key = { it.anilistId }) { entry ->
                    AnimeCard(
                        entry = entry,
                        onClick = { navController.navigate("detail/${entry.anilistId}") },
                    )
                }
            }
        }
    }
}

/*
 * Shared empty-state placeholder used across screens. Centred icon + text.
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
