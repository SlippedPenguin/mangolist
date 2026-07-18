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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/*
 * Airing — v1.0.1: 7-day schedule from AniList's GetAiringSchedule, plus
 * a per-anime `On my list` lookup that hits `Media.nextAiringEpisode`
 * for every anime in the user's tracking list.
 *
 * v1.0's first attempt filtered the bulk 50-slot global schedule by the
 * user's local `anilistId` map. That failed because AniList's `perPage:50`
 * cap lets popular shows crowd out the user's tracked show if their next
 * episode isn't in the busiest 7-day window — the user reported
 * "I'm watching a show that's airing but it doesn't show up". The
 * per-anime path queries show-by-show with `id_in`, so a user's show is
 * always retrievable regardless of global volume.
 *
 * Two modes:
 *   - "On my list"  — `AniListClient.getNextAiringFor(trackedIds)` per
 *                     anime's `nextAiringEpisode`. No 7-day window. Empty
 *                     `nextAiringEpisode` is filtered.
 *                     Statuses included: `plan`, `watching`, `paused`,
 *                     `repeating`. Excluded: `completed`, `dropped`.
 *   - "All airing" — the original v0.6 7-day bulk schedule, paginated
 *                     one-shot via `getAiringSchedule()`.
 *
 * `AiringCard` is unchanged: it still reads `progressByAnimeId[animeId]`
 * for the "Your: 3 / 12" badge regardless of which slab of slots
 * generated the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiringScreen(
    onNavigateDetail: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()

    var myListSlots by remember { mutableStateOf<List<AiringSlot>?>(null) }
    var allSlots    by remember { mutableStateOf<List<AiringSlot>?>(null) }
    var loading     by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    var mode by remember { mutableStateOf(AiringMode.ON_MY_LIST) }

    val localEntries by app.database.animeDao()
        .observeAll()
        .collectAsState(initial = emptyList())

    // Pre-compute the lookup map once per Room emission so AiringCard's
    // "Your: 3 / 12" badge stays cheap. The map is no longer used to
    // filter slots (the per-anime path replaces v1.0's client-side filter)
    // — only for the progress badge.
    val progressByAnimeId = remember(localEntries) {
        localEntries.associate { it.anilistId to it }
    }

    // Tracked-IDs derived from Room. Filters out statuses that don't have
    // expected upcoming episodes: completed shows are done, dropped shows
    // are users' signal that they don't want updates.
    val trackedIds = remember(localEntries) {
        localEntries
            .filter { it.status in listOf("plan", "watching", "paused", "repeating") }
            .map { it.anilistId }
    }

    // Initial parallel fetch on first composition. Both slabs load
    // concurrently so switching tabs is instant after that.
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { allSlots = app.anilistClient.getAiringSchedule() }
            launch {
                myListSlots = if (trackedIds.isEmpty()) emptyList()
                              else app.anilistClient.getNextAiringFor(trackedIds)
            }
        }
        loading = false
        now = System.currentTimeMillis() / 1000
    }

    // When the local list changes (add / edit / delete / status flip), only
    // re-fetch the "On my list" slab — the global schedule doesn't depend
    // on the user's list. This LaunchedEffect re-fires on every localEntries
    // change. Its first fire (with `localEntries = emptyList()`) is a
    // no-op because `getNextAiringFor(emptyList())` is fast-fail.
    LaunchedEffect(trackedIds) {
        myListSlots = if (trackedIds.isEmpty()) emptyList()
                      else app.anilistClient.getNextAiringFor(trackedIds)
    }

    // Pull-to-refresh re-fetches both slabs in parallel.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            coroutineScope {
                launch { allSlots = app.anilistClient.getAiringSchedule() }
                launch {
                    myListSlots = if (trackedIds.isEmpty()) emptyList()
                                  else app.anilistClient.getNextAiringFor(trackedIds)
                }
            }
            now = System.currentTimeMillis() / 1000
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

    val activeSlots = when {
        mode == AiringMode.ON_MY_LIST -> myListSlots
        mode == AiringMode.ALL        -> allSlots
        else                         -> null
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
                activeSlots == null || activeSlots!!.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    ) {
                        Text(
                            text = when (mode) {
                                AiringMode.ON_MY_LIST -> {
                                    when {
                                        localEntries.isEmpty() ->
                                            "Add or sync some anime first — the airing tab is empty without a local list."
                                        trackedIds.isEmpty() ->
                                            "None of your tracking shows are still airing." +
                                                " Mark a show as Completed or Dropped to clear it from this view."
                                        else ->
                                            "None of your tracking shows have an upcoming episode scheduled."
                                    }
                                }
                                AiringMode.ALL        -> "No airing schedule available right now."
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when (mode) {
                                AiringMode.ON_MY_LIST -> "Pull to refresh, or add a still-airing show to your list."
                                AiringMode.ALL        -> "Pull to refresh or check back later."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    val itemsList = activeSlots!!
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
