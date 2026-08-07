package com.slippedpenguin.mangolist.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import android.widget.Toast
import coil.compose.AsyncImage
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.BuildConfig
import com.slippedpenguin.mangolist.data.ActivityItem
import com.slippedpenguin.mangolist.data.ScoreDisplay
import com.slippedpenguin.mangolist.data.ScoreScale
import com.slippedpenguin.mangolist.data.SyncDiagnostics
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.CenteredPillTabs
import com.slippedpenguin.mangolist.ui.components.CoverImage
import com.slippedpenguin.mangolist.ui.components.OfflineBanner
import com.slippedpenguin.mangolist.ui.components.StatusIcon
import com.slippedpenguin.mangolist.ui.components.statusFilterLabel
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.BgInput
import com.slippedpenguin.mangolist.ui.theme.StatusCompleted
import com.slippedpenguin.mangolist.ui.theme.StatusDropped
import com.slippedpenguin.mangolist.ui.theme.StatusPlan
import com.slippedpenguin.mangolist.ui.theme.StatusWatching
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.tierColor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale

/*
 * Profile — tabbed AniHyou-style profile: Overview (quick stats + favorites
 * + account), Activity (live AniList feed), Stats (summary + bar breakdowns),
 * Settings (About / sync diagnostics).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()
    val userName by app.tokenStore.userName.collectAsState(initial = null)
    val accessToken by app.tokenStore.accessToken.collectAsState(initial = null)
    val userId by app.tokenStore.userId.collectAsState(initial = null)
    val avatarUrl by app.tokenStore.avatarUrl.collectAsState(initial = null)
    val scoreScale by app.tokenStore.scoreScale.collectAsState(initial = ScoreScale.Default)
    val entries  by app.database.animeDao().observeAll()
        .collectAsState(initial = emptyList())

    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            try {
                val tok = accessToken
                val id  = userId
                if (!tok.isNullOrBlank() && !id.isNullOrBlank()) {
                    // v1.3: pull both ANIME and MANGA lists in parallel.
                    // Previously only ANIME was synced here, which is why
                    // manga never showed on the Watch tab after a Profile
                    // pull-to-refresh.
                    val (animeResult, mangaResult) = awaitAll(
                        async { app.anilistClient.syncUserList(tok, id.toInt(), "ANIME") },
                        async { app.anilistClient.syncUserList(tok, id.toInt(), "MANGA") },
                    )
                    SyncDiagnostics.summarizePull(animeResult, mangaResult)
                    val combined = (animeResult.entries.orEmpty() + mangaResult.entries.orEmpty())
                    if (combined.isNotEmpty()) {
                        app.database.animeDao().mergePullResults(combined)
                    }
                    val firstErr = animeResult.error ?: mangaResult.error
                    if (firstErr != null) {
                        Toast.makeText(
                            context,
                            if (firstErr.length > 150) firstErr.take(150) + "…" else firstErr,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    val stats = remember(entries) { computeLocalStats(entries) }

    // v1.5.4: AniHyou-style tabbed profile — identity + account actions in
    // Overview, breakdowns in Stats, and the sync debugger hidden inside
    // Settings instead of squatting on every scroll.
    // v1.5.5: an Activity tab now owns the "Edited X ago" timestamps that
    // used to sit on every watchlist card, and Settings splits into
    // General / Debug sub-tabs so the debugger is tucked away.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsTab by rememberSaveable { mutableIntStateOf(0) }

    // v1.5.6: icon-driven profile header + centered pill tab row (AniHyou
    // style), and a SaveableStateHolder so each tab keeps its own scroll
    // position + state when you flip between Overview / Activity / Stats /
    // Settings and back.
    val tabHolder = rememberSaveableStateHolder()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (!isRefreshing) isRefreshing = true },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OfflineBanner()

            // Header row — avatar + name. v1.5.7: the settings gear was
            // removed because the Settings tab (gear icon) already sits in
            // the tab row below — two gears read as a broken header.
            ProfileHeaderRow(
                avatarUrl = avatarUrl,
                userName = userName,
            )

            CenteredPillTabs(
                tabs = listOf(
                    "Overview",
                    "Activity",
                    "Stats",
                    "Settings",
                ),
                icons = listOf(
                    Icons.Outlined.Person,
                    Icons.Outlined.History,
                    Icons.Outlined.BarChart,
                    Icons.Outlined.Settings,
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                // v1.5.8: pure icons — the labels were dropped from the pill
                // row so the profile header reads clean and icon-driven.
                showLabels = false,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            when (selectedTab) {
                0 -> Box(modifier = Modifier.weight(1f)) {
                    tabHolder.SaveableStateProvider("overview") {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    // v1.5.7: Overview is no longer a bare greeting + two
                    // buttons. It now leads with quick stats and favorites
                    // (AniHyou-style), with account actions at the bottom.

                    // Greeting — small kicker + name.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "PROFILE",
                            style = MaterialTheme.typography.labelMedium,
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            text = if (userName == null) "Sign in to sync your lists" else "Hi, $userName",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Quick stats — episodes, days watched, mean score.
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            QuickStat("${stats.episodesWatched}", "Episodes", Modifier.weight(1f))
                            QuickStat(formatDays(stats.daysWatched), "Days watched", Modifier.weight(1f))
                            QuickStat(formatMean(stats.personalMean, scoreScale), "Mean score", Modifier.weight(1f))
                        }
                    }

                    // Favorites — tap a cover to open its detail screen.
                    val favorites = remember(entries) { entries.filter { it.favourite } }
                    if (favorites.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                text = "Favorites",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            FavoritesRow(
                                favorites = favorites.take(12),
                                onNavigateDetail = { id, type -> navController.navigate("detail/$type/$id") },
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Account actions live in Overview.
                    if (userName == null) {
                        Button(
                            onClick = {
                                val intent = CustomTabsIntent.Builder().build()
                                intent.launchUrl(context, Uri.parse(buildAniListAuthorizeUrl()))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Log in with AniList") }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val token = accessToken
                                    val id = userId
                                    if (token.isNullOrBlank() || id.isNullOrBlank()) {
                                        Toast.makeText(context, "Not signed in", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    Toast.makeText(context, "Syncing...", Toast.LENGTH_SHORT).show()
                                    val (animeResult, mangaResult) = awaitAll(
                                        async { app.anilistClient.syncUserList(token, id.toInt(), "ANIME") },
                                        async { app.anilistClient.syncUserList(token, id.toInt(), "MANGA") },
                                    )
                                    SyncDiagnostics.summarizePull(animeResult, mangaResult)
                                    val combined = (animeResult.entries.orEmpty() + mangaResult.entries.orEmpty())
                                    if (combined.isNotEmpty()) {
                                        app.database.animeDao().mergePullResults(combined)
                                        Toast.makeText(context, "Synced ${combined.size} entries", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val raw = animeResult.error ?: mangaResult.error ?: "Sync failed"
                                        val msg = if (raw.length > 150) raw.take(150) + "…" else raw
                                        Toast.makeText(context, "Sync failed: $msg", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Sync now") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    app.tokenStore.clear()
                                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Sign out", color = StatusDropped) }
                    }
                }
                }
                }
                1 -> Box(modifier = Modifier.weight(1f)) {
                    tabHolder.SaveableStateProvider("activity") {
                        ActivityTab(
                            accessToken = accessToken,
                            userId = userId,
                            userName = userName,
                            avatarUrl = avatarUrl,
                            isRefreshing = isRefreshing,
                            entries = entries,
                            onNavigateDetail = { id, type -> navController.navigate("detail/$type/$id") },
                        )
                    }
                }
                2 -> Box(modifier = Modifier.weight(1f)) {
                    tabHolder.SaveableStateProvider("stats") {
                    Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // v1.5.8: revamped stats — one clean summary card plus
                    // AniHyou-style percentage-bar breakdowns. The duplicated
                    // "AniList stats" card (which repeated the local numbers)
                    // and the community-mean row are gone; the year split was
                    // dropped as noise. The rest reads as a single glance.

                    // Your stats
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = "YOUR STATS",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                letterSpacing = 2.sp,
                            )
                            StatRow(
                                label = "Episodes watched",
                                value = stats.episodesWatched.toString(),
                            )
                            StatRow(
                                label = "Days watched",
                                value = formatDays(stats.daysWatched),
                                hint = "estimated from format-aware durations",
                            )
                            StatRow(
                                label = "Mean score",
                                value = formatMean(stats.personalMean, scoreScale),
                                hint = if (stats.personalMean == null) "Rate your anime to see your mean." else null,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Tier breakdown
                    if (stats.tierCounts.isNotEmpty()) {
                        val total = stats.tierCounts.sumOf { it.second }
                        BreakdownCard(title = "By tier") {
                            stats.tierCounts.forEach { (tier, count) ->
                                BreakdownBarRow(
                                    label = tier ?: "Unranked",
                                    count = count,
                                    total = total,
                                    color = tierColor(tier),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Genre distribution (top 8)
                    if (stats.genreCounts.isNotEmpty()) {
                        val genres = stats.genreCounts.take(8)
                        val total = genres.sumOf { it.second }
                        BreakdownCard(title = "Top genres") {
                            genres.forEach { (genre, count) ->
                                BreakdownBarRow(
                                    label = genre,
                                    count = count,
                                    total = total,
                                    color = StatusWatching,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Format distribution
                    if (stats.formatCounts.isNotEmpty()) {
                        val total = stats.formatCounts.sumOf { it.second }
                        BreakdownCard(title = "By format") {
                            stats.formatCounts.forEach { (fmt, count) ->
                                BreakdownBarRow(
                                    label = fmt.ifBlank { "Unknown" },
                                    count = count,
                                    total = total,
                                    color = StatusPlan,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                }
                }
                }
                3 -> Box(modifier = Modifier.weight(1f)) {
                    tabHolder.SaveableStateProvider("settings") {
                    Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // v1.5.5: Settings splits into General / Debug sub-tabs —
                    // the sync debugger now lives under Debug.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settingsTab == 0,
                            onClick = { settingsTab = 0 },
                            label = { Text("General") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.22f),
                                selectedLabelColor = Accent,
                            ),
                        )
                        FilterChip(
                            selected = settingsTab == 1,
                            onClick = { settingsTab = 1 },
                            label = { Text("Debug") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.22f),
                                selectedLabelColor = Accent,
                            ),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    when (settingsTab) {
                        0 -> AboutCard(userName = userName)
                        1 -> DiagnosticsCard()
                    }
                }
                }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *\
 *  Local stats aggregation — runs on the Compose recomposition path   *
 *  but only on `entries` change (remember(entries) wraps the call).    *
\* ------------------------------------------------------------------ */
private data class LocalStats(
    val episodesWatched: Int,
    val minutesWatched: Int,
    val daysWatched: Double,
    val personalMean: Double?,
    val tierCounts:   List<Pair<String?, Int>>,
    val genreCounts:  List<Pair<String, Int>>,
    val formatCounts: List<Pair<String, Int>>,
)

