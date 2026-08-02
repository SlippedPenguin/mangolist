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
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(libraryFilters, key = { it.label }) { filter ->
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
