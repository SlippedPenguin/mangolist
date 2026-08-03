package com.slippedpenguin.mangolist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slippedpenguin.mangolist.ui.theme.Accent

private data class LibraryFilter(val key: String?, val label: String)

private val libraryFilters = listOf(
    LibraryFilter(null, "All"),
    LibraryFilter("watching", "Watching"),
    LibraryFilter("completed", "Completed"),
    LibraryFilter("plan", "Planning"),
    LibraryFilter("paused", "Paused"),
    LibraryFilter("repeating", "Repeating"),
    LibraryFilter("dropped", "Dropped"),
    LibraryFilter("__favorites__", "Favorites"),
)

@Composable
fun LibraryFilterBar(
    selectedStatus: String?,
    counts: Map<String?, Int>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // v1.5.1: declutter — "All" always shows; status chips with zero
    // entries are hidden (plus the currently-selected chip is kept so a
    // selection never vanishes mid-session). The island reads as a short,
    // relevant list instead of seven always-on buttons.
    val visibleFilters = libraryFilters.filter { filter ->
        filter.key == null || (counts[filter.key] ?: 0) > 0 || selectedStatus == filter.key
    }
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(visibleFilters, key = { it.label }) { filter ->
            FilterChip(
                selected = selectedStatus == filter.key,
                onClick = { onSelect(filter.key) },
                label = { Text("${filter.label} ${counts[filter.key] ?: 0}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent.copy(alpha = 0.22f),
                    selectedLabelColor = Accent,
                ),
            )
        }
    }
}
