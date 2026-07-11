package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.EloEngine
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.AnimeCard
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.Border
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextPrimary
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.tierColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Tiers — vertical rail of five rows (S / A / B / C / D) plus an Unranked
 * bucket. Each row is sorted by Elo descending. The header shows the
 * live Elo range for that tier ("1850–2050") so the user gets a quick
 * sense of where their collection sits.
 *
 * v0.5 long-press wiring:
 *   1. Long-press any AnimeCard => ModalBottomSheet shows the five
 *      tiers + an "Unranked" option.
 *   2. Pick a target tier => if the target tier has 3+ opponents,
 *      open VsModeDialog (3 head-to-head matches driven by
 *      EloEngine.update). Otherwise pre-commit at INITIAL_ELO.
 *   3. Each match tap lights up the winner for ~900ms then advances.
 *   4. After 3 matches, the root entry gets `tier = target, elo =
 *      finalElo`; opponent entries have their elos updated in place.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    // Tier-picker sheet state — non-null means a long-press is in flight.
    var longPressEntry by remember { mutableStateOf<AnimeEntry?>(null) }

    // Vs-mode dialog state — non-null root opens the dialog.
    var vsRoot       by remember { mutableStateOf<AnimeEntry?>(null) }
    var vsTargetTier by remember { mutableStateOf<String?>(null) }
    var vsOpponents  by remember { mutableStateOf<List<AnimeEntry>>(emptyList()) }
    // Mutable snapshotting of mid-flow elos so we don't need to read from
    // Room during dialog interactions.
    var vsRootElo        by remember { mutableStateOf(1500) }
    var vsOpponentElos   by remember { mutableStateOf(listOf<Int>()) }
    var vsStep           by remember { mutableStateOf(0) }     // 0..2
    var vsPicked         by remember { mutableStateOf<Int?>(null) } // 0=root, 1=opponent

    fun startVsMode(entry: AnimeEntry, targetTier: String) {
        val pool = (byTier[targetTier]?.value.orEmpty())
            .filter { it.anilistId != entry.anilistId }
        if (pool.size < 3) {
            // Target tier is too sparse for vs-mode. Just commit directly.
            scope.launch {
                dao.update(
                    entry.copy(
                        tier = targetTier,
                        elo = EloEngine.INITIAL_ELO,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
            return
        }
        vsRoot = entry
        vsTargetTier = targetTier
        vsOpponents = pool.shuffled().take(3)
        vsOpponentElos = vsOpponents.map { it.elo }
        vsRootElo = entry.elo
        vsStep = 0
        vsPicked = null
    }

    fun pickInMatch(pickedRoot: Boolean) {
        if (vsPicked != null) return  // ignore double-tap during the reveal pause
        val oppElo = vsOpponentElos[vsStep]
        val upd = if (pickedRoot) EloEngine.update(vsRootElo, oppElo)
                  else            EloEngine.update(oppElo, vsRootElo)
        if (pickedRoot) {
            vsRootElo = upd.newWinner
        } else {
            vsOpponentElos = vsOpponentElos.toMutableList().apply {
                this[vsStep] = upd.newWinner
            }
        }
        vsPicked = if (pickedRoot) 0 else 1
        scope.launch {
            delay(900) // reveal pause so the winner pulse is readable
            vsPicked = null
            if (vsStep < 2) {
                vsStep++
            } else {
                val root = vsRoot
                val tier = vsTargetTier
                val opponents = vsOpponents
                val oppElos = vsOpponentElos
                val now = System.currentTimeMillis()
                if (root != null && tier != null) {
                    dao.update(
                        root.copy(
                            tier = tier,
                            elo = vsRootElo,
                            updatedAt = now,
                        )
                    )
                    opponents.forEachIndexed { idx, op ->
                        if (oppElos[idx] != op.elo) {
                            dao.update(
                                op.copy(elo = oppElos[idx], updatedAt = now)
                            )
                        }
                    }
                }
                vsRoot = null
                vsTargetTier = null
                vsOpponents = emptyList()
                vsOpponentElos = emptyList()
                vsStep = 0
            }
        }
    }

    fun cancelVsMode() {
        vsRoot = null
        vsTargetTier = null
        vsOpponents = emptyList()
        vsOpponentElos = emptyList()
        vsStep = 0
        vsPicked = null
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                        eloRange = eloRangeOf(entries),
                    )
                }
                items(entries, key = { it.anilistId }) { entry ->
                    AnimeCard(
                        entry = entry,
                        onClick = { navController.navigate("detail/${entry.anilistId}") },
                        onLongClick = { longPressEntry = entry },
                    )
                }
            }
            item(key = "header_unranked") {
                TierHeader(
                    tier = null,
                    count = unranked.size,
                    eloRange = eloRangeOf(unranked),
                )
            }
            items(unranked, key = { it.anilistId }) { entry ->
                AnimeCard(
                    entry = entry,
                    onClick = { navController.navigate("detail/${entry.anilistId}") },
                    onLongClick = { longPressEntry = entry },
                )
            }
        }
    }

    // Long-press → ModalBottomSheet with the five tier buttons.
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
                    TextButton(
                        onClick = {
                            val entry = sheetFor
                            longPressEntry = null
                            startVsMode(entry, tier)
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
                                text = tierHint(
                                    tier,
                                    byTier[tier]?.value.orEmpty(),
                                ),
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
                    onClick = { longPressEntry = null },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    }

    // Vs-mode dialog
    val vsModeRoot = vsRoot
    val vsModeTarget = vsTargetTier
    if (vsModeRoot != null && vsModeTarget != null && vsOpponents.isNotEmpty()) {
        VsModeDialog(
            root = vsModeRoot,
            rootElo = vsRootElo,
            opponent = vsOpponents[vsStep],
            opponentElo = vsOpponentElos[vsStep],
            targetTier = vsModeTarget,
            step = vsStep,
            total = 3,
            picked = vsPicked,
            onPick = { pickInMatch(it) },
            onCancel = { cancelVsMode() },
        )
    }
}

