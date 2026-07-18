package com.slippedpenguin.mangolist.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.tierColor

/*
 * AnimePosterCard — compact vertical poster card used inside the horizontal
 * `LazyRow` carousels on the Explore screen (Popular / Trending / Coming
 * Soon / Top Rated). Distinct from `AnimeCard` (full-width list row):
 *
 *   - 140dp wide, poster + title underneath (vs. cover-thumbnail + multi-line
 *     status row + trailing tier badge for `AnimeCard`).
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
        modifier = modifier
            .width(140.dp)
            .padding(4.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onClick() },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Tier-tinted fallback if the cover URL is missing or the
                // AsyncImage fails to load — prevents an unstyled gray box.
                AsyncImage(
                    model = entry.cover,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(tierColor(entry.tier).copy(alpha = 0.3f)),
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
