package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.theme.TierUnranked
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Add — live AniList search.
 *
 * Flow:
 *   1. User types in the search field. `query` state holds the raw text.
 *   2. `LaunchedEffect(query)` debounces 350ms then calls
 *      `app.anilistClient.search(query)` (Apollo 4.x SearchAnime query).
 *   3. Results render as tappable rows (cover + title + subtitle + "+ Add").
 *   4. Tapping "+ Add":
 *        - upserts the AnimeEntry into Room (kills any conflicting row by
 *          anilistId primary key)
 *        - navigates to the detail screen for that anilistId
 *      If already in the list, the primary-key replace keeps the existing
 *      row's tier/elo/currentEp untouched (we only need a defensive
 *      no-op there).
 */
@Composable
fun AddScreen(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AnimeEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var inListIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Refresh the already-in-list set so we can disable "+ Add" when needed.
    LaunchedEffect(Unit) {
        app.database.animeDao().observeAll().collect { all ->
            inListIds = all.map { it.anilistId }.toSet()
        }
    }

    // Debounced search.
    LaunchedEffect(query) {
        val cleaned = query.trim()
        if (cleaned.length < 2) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        // Wait for the user to stop typing.
        delay(350)
        loading = true
        results = app.anilistClient.search(cleaned)
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Add anime",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search AniList…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = TierUnranked.copy(alpha = 0.25f),
            ),
        )

        Spacer(Modifier.height(16.dp))

        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            query.trim().length < 2 -> {
                Text(
                    text = "Type at least 2 letters to search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            results.isEmpty() -> {
                Text(
                    text = "No matches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.anilistId }) { result ->
                        SearchResultRow(
                            entry = result,
                            inList = result.anilistId in inListIds,
                            onAdd = {
                                scope.launch {
                                    // Room's upsert is REPLACE-on-conflict, so calling it
                                    // on a row that already exists would DELETE the
                                    // existing tier / elo / currentEp / notes and re-insert
                                    // fresh search-shaped defaults. Only insert when this
                                    // anime is genuinely new to the local list. We re-check
                                    // the membership here because `inList` is the SearchResultRow
                                    // argument NAME (named-arg syntax doesn't bind a local at
                                    // the call site) — both `result` and `inListIds` are in
                                    // scope from the items-lambda / AddScreen local.
                                    if (result.anilistId !in inListIds) {
                                        app.database.animeDao().upsert(result)
                                    }
                                    navController.navigate("detail/${result.anilistId}")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(entry: AnimeEntry, inList: Boolean, onAdd: () -> Unit) {
    val year = entry.year?.toString().orEmpty()
    val epLabel = entry.episodes?.let { " · $it ep" } ?: ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = entry.cover,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 56.dp, height = 80.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (year.isNotEmpty() || epLabel.isNotEmpty()) {
                Text(
                    text = (year + epLabel).trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = onAdd) {
            Text(if (inList) "Open" else "+ Add")
        }
    }
}
