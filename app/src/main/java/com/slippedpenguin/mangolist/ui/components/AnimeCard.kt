package com.slippedpenguin.mangolist.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.Accent2
import com.slippedpenguin.mangolist.ui.theme.BorderSubtle
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.brandGradient
import com.slippedpenguin.mangolist.ui.theme.tierColor

/*
 * AnimeCard — the workhorse row used by the Watchlist, Tier rows, and Airing
 * schedules. Tap fires `onClick`; long-press fires `onLongClick` (TiersScreen
 * passes null for both so its drag wrapper owns gestures).
 *
 * v1.9 — one-tap tracking + polish:
 *   - `onQuickIncrement` (set by the watchlists): renders a tappable
 *     progress pill — "7 / 12 ep" in a brand-gradient frame. Tapping it
 *     increments progress right from the list (ManGo's headliner) with a
 *     haptic tick, WITHOUT opening the title. Tapping anywhere else on the
 *     card still opens Detail.
 *   - Card restyled to the v1.8/1.9 language: shapes.medium geometry,
 *     hairline border, gradient progress hairline instead of the flat bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeCard(
    entry: AnimeEntry,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = {},
    onLongClick: (() -> Unit)? = {},
    showTier: Boolean = true,
    rankText: String? = null,
    showSyncPending: Boolean = false,
    showFavorite: Boolean = false,
    onQuickIncrement: (() -> Unit)? = null,
) {
    // v1.7.1: onClick/onLongClick are nullable — passing null renders a
    // plain, gesture-free card (the tierlist drag wrapper owns gestures).
    val interaction = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick ?: {},
            onLongClick = onLongClick ?: {},
        )
    } else Modifier

    // v1.9: the pill is a separate click target *inside* the card; the
    // outer combinedClickable only receives taps that land outside it.
    val view = LocalView.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(interaction),
        colors = CardDefaults.cardColors(
            // v1.8: cards sit on the true-black canvas and barely lift —
            // depth comes from the hairline, not gray boxes.
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        // v1.5.0: subtle hairline border + shadow-less depth (Anihyou-style
        // cards read as layered surfaces, not raised shadows).
        border = BorderStroke(1.dp, BorderSubtle),
        // v1.8: inherit the app shape scale (shapes.medium = 16dp) so every
        // card in the app shares one geometry language.
        shape = MaterialTheme.shapes.medium,
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
            // never leaves a blank dark box.
            CoverImage(
                model = entry.cover,
                contentDescription = entry.title,
                tint = tierColor(entry.tier).copy(alpha = 0.35f),
                label = entry.title,
                modifier = Modifier
                    .size(width = 68.dp, height = 98.dp)
                    .clip(MaterialTheme.shapes.small),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showFavorite && entry.favourite || showSyncPending && entry.isPendingSync()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        if (showFavorite && entry.favourite) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "Favorite",
                                tint = Accent,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        if (showSyncPending && entry.isPendingSync()) {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = "Local edit pending sync",
                                tint = Accent.copy(alpha = 0.75f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                // v1.9: the progress pill IS the +1 button on list surfaces.
                if (onQuickIncrement != null) {
                    QuickProgressPill(
                        label = progressPillLabel(entry),
                        fraction = entryProgressFraction(entry),
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onQuickIncrement()
                        },
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusIcon(status = entry.status, mediaType = entry.mediaType)
                        Text(
                            text = progressPillLabel(entry),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                        )
                    }
                    entryProgressFraction(entry)?.let { fraction ->
                        Spacer(Modifier.height(6.dp))
                        LinearProgressHairline(fraction)
                    }
                }
            }

            if (showTier) {
                EloBadge(tier = entry.tier, rankText = rankText)
            }
        }
    }
}

/*
 * QuickProgressPill — v1.9. A bordered pill showing "7 / 12 ep" with a
 * tiny + icon; the whole thing is the tap target for +1 from the list.
 * Gradient border at low alpha signals "action", distinct from the rest
 * of the card's flat hairline language.
 */
@Composable
private fun QuickProgressPill(
    label: String,
    fraction: Float?,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, Accent.copy(alpha = 0.35f)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increment progress",
                    tint = Accent,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(14.dp),
                )
            }
        }
        fraction?.let {
            Spacer(Modifier.width(8.dp))
            LinearProgressHairline(it, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LinearProgressHairline(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .background(Brush.horizontalGradient(listOf(Accent, Accent2))),
        )
    }
}

/*
 * `isPendingSync` — entry has local edits not yet pushed to AniList.
 *
 * - `syncedAt == null` → never been pushed.
 * - `updatedAt > syncedAt` → user edited the entry after the last push.
 */
internal fun AnimeEntry.isPendingSync(): Boolean {
    val synced = syncedAt ?: return true
    return updatedAt > synced
}

/*
 * `entryProgressTotal` — the series/chapter/volume total, normalized by
 * format. Delegates to the shared v1.9 helper so Detail, the card, and the
 * swipe actions all agree on what "total" means.
 */
private fun entryProgressTotal(entry: AnimeEntry): Int? =
    com.slippedpenguin.mangolist.data.progressTotalFor(entry)

/*
 * `entryProgressFraction` — 0..1 progress. Null when there's no known total.
 */
private fun entryProgressFraction(entry: AnimeEntry): Float? {
    val total = entryProgressTotal(entry) ?: return null
    if (total <= 0) return null
    return (entry.currentEp.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/*
 * "7 / 12 ep" label, manga/novel-aware. Shared shape for both pill and
 * plain rendering.
 */
private fun progressPillLabel(entry: AnimeEntry): String {
    val unit = com.slippedpenguin.mangolist.data.progressUnitFor(entry)
    val total = entryProgressTotal(entry)
    return if (total != null && total > 0) "${entry.currentEp} / $total $unit"
    else "${entry.currentEp} $unit"
}
