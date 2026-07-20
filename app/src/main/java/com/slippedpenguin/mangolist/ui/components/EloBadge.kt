package com.slippedpenguin.mangolist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slippedpenguin.mangolist.ui.theme.tierColor

/*
 * EloBadge — tier letter on top, the user's *rank within that tier* below
 * (e.g. "#3 of 8"). We deliberately stopped printing the raw Elo number
 * because naked Elo (1500, 1620, …) on cards is meaningless to anyone who
 * hasn't seen the engine docs. Rank-within-tier tells the user the
 * concrete thing they care about: how this anime stacks up against the
 * others they've put in the same tier.
 *
 * When the entry is unranked (tier == null) or no rank text is supplied,
 * the slot shows a centered dash so multiple unranked cards line up
 * neatly on the latch screen.
 */
@Composable
fun EloBadge(
    tier: String?,
    rankText: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tierColor(tier).copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = tier ?: "—",
                style = MaterialTheme.typography.labelMedium,
                color = tierColor(tier),
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = rankText ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}