private fun computeLocalStats(entries: List<AnimeEntry>): LocalStats {
    if (entries.isEmpty()) {
        return LocalStats(0, 0, 0.0, null, emptyList(), emptyList(), emptyList())
    }
    val episodesWatched = entries.sumOf { it.currentEp }
    val minutesWatched  = entries.sumOf { it.currentEp * defaultDurationMinutes(it.format) }
    val daysWatched     = minutesWatched / 1440.0

    // v1.5.8: community mean (AniList's average score) was dropped from the
    // Stats tab — it's not a stat about the user's own list. `averageScore`
    // still syncs into Room for card badges; it's just no longer aggregated
    // here. Means are kept on the 0-100 AniList scale so the ScoreScale
    // toggle can map them at display time without losing precision.
    val personalScores = entries.mapNotNull { it.personalScore }.filter { it > 0 }
    val personalMean = if (personalScores.isNotEmpty()) personalScores.average() else null

    val tierOrder = listOf("S", "A", "B", "C", "D", null)
    val rawTier = entries.groupingBy { it.tier }.eachCount()
    val tierCounts = tierOrder
        .filter { it in rawTier }
        .map { it to (rawTier[it] ?: 0) }

    // Split comma-separated genres and count individually.
    val genreCounts = entries
        .flatMap { it.genres.split(",").map { g -> g.trim() }.filter { it.isNotBlank() } }
        .groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }
        .map { it.key to it.value }

    val formatCounts = entries
        .groupingBy { prettyFormat(it.format) }.eachCount()
        .entries.sortedByDescending { it.value }
        .map { it.key to it.value }

    return LocalStats(
        episodesWatched = episodesWatched,
        minutesWatched  = minutesWatched,
        daysWatched     = daysWatched,
        personalMean    = personalMean,
        tierCounts      = tierCounts,
        genreCounts     = genreCounts,
        formatCounts    = formatCounts,
    )
}

