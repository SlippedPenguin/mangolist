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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.BuildConfig
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.tierColor
import java.util.Locale

/*
 * Profile — login state + live local stats + sync button.
 *
 * v0.5 adds:
 *   - Episodes watched, minutes watched, and community mean score in
 *     place of the previous "—" placeholders. Minutes uses a format-aware
 *     duration fallback (TV=24m, MOVIE=80m, etc.) since AnimeEntry doesn't
 *     carry an explicit runtime column.
 *   - Status + tier count breakdowns so the user can see "5 watching,
 *     12 completed, ..." without scrolling.
 *   - Each tier count uses its tier color to give a quick visual map of
 *     where the collection lives.
 */
@Composable
fun ProfileScreen(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val userName by app.tokenStore.userName.collectAsState(initial = null)
    val entries   by app.database.animeDao().observeAll()
        .collectAsState(initial = emptyList())

    val stats = remember(entries) { computeLocalStats(entries) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                    label = "Time watched",
                    value = formatMinutes(stats.minutesWatched),
                )
                StatRow(
                    label = "Community mean score",
                    value = stats.meanScore?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    hint = if (stats.meanScore == null) "Add some anime to see your mean."
                           else "AniList community avg",
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (stats.statusCounts.isNotEmpty()) {
            BreakdownCard(title = "By status") {
                val byCount = stats.statusCounts.sortedByDescending { it.second }
                for ((status, count) in byCount) {
                    BreakdownRow(
                        label = status.uppercase(),
                        count = count,
                        color = statusColor(status),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (stats.tierCounts.isNotEmpty()) {
            BreakdownCard(title = "By tier") {
                for ((tier, count) in stats.tierCounts) {
                    BreakdownRow(
                        label = tier ?: "Unranked",
                        count = count,
                        color = tierColor(tier),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
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
                onClick = { /* v0.5: pull viewer statistics and reconcile lists */ },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sync now (v0.5)") }
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
    val meanScore: Double?,
    val statusCounts: List<Pair<String, Int>>,
    val tierCounts:   List<Pair<String?, Int>>,
)

/*
 * computeLocalStats — pure function over the entries list. Mean score
 * averages the AniList community `averageScore` (already 0-100), then
 * divides by 10 once for display. AnimeEntry doesn't carry a personal
 * rating yet, so we intentionally label the field "Community mean score"
 * until v0.5+.
 */
private fun computeLocalStats(entries: List<AnimeEntry>): LocalStats {
    if (entries.isEmpty()) {
        return LocalStats(0, 0, null, emptyList(), emptyList())
    }
    val episodesWatched = entries.sumOf { it.currentEp }
    val minutesWatched  = entries.sumOf { it.currentEp * defaultDurationMinutes(it.format) }
    val scores = entries.mapNotNull { it.averageScore }.filter { it > 0 }
    val meanScore = if (scores.isNotEmpty()) scores.average() / 10.0 else null

    val statusCounts = entries.groupingBy { it.status }.eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }

    // Tier ordering: S → A → B → C → D → Unranked (matches TierHeader layout).
    val tierOrder = listOf("S", "A", "B", "C", "D", null)
    val rawTier = entries.groupingBy { it.tier }.eachCount()
    val tierCounts = tierOrder
        .filter { it in rawTier }
        .map { it to (rawTier[it] ?: 0) }

    return LocalStats(
        episodesWatched = episodesWatched,
        minutesWatched  = minutesWatched,
        meanScore       = meanScore,
        statusCounts    = statusCounts,
        tierCounts      = tierCounts,
    )
}

/*
 * Format-aware duration table. AnimeEntry doesn't carry a runtime column
 * (no schema migration), so we use a best-effort fallback per AniList
 * format. Defaults to 24m so OVAs / ONAs / SPECIALS line up with TV.
 */
private fun defaultDurationMinutes(format: String?): Int = when (format) {
    "TV"          -> 24
    "TV_SHORT"    -> 8
    "MOVIE"       -> 80
    "MUSIC"       -> 4
    "OVA", "ONA", "SPECIAL" -> 24
    else          -> 24
}

private fun statusColor(status: String) = when (status) {
    "watching"  -> Accent
    "completed" -> MaterialTheme.colorScheme.secondary
    "dropped"   -> MaterialTheme.colorScheme.error
    "paused"    -> TextMuted
    "repeating" -> Accent
    else        -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatMinutes(total: Int): String {
    if (total <= 0) return "—"
    val hours = total / 60
    val mins  = total % 60
    return when {
        hours == 0 -> "${mins}m"
        mins  == 0 -> "${hours}h"
        else       -> "${hours}h ${mins}m"
    }
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

/*
 * Builds the AniList OAuth implicit-flow authorize URL.
 *
 *   https://anilist.co/api/v2/oauth/authorize
 *     ?client_id=<from-BuildConfig>
 *     &response_type=token
 *     &redirect_uri=com.slippedpenguin.mangolist://callback
 *
 * Implicit flow puts the access_token in the URL fragment (#access_token=...)
 * so we don't need a client_secret. URL-encode both values just in case
 * either ever picks up a reserved character.
 */
private fun buildAniListAuthorizeUrl(): String {
    val clientId    = BuildConfig.ANILIST_CLIENT_ID
    val redirectUri = BuildConfig.ANILIST_REDIRECT_URI
    return buildString {
        append("https://anilist.co/api/v2/oauth/authorize")
        append("?client_id=").append(Uri.encode(clientId))
        append("&response_type=token")
        append("&redirect_uri=").append(Uri.encode(redirectUri))
    }
}
