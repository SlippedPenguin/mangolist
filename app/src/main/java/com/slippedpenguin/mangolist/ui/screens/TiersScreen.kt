package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.EloEngine
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.AnimeCard
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.TextPrimary
import com.slippedpenguin.mangolist.ui.theme.tierColor

/*
 * Tiers — vertical rail of five rows (S / A / B / C / D) plus an Unranked
 * section at the bottom. Each row is sorted by Elo descending. The header
 * circle is a constant-width swatch so a single empty tier doesn't shift
 * alignment.
 *
 * v1.0 adds the vs-mode invocation (long-press a card → "Rank it" → pick a
 * home tier → 3 head-to-head matches → `EloEngine.update` mutates entries).
 */
@Composable
fun TiersScreen(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val dao = remember { app.database.animeDao() }

    val byTier = EloEngine.TIERS.associateWith { tier ->
        dao.observeByTier(tier).collectAsState(initial = emptyList())
    }
    val unranked by dao.observeUnranked().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        EloEngine.TIERS.forEach { tier ->
            item(key = "header_$tier") { TierHeader(tier = tier, count = byTier[tier]?.value?.size ?: 0) }
            val entries = byTier[tier]?.value ?: emptyList()
            items(entries, key = { it.anilistId }) { entry ->
                AnimeCard(
                    entry = entry,
                    onClick = { navController.navigate("detail/${entry.anilistId}") },
                )
            }
        }
        item(key = "header_unranked") {
            TierHeader(tier = null, count = unranked.size)
        }
        items(unranked, key = { it.anilistId }) { entry ->
            AnimeCard(
                entry = entry,
                onClick = { navController.navigate("detail/${entry.anilistId}") },
            )
        }
    }
}

@Composable
private fun TierHeader(tier: String?, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = tier ?: "Unranked",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = tierColor(tier),
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(tierColor(tier).copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Text(
            text = if (tier != null) "sorted by Elo" else "long-press to rank",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}
