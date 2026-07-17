package com.slippedpenguin.mangolist.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
 * gestures route through one Model 3 Card without the click-source conflict
 * that Material3's Card API causes when both `onClick` and a long-press are
 * needed.
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
                Text(
                    text = entry.title,
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
                    StatusPill(status = entry.status)
                    entryProgressText(entry)
                }
            }

            if (showTier) {
                EloBadge(tier = entry.tier, rankText = rankText)
            }
        }
    }
}

/*
 * Tiny helper — formats "0 / 12" (or "12 / 12 · completed") under each card.
 * Lives in this file because every AnimeCard slot needs the same shape.
 */
@Composable
private fun entryProgressText(entry: AnimeEntry) {
    val total = entry.episodes
    val now = entry.currentEp
    val text = if (total != null && total > 0) "$now / $total" else "$now ep"
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
    )
}
