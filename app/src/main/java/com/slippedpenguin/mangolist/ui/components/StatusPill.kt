package com.slippedpenguin.mangolist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.BorderStrong
import com.slippedpenguin.mangolist.ui.theme.TierC
import com.slippedpenguin.mangolist.ui.theme.TierD
import com.slippedpenguin.mangolist.ui.theme.TierUnranked

/*
 * Status pill — small labeled badge for the six list statuses AniHyou
 * surfaces today: "plan" / "watching" / "completed" / "dropped" and the
 * v0.5 pair "paused" / "repeating". Colors are stable across the app;
 * matches the JS prototype's `.status-pill` rule.
 */
@Composable
fun StatusPill(
    status: String,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (status) {
        "plan"      -> "PLAN"      to TierUnranked
        "watching"  -> "WATCHING"  to Accent
        "completed" -> "COMPLETED" to TierC
        "dropped"   -> "DROPPED"   to TierD
        "paused"    -> "PAUSED"    to TextMuted
        "repeating" -> "REPEATING" to Accent
        else        -> status.uppercase() to BorderStrong
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}
