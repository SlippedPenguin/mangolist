package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.EloEngine
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.AnimeCard
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextPrimary
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.tierColor
import kotlinx.coroutines.launch

/*
 * Tiers — v1.2 simplification.
 *
 *   - Five rows (S / A / B / C / D) + an Unranked bucket. Each row
 *     sorted by Elo descending. Header shows the tier letter + count +
 *     elo range hint.
 *
 *   - **vs-mode removed.** v0.5 had a 3-round head-to-head dialog where
 *     picking a tier with ≥ 3 opponents would open VsModeDialog and
 *     walk the user through 3 picks to settle the entry's Elo. The user
 *     flagged tier-list as "kinda wack" — vs-mode was the wack part —
 *     so we drop the entire flow: picking a tier from the bottom sheet
 *     commits immediately with `elo = INITIAL_ELO`. No more 3-round
 *     horse-trading ceremony. The local Elo engine still runs vs-mode
 *     for any entry that already has a starting Elo from a previous
 *     version (EloEngine.update is unchanged and still used by tests),
 *     but the UI no longer surfaces the dialog.
 *
 *   - Tier letters stay as S/A/B/C/D (user feedback wanted the existing
 *     labels preserved, only the ceremony simplified).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiersScreen(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val dao = remember { app.database.animeDao() }
    val scope = rememberCoroutineScope()

    val byTier = EloEngine.TIERS.associateWith { tier ->
        dao.observeByTier(tier).collectAsState(initial = emptyList())
    }
    val unranked by dao.observeUnranked().collectAsState(initial = emptyList())
    val accessToken by app.tokenStore.accessToken.collectAsState(initial = null)
    val userId      by app.tokenStore.userId.collectAsState(initial = null)

    var isRefreshing by remember { mutableStateOf(false) }
    var longPressEntry by remember { mutableStateOf<AnimeEntry?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                val tok = accessToken
                val id  = userId
                if (tok.isNullOrBlank() || id.isNullOrBlank()) return@PullToRefreshBox
                scope.launch {
                    isRefreshing = true
                    try {
                        // v1.2: pull both ANIME and MANGA on refresh — they
                        // share the same Room table keyed by anilistId.
                        val animeResult = app.anilistClient.syncUserList(tok, id.toInt(), "ANIME")
                        val mangaResult = app.anilistClient.syncUserList(tok, id.toInt(), "MANGA")
                        val combined = (animeResult.entries.orEmpty() + mangaResult.entries.orEmpty())
                        if (combined.isNotEmpty()) {
                            val existing = app.database.animeDao().getAll().associateBy { it.anilistId }
                            val merged = combined.map { it.preserveLocalFields(existing[it.anilistId]) }
                            app.database.animeDao().upsertAll(merged)
                        }
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                EloEngine.TIERS.forEach { tier ->
                    val entries = byTier[tier]?.value ?: emptyList()
                    item(key = "header_$tier") {
                        TierHeader(
                            tier = tier,
                            count = entries.size,
                            eloRange = entries.minOfOrNull { it.elo }?.let { lo ->
                                lo..(entries.maxOfOrNull { it.elo } ?: lo)
                            },
                        )
                    }
                    items(entries, key = { it.anilistId }) { entry ->
                        AnimeCard(
                            entry = entry,
                            rankText = rankWithinTierText(entry, entries),
                            onClick = { navController.navigate("detail/${entry.anilistId}") },
                            onLongClick = { longPressEntry = entry },
                        )
                    }
                }
                item(key = "header_unranked") {
                    TierHeader(
                        tier = null,
                        count = unranked.size,
                        eloRange = null,
                    )
                }
                items(unranked, key = { it.anilistId }) { entry ->
                    AnimeCard(
                        entry = entry,
                        rankText = null,
                        onClick = { navController.navigate("detail/${entry.anilistId}") },
                        onLongClick = { longPressEntry = entry },
                    )
                }
            }
        }
    }

    // Long-press → ModalBottomSheet with the five tier buttons. Picking
    // any tier commits the entry at INITIAL_ELO — no head-to-head rounds.
    val sheetFor = longPressEntry
    if (sheetFor != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { longPressEntry = null },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Rank “${sheetFor.title.take(28)}${if (sheetFor.title.length > 28) "…" else ""}” into…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                )
                EloEngine.TIERS.forEach { tier ->
                    val inTier = byTier[tier]?.value.orEmpty()
                    TextButton(
                        onClick = {
                            val entry = sheetFor
                            longPressEntry = null
                            // Direct commit — no vs-mode ceremony. The
                            // entry's previous tier (if any) is replaced;
                            // its previous Elo is reset to INITIAL_ELO so
                            // tier rank within the new tier starts fresh.
                            scope.launch {
                                dao.update(
                                    entry.copy(
                                        tier = tier,
                                        elo = EloEngine.INITIAL_ELO,
                                        updatedAt = System.currentTimeMillis(),
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = tier,
                                style = MaterialTheme.typography.titleLarge,
                                color = tierColor(tier),
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = if (inTier.isEmpty()) "(empty · first in tier)" else "${inTier.size} in tier",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                            Spacer(Modifier.weight(1f))
                            if (sheetFor.tier == tier) {
                                Text(
                                    text = "current",
                                    color = TextMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            sheetFor.let { e ->
                                dao.update(
                                    e.copy(
                                        tier = null,
                                        elo = EloEngine.INITIAL_ELO,
                                        updatedAt = System.currentTimeMillis(),
                                    )
                                )
                            }
                            longPressEntry = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Text("Unranked", color = TextSecondary)
                }
                TextButton(
                    onClick = { longPressEntry = null },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    }
}

/*
 * TierHeader — tier letter chip + count badge. v1.2 simplification: the
 * Elo range hint still appears in plain text (e.g. "1850–2050") so the
 * user retains a quick sense of where the tier sits, but the dialog no
 * longer drills down into vs-mode rounds.
 */
@Composable
private fun TierHeader(tier: String?, count: Int, eloRange: IntRange?) {
    val accent = tierColor(tier)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            text = tier ?: "Unranked",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            color = accent,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accent.copy(alpha = 0.18f))
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
            text = eloRange?.let { "${it.first}–${it.last} Elo" } ?: "long-press any card to rank",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

/*
 * rankWithinTierText — "#3 of 8" style label for an entry inside its
 * tier. Sorted by Elo descending so #1 is the entry the user already
 * likes most in that tier. Returns null for the unranked bucket (the
 * badge then falls back to a centered dash).
 */
private fun rankWithinTierText(
    target: AnimeEntry,
    tierEntries: List<AnimeEntry>,
): String? {
    val sorted = tierEntries.sortedByDescending { it.elo }
    val idx = sorted.indexOfFirst { it.anilistId == target.anilistId }
    return if (idx < 0) null else "#${idx + 1} of ${sorted.size}"
}
