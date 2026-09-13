package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.EloEngine
import com.slippedpenguin.mangolist.data.RankSession
import com.slippedpenguin.mangolist.data.TierListModel
import com.slippedpenguin.mangolist.data.TierSection
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.AnimeCard
import com.slippedpenguin.mangolist.ui.components.CoverImage
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextPrimary
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.tierColor
import kotlinx.coroutines.launch

/*
 * Tiers — v1.7.1: tier-maker layout + working drag.
 *
 * Layout (the v1.7.0 "labeled list of cards" read as a settings page, not
 * a tier list): every ranked tier renders as a tier-maker block — a big
 * tier-colored letter rail on the left, the entries as a compact poster
 * grid on the right (FlowRow wraps like tiermaker.com). The Unranked
 * backlog stays as full-width cards: it's a working list, and progress on
 * the posters matters there.
 *
 * Drag (fixed from v1.7.0, where AnimeCard's combinedClickable consumed
 * the long-press before the drag detector ever saw it):
 *   - Tier cards are rendered gesture-free (AnimeCard onClick = null) and
 *     the WRAPPER chains long-press-drag BEFORE combinedClickable. A quick
 *     tap falls through to Detail; a hold starts the drag with a haptic.
 *   - The probe is a full Offset (x + y): horizontal position inside a
 *     FlowRow picks insert-before vs insert-after the hit card.
 *   - Drop zones spring open under the live probe ("drop into A"), the
 *     dragged card translates at draw time (probe − current resting
 *     center) so it stays glued to the finger across reorders.
 *   - Ranking writes never touch updatedAt (local-only fields — bumping
 *     it would drain no-op pushes to AniList).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    val scoredUnranked = remember(allEntries) {
        allEntries.count { it.tier == null && (it.personalScore ?: 0) > 0 }
    }
    // v1.7.1: h2h is gated to finished-or-rated titles — the button shows
    // how many are actually READY so the flow never opens into a dead end.
    val h2hReady = remember(allEntries) { allEntries.count { RankSession.isEligible(it) } }

    // ---- drag state ----
    var dragSections by remember { mutableStateOf<List<TierSection>?>(null) }
    var dragEntryId by remember { mutableStateOf<Int?>(null) }
    // Finger position in root coordinates, accumulated per pointer event
    // from the drag-start anchor. Drives BOTH the drop probe and the visual
    // translation (probe − current resting center, evaluated at draw time).
    var dragProbe by remember { mutableStateOf(Offset.Zero) }
    var isRefreshing by remember { mutableStateOf(false) }
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

    val draggedSection: String? = dragEntryId?.let { id ->
        displaySections.firstOrNull { s -> s.entries.any { it.anilistId == id } }?.tier
    }
    val pendingTarget: Pair<String?, Int>? = if (dragActive && dragEntryId != null) {
        resolveDropTarget(displaySections, rowBounds, dropZoneBounds, headerBounds, dragProbe, dragEntryId, draggedSection)
    } else null

    fun startDrag(entry: AnimeEntry) {
        val anchor = rowBounds[entry.anilistId]?.center ?: return
        dragProbe = anchor
        dragEntryId = entry.anilistId
        dragSections = flowSections
    }

    fun dragTo(delta: Offset) {
        val id = dragEntryId ?: return
        dragProbe += delta
        val current = displaySections
        val target = resolveDropTarget(current, rowBounds, dropZoneBounds, headerBounds, dragProbe, id, draggedSection) ?: return
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
        dragProbe = Offset.Zero
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
                item(key = "tier_intro") {
                    if (!dragActive) {
                        TierIntroCard(
                            scoredUnranked = scoredUnranked,
                            h2hReady = h2hReady,
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
                    if (section.tier == null) {
                        // Unranked backlog — full cards (progress matters here).
                        item(key = "header_unranked") {
                            UnrankedHeader(
                                count = section.entries.size,
                                modifier = Modifier.onGloballyPositioned {
                                    headerBounds[null] = it.boundsInRoot()
                                },
                            )
                        }
                        items(section.entries, key = { it.anilistId }) { entry ->
                            TierRowCard(
                                entry = entry,
                                isDragging = dragEntryId == entry.anilistId,
                                dragActive = dragActive,
                                dragProbe = dragProbe,
                                navController = navController,
                                rowBounds = rowBounds,
                                onDragStart = ::startDrag,
                                onDragDelta = ::dragTo,
                                onDragEnd = ::endDrag,
                            )
                        }
                        item(key = "drop_unranked") {
                            DropZone(
                                tier = null,
                                highlighted = pendingTarget?.first == null && draggedSection != null,
                                modifier = Modifier.onGloballyPositioned {
                                    dropZoneBounds[null] = it.boundsInRoot()
                                },
                            )
                        }
                    } else {
                        // Ranked tier — tier-maker block: letter rail + poster grid.
                        item(key = "tier_block_${section.tier}") {
                            TierBlock(
                                section = section,
                                isDragTarget = pendingTarget?.first == section.tier,
                                dragEntryId = dragEntryId,
                                dragActive = dragActive,
                                dragProbe = dragProbe,
                                navController = navController,
                                rowBounds = rowBounds,
                                headerBounds = headerBounds,
                                onDragStart = ::startDrag,
                                onDragDelta = ::dragTo,
                                onDragEnd = ::endDrag,
                            )
                        }
                        item(key = "drop_${section.tier}") {
                            DropZone(
                                tier = section.tier,
                                highlighted = pendingTarget?.first == section.tier && draggedSection != section.tier,
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
}

/*
 * resolveDropTarget — hit-test the probe against (1) card rects (insert
 * before/after based on x within the hit card), (2) per-tier drop zones
 * (insert at end), (3) tier rails/headers (insert at start). Stale rects
 * from recomposed rows are skipped via section membership validation.
 */
