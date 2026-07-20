package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.ui.components.AnimeCard
import com.slippedpenguin.mangolist.ui.components.OfflineBanner
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.TextSecondary

/*
 * Home — v1.4 Anihyou-style landing tab.
 *
 *   - **Currently watching/reading** — the user's active (watching / paused /
 *     repeating) entries sorted by most recently updated. Shows up to 12
 *     items so the home feed stays tight.
 *   - **Recent activity** — last 6 entries that were edited (sorted by
 *     updatedAt DESC) with a progress + status line.
 *   - **Tiers link** — a prominent card that navigates to the full TiersScreen
 *     (still available as a friend-routed composable, not a bottom tab).
 *
 * Tapping any card opens the detail screen. The tier link fires
 * `navController.navigate("tiers")`.
 */
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val dao = remember { app.database.animeDao() }
    val entries by dao.observeAll().collectAsState(initial = emptyList())

    val inProgress = remember(entries) {
        entries
            .filter { it.status in listOf("watching", "paused", "repeating") }
            .sortedByDescending { it.updatedAt }
            .take(12)
    }
    val recent = remember(entries) {
        entries.sortedByDescending { it.updatedAt }.take(6)
    }
    val hasTiers = remember(entries) { entries.any { it.tier != null } }

    if (entries.isEmpty()) {
        // Use Imported Icons.Outlined.Visibility from material-icons-core
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Text(
                    text = "Welcome to MangoList",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Sign in on the Profile tab and sync to pull your AniList data. Your watchlist and tiers will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { OfflineBanner() }

            // --- Currently watching/reading ---
            if (inProgress.isNotEmpty()) {
                item {
                    Text(
                        text = "In Progress",
                        style = MaterialTheme.typography.titleMedium,
                        color = Accent,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                items(inProgress, key = { "home_ip_${it.anilistId}" }) { entry ->
                    AnimeCard(
                        entry = entry,
                        onClick = { navController.navigate("detail/${entry.mediaType}/${entry.anilistId}") },
                        showSyncPending = true,
                        showFavorite = true,
                    )
                }
            }

            // --- Recent activity ---
            if (recent.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.titleMedium,
                        color = Accent,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                items(recent, key = { "home_rc_${it.anilistId}" }) { entry ->
                    AnimeCard(
                        entry = entry,
                        onClick = { navController.navigate("detail/${entry.mediaType}/${entry.anilistId}") },
                        showRelativeTimestamp = true,
                        showTier = false,
                    )
                }
            }

            // --- Tiers shortcut ---
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    onClick = { navController.navigate("tiers") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Tier Rankings",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (hasTiers)
                                "View your S–D tier rankings. Long-press any card to assign a tier."
                            else
                                "No entries ranked yet. Tap to start building your tier list.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
