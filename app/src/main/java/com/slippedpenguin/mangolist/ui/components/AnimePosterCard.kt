package com.slippedpenguin.mangolist.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.theme.BorderSubtle
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.tierColor

/*
 * AnimePosterCard — compact vertical poster card used inside the horizontal
 * `LazyRow` carousels on the Explore screen (Popular / Trending / Coming
 * Soon / Top Rated) and the genre-filtered `LazyVerticalGrid` (one tile
 * per genre result). Distinct from `AnimeCard` (full-width list row):
 *
 *   - Width is caller-controlled via the `modifier` parameter. Carousels
 *     pass `Modifier.width(140.dp)` so cards stay at fixed density across
 *     the row; the genre grid passes `Modifier.fillMaxWidth()` so each
 *     tile scales to its adaptive cell width on phones *and* tablets.
 *   - No status pill / progress text / tier badge. The Explore flows are
 *     browsing-only; tracking belongs on Watchlist.
 *   - No favourites / sync-pending badges — those overlay the user's local
 *     list, not the discovery browsing surface.
 *
 * Tap fires `onClick` which routes to DetailScreen for the underlying
 * anilistId. The card never reads or writes to Room; it's purely a
 * presentation component.
 */
@Composable
fun AnimePosterCard(
    entry: AnimeEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(4.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onClick() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            // v1.5.0: hairline border keeps poster tiles crisp on the
            // dark Explore carousels (Anihyou-style flat surfaces).
            border = BorderStroke(1.dp, BorderSubtle),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // CoverImage draws a tier-tinted letter placeholder when the
                // cover is missing or fails to load, so poster tiles never
                // render as a blank dark box on the Explore carousels.
                CoverImage(
                    model = entry.cover,
                    contentDescription = entry.title,
                    tint = tierColor(entry.tier).copy(alpha = 0.35f),
                    label = entry.title,
                    modifier = Modifier.fillMaxSize(),
                )
                if (entry.averageScore != null && entry.averageScore > 0) {
                    // AniList's averageScore is 0-100; render as a small badge
                    // in the top-right. We deliberately use the raw value (e.g.
                    // "82") rather than a star emoji per v0.9's UI brief.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${entry.averageScore}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entry.year?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            entry.format?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }
    }
}