private fun resolveDropTarget(
    sections: List<TierSection>,
    rowBounds: Map<Int, Rect>,
    dropZoneBounds: Map<String?, Rect>,
    headerBounds: Map<String?, Rect>,
    probe: Offset,
    draggedId: Int,
    draggedSection: String?,
): Pair<String?, Int>? {
    for ((id, rect) in rowBounds) {
        if (id == draggedId) continue
        if (rect.contains(probe)) {
            val section = sections.firstOrNull { s -> s.entries.any { it.anilistId == id } } ?: continue
            val idx = section.entries.indexOfFirst { it.anilistId == id }
            val insertAfter = probe.x > rect.center.x
            return section.tier to (idx + if (insertAfter) 1 else 0)
        }
    }
    for ((tier, rect) in dropZoneBounds) {
        if (tier == draggedSection) continue
        if (rect.contains(probe)) {
            val size = sections.firstOrNull { it.tier == tier }?.entries?.size ?: 0
            return tier to size
        }
    }
    for ((tier, rect) in headerBounds) {
        if (rect.contains(probe)) return tier to 0
    }
    return null
}

/*
 * TierBlock — the tier-maker row: colored letter rail + count on the left,
 * compact poster grid (FlowRow) on the right. The rail registers its bounds
 * as an "insert at start" drop zone for its tier.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierBlock(
    section: TierSection,
    isDragTarget: Boolean,
    dragEntryId: Int?,
    dragActive: Boolean,
    dragProbe: Offset,
    navController: NavController,
    rowBounds: MutableMap<Int, Rect>,
    headerBounds: MutableMap<String?, Rect>,
    onDragStart: (AnimeEntry) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val accent = tierColor(section.tier)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDragTarget) accent.copy(alpha = 0.10f) else Color.Transparent),
        verticalAlignment = Alignment.Top,
    ) {
        // Letter rail — also the "insert at start" hit zone.
        Column(
            modifier = Modifier
                .width(64.dp)
                .padding(4.dp)
                .onGloballyPositioned { headerBounds[section.tier] = it.boundsInRoot() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = section.tier ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    color = accent,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${section.entries.size}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
        if (section.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "hold & drag a card here",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        } else {
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                section.entries.forEach { entry ->
                    TierMiniCard(
                        entry = entry,
                        isDragging = dragEntryId == entry.anilistId,
                        dragActive = dragActive,
                        dragProbe = dragProbe,
                        accent = accent,
                        navController = navController,
                        rowBounds = rowBounds,
                        onDragStart = onDragStart,
                        onDragDelta = onDragDelta,
                        onDragEnd = onDragEnd,
                    )
                }
            }
        }
    }
}

/*
 * TierMiniCard — compact poster card used inside tier blocks. Gesture-free
 * poster + title; the wrapper owns tap + long-press-drag (same contract as
 * TierRowCard, so a tap opens Detail and a hold drags).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TierMiniCard(
    entry: AnimeEntry,
    isDragging: Boolean,
    dragActive: Boolean,
    dragProbe: Offset,
    accent: Color,
    navController: NavController,
    rowBounds: MutableMap<Int, Rect>,
    onDragStart: (AnimeEntry) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier.onGloballyPositioned { rowBounds[entry.anilistId] = it.boundsInRoot() },
    ) {
        Column(
            modifier = Modifier
                .width(76.dp)
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    if (isDragging) {
                        val resting = rowBounds[entry.anilistId]
                        if (resting != null) {
                            translationX = dragProbe.x - resting.center.x
                            translationY = dragProbe.y - resting.center.y
                        }
                        scaleX = 1.06f
                        scaleY = 1.06f
                        shadowElevation = 16f
                    }
                }
                .pointerDragInput(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDragStart(entry)
                    },
                    onDragDelta = onDragDelta,
                    onDragEnd = onDragEnd,
                )
                .then(
                    if (!dragActive) {
                        Modifier.combinedClickable(
                            onClick = { navController.navigate("detail/${entry.mediaType}/${entry.anilistId}") },
                        )
                    } else Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 106.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isDragging) Modifier.background(accent.copy(alpha = 0.35f)) else Modifier
                    ),
            ) {
                CoverImage(
                    model = entry.cover,
                    contentDescription = entry.title,
                    label = entry.title,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/*
 * TierRowCard — full-width card wrapper for the Unranked backlog. Same
 * gesture contract as TierMiniCard: wrapper owns tap + long-press-drag;
 * the AnimeCard itself renders gesture-free.
 */
