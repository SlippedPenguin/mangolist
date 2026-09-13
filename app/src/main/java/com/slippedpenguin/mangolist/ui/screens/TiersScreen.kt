package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.EloEngine
import com.slippedpenguin.mangolist.data.ScoreScale
import com.slippedpenguin.mangolist.data.TierListModel
import com.slippedpenguin.mangolist.data.TierSection
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.AnimeCard
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextPrimary
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.tierColor
import kotlinx.coroutines.launch

/*
 * Tiers — v1.7 drag & drop rebuild.
 *
 *   - Ranking is now a single gesture: press-hold a card and drag it into
 *     any tier row (or within its tier). The list live-reorders under the
 *     finger and the drop persists immediately. The v1.2 long-press →
 *     bottom-sheet → tap detour is gone — that detour was the friction.
 *   - "Unrank" happens by dragging into the Unranked bucket (or via the
 *     Detail screen's tracking card).
 *   - A "Rank unranked head-to-head" button opens the h2h flow
 *     (RankHeadToHeadScreen) for batch-ranking a fresh season one tap at
 *     a time; the one-tap "Rank from my ratings" seed remains.
 *
 * Implementation shape:
 *   - All reorder math lives in the pure TierListModel (unit-tested in CI).
 *     This file is the gesture shell.
 *   - Single LazyColumn through the whole drag: switching layouts mid-gesture
 *     would dispose the node holding the pointer stream and kill the drag.
 *     While a drag is active the DAO flows are frozen into a snapshot (Room
 *     updates can't yank rows mid-gesture) and each tier section renders one
 *     extra DropZone item after its last row — that zone is both the visual
 *     drop indicator (it grows and lights up when it's the pending target)
 *     and the hit target for "insert at end of tier", which is how empty
 *     tiers accept cards.
 *   - Row/dropzone bounds are tracked via onGloballyPositioned and validated
 *     against the current sections, so stale bounds from scrolled-away rows
 *     can never produce a phantom target.
 *   - Ranking writes never touch updatedAt (tier/elo/tierRank are
 *     local-only — bumping it would drain no-op pushes to AniList).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiersScreen(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val dao = remember { app.database.animeDao() }
    val scope = rememberCoroutineScope()

    val byTier = EloEngine.TIERS.associateWith { tier ->
        dao.observeByTier(tier).collectAsState(initial = emptyList())
    }
    val unranked by dao.observeUnranked().collectAsState(initial = emptyList())
    val allEntries by dao.observeAll().collectAsState(initial = emptyList())
    val accessToken by app.tokenStore.accessToken.collectAsState(initial = null)
    val userId      by app.tokenStore.userId.collectAsState(initial = null)
    val scoreScale by app.tokenStore.scoreScale.collectAsState(initial = ScoreScale.Default)

    // v1.5.1: titles that have a personal score but no tier yet — the
    // "Rank from my ratings" seed can rank these in one tap.
    val scoredUnranked = remember(allEntries) {
        allEntries.count { it.tier == null && (it.personalScore ?: 0) > 0 }
    }
    // v1.7: the h2h pool — every title without a tier yet.
    val unrankedCount = allEntries.count { it.tier == null }

    // ---- drag state ----
    // Non-null while a drag is active: the frozen snapshot the gesture
    // mutates. Null otherwise — the UI then renders straight from Room flows.
    var dragSections by remember { mutableStateOf<List<TierSection>?>(null) }
    var dragEntryId by remember { mutableStateOf<Int?>(null) }
    // The finger's root Y, accumulated per pointer event from the drag-start
    // center. Doubles as the drop probe AND the anchor for the visual
    // translation: the card draws at (probe − its CURRENT resting center),
    // evaluated at draw time — so when a reorder shifts the card's resting
    // slot mid-drag, the translation compensates and the card stays glued
    // to the finger instead of jumping.
    var dragProbeY by remember { mutableStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }
    // Bounds in root coordinates, refreshed on every layout pass.
    val rowBounds = remember { mutableMapOf<Int, Rect>() }
    val dropZoneBounds = remember { mutableMapOf<String?, Rect>() }
    val headerBounds = remember { mutableMapOf<String?, Rect>() }

    val flowSections = TierListModel.sectionsFrom(
        EloEngine.TIERS,
        EloEngine.TIERS.associateWith { byTier[it]?.value.orEmpty() },
        unranked,
    )
    val displaySections = dragSections ?: flowSections
    val dragActive = dragEntryId != null

    // The section the dragged card currently sits in (for drop-zone highlight).
    val draggedSection: String? = dragEntryId?.let { id ->
        displaySections.firstOrNull { s -> s.entries.any { it.anilistId == id } }?.tier
    }

    fun resolveDropTarget(draggedCenterY: Float, draggedId: Int): Pair<String?, Int>? {
        // 1) A row whose bounds contain the dragged card's center (skipping
        //    the dragged card itself, and rows that no longer exist in any
        //    section) = insert above it. The dragged card's own bounds act
        //    as the finger proxy: wherever the card is, the finger is.
        for ((id, rect) in rowBounds) {
            if (id == draggedId) continue
            if (draggedCenterY >= rect.top && draggedCenterY <= rect.bottom) {
                val section = displaySections.firstOrNull { s ->
                    s.entries.any { it.anilistId == id }
                } ?: continue
                val idx = section.entries.indexOfFirst { it.anilistId == id }
                return section.tier to idx
            }
        }
        // 2) A tier drop zone = insert at the end of that tier. Skipped for
        //    the tier the card already sits in (no-op move).
        for ((tier, rect) in dropZoneBounds) {
            if (tier == draggedSection) continue
            if (draggedCenterY >= rect.top && draggedCenterY <= rect.bottom) {
                val size = displaySections.firstOrNull { it.tier == tier }?.entries?.size ?: 0
                return tier to size
            }
        }
        // 3) A tier header (empty tiers have their drop zone right below,
        //    but headers are the wider, easier target).
        for ((tier, rect) in headerBounds) {
            if (draggedCenterY >= rect.top && draggedCenterY <= rect.bottom) return tier to 0
        }
        return null
    }

    fun startDrag(entry: AnimeEntry) {
        // The card is visible and laid out by the time it can be long-pressed,
        // so its resting bounds are registered. (Null bounds would mean a
        // recomposition raced the gesture — bail out instead of mis-dropping.)
        val startCenter = rowBounds[entry.anilistId]?.center?.y ?: return
        dragProbeY = startCenter
        dragEntryId = entry.anilistId
        // Freeze the flows into the mutable gesture snapshot.
        dragSections = flowSections
    }

    fun dragTo(deltaY: Float) {
        val id = dragEntryId ?: return
        dragProbeY += deltaY
        val current = displaySections
        val target = resolveDropTarget(dragProbeY, id) ?: return
        val currentSpot = current.firstOrNull { s -> s.entries.any { it.anilistId == id } }
            ?.let { s -> s.tier to s.entries.indexOfFirst { it.anilistId == id } }
        if (currentSpot != null && currentSpot != target) {
            dragSections = TierListModel.move(current, id, target.first, target.second)
        }
    }

    fun endDrag() {
        val snapshot = dragSections
        val id = dragEntryId
        if (snapshot != null && id != null) {
            val rows = TierListModel.commitRows(snapshot, id)
            if (rows.isNotEmpty()) {
                scope.launch { rows.forEach { dao.update(it) } }
            }
        }
        dragSections = null
        dragEntryId = null
        dragProbeY = 0f
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

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
                            app.database.animeDao().mergePullResults(combined)
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
                userScrollEnabled = !dragActive,
            ) {
                // Intro card stays composed during a drag (hidden, not removed)
                // so rows don't shift out from under the finger at drag start.
                item(key = "tier_intro") {
                    if (!dragActive) {
                        TierIntroCard(
                            scoredUnranked = scoredUnranked,
                            unrankedCount = unrankedCount,
                            onAutoRank = {
                                scope.launch {
                                    val scorable = dao.getAll()
                                        .filter { it.tier == null && (it.personalScore ?: 0) > 0 }
                                    if (scorable.isEmpty()) return@launch
                                    scorable.forEach { e ->
                                        val score = e.personalScore
                                        // v1.5.1: deliberately DON'T bump updatedAt —
                                        // tier/elo are local-only (never uploaded), so
                                        // touching updatedAt would flag every ranked
                                        // title as "pending sync" and trigger a wave
                                        // of no-op pushes to AniList.
                                        dao.update(
                                            e.copy(
                                                tier = EloEngine.tierForScore(score),
                                                elo = EloEngine.eloForScore(score),
                                            )
                                        )
                                    }
                                    android.widget.Toast.makeText(
                                        context,
                                        "Ranked ${scorable.size} titles from your ratings",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onRankHeadToHead = { navController.navigate("rank_h2h") },
                        )
                    }
                }
                displaySections.forEach { section ->
                    val isTargetZone = dragActive && draggedSection != section.tier
                    item(key = "header_${section.tier ?: "unranked"}") {
                        TierHeader(
                            tier = section.tier,
                            count = section.entries.size,
                            rangeText = scoreRangeText(section.entries, scoreScale),
                            modifier = Modifier.onGloballyPositioned {
                                headerBounds[section.tier] = it.boundsInRoot()
                            },
                        )
                    }
                    items(section.entries, key = { it.anilistId }) { entry ->
                        TierCard(
                            entry = entry,
                            sectionEntries = section.entries,
                            isDragging = dragEntryId == entry.anilistId,
                            dragActive = dragActive,
                            dragProbeY = dragProbeY,
                            navController = navController,
                            rowBounds = rowBounds,
                            onDragStart = ::startDrag,
                            onDragDelta = ::dragTo,
                            onDragEnd = ::endDrag,
                        )
                    }
                    item(key = "drop_${section.tier ?: "unranked"}") {
                        DropZone(
                            tier = section.tier,
                            // Highlight the zone when the dragged card could
                            // land here (any tier other than its own).
                            highlighted = isTargetZone,
                            modifier = Modifier.onGloballyPositioned {
                                dropZoneBounds[section.tier] = it.boundsInRoot()
                            },
                        )
                    }
                }
            }
        }
    }
}

/*
 * TierCard — AnimeCard wrapped in the drag gesture layer. A long-press
 * starts the drag (the card's own combinedClickable keeps handling taps);
 * during the drag the wrapper reports the finger's root Y so the screen can
 * re-target the drop position, and the dragged card visually lifts (scale).
 * While ANY drag is active, taps on other cards are ignored — the gesture
 * owns the screen until it ends.
 */
