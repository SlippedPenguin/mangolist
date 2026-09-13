package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.RankSession
import com.slippedpenguin.mangolist.ui.components.CoverImage
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextPrimary
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/*
 * RankHeadToHeadScreen — v1.7.1.
 *
 * Two titles side-by-side; tap the one you liked more; the next pair
 * appears. Every judgment runs through the pure RankSession reducer
 * (EloEngine.update + proposeTier), so the math is unit-tested in CI and
 * this file only renders state and persists `session.committedRows`.
 *
 * v1.7.1 changes (user feedback):
 *   - Sessions are PER MEDIUM — a segmented Anime/Manga toggle re-seeds
 *     the session. Anime and manga are different mediums; comparing them
 *     head-to-head was meaningless.
 *   - The pool only contains titles a judgment can actually be made about:
 *     finished (completed status or reached the known episode/chapter cap)
 *     or already rated. Unfinished + unrated titles are excluded (see
 *     RankSession.isEligible) — they can still be tiered manually by drag.
 *
 * The session lives in `remember` keyed on the medium; committed rows are
 * persisted on every choose(). Ranking writes never touch updatedAt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankHeadToHeadScreen(navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val dao = remember { app.database.animeDao() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var medium by rememberSaveable { mutableStateOf("ANIME") }
    var session by remember { mutableStateOf<RankSession?>(null) }
    var poolSizeAtSeed by remember { mutableStateOf(0) }
    var seededFor by remember { mutableStateOf<String?>(null) }

    fun seed(targetMedium: String) {
        if (seededFor == targetMedium) return
        seededFor = targetMedium
        scope.launch {
            val all = dao.getAll()
            val inMedium = all.filter { it.mediaType == targetMedium }
            val ranked = inMedium.filter { it.tier != null }
            val pool = inMedium.filter { RankSession.isEligible(it) }
            poolSizeAtSeed = pool.size
            session = RankSession.start(ranked, pool)
        }
    }

    fun commit(next: RankSession) {
        session = next
        val rows = next.committedRows
        if (rows.isNotEmpty()) {
            scope.launch { rows.forEach { dao.update(it) } }
        }
    }

    // Re-seed whenever the medium changes (and on first composition).
    // LaunchedEffect keeps the state writes out of composition.
    androidx.compose.runtime.LaunchedEffect(medium) { seed(medium) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 60.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            // Medium toggle — the session below is scoped to this medium.
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = medium == "ANIME",
                    onClick = { medium = "ANIME" },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Anime") }
                SegmentedButton(
                    selected = medium == "MANGA",
                    onClick = { medium = "MANGA" },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Manga") }
            }
            Spacer(Modifier.height(12.dp))

            val s = session
            when {
                s == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
                s.isDone && poolSizeAtSeed == 0 -> {
                    EmptySessionState(
                        medium = medium,
                        onBack = { navController.popBackStack() },
                    )
                }
                s.isDone -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "All ranked!",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${s.judgedCount} ${medium.mediumTitle} placed into tiers. " +
                                "Fine-tune the order by dragging on the tier list.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { navController.popBackStack() }) {
                            Text("Back to tiers")
                        }
                    }
                }
                else -> {
                    val candidate = s.candidate
                    val opponent = s.opponent
                    if (candidate == null || opponent == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    } else {
                        ProgressHeader(
                            judged = s.judgedCount,
                            total = poolSizeAtSeed,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Which did you enjoy more?",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            H2HCard(
                                entry = candidate,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    commit(s.choose(candidateWins = true))
                                },
                            )
                            Text(
                                text = "VS",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextMuted,
                                fontWeight = FontWeight.Black,
                            )
                            H2HCard(
                                entry = opponent,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    commit(s.choose(candidateWins = false))
                                },
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = { commit(s.skip()) },
                            enabled = s.remainingCount > 1,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("Can't decide — skip")
                        }
                    }
                }
            }
        }
    }
}

private val String.mediumTitle: String
    get() = if (this == "MANGA") "manga" else "anime"

@Composable
private fun ProgressHeader(judged: Int, total: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$judged ranked",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Text(
                text = "${(total - judged).coerceAtLeast(0)} to go",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else judged.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

/*
 * EmptySessionState — explains WHY the pool is empty instead of showing a
 * blank screen. Two distinct causes with distinct guidance (ux: empty-states,
 * error-clarity): nothing finished/rated yet vs. everything already ranked.
 */
@Composable
private fun EmptySessionState(medium: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Nothing ready to rank",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Head-to-head compares titles you've finished or rated — " +
                "and only within the same medium.\n\n" +
                "Finish or rate some ${medium.mediumTitle}, or drag them into tiers manually.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack) {
            Text("Back to tiers")
        }
    }
}

/*
 * H2HCard — one side of the comparison. Big poster + title + year/progress.
 * Press feedback: scale down to 0.97 on press (ui-ux-pro-max: scale-feedback
 * 0.95–1.05, restore on release) + the tap handler fires the haptic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun H2HCard(
    entry: com.slippedpenguin.mangolist.data.local.AnimeEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "h2hPressScale",
    )
    Card(
        onClick = {
            pressed = true
            onClick()
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(20.dp),
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            .also { source ->
                androidx.compose.runtime.LaunchedEffect(source) {
                    source.interactions.collect { interaction ->
                        if (interaction is androidx.compose.foundation.interaction.PressInteraction.Press) pressed = true
                        if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release ||
                            interaction is androidx.compose.foundation.interaction.PressInteraction.Cancel
                        ) pressed = false
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoverImage(
                model = entry.cover,
                contentDescription = entry.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val meta = listOfNotNull(
                entry.year?.toString(),
                when (entry.mediaType) {
                    "MANGA" -> entry.chapters?.let { "$it ch" }
                    else -> entry.episodes?.let { "$it ep" }
                },
                (entry.personalScore ?: 0).takeIf { it > 0 }?.let { "rated" },
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}
