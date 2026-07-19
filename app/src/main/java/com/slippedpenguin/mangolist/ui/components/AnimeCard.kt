package com.slippedpenguin.mangolist.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.tierColor

/*
 * AnimeCard — the workhorse row used by the Watchlist, Tier rows, and Airing
 * schedules. Tap fires `onClick`; long-press fires `onLongClick` (TiersScreen
 * uses this to open the tier-picker sheet that feeds vs-mode). The EloBadge
 * on the trailing edge is opt-out via `showTier = false` for surfaces that
 * don't rank yet.
 *
 * v0.5: switched from `Card.onClick` to `Modifier.combinedClickable` so both
 * gestures route through one Material3 Card without the click-source conflict
 * that Material3's Card API causes when both `onClick` and a long-press are
 * needed.
 *
 * v0.8.5 (Anihyou parity): two opt-in UX extras for list surfaces:
 *   - `showSyncPending = true` adds a small cloud-upload icon next to the
 *     title when the entry has local edits not yet pushed to AniList
 *     (syncedAt is null OR updatedAt > syncedAt).
 *   - `showRelativeTimestamp = true` adds an "Edited X ago" line under the
 *     status pill + progress row using android.text.format.DateUtils.
 *     Defaults to false so Tiers/Airing rows stay compact.
 *
 * v0.9.0 (favourites): a third opt-in
 *   - `showFavorite = true` renders a small filled `Star` icon next to the
 *     title when `entry.favourite == true`. Order: title -> favorite ->
 *     cloud-upload so favourite reads as a property and cloud-upload reads
 *     as transient dirty state. Tiers/Airing omit it; Watchlist passes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeCard(
    entry: AnimeEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    showTier: Boolean = true,
    rankText: String? = null,
    showSyncPending: Boolean = false,
    showRelativeTimestamp: Boolean = false,
    showFavorite: Boolean = false,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Poster — falls back to a tier-tinted block if the URL is missing or fails to load.
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tierColor(entry.tier).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = entry.cover,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (showFavorite && entry.favourite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Favorite",
                            tint = Accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (showSyncPending && entry.isPendingSync()) {
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = "Local edit pending sync",
                            tint = Accent.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusPill(status = entry.status)
                    entryProgressText(entry)
                }
                if (showRelativeTimestamp) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Edited ${relativeTimeText(entry.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
            }

            if (showTier) {
                EloBadge(tier = entry.tier, rankText = rankText)
            }
        }
    }
}

/*
 * `isPendingSync` — entry has local edits not yet pushed to AniList.
 *
 * - `syncedAt == null` → never been pushed (e.g. added from Add tab and the
 *   user hasn't hit sync yet).
 * - `updatedAt > syncedAt` → user edited the entry after the last push.
 *
 * Used by AnimeCard's `showSyncPending` opt-in and any future "needs sync"
 * indicators (e.g. a top-bar badge driven by dao.observePendingCount()).
 */
internal fun AnimeEntry.isPendingSync(): Boolean {
    val synced = syncedAt ?: return true
    return updatedAt > synced
}

/*
 * `relativeTimeText` — single source of truth for "X ago" labels on
 * cards. Wraps android.text.format.DateUtils so the user's locale +
 * system preferences pick the right format (e.g. "2h ago" vs
 * "2 hours ago"). MINUTE_IN_MILLIS keeps minutes as the smallest
 * displayed unit; FORMAT_ABBREV_RELATIVE picks the abbreviation.
 */
private fun relativeTimeText(epochMs: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

/*
 * Tiny helper — formats "0 / 12" (or "12 / 12 · completed") under each card.
 * Lives in this file because every AnimeCard slot needs the same shape.
 * v1.3: manga/novel-aware unit label.
 */
@Composable
private fun entryProgressText(entry: AnimeEntry) {
    val total = entry.episodes
    val now = entry.currentEp
    val unit = when {
        entry.format == "NOVEL" || entry.format == "LIGHT_NOVEL" -> "vol"
        entry.mediaType == "MANGA" -> "ch"
        else -> "ep"
    }
    val text = if (total != null && total > 0) "$now / $total $unit" else "$now $unit"
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
    )
}
