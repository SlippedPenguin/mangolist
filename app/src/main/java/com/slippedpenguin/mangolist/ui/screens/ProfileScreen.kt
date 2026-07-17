package com.slippedpenguin.mangolist.ui.screens

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import android.widget.Toast
import coil.compose.AsyncImage
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.BuildConfig
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.StatusDropped
import com.slippedpenguin.mangolist.ui.theme.StatusPlan
import com.slippedpenguin.mangolist.ui.theme.StatusWatching
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.statusColor
import com.slippedpenguin.mangolist.ui.theme.tierColor
import kotlinx.coroutines.launch
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
@Composable
fun ProfileScreen(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()
    val userName by app.tokenStore.userName.collectAsState(initial = null)
    val accessToken by app.tokenStore.accessToken.collectAsState(initial = null)
    val userId by app.tokenStore.userId.collectAsState(initial = null)
    val entries  by app.database.animeDao().observeAll()
        .collectAsState(initial = emptyList())

    val stats = remember(entries) { computeLocalStats(entries) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Avatar + greeting
        AsyncImage(
            model = null,  // v0.7: pull from cached viewer avatar
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
            text = "${entries.size} anime in your local list",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

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
                    value = stats.communityMean?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    hint = if (stats.communityMean == null) "Add some anime to see your mean."
                           else "AniList community avg",
                )
                StatRow(
                    label = "Your mean score",
                    value = stats.personalMean?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    hint = if (stats.personalMean == null) "Rate your anime to see your mean."
                           else "your personal avg",
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Status breakdown
        if (stats.statusCounts.isNotEmpty()) {
            BreakdownCard(title = "By status") {
                Column {
                    val byCount = stats.statusCounts.sortedByDescending { it.second }
                    for ((status, count) in byCount) {
                        BreakdownRow(
                            label = status.uppercase(),
                            count = count,
                            color = statusColor(status),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

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

        Spacer(Modifier.height(8.dp))

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
                        if (accessToken.isNullOrBlank() || userId.isNullOrBlank()) {
                            Toast.makeText(context, "Not signed in", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        Toast.makeText(context, "Syncing...", Toast.LENGTH_SHORT).show()
                        val synced = app.anilistClient.syncUserList(accessToken, userId.toInt())
                        if (synced != null) {
                            val existing = app.database.animeDao().getAll().associateBy { it.anilistId }
                            val merged = synced.map { it.preserveLocalFields(existing[it.anilistId]) }
                            app.database.animeDao().upsertAll(merged)
                            Toast.makeText(context, "Synced ${merged.size} anime", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Sync failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sync now (v0.6)") }
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
    val statusCounts: List<Pair<String, Int>>,
    val tierCounts:   List<Pair<String?, Int>>,
    val genreCounts:  List<Pair<String, Int>>,
    val formatCounts: List<Pair<String, Int>>,
    val yearCounts:   List<Pair<Int?, Int>>,
)

private fun computeLocalStats(entries: List<AnimeEntry>): LocalStats {
    if (entries.isEmpty()) {
        return LocalStats(0, 0, 0.0, null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
    val episodesWatched = entries.sumOf { it.currentEp }
    val minutesWatched  = entries.sumOf { it.currentEp * defaultDurationMinutes(it.format) }
    val daysWatched     = minutesWatched / 1440.0

    val communityScores = entries.mapNotNull { it.averageScore }.filter { it > 0 }
    val communityMean = if (communityScores.isNotEmpty()) communityScores.average() / 10.0 else null

    val personalScores = entries.mapNotNull { it.personalScore }.filter { it > 0 }
    val personalMean = if (personalScores.isNotEmpty()) personalScores.average() / 10.0 else null

    val statusCounts = entries.groupingBy { it.status }.eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }

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
        statusCounts    = statusCounts,
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