@Composable
private fun TierCard(
    entry: AnimeEntry,
    sectionEntries: List<AnimeEntry>,
    isDragging: Boolean,
    dragActive: Boolean,
    dragProbeY: Float,
    navController: NavController,
    rowBounds: MutableMap<Int, Rect>,
    onDragStart: (AnimeEntry) -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val rankText = rankWithinTierText(entry, sectionEntries)
    // OUTER box: registers the resting bounds (both the drop-probe reference
    // and the translation anchor — they update after every reorder's layout
    // pass, which is what keeps the card glued to the finger).
    Box(
        modifier = Modifier.onGloballyPositioned { rowBounds[entry.anilistId] = it.boundsInRoot() },
    ) {
        // INNER box: carries the gesture + the visual translation. The layer
        // lambda reads dragProbeY (a State) and the freshest resting bounds at
        // draw time, so translation = probe − currentRestingCenter always.
        Box(
            modifier = Modifier
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    if (isDragging) {
                        val resting = rowBounds[entry.anilistId]
                        translationY = if (resting != null) dragProbeY - resting.center.y else 0f
                        scaleX = 1.03f
                        scaleY = 1.03f
                        shadowElevation = 16f
                    }
                }
                .pointerDragInput(
                    onDragStart = { onDragStart(entry) },
                    onDragDelta = onDragDelta,
                    onDragEnd = onDragEnd,
                ),
        ) {
            AnimeCard(
                entry = entry,
                rankText = rankText,
                onClick = if (dragActive) ({}) else ({ navController.navigate("detail/${entry.mediaType}/${entry.anilistId}") }),
                onLongClick = {},
            )
        }
    }
}

