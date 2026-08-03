package com.slippedpenguin.mangolist.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.slippedpenguin.mangolist.ui.theme.BorderStrong
import com.slippedpenguin.mangolist.ui.theme.StatusCompleted
import com.slippedpenguin.mangolist.ui.theme.StatusDropped
import com.slippedpenguin.mangolist.ui.theme.StatusPaused
import com.slippedpenguin.mangolist.ui.theme.StatusPlan
import com.slippedpenguin.mangolist.ui.theme.StatusRepeating
import com.slippedpenguin.mangolist.ui.theme.StatusWatching

/*
 * Status pill — small labeled badge for the six list statuses AniHyou
 * surfaces today: "plan" / "watching" / "completed" / "dropped" and the
 * v0.5 pair "paused" / "repeating". Colors are stable across the app;
 * matches the JS prototype's `.status-pill` rule.
 *
 * v1.5.3: media-type-aware labels — manga reads "READING" / "REREADING"
 * instead of "WATCHING" / "REPEATING". AniList stores the same status
 * string for both, so the label is derived here at render time.
 */
@Composable
fun StatusPill(
    status: String,
    modifier: Modifier = Modifier,
    mediaType: String = "ANIME",
) {
    val (label, color) = when (status) {
        "plan"      -> "PLAN"      to StatusPlan
        "watching"  -> (if (mediaType == "MANGA") "READING" else "WATCHING") to StatusWatching
        "completed" -> "COMPLETED" to StatusCompleted
        "dropped"   -> "DROPPED"   to StatusDropped
        "paused"    -> "PAUSED"    to StatusPaused
        "repeating" -> (if (mediaType == "MANGA") "REREADING" else "REPEATING") to StatusRepeating
        else        -> status.uppercase() to BorderStrong
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}