/*
 * TierHeader — tier letter chip + count badge + Elo range hint. A
 * colored left-stripe (Box of 4.dp width) reinforces which tier you're
 * looking at without taking extra vertical space.
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
            text = eloRangeHint(tier, count, eloRange),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

/* ------------------------------------------------------------------ *\
 *  VsModeDialog — three head-to-head cards driven by EloEngine.update *
\* ------------------------------------------------------------------ */
@Composable
private fun VsModeDialog(
    root: AnimeEntry,
    rootElo: Int,
    opponent: AnimeEntry,
    opponentElo: Int,
    targetTier: String,
    step: Int,
    total: Int,
    picked: Int?,
    onPick: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Match ${step + 1} of $total · rank into tier $targetTier",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = "Tap the anime you rank higher.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    VsModeCard(
                        entry = root,
                        elo = rootElo,
                        label = "ROOT",
                        isWinner = picked == 0,
                        isLoser = picked == 1,
                        enabled = picked == null,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(true) },
                    )
                    VsModeCard(
                        entry = opponent,
                        elo = opponentElo,
                        label = "OPPONENT",
                        isWinner = picked == 1,
                        isLoser = picked == 0,
                        enabled = picked == null,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(false) },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Winner gets +Elo · loser gets −Elo (K=32)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

/*
 * VsModeCard — single head-to-head side. Outline flips to Accent when
 * the side has won the current match; dims when it lost; tappable only
 * while neither has been picked yet.
 */
@Composable
private fun VsModeCard(
    entry: AnimeEntry,
    elo: Int,
    label: String,
    isWinner: Boolean,
    isLoser: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val borderColor = when {
        isWinner -> Accent
        isLoser  -> Border
        else     -> Border
    }
    val borderWidth = if (isWinner) 2.dp else 1.dp
    Card(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoser) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(borderWidth, borderColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = entry.cover,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Elo $elo",
                    style = MaterialTheme.typography.labelMedium,
                    color = tierColor(entry.tier),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ *\
 *  Helpers — Elo bounds, hint text, layout-side sp helper             *
\* ------------------------------------------------------------------ */
private fun eloRangeOf(entries: List<AnimeEntry>): IntRange? {
    if (entries.isEmpty()) return null
    val lo = entries.minOf { it.elo }
    val hi = entries.maxOf { it.elo }
    return lo..hi
}

private fun eloRangeHint(tier: String?, count: Int, eloRange: IntRange?): String {
    val rangeTail = "Elo · long-press to rank"
    if (count == 0) {
        return if (tier == null) "long-press any card to rank"
               else "tap a card → long-press to rank"
    }
    val lo = eloRange?.first ?: return "long-press to rank further"
    val hi = eloRange?.last ?: return "long-press to rank further"
    return "$lo-$hi $rangeTail"
}

private fun tierHint(
    tier: String,
    entries: List<AnimeEntry>,
): String {
    val inTier = entries
    return when {
        inTier.size < 3 -> "(vs-mode skipped · ${inTier.size} in tier)"
        inTier.isEmpty() -> "(empty · instant rank)"
        else -> {
            val med = EloEngine.medianElo(tier, inTier) ?: EloEngine.INITIAL_ELO
            "median $med"
        }
    }
}