private fun defaultDurationMinutes(format: String?): Int = when (format) {
    "TV"          -> 24
    "TV_SHORT"    -> 8
    "MOVIE"       -> 80
    "MUSIC"       -> 4
    "OVA", "ONA", "SPECIAL" -> 24
    else          -> 24
}

private fun formatDays(days: Double): String {
    if (days <= 0.0) return "—"
    return String.format(Locale.US, "%.1f", days)
}

/*
 * formatMean — render a community or personal mean that's stored on
 * the 0-100 AniList scale. Returns "—" for null/0 so the StatRow never
 * shows literal "null".
 */
private fun formatMean(mean: Double?, scale: ScoreScale): String {
    if (mean == null || mean <= 0.0) return "—"
    return ScoreDisplay.label(mean.roundToInt(), scale)
}

/* ------------------------------------------------------------------ *\
 *  Reusable stat rows                                                 *
\* ------------------------------------------------------------------ */
@Composable
private fun StatRow(label: String, value: String, hint: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BreakdownCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/*
 * BreakdownBarRow — v1.5.8. AniHyou-style stat row: label on the left,
 * count on the right, and a thin proportional bar underneath so the
 * breakdown reads at a glance instead of a list of numbers.
 */
@Composable
private fun BreakdownBarRow(
    label: String,
    count: Int,
    total: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    val fraction = if (total > 0) count.toFloat() / total else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BgInput),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

private fun prettyFormat(fmt: String?): String = when (fmt) {
    "TV"          -> "TV"
    "TV_SHORT"    -> "TV Short"
    "MOVIE"       -> "Movie"
    "OVA"         -> "OVA"
    "ONA"         -> "ONA"
    "SPECIAL"     -> "Special"
    "MUSIC"       -> "Music"
    null          -> "Unknown"
    else          -> fmt.lowercase().replaceFirstChar { it.uppercase() }
}

/*
 * ActivityTab — v1.5.8. AniHyou-style activity feed.
 *
 * When signed in, fetches the user's real AniList activity (ListActivity +
 * TextActivity) so each row reads like AniHyou's feed: "You watched episode
 * 5 of [Title]" with the exact episode/progress, timestamp, like/reply
 * counts, and the media cover thumb. Falls back to the local-edit list
 * (cover + status + "X ago") when offline, not signed in, or the fetch
 * returns nothing.
 */
@Composable
private fun ActivityTab(
    accessToken: String?,
    userId: String?,
    userName: String?,
    avatarUrl: String?,
    isRefreshing: Boolean,
    entries: List<AnimeEntry>,
    onNavigateDetail: (Int, String) -> Unit,
) {
    val app = remember { LocalContext.current.applicationContext as AnimeApp }

    // null = loading; empty list = loaded but nothing (fall back to local).
    var activities by remember { mutableStateOf<List<ActivityItem>?>(null) }

    LaunchedEffect(accessToken, userId, isRefreshing) {
        val tok = accessToken
        val id = userId
        if (tok.isNullOrBlank() || id.isNullOrBlank()) {
            activities = emptyList()
            return@LaunchedEffect
        }
        // Do NOT blank out previously loaded rows here: pull-to-refresh flips
        // isRefreshing true→false, which re-runs this effect — resetting to
        // null would flash a spinner over an already-populated feed. Only a
        // genuinely-empty `activities` (initial load) shows the spinner.
        activities = app.anilistClient.getUserActivities(tok, id.toInt())
    }

    val items = activities
    val signedIn = !accessToken.isNullOrBlank() && !userId.isNullOrBlank()
    val activity = remember(entries) { entries.sortedByDescending { it.updatedAt } }

    when {
        // Loading real feed — show a spinner.
        signedIn && items == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Accent)
            }
        }
        // Real feed arrived with rows — AniHyou-style list.
        signedIn && items != null && items.isNotEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    ActivityRow(
                        item = item,
                        userName = userName,
                        avatarUrl = avatarUrl,
                        onNavigateDetail = onNavigateDetail,
                    )
                }
            }
        }
        // Not signed in, offline, or nothing from AniList — fall back to the
        // local edit log so the tab is never a dead end.
        else -> {
            LocalActivityList(
                activity = activity,
                onNavigateDetail = onNavigateDetail,
            )
        }
    }
}

