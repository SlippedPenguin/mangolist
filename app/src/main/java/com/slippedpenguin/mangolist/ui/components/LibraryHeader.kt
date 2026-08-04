package com.slippedpenguin.mangolist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.TextSecondary

/**
 * Compact library header shared by the Anime and Manga destinations.
 *
 * v1.5.6: the "Discover" button is gone; the top-right corner now holds
 * two icon buttons — a filter (category selection, AniHyou style) and the
 * three-line sort button. Both open DropdownMenus with the shared
 * filter/sort vocabularies from LibraryFilterBar.kt.
 */
@Composable
fun LibraryHeader(
    title: String,
    subtitle: String,
    total: Int,
    active: Int,
    planned: Int,
    selectedStatus: String?,
    counts: Map<String?, Int>,
    onSelectStatus: (String?) -> Unit,
    sortMode: LibrarySortMode,
    onSortModeChange: (LibrarySortMode) -> Unit,
    mediaType: String = "ANIME",
) {
    var filterMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }

                // Category selection — corner filter button (AniHyou style).
                Box {
                    IconButton(onClick = { filterMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = "Filter by status",
                            tint = if (selectedStatus != null) Accent else TextSecondary,
                        )
                    }
                    DropdownMenu(
                        expanded = filterMenuOpen,
                        onDismissRequest = { filterMenuOpen = false },
                    ) {
                        libraryFilterKeys
                            .filter { key -> key == null || (counts[key] ?: 0) > 0 || key == selectedStatus }
                            .forEach { key ->
                                val isCurrent = key == selectedStatus
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${statusFilterLabel(key, mediaType)} (${counts[key] ?: 0})",
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    trailingIcon = {
                                        if (isCurrent) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Current filter",
                                                tint = Accent,
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectStatus(key)
                                        filterMenuOpen = false
                                    },
                                )
                            }
                    }
                }

                // Three-line sort button.
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Sort,
                            contentDescription = "Sort list",
                            tint = Accent,
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        LibrarySortMode.entries.forEach { mode ->
                            val isCurrent = mode == sortMode
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.label,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                trailingIcon = {
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Current sort",
                                            tint = Accent,
                                        )
                                    }
                                },
                                onClick = {
                                    onSortModeChange(mode)
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryMetric(value = total.toString(), label = "Titles", modifier = Modifier.weight(1f))
                LibraryMetric(value = active.toString(), label = "In progress", modifier = Modifier.weight(1f))
                LibraryMetric(value = planned.toString(), label = "Planning", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LibraryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = Accent,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}