/*
 * DropZone — the per-tier drop target rendered after each tier's last row.
 * Idle it is a thin invisible spacer; while a drag is in flight it grows
 * and shows the tier-colored dashed bar, so the user can see exactly where
 * the card will land. Its bounds double as the "insert at end of tier"
 * hit area (this is how empty tiers accept cards).
 */
@Composable
private fun DropZone(tier: String?, highlighted: Boolean, modifier: Modifier = Modifier) {
    val accent = tierColor(tier)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .animateContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (highlighted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "drop into ${tier ?: "Unranked"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

/*
 * pointerDragInput — tiny helper wrapping detectDragGesturesAfterLongPress
 * into a reusable Modifier. Lives here because only the tierlist needs it.
 * The long-press threshold is the system default (~400ms), so the gesture
 * reads as "hold, then move" and never fights plain taps. `onDragDelta`
 * forwards the vertical pointer movement per event; the screen accumulates
 * it and hit-tests startCenter + offset (see TiersScreen.dragTo).
 */
private fun Modifier.pointerDragInput(
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { onDragStart() },
            onDrag = { change, _ -> onDragDelta(change.positionChange().y) },
            onDragEnd = onDragEnd,
            onDragCancel = onDragEnd,
        )
    }
)

private fun scoreRangeText(entries: List<AnimeEntry>, scale: ScoreScale): String? {
    val scores = entries.mapNotNull { it.personalScore }.filter { it > 0 }
    return if (scores.isNotEmpty()) {
        "${formatScore(scores.min(), scale)} – ${formatScore(scores.max(), scale)}"
    } else null
}

