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
 * EloBadge — tier letter on top, current Elo score below. Floats on the
 * right edge of an AnimeCard. When the entry is unranked, the letter slot
 * shows a centered "·" but keeps the same outline so multiple unranked
 * cards line up neatly on the latch screen.
 */
@Composable
fun EloBadge(
    tier: String?,
    elo: Int,
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
                text = tier ?: "·",
                style = MaterialTheme.typography.labelMedium,
                color = tierColor(tier),
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = elo.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}
