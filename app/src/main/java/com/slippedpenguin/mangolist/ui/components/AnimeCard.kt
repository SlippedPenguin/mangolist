package com.slippedpenguin.mangolist.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.BorderSubtle
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
 *
 * v1.5.5: the text status pill became a small colored icon (`StatusIcon`)
 * and every card shows a thin progress bar (current progress / total) so
 * list rows read at a glance. The "Edited X ago" line was removed — that
 * detail moved to the Profile Activity tab.
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        // v1.5.0: subtle hairline border + shadow-less depth (Anihyou-style
        // cards read as layered surfaces, not raised shadows).
        border = BorderStroke(1.dp, BorderSubtle),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Poster — CoverImage renders a tier-tinted letter placeholder
            // when the URL is missing or fails to load, so a broken cover
            // never leaves a blank dark box (the "manga cards show nothing"
            // bug was this: data fine, fallback absent).
            // v1.5.7: bigger poster (AniHyou-sized rows) so the cover art
            // carries the card. 68x98 keeps a 2:3 manga ratio.
            CoverImage(
                model = entry.cover,
                contentDescription = entry.title,
                tint = tierColor(entry.tier).copy(alpha = 0.35f),
                label = entry.title,
                modifier = Modifier
                    .size(width = 68.dp, height = 98.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )

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
                    StatusIcon(status = entry.status, mediaType = entry.mediaType)
                    entryProgressText(entry)
                }
                entryProgressFraction(entry)?.let { fraction ->
                    Spacer(Modifier.height(6.dp))
                    // v1.5.7: StrokeCap.Butt removes the rounded end-cap that
                    // rendered as a stray "dot" at the tip of the bar.
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp)),
                        color = Accent.copy(alpha = 0.85f),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Butt,
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
 * `entryProgressTotal` — the series/chapter/volume total used by both the
 * progress text and the progress bar, normalized by format so anime counts
 * episodes, manga counts chapters, and novels count volumes.
 */
private fun entryProgressTotal(entry: AnimeEntry): Int? = when {
    entry.format == "NOVEL" || entry.format == "LIGHT_NOVEL" -> entry.volumes ?: entry.episodes
    entry.mediaType == "MANGA" -> entry.chapters ?: entry.episodes
    else -> entry.episodes
}

/*
 * `entryProgressFraction` — 0..1 progress for the card's LinearProgressIndicator.
 * Null when there's no known total (nothing to measure).
 */
private fun entryProgressFraction(entry: AnimeEntry): Float? {
    val total = entryProgressTotal(entry) ?: return null
    if (total <= 0) return null
    return (entry.currentEp.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/*
 * Tiny helper — formats "0 / 12" (or "12 / 12 · completed") under each card.
 * Lives in this file because every AnimeCard slot needs the same shape.
 * v1.3: manga/novel-aware unit label and total.
 */
@Composable
private fun entryProgressText(entry: AnimeEntry) {
    val unit = when {
        entry.format == "NOVEL" || entry.format == "LIGHT_NOVEL" -> "vol"
        entry.mediaType == "MANGA" -> "ch"
        else -> "ep"
    }
    val total = entryProgressTotal(entry)
    val now = entry.currentEp
    val text = if (total != null && total > 0) "$now / $total $unit" else "$now $unit"
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
    )
}
