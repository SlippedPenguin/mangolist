package com.slippedpenguin.mangolist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.TextSecondary

private val libraryFilterKeys = listOf(
    null,
    "watching",
    "completed",
    "plan",
    "paused",
    "repeating",
    "dropped",
    "__favorites__",
)

/*
 * v1.5.3: manga-aware status filter labels. AniList stores one status
 * string for both media types, so the display label is derived at render
 * time: anime "watching"/"repeating", manga "reading"/"rereading".
 */
internal fun statusFilterLabel(status: String?, mediaType: String): String = when (status) {
    "watching"  -> if (mediaType == "MANGA") "Reading" else "Watching"
    "repeating" -> if (mediaType == "MANGA") "Rereading" else "Repeating"
    "__favorites__" -> "Favorites"
    null        -> "All"
    else        -> status.replaceFirstChar { it.uppercase() }
}

/*
 * LibraryFilterBar — AniHyou-style collapsible category filter.
 *
 * v1.5.3 collapsed the category row behind a corner button; v1.5.5 centers
 * it: the collapsed state is a single centered pill (icon + current filter
 * + count) and the expanded category chips wrap centered instead of hugging
 * the left edge, so the top of the list always reads balanced.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterBar(
    selectedStatus: String?,
    counts: Map<String?, Int>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    mediaType: String = "ANIME",
) {
    // The bar opens collapsed; rememberSaveable keeps the open/closed
    // state across config changes (rotation) but resets per screen visit.
    var expanded by rememberSaveable { mutableStateOf(false) }

    val visibleFilters = libraryFilterKeys.filter { key ->
        key == null || (counts[key] ?: 0) > 0 || selectedStatus == key
    }
    val currentLabel = statusFilterLabel(selectedStatus, mediaType)
    val currentCount = counts[selectedStatus] ?: 0

    Column(modifier = modifier) {
        // Centered pill — icon + active filter + count as one tap target.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (expanded) Accent.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = if (expanded) "Collapse status filter"
                                         else "Expand status filter",
                    tint = if (expanded) Accent else TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "$currentLabel · $currentCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedStatus == null) TextSecondary else Accent,
                )
            }
        }

        // v1.5.5: chips slide open/closed under the pill and wrap centered
        // instead of hugging the left edge of a LazyRow.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = androidx.compose.animation.core.tween(180)) + fadeIn(),
            exit = shrinkVertically(animationSpec = androidx.compose.animation.core.tween(140)) + fadeOut(),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                visibleFilters.forEach { key ->
                    FilterChip(
                        selected = selectedStatus == key,
                        onClick = { onSelect(key) },
                        label = { Text("${statusFilterLabel(key, mediaType)} ${counts[key] ?: 0}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.22f),
                            selectedLabelColor = Accent,
                        ),
                    )
                }
            }
        }
    }
}
