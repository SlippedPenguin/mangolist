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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import com.slippedpenguin.mangolist.data.ScoreDisplay
import com.slippedpenguin.mangolist.data.ScoreScale
import com.slippedpenguin.mangolist.data.SyncDiagnostics
import com.slippedpenguin.mangolist.data.local.AnimeEntry
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
 * Profile — v0.6: full AniHyou-parity stats surface.
 *
 *   - Avatar + greeting (from TokenStore / GetViewer)
 *   - Episodes watched, days watched, community mean score, personal mean score
 *   - Status breakdown + tier breakdown
 *   - Genre distribution, format distribution, year distribution
 *   - Sign-in / Sync CTA
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(@Suppress("UNUSED_PARAMETER") navController: NavController) {
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

    var viewer by remember { mutableStateOf<com.slippedpenguin.mangolist.data.AnimeViewer?>(null) }
    var viewerLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun refreshViewer() {
        val tok = accessToken
        if (!tok.isNullOrBlank()) {
            viewerLoading = true
            try {
                viewer = app.anilistClient.getViewer(tok)
            } finally {
                viewerLoading = false
            }
        } else {
            viewer = null
        }
    }

    LaunchedEffect(accessToken) {
        refreshViewer()
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            try {
                refreshViewer()
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
    val profileTabs = listOf("Overview", "Activity", "Stats", "Settings")

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (!isRefreshing) isRefreshing = true },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OfflineBanner()
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
            ) {
                profileTabs.forEachIndexed { index, label ->
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
            when (selectedTab) {
                0 -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Avatar + greeting
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (userName == null) "Not signed in" else "Hi, $userName",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${entries.size} entries in your local list",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(24.dp))

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
                        Spacer(Modifier.height(12.dp))
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
                1 -> Column(modifier = Modifier.weight(1f)) {
                    ActivityTab(
                        entries = entries,
                        onNavigateDetail = { id, type -> navController.navigate("detail/$type/$id") },
                    )
                }
                2 -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Core stats card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            StatRow(
                                label = "Episodes watched",
                                value = stats.episodesWatched.toString(),
                            )
                            StatRow(
                                label = "Days watched",
                                value = formatDays(stats.daysWatched),
                                hint = "based on format-aware duration estimates",
                            )
                            StatRow(
                                label = "Community mean score",
                                value = formatMean(stats.communityMean, scoreScale),
                                hint = if (stats.communityMean == null) "Add some anime to see your mean."
                                       else "AniList community avg",
                            )
                            StatRow(
                                label = "Your mean score",
                                value = formatMean(stats.personalMean, scoreScale),
                                hint = if (stats.personalMean == null) "Rate your anime to see your mean."
                                       else "your personal avg",
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // AniList viewer stats card
                    if (viewer != null || viewerLoading) {
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
                                    text = "AniList stats",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (viewerLoading) {
                                    Text(
                                        text = "Loading…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                    )
                                } else {
                                    viewer?.let { v ->
                                        StatRow(
                                            label = "Anime count",
                                            value = v.animeCount?.toString() ?: "—",
                                        )
                                        StatRow(
                                            label = "Mean score",
                                            value = v.animeMeanScore?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                                        )
                                        StatRow(
                                            label = "Episodes watched",
                                            value = v.episodesWatched?.toString() ?: "—",
                                        )
                                        StatRow(
                                            label = "Days watched",
                                            value = v.minutesWatched?.let { String.format(Locale.US, "%.1f", it / 1440.0) } ?: "—",
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // v1.5.3: the per-status breakdown is gone — the Anime/Manga
                    // watchlist filter island is the single source of status counts.
                    // Profile keeps tier / genre / format / year distribution.

                    // Tier breakdown
                    if (stats.tierCounts.isNotEmpty()) {
                        BreakdownCard(title = "By tier") {
                            Column {
                                for ((tier, count) in stats.tierCounts) {
                                    BreakdownRow(
                                        label = tier ?: "Unranked",
                                        count = count,
                                        color = tierColor(tier),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Genre distribution (top 8)
                    if (stats.genreCounts.isNotEmpty()) {
                        BreakdownCard(title = "Top genres") {
                            Column {
                                for ((genre, count) in stats.genreCounts.take(8)) {
                                    BreakdownRow(
                                        label = genre,
                                        count = count,
                                        color = StatusWatching,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Format distribution
                    if (stats.formatCounts.isNotEmpty()) {
                        BreakdownCard(title = "By format") {
                            Column {
                                for ((fmt, count) in stats.formatCounts) {
                                    BreakdownRow(
                                        label = fmt.ifBlank { "Unknown" },
                                        count = count,
                                        color = StatusPlan,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Year distribution (top 8)
                    if (stats.yearCounts.isNotEmpty()) {
                        BreakdownCard(title = "By year") {
                            Column {
                                for ((year, count) in stats.yearCounts.take(8)) {
                                    BreakdownRow(
                                        label = year?.toString() ?: "TBA",
                                        count = count,
                                        color = StatusDropped,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                }
                3 -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
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
                    Spacer(Modifier.height(16.dp))
                    when (settingsTab) {
                        0 -> AboutCard(userName = userName)
                        1 -> DiagnosticsCard()
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
    val communityMean: Double?,
    val personalMean: Double?,
    val tierCounts:   List<Pair<String?, Int>>,
    val genreCounts:  List<Pair<String, Int>>,
    val formatCounts: List<Pair<String, Int>>,
    val yearCounts:   List<Pair<Int?, Int>>,
)

private fun computeLocalStats(entries: List<AnimeEntry>): LocalStats {
    if (entries.isEmpty()) {
        return LocalStats(0, 0, 0.0, null, null, emptyList(), emptyList(), emptyList(), emptyList())
    }
    val episodesWatched = entries.sumOf { it.currentEp }
    val minutesWatched  = entries.sumOf { it.currentEp * defaultDurationMinutes(it.format) }
    val daysWatched     = minutesWatched / 1440.0

    // Means are kept on the 0-100 AniList scale so the ScoreScale toggle
    // can map them at display time without losing precision.
    val communityScores = entries.mapNotNull { it.averageScore }.filter { it > 0 }
    val communityMean = if (communityScores.isNotEmpty()) communityScores.average() else null

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

    val yearCounts = entries
        .groupingBy { it.year }.eachCount()
        .entries.sortedByDescending { it.value }
        .map { it.key to it.value }

    return LocalStats(
        episodesWatched = episodesWatched,
        minutesWatched  = minutesWatched,
        daysWatched     = daysWatched,
        communityMean   = communityMean,
        personalMean    = personalMean,
        tierCounts      = tierCounts,
        genreCounts     = genreCounts,
        formatCounts    = formatCounts,
        yearCounts      = yearCounts,
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

@Composable
private fun BreakdownRow(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 8.dp, height = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.ExtraBold,
        )
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
 * ActivityTab — v1.5.5. Replaces the per-card "Edited X ago" lines that used
 * to sit on every watchlist card. Sorted by most-recently-updated, each row
 * shows the cover, title, status icon, and how long ago it was edited.
 */
@Composable
private fun ActivityTab(
    entries: List<AnimeEntry>,
    onNavigateDetail: (Int, String) -> Unit,
) {
    val activity = remember(entries) {
        entries.sortedByDescending { it.updatedAt }.take(40)
    }
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            activity.forEach { entry ->
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
