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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Airing — v1.0.2: 7-day schedule from AniList's GetAiringSchedule, plus
 * a per-anime `On my list` lookup that hits `Media.nextAiringEpisode`
 * for every anime in the user's tracking list, *and* a top-bar Sync
 * button that freshens the local Room list without forcing the user to
 * route to Profile.
 *
 * v1.0.1 first attempt: per-anime path with no in-place sync. If the user's
 * local list was stale (they added a show on AniList website but never
 * tapped Sync), `trackedIds` was empty and the per-anime lookup returned
 * nothing even though AniList has the show. This v1.0.2 adds a Sync
 * action that pulls fresh data straight from AniList into Room. After
 * sync, Room emits new entries, `trackedIds` recomputes, and the
 * per-anime lookup re-fires automatically.
 *
 * Two modes (unchanged from v1.0.1):
 *   - "On my list"  — `AniListClient.getNextAiringFor(trackedIds)`.
 *                     Statuses: `plan`, `watching`, `paused`, `repeating`.
 *   - "All airing" — `getAiringSchedule()` 7-day bulk query.
 *
 * `AiringCard` is unchanged.
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

    // Auth state — drives whether the Sync button is visible. The user has
    // to be logged in to pull a fresh list from AniList.
    //
    // TokenStore exposes `accessToken: Flow<String?>` (not `token`!) and
    // `userId: Flow<String?>` — DataStore Preferences stores only primitives,
    // so userId is a String that we parse to Int here. Both flows are
    // nullable because Preferences.get-or-null defaults to missing.
    val token by app.tokenStore.accessToken.collectAsState(initial = null)
    val userIdStr by app.tokenStore.userId.collectAsState(initial = null)
    val userId = remember(userIdStr) { userIdStr?.toIntOrNull() ?: 0 }

    var isSyncing by remember { mutableStateOf(false) }
    var lastSyncError by remember { mutableStateOf<String?>(null) }

    // Local list snapshot for both "On my list" filtering and the per-card
    // progress badge.
    val localEntries by app.database.animeDao()
        .observeAll()
        .collectAsState(initial = emptyList())

    val progressByAnimeId = remember(localEntries) {
        localEntries.associate { it.anilistId to it }
    }

    // Tracked IDs from Room. Excludes `completed` and `dropped` since the
    // user signaled no future episodes are wanted for those.
    val trackedIds = remember(localEntries) {
        localEntries
            .filter { it.status in listOf("plan", "watching", "paused", "repeating") }
            .map { it.anilistId }
    }

    /**
     * Pull the user's anime list fresh from AniList and upsert into Room,
     * preserving local-only tier/elo. After upsert, the Room Flow re-emits
     * `localEntries`, which re-keys `trackedIds`, which re-fires
     * `LaunchedEffect(trackedIds)` and re-runs the per-anime airing query.
     * The whole chain is automatic — no manual refresh needed.
     */
    suspend fun syncNow() {
        // Smart-cast `token` once into a non-null local. After this, `t`
        // is `String` (not `String?`), so downstream calls don't need `!!`.
        val t = token ?: return
        if (t.isBlank() || userId <= 0 || isSyncing) return
        isSyncing = true
        lastSyncError = null
        try {
            // v1.2.1: sync both ANIME and MANGA so manga entries
            // flowing from AniList arrive in Room. Without this,
            // only anime rows land and the manga filter is empty.
            val animeResult = app.anilistClient.syncUserList(t, userId, "ANIME")
            val mangaResult = app.anilistClient.syncUserList(t, userId, "MANGA")
            val combined = (animeResult.entries.orEmpty() + mangaResult.entries.orEmpty())
            if (combined.isNotEmpty()) {
                val existing = app.database.animeDao().getAll().associateBy { it.anilistId }
                val merged = combined.map { incoming ->
                    incoming.preserveLocalFields(existing[incoming.anilistId])
                }
                app.database.animeDao().upsertAll(merged)
            }
            val firstErr = animeResult.error ?: mangaResult.error
            if (firstErr != null) {
                lastSyncError = firstErr
            }
        } catch (e: Exception) {
            lastSyncError = e.message ?: "Sync failed"
        } finally {
            isSyncing = false
        }
    }

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

    LaunchedEffect(trackedIds) {
        myListSlots = if (trackedIds.isEmpty()) emptyList()
                      else app.anilistClient.getNextAiringFor(trackedIds)
    }

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

        // Top utility row: trailing Sync-from-AniList button so the user
        // can freshen local data without leaving the screen. Discoverable
        // because it's right above the tab strip; visible even when local
        // list is empty.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!token.isNullOrBlank() && userId > 0) {
                IconButton(
                    onClick = { scope.launch { syncNow() } },
                    enabled = !isSyncing,
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = "Sync from AniList",
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        lastSyncError?.let { err ->
            Text(
                text = "Sync failed: $err",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

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
                            text = when {
                                mode == AiringMode.ON_MY_LIST &&
                                    localEntries.isEmpty() ->
                                    "Your local list is empty."
                                mode == AiringMode.ON_MY_LIST &&
                                    trackedIds.isEmpty() ->
                                    "None of your shows are still airing. Mark a show as Completed or Dropped to clear it from this view."
                                mode == AiringMode.ON_MY_LIST ->
                                    "None of your tracking shows have a published next episode yet."
                                mode == AiringMode.ALL ->
                                    "No airing schedule available right now."
                                else ->
                                    "Nothing to display."
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when {
                                mode == AiringMode.ON_MY_LIST &&
                                    localEntries.isEmpty() &&
                                    !token.isNullOrBlank() ->
                                    "Tap the Sync icon at the top-right to pull your AniList list into the app."
                                mode == AiringMode.ON_MY_LIST &&
                                    localEntries.isEmpty() ->
                                    "Sign in from the Profile tab, then tap Sync to pull your AniList list."
                                mode == AiringMode.ON_MY_LIST ->
                                    "Wait for AniList to publish the next schedule, or tap Sync to retry."
                                mode == AiringMode.ALL ->
                                    "Pull to refresh or check back later."
                                else ->
                                    ""
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

private fun dayBucket(epochSec: Long): String {
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
                    slot.averageScore?.takeIf { it > 0 }?.let { score ->
                        // v1.2 Airing enrichment: render the AniList
                        // average-score badge next to the episode count
                        // when non-null. Mirrors the AnimePosterCard style
                        // so the air tab and the explore surface agree.
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Accent.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "$score",
                                style = MaterialTheme.typography.labelSmall,
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
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
