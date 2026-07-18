package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.AiringSlot
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.OfflineBanner
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/*
 * Airing — v1.0: 7-day schedule from AniList's GetAiringSchedule, plus a
 * `On my list` filter so users can quickly see what's next on their own
 * list before the season dumps them into the raw 700+ slots.
 *
 * Two modes, top-level tabs:
 *   - "On my list"  — filters to slots whose animeId appears in the user's
 *                     Room list, then enriches each card with that list's
 *                     current progress (e.g. "Your: 3 / 12"). Tracks
 *                     changes in the local list flow via Room observation
 *                     so the filter updates immediately when something is
 *                     added or status changes to "completed".
 *   - "All airing" — the original v0.6 surface, every airing slot for the
 *                     next week.
 *
 * Unauthenticated users see the same screen but the "On my list" tab will
 * be empty (or hidden if the Room DB is empty) — the "All airing" surface
 * stays available so anonymous browsing is still useful.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiringScreen(
    onNavigateDetail: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()

    var slots by remember { mutableStateOf<List<AiringSlot>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var now by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    var isRefreshing by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(AiringMode.ON_MY_LIST) }

    // Local list snapshot for both "On my list" filtering and the per-card
    // progress badge. observeAll() emits on every Room insert/update, so the
    // badge stays current without us re-fetching the projection.
    val localEntries by app.database.animeDao()
        .observeAll()
        .collectAsState(initial = emptyList())

    suspend fun reload() {
        slots = app.anilistClient.getAiringSchedule()
        loading = false
        now = System.currentTimeMillis() / 1000
    }

    LaunchedEffect(Unit) {
        reload()
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            reload()
            isRefreshing = false
        }
    }

    // Tick every 60s to keep the countdown fresh.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis() / 1000
        }
    }

    // Pre-compute the lookup map once per Room emission so card rendering
    // stays cheap. Restricting to fields the card needs keeps the projection
    // small.
    val progressByAnimeId = remember(localEntries) {
        localEntries.associate { it.anilistId to it }
    }

    val filteredSlots = remember(slots, mode, progressByAnimeId) {
        when {
            slots == null -> null
            mode == AiringMode.ALL         -> slots
            mode == AiringMode.ON_MY_LIST  -> slots!!.filter { it.animeId in progressByAnimeId }
            else                          -> slots
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OfflineBanner()

        TabRow(
            selectedTabIndex = mode.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AiringMode.values().forEachIndexed { index, tabMode ->
                Tab(
                    selected = mode.ordinal == index,
                    onClick = { mode = tabMode },
                    text = {
                        val count = filteredSlots?.size ?: 0
                        Text(
                            text = when (tabMode) {
                                AiringMode.ON_MY_LIST -> "On my list"
                                AiringMode.ALL        -> "All airing"
                            },
                            fontWeight = if (mode.ordinal == index) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { if (!isRefreshing) isRefreshing = true },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                filteredSlots == null || filteredSlots!!.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    ) {
                        Text(
                            text = when (mode) {
                                AiringMode.ON_MY_LIST -> "Nothing on your list is airing this week."
                                AiringMode.ALL        -> "No airing schedule available right now."
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when (mode) {
                                AiringMode.ON_MY_LIST -> "Pull to refresh, or check back when something on your list has a new episode scheduled."
                                AiringMode.ALL        -> "Pull to refresh or check back later."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    val itemsList = filteredSlots!!
                    // Group by day
                    val grouped = itemsList.groupBy { dayBucket(it.airingAt) }
                    val days = grouped.keys.sorted()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        days.forEach { dayLabel ->
                            val daySlots = grouped[dayLabel].orEmpty()
                            item(key = "header_$dayLabel") {
                                Text(
                                    text = dayLabel.uppercase(),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                    ),
                                    color = Accent,
                                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                                )
                            }
                            items(daySlots, key = { "air_${it.id}" }) { slot ->
                                AiringCard(
                                    slot = slot,
                                    now = now,
                                    localEntry = progressByAnimeId[slot.animeId],
                                    onClick = { onNavigateDetail?.invoke(slot.animeId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class AiringMode { ON_MY_LIST, ALL }

/*
 * Day-bucket label: "Today", "Tomorrow", "Wed, Jul 15", etc.
 */
private fun dayBucket(epochSec: Long): String {
    val cal = java.util.Calendar.getInstance(TimeZone.getDefault()).apply {
        timeInMillis = epochSec * 1000
    }
    val nowCal = java.util.Calendar.getInstance(TimeZone.getDefault())
    val diffDays = ((epochSec / 86400) - (nowCal.timeInMillis / 86400 / 1000)).toInt()
    return when (diffDays) {
        0    -> "Today"
        1    -> "Tomorrow"
        else -> {
            val fmt = SimpleDateFormat("EEE, MMM d", Locale.US)
            fmt.timeZone = TimeZone.getDefault()
            fmt.format(Date(epochSec * 1000))
        }
    }
}

private fun countdownText(airingAtSec: Long, nowSec: Long): String {
    if (airingAtSec <= nowSec) return "Airing now"
    val diff = airingAtSec - nowSec
    val days  = TimeUnit.SECONDS.toDays(diff)
    val hours = TimeUnit.SECONDS.toHours(diff) % 24
    val mins  = TimeUnit.SECONDS.toMinutes(diff) % 60
    return when {
        days > 0   -> "in ${days}d ${hours}h"
        hours > 0  -> "in ${hours}h ${mins}m"
        mins  > 0  -> "in ${mins}m"
        else       -> "soon"
    }
}

@Composable
private fun AiringCard(
    slot: AiringSlot,
    now: Long,
    localEntry: AnimeEntry?,
    onClick: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = slot.coverLarge,
                    contentDescription = slot.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = slot.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Ep ${slot.episode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    // Per-card progress badge: only render when the user is
                    // tracking this anime locally. Format depends on
                    // whether the entry reports a final episode count or
                    // is still airing (episodes == null).
                    localEntry?.let { entry ->
                        val myEp = entry.currentEp
                        val total = entry.episodes
                        val text = when {
                            total != null && total > 0 -> "Your: $myEp / $total"
                            else                       -> "Your: $myEp ep watched"
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelMedium,
                            color = Accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = countdownText(slot.airingAt, now),
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatAirTime(slot.airingAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
    }
}

private fun formatAirTime(epochSec: Long): String {
    val fmt = SimpleDateFormat("h:mm a", Locale.US)
    fmt.timeZone = TimeZone.getDefault()
    return fmt.format(Date(epochSec * 1000))
}
