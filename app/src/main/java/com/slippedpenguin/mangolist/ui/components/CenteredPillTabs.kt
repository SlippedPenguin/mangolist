package com.slippedpenguin.mangolist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.brandGradient

/*
 * CenteredPillTabs — v1.5.6. Centered, pill-style tab row used by the
 * Anime / Manga top-level tabs (Watchlist / Explore / Airing) and the
 * Profile tab row (Overview / Activity / Stats / Settings).
 *
 * Replaces the left-aligned ScrollableTabRow so the sub-navigation reads
 * balanced on wide screens, matching AniHyou's centered pill look.
 * The rows are short (2–4 pills) so they always fit — no scrolling needed.
 * Pass `icons` (same length as `tabs`) to render a leading icon per pill,
 * which is how Profile gets its icon-driven tab row.
 *
 * v1.5.8: `showLabels` — when false the text label is dropped entirely so
 * the row is pure icons (Profile's tab row). `tabs` strings still drive
 * accessibility content descriptions in icon-only mode.
 *
 * v1.8 redesign: the selected pill fills with the brand gradient
 * (periwinkle→violet) and its label flips to on-primary — pills now read
 * as the app's accent instead of a translucent tint.
 */
@Composable
fun CenteredPillTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null,
    showLabels: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        // Brush overload only — the color branch must be
                        // wrapped in SolidColor to type-match the gradient.
                        if (selected) brandGradient()
                        else SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    icons?.getOrNull(index)?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = if (showLabels) null else label,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else TextSecondary,
                            modifier = Modifier.size(if (showLabels) 16.dp else 18.dp),
                        )
                    }
                    if (showLabels) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
