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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.AiringSlot
import com.slippedpenguin.mangolist.ui.components.OfflineBanner
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextPrimary
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/*
 * Airing — v0.6: live 7-day schedule from AniList's GetAiringSchedule
 * query. Each card shows the cover, title, episode number, and a
 * relative countdown (\"in 3d 12h\"). Tap navigates to the detail screen.
 *
 * Falls back to the v0.5 placeholder on any fetch error.
 */
@Composable
fun AiringScreen(
    onNavigateDetail: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }

    var slots by remember { mutableStateOf<List<AiringSlot>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var now by remember { mutableStateOf(System.currentTimeMillis() / 1000) } // epoch seconds

    LaunchedEffect(Unit) {
        slots = app.anilistClient.getAiringSchedule()
        loading = false
    }

    // Tick every 60s to keep the countdown fresh.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis() / 1000
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OfflineBanner()
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            slots == null || slots!!.isEmpty() -> {
                // Fallback placeholder — same as v0.5 stub.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                ) {
                    Text(
                        text = "Airing this week",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No airing schedule available right now. Pull to refresh or check back later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                val items = slots!!
                // Group by day
                val grouped = items.groupBy { dayBucket(it.airingAt) }
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
                                onClick = { onNavigateDetail?.invoke(slot.animeId) },
                            )
                        }
                    }
                }
            }
        }
    }

/*
 * Day-bucket label: \"Today\", \"Tomorrow\", \"Wed, Jul 15\", etc.
 */
private fun dayBucket(epochSec: Long): String {
    val cal = java.util.Calendar.getInstance(TimeZone.getDefault()).apply {
        timeInMillis = epochSec * 1000
    }
    val now = java.util.Calendar.getInstance(TimeZone.getDefault())
    val diffDays = ((epochSec / 86400) - (now.timeInMillis / 86400 / 1000)).toInt()
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
                Text(
                    text = "Ep ${slot.episode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
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
