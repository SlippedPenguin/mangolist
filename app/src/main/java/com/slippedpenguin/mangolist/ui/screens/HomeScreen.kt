package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
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
import com.slippedpenguin.mangolist.ui.theme.Accent2
import com.slippedpenguin.mangolist.ui.theme.BorderSubtle
import com.slippedpenguin.mangolist.ui.theme.SurfaceContainerHigh
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.brandGradient
import com.slippedpenguin.mangolist.ui.theme.brandGradientSoft

/*
 * Home — v1.8 redesign.
 *
 * The old dashboard (boxed metric cards + generic shortcut card) read like
 * a settings screen. The new Home leads with identity:
 *
 *   1. **Hero** — big Bebas greeting + one-line library summary.
 *   2. **Stat row** — three inline numbers separated by hairlines (no cards).
 *   3. **Continue strip** — active titles as posters (falls back to
 *      Favorites, then the tier banner fills the space).
 *   4. **Tier banner** — the brand-gradient call to action into the tier list.
 *
 * Same data sources as v1.5.7: one observeAll() Flow, greeting by hour.
 */
@Composable
fun HomeScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val entries by app.database.animeDao().observeAll().collectAsState(initial = emptyList())
    val userName by app.tokenStore.userName.collectAsState(initial = null)

    val inProgress = remember(entries) {
        entries.filter { it.status in listOf("watching", "paused", "repeating") }
    }
    val animeCount = remember(entries) { entries.count { it.mediaType == "ANIME" } }
    val mangaCount = remember(entries) { entries.count { it.mediaType == "MANGA" } }
    val rankedCount = remember(entries) { entries.count { it.tier != null } }
    val favorites = remember(entries) { entries.filter { it.favourite } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            // v1.9: status-bar inset padding — the old TopAppBar used to
            // supply this; without it the greeting rides up behind the
            // camera cutout on tall devices.
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
    ) {
        item { OfflineBanner() }

        if (entries.isEmpty()) {
            item {
                WelcomeCard(
                    onProfile = { navController.navigate("profile") },
                    onExplore = { navController.navigate("anime?tab=1") },
                )
            }
        } else {
            item {
                HeroGreeting(
                    userName = userName,
                    summary = "$animeCount anime · $mangaCount manga",
                )
            }
            item {
                StatRow(
                    stats = listOf(
                        Triple(entries.size.toString(), "Titles", null),
                        Triple(inProgress.size.toString(), "In progress", null),
                        Triple(rankedCount.toString(), "Ranked", null),
                    ),
                )
            }

            // Continue strip: active titles first; favorites as fallback.
            val continueItems = if (inProgress.isNotEmpty()) inProgress else favorites
            if (continueItems.isNotEmpty()) {
                item {
                    PosterStrip(
                        kicker = if (inProgress.isNotEmpty()) "Continue" else "Favorites",
                        items = continueItems.take(12),
                        onNavigateDetail = { id, type -> navController.navigate("detail/$type/$id") },
                    )
                }
            }
        }

        item {
            TierBanner(
                ranked = rankedCount,
                onClick = { navController.navigate("tiers") },
            )
        }
    }
}

@Composable
private fun HeroGreeting(userName: String?, summary: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            text = if (userName != null) "${timeGreeting()}, $userName" else timeGreeting(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
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
 * StatRow — three inline numbers divided by hairlines. No card chrome:
 * on a true-black canvas the numbers ARE the design.
 */
@Composable
private fun StatRow(stats: List<Triple<String, String, Unit?>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { index, (value, label, _) ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(BorderSubtle),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Accent,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun PosterStrip(
    kicker: String,
    items: List<AnimeEntry>,
    onNavigateDetail: (Int, String) -> Unit,
) {
    Column {
        Text(
            text = kicker.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
            modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.anilistId }) { entry ->
                Column(
                    modifier = Modifier
                        .width(92.dp)
                        .clickable { onNavigateDetail(entry.anilistId, entry.mediaType) },
                ) {
                    CoverImage(
                        model = entry.cover,
                        contentDescription = entry.title,
                        label = entry.title,
                        modifier = Modifier
                            .size(width = 92.dp, height = 130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.height(5.dp))
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

/*
 * TierBanner — the one gradient element on Home. A full-bleed rounded card
 * in the brand gradient with Bebas type: the tier list is this app's
 * signature feature, so its entry point gets the loudest surface.
 */
@Composable
private fun TierBanner(ranked: Int, onClick: () -> Unit) {
    val banner: Brush = brandGradient()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .background(banner)
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TIER LIST",
                    style = MaterialTheme.typography.headlineSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                )
                Text(
                    text = if (ranked > 0) "$ranked ranked — drag to perfect it" else "Rank what you've finished",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun WelcomeCard(onProfile: () -> Unit, onExplore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(brandGradientSoft()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Accent)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Your anime corner,\norganized.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Connect AniList to bring your watchlist into MangoList, then track progress and build your tiers.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onProfile,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = androidx.compose.ui.graphics.Color(0xFF0A0A14)),
            ) {
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