/*
 * TierIntroCard — v1.7. Explains how tiers persist (private, on-device
 * only — never pushed to AniList) and offers two ranking entry points:
 * the head-to-head session for batch-ranking unranked titles, and the
 * one-tap "Rank from my ratings" seed (v1.5.1).
 */
@Composable
private fun TierIntroCard(
    scoredUnranked: Int,
    unrankedCount: Int,
    onAutoRank: () -> Unit,
    onRankHeadToHead: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your tiers save only on this device",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tiers live in your local list and are never uploaded to AniList. " +
                    "Hold a card and drag it into a tier — or rank unranked titles head-to-head below.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRankHeadToHead,
                enabled = unrankedCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {                    Text(
                    text = if (unrankedCount > 0) "Rank $unrankedCount unranked head-to-head"
                           else "Everything's ranked",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (scoredUnranked > 0) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAutoRank,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Rank $scoredUnranked from my ratings")
                }
            }
        }
    }
}

/*
 * TierHeader — tier letter chip + count badge + score range.
 */
@Composable
private fun TierHeader(tier: String?, count: Int, rangeText: String?, modifier: Modifier = Modifier) {
    val accent = tierColor(tier)
    Row(
        modifier = modifier
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
            text = rangeText?.let { "$it score" } ?: "hold & drag a card here",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

/*
 * formatScore — v1.5.7. Renders a stored 0-100 score on the user's scale
 * for the tier header range ("8.5" for out-of-10, "85" for out-of-100).
 */
private fun formatScore(score: Int, scale: ScoreScale): String = when (scale) {
    ScoreScale.OUT_OF_10  -> "%.1f".format(score / 10.0)
    ScoreScale.OUT_OF_100 -> score.toString()
}

private fun rankWithinTierText(
    target: AnimeEntry,
    tierEntries: List<AnimeEntry>,
): String? {
    val sorted = tierEntries.sortedWith(
        compareByDescending<AnimeEntry> { it.personalScore ?: 0 }.thenByDescending { it.elo },
    )
    val idx = sorted.indexOfFirst { it.anilistId == target.anilistId }
    return if (idx < 0) null else "#${idx + 1} of ${sorted.size}"
}