@Composable
private fun TierRowCard(
    entry: AnimeEntry,
    isDragging: Boolean,
    dragActive: Boolean,
    dragProbe: Offset,
    navController: NavController,
    rowBounds: MutableMap<Int, Rect>,
    onDragStart: (AnimeEntry) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier.onGloballyPositioned { rowBounds[entry.anilistId] = it.boundsInRoot() },
    ) {
        Box(
            modifier = Modifier
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    if (isDragging) {
                        val resting = rowBounds[entry.anilistId]
                        if (resting != null) translationY = dragProbe.y - resting.center.y
                        scaleX = 1.03f
                        scaleY = 1.03f
                        shadowElevation = 16f
                    }
                }
                .pointerDragInput(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDragStart(entry)
                    },
                    onDragDelta = onDragDelta,
                    onDragEnd = onDragEnd,
                )
                .then(
                    if (!dragActive) {
                        Modifier.combinedClickable(
                            onClick = { navController.navigate("detail/${entry.mediaType}/${entry.anilistId}") },
                        )
                    } else Modifier
                ),
        ) {
            AnimeCard(entry = entry, onClick = null, onLongClick = null)
        }
    }
}

/*
 * pointerDragInput — long-press-drag helper. `onDragDelta` forwards the
 * pointer movement per event; the screen accumulates it into the probe.
 */
private fun Modifier.pointerDragInput(
    onDragStart: () -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { onDragStart() },
            onDrag = { change, _ -> onDragDelta(change.positionChange()) },
            onDragEnd = onDragEnd,
            onDragCancel = onDragEnd,
        )
    }
)

/*
 * DropZone — springs open under the live probe into a tier-tinted landing
 * strip; doubles as the "insert at end of tier" hit area.
 */
@Composable
private fun DropZone(tier: String?, highlighted: Boolean, modifier: Modifier = Modifier) {
    val accent = tierColor(tier)
    val stripHeight by animateDpAsState(
        targetValue = if (highlighted) 40.dp else 6.dp,
        animationSpec = tween(durationMillis = 180),
        label = "dropZoneHeight",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stripHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(if (highlighted) accent.copy(alpha = 0.14f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (highlighted) {
                Text(
                    text = "drop into ${tier ?: "Unranked"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/*
 * UnrankedHeader — backlog header with drag hint.
 */
@Composable
private fun UnrankedHeader(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Unranked",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            color = TextSecondary,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
            text = "hold & drag a card into a tier",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }
}

/*
 * TierIntroCard — v1.7.1. Two ranking entry points; the h2h button shows
 * the READY count (finished-or-rated titles) so the gate is visible up
 * front instead of surprising the user inside the flow.
 */
@Composable
private fun TierIntroCard(
    scoredUnranked: Int,
    h2hReady: Int,
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
                    "Hold a card and drag it into a tier — or settle a batch head-to-head.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRankHeadToHead,
                enabled = h2hReady > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (h2hReady > 0) "Rank $h2hReady ready titles head-to-head"
                           else "Nothing ready to rank yet",
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


