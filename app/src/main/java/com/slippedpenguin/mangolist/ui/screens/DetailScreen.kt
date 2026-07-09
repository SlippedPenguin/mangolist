package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.EloEngine
import com.slippedpenguin.mangolist.ui.components.StatusPill
import com.slippedpenguin.mangolist.ui.theme.BgDeep
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.tierColor

/*
 * Detail — hero image, status pill, episode +/− controls, tier changer.
 * v1.0 wires:
 *   - the − / + buttons to AppDatabase.update(currentEp clamp [0, episodes])
 *   - the "Change tier" sheet to a quick pop-up that resets elo on the
 *     engine's INITIAL_ELO and triggers the vs-mode ladder
 *   - SaveMediaListEntry mutation that pushes progress back to AniList
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(navController: NavController, anilistId: Int) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val entry by app.database.animeDao().observeById(anilistId)
        .collectAsState(initial = null)

    val e = entry
    Column(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        TopAppBar(
            title = { Text(e?.title ?: "Loading…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        if (e == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Not in your list yet — visit the Add tab.", color = TextSecondary)
            }
            return@Column
        }

        // Hero — cover image with a tier-coloured tint behind it so the screen
        // never goes fully white during a load failure.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(tierColor(e.tier).copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = e.cover,
                contentDescription = e.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(status = e.status)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Elo ${e.elo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tierColor(e.tier),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.height(16.dp))

            EpisodeRow(
                current = e.currentEp,
                total = e.episodes,
                onMinus = { /* v1.0: dao.update(e.copy(currentEp = max(0, current-1))) */ },
                onPlus  = { /* v1.0: dao.update(e.copy(currentEp = min(total ?: +∞, current+1))) */ },
            )

            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = { /* v1.0: open tier picker sheet → vs-mode */ },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Change tier") }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { /* v1.0: SaveMediaListEntry mutation */ },
                modifier = Modifier.fillMaxWidth(),
                enabled = e.tier != null,
            ) { Text("Sync to AniList") }
        }
    }
}

@Composable
private fun EpisodeRow(current: Int, total: Int?, onMinus: () -> Unit, onPlus: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onMinus, enabled = current > 0) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(
                text = "$current / ${total ?: "?"}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            TextButton(
                onClick = onPlus,
                enabled = total?.let { current < it } ?: true,
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
