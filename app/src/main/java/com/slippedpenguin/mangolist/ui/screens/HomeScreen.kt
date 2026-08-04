package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.CoverImage
import com.slippedpenguin.mangolist.ui.components.OfflineBanner
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.BgCardHover
import com.slippedpenguin.mangolist.ui.theme.TextSecondary

/**
 * Home dashboard: a quick read of the library and a clear route into tier
 * ranking. v1.5.5 dropped the "Pick up where you left off" list and the
 * timestamped "Recent activity" list — activity now lives on the Profile
 * Activity tab, keeping Home a clean at-a-glance dashboard.
 */
@Composable
fun HomeScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val entries by app.database.animeDao().observeAll().collectAsState(initial = emptyList())
    val userName by app.tokenStore.userName.collectAsState(initial = null)

    val inProgress = remember(entries) {
        entries.count { it.status in listOf("watching", "paused", "repeating") }
    }
    val animeCount = remember(entries) { entries.count { it.mediaType == "ANIME" } }
    val mangaCount = remember(entries) { entries.count { it.mediaType == "MANGA" } }
    val rankedCount = remember(entries) { entries.count { it.tier != null } }
    val favorites = remember(entries) { entries.filter { it.favourite } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { OfflineBanner() }
        item {
            if (entries.isEmpty()) {
                WelcomeCard(
                    onProfile = { navController.navigate("profile") },
                    onExplore = { navController.navigate("anime?tab=1") },
                )
            } else {
                // v1.5.7: greeting header + compact metrics instead of the
                // old plain "Your library" title.
                HomeDashboard(
                    userName = userName,
                    total = entries.size,
                    inProgress = inProgress,
                    ranked = rankedCount,
                    animeCount = animeCount,
                    mangaCount = mangaCount,
                )
            }
        }

        // v1.5.7: favorites strip — tap a cover to open its detail screen.
        if (favorites.isNotEmpty()) {
            item {
                HomeFavoritesStrip(
                    favorites = favorites.take(12),
                    onNavigateDetail = { id, type -> navController.navigate("detail/$type/$id") },
                )
            }
        }

        // v1.5.2: tier shortcut moved above the fold so the tier list is
        // reachable without scrolling past every active title.
        item {
            TierShortcut(
                onClick = { navController.navigate("tiers") },
            )
        }
    }
}

@Composable
private fun WelcomeCard(onProfile: () -> Unit, onExplore: () -> Unit) {
    Card(
        modifier = Modifier.padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Accent)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Your anime corner, organized.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Connect AniList to bring your watchlist into MangoList, then track progress and build your tiers.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onProfile) {
                    Icon(Icons.Outlined.Person, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Connect")
                }
                OutlinedButton(onClick = onExplore) {
                    Icon(Icons.Outlined.Explore, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Explore")
                }
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    userName: String?,
    total: Int,
    inProgress: Int,
    ranked: Int,
    animeCount: Int,
    mangaCount: Int,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        // v1.5.7: time-of-day greeting + library subtitle (AniHyou-style).
        Text(
            text = if (userName != null) "${timeGreeting()}, $userName" else timeGreeting(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "$total titles · $animeCount anime · $mangaCount manga",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 3.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardMetric(total.toString(), "Titles", Modifier.weight(1f))
            DashboardMetric(inProgress.toString(), "In progress", Modifier.weight(1f))
            DashboardMetric(ranked.toString(), "Ranked", Modifier.weight(1f))
        }
    }
}

private fun timeGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11   -> "Good morning"
        in 12..16  -> "Good afternoon"
        in 17..21  -> "Good evening"
        else       -> "Good night"
    }
}

/*
 * HomeFavoritesStrip — v1.5.7. Horizontal row of favorite covers on Home.
 * Tap any cover to open its detail screen.
 */
@Composable
private fun HomeFavoritesStrip(
    favorites: List<AnimeEntry>,
    onNavigateDetail: (Int, String) -> Unit,
) {
    Column {
        SectionHeading(kicker = "Favorites", title = "Your top picks")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(favorites, key = { it.anilistId }) { entry ->
                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .clickable { onNavigateDetail(entry.anilistId, entry.mediaType) },
                ) {
                    CoverImage(
                        model = entry.cover,
                        contentDescription = entry.title,
                        label = entry.title,
                        modifier = Modifier
                            .size(width = 84.dp, height = 118.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardMetric(value: String, label: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = Accent, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun SectionHeading(kicker: String, title: String) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp)) {
        Text(
            text = kicker.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun TierShortcut(onClick: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = BgCardHover),
        shape = RoundedCornerShape(18.dp),
    ) {
        // v1.5.4: single-line shortcut — no subtitle, and the icon uses the
        // periwinkle Accent instead of the S-tier pink/red.
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, tint = Accent)
            }
            Text(
                text = "Build your tier list",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            Text("Open", color = Accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}
