package com.slippedpenguin.mangolist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * v1.5.1 pinned a full row of status chips above the list so categories
 * never scrolled out of reach — but that made the island permanently eat
 * vertical space and read as a second tab row. v1.5.3 collapses it behind
 * a corner button (filter icon + current selection) exactly like the
 * reference client: tap to expand the chips, tap again to collapse.
 */
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
        // Corner button — the whole tap target shows the active filter so
        // the current category is always readable even when collapsed.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$currentLabel · $currentCount",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selectedStatus == null) TextSecondary else Accent,
                modifier = Modifier.padding(start = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = if (expanded) "Collapse status filter"
                                         else "Expand status filter",
                    tint = if (expanded) Accent else TextSecondary,
                )
            }
        }

        // v1.5.3: chips slide open/closed under the corner button instead
        // of squatting permanently above the list.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = androidx.compose.animation.core.tween(180)) + fadeIn(),
            exit = shrinkVertically(animationSpec = androidx.compose.animation.core.tween(140)) + fadeOut(),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleFilters, key = { it ?: "all" }) { key ->
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