/*
 * ActivityRow — v1.5.8. One AniHyou-style feed row: avatar, action text
 * with the media title in semibold (e.g. "You watched episode 5 of
 * Attack on Titan"), a muted time-ago + like/reply footer, and the cover
 * thumb on the right. Tapping a list activity opens the media.
 */
@Composable
private fun ActivityRow(
    item: ActivityItem,
    userName: String?,
    avatarUrl: String?,
    onNavigateDetail: (Int, String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (item.mediaId != null) {
                    Modifier.clickable { onNavigateDetail(item.mediaId, item.mediaType ?: "ANIME") }
                } else Modifier,
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar (falls back to a person glyph).
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activitySummary(item, userName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = relativeTimeText(item.createdAt * 1000L),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                    if (item.likeCount > 0) {
                        Text(
                            text = "♥ ${item.likeCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                    if (item.replyCount > 0) {
                        Text(
                            text = "💬 ${item.replyCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                }
            }
            if (item.mediaCover != null) {
                Spacer(Modifier.width(12.dp))
                CoverImage(
                    model = item.mediaCover,
                    contentDescription = item.mediaTitle ?: "Cover",
                    label = item.mediaTitle ?: "Cover",
                    modifier = Modifier
                        .size(width = 48.dp, height = 68.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

/*
 * activitySummary — build the AniHyou-style action text for one feed row.
 * ListActivity status strings come from AniList as lowercase verbs
 * ("watched episode", "completed", "read chapter", …) with `progress`
 * carrying the episode/chapter number; text activities show the message.
 */
private fun activitySummary(item: ActivityItem, userName: String?): String {
    val who = userName ?: "You"
    if (item.type == "text") {
        return item.text?.takeIf { it.isNotBlank() } ?: "$who posted an update"
    }
    val title = item.mediaTitle ?: "a title"
    val verb = when (item.status) {
        "watched episode" -> item.progress?.let { "watched episode $it" } ?: "watched"
        "read chapter"    -> item.progress?.let { "read chapter $it" } ?: "read"
        "read"            -> "read"
        "watched"         -> "watched"
        "completed"       -> "completed"
        "rewatched"       -> "rewatched"
        "reread"          -> "re-read"
        "dropped"         -> "dropped"
        "paused"          -> "paused"
        "planned"         -> "added to plan"
        else              -> item.status?.takeIf { it.isNotBlank() } ?: "updated"
    }
    return "$who $verb $title"
}

/*
 * LocalActivityList — v1.5.8. The offline/local fallback for the Activity
 * tab: most-recently-edited entries with cover, title, status icon and
 * "X ago" (the pre-v1.5.8 behavior, now used only when the live feed
 * can't load).
 */
@Composable
private fun LocalActivityList(
    activity: List<AnimeEntry>,
    onNavigateDetail: (Int, String) -> Unit,
) {
    // v1.5.7: paged instead of one long infinite scroll — 12 rows per
    // page with a "Show more" button at the bottom.
    var visibleCount by rememberSaveable { mutableIntStateOf(12) }
    val shown = remember(activity, visibleCount) { activity.take(visibleCount) }
    if (activity.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No activity yet — edits and progress updates will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shown.forEach { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateDetail(entry.anilistId, entry.mediaType) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverImage(
                            model = entry.cover,
                            contentDescription = entry.title,
                            label = entry.title,
                            modifier = Modifier
                                .size(width = 44.dp, height = 62.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                StatusIcon(status = entry.status, mediaType = entry.mediaType)
                                Text(
                                    text = "${statusFilterLabel(entry.status, entry.mediaType)} · ${relativeTimeText(entry.updatedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                )
                            }
                        }
                    }
                }
            }
            if (activity.size > visibleCount) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { visibleCount += 12 },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = "Show more (${activity.size - visibleCount} remaining)",
                        color = Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/*
 * `relativeTimeText` — "X ago" formatting for the Activity tab. Wraps
 * android.text.format.DateUtils so the user's locale + system preferences
 * pick the right format ("2h ago" vs "2 hours ago").
 */
private fun relativeTimeText(epochMs: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

/*
 * AboutCard — the General sub-tab inside Profile Settings. A lightweight
 * place for non-debug info (version, sign-in state) so the Settings tab is
 * never just a wall of debug text.
 */
@Composable
private fun AboutCard(userName: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "ABOUT",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                letterSpacing = 2.sp,
            )
            StatRow(label = "App version", value = "MangoList ${BuildConfig.VERSION_NAME}")
            StatRow(label = "Signed in", value = userName ?: "Not signed in")
            Text(
                text = "Tier rankings are stored on-device and never uploaded to AniList.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

/*
 * Sync diagnostics — in-app alternative to `adb logcat`. Renders the last
 * sync attempt step-by-step (network, token, HTTP, GraphQL, parse, DB write)
 * from SyncDiagnostics, with a copy-to-clipboard button so the user can paste
 * the exact failure back into a bug report without developer tools.
 */
@Composable
private fun DiagnosticsCard() {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var refreshKey by remember { mutableStateOf(0) }
    // Poll once a second so the card stays live while a sync runs in the
    // background (pull-to-refresh, background worker, login sync).
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            refreshKey++
        }
    }
    val summary = remember(refreshKey) { SyncDiagnostics.summary() }
    val lines   = remember(refreshKey) { SyncDiagnostics.snapshot() }
    val ok = summary.startsWith("Sync") && summary.contains("OK")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SYNC DIAGNOSTICS",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 10.dp, height = 10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                summary.contains("FAILED") -> StatusDropped
                                ok -> StatusCompleted
                                else -> TextMuted
                            },
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgInput)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                if (lines.isEmpty()) {
                    Text(
                        text = "No sync activity yet. Pull to refresh or tap \"Sync now\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                } else {
                    Column {
                        lines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val text = buildString {
                            appendLine("MangoList ${BuildConfig.VERSION_NAME} — sync diagnostics")
                            appendLine(summary)
                            appendLine("— log —")
                            append(SyncDiagnostics.text())
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText("MangoList diagnostics", text))
                        Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Copy diagnostics") }
                TextButton(
                    onClick = {
                        SyncDiagnostics.clear()
                        Toast.makeText(context, "Diagnostics cleared", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Clear") }
            }
        }
    }
}

/*
 * ProfileHeaderRow — v1.5.6. Icon-driven profile header: circular avatar +
 * username. v1.5.7: the settings gear was dropped because the Settings tab
 * (gear icon) already lives in the tab row below — the duplicate gear read
 * as a broken header. v1.5.8: the "N entries in your list" subtitle is gone
 * too — the list size is noise under the name, and the Stats tab owns the
 * meaningful numbers.
 */
@Composable
private fun ProfileHeaderRow(
    avatarUrl: String?,
    userName: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = userName ?: "Not signed in",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/*
 * QuickStat — v1.5.7. One value+label column for the Overview quick-stats
 * card (episodes / days watched / mean score), matching AniHyou's stat
 * columns on the profile.
 */
@Composable
private fun QuickStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = Accent,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

/*
 * FavoritesRow — v1.5.7. Horizontal strip of favorite covers on the
 * Overview tab. Tap any cover to open its detail screen.
 */
@Composable
private fun FavoritesRow(
    favorites: List<AnimeEntry>,
    onNavigateDetail: (Int, String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

/*
 * Builds the AniList OAuth authorization-code-flow authorize URL.
 *
 * v0.6.2: switched from implicit (response_type=token) to authorization code
 * (response_type=code). AniList clients registered as "Authorization Code Grant"
 * reject implicit requests with "authorization grant type is not supported".
 * After the user authorizes, AniList redirects back with ?code=... —
 * MainActivity.handleAuthRedirect exchanges that code for an access_token
 * at /api/v2/oauth/token.
 */
private fun buildAniListAuthorizeUrl(): String {
    val clientId    = BuildConfig.ANILIST_CLIENT_ID
    val redirectUri = BuildConfig.ANILIST_REDIRECT_URI
    return buildString {
        append("https://anilist.co/api/v2/oauth/authorize")
        append("?client_id=").append(Uri.encode(clientId))
        append("&response_type=code")
        append("&redirect_uri=").append(Uri.encode(redirectUri))
    }
}
