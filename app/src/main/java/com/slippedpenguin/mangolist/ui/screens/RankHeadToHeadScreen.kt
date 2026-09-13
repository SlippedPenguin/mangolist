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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
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
 * RankHeadToHeadScreen — v1.7 "Rank unranked" batch-ranking flow.
 *
 * Two titles side-by-side; tap the one you liked more; the next pair
 * appears. Every judgment runs through the pure RankSession reducer
 * (EloEngine.update + proposeTier), so the math is unit-tested in CI and
 * this file only renders state and persists `session.committedRows`.
 *
 * The session is seeded once per entry from a single DAO read and lives in
 * `remember` — a process death mid-session simply loses the unsaved tail,
 * which is acceptable for a few-second-per-judgment flow (the same trade
 * every tier-maker app makes). Committed rows are persisted as they're
 * produced, on every choose().
 *
 * Ranking writes never touch updatedAt (local-only fields — see
 * TierListModel). Airplane-mode safe: nothing here hits the network.
 */
@Composable
fun RankHeadToHeadScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val dao = remember { app.database.animeDao() }
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<RankSession?>(null) }
    var seeded by remember { mutableStateOf(false) }

    fun seed() {
        if (seeded) return
        seeded = true
        scope.launch {
            val all = dao.getAll()
            val ranked = all.filter { it.tier != null }
            val unranked = all.filter { it.tier == null }
            session = RankSession.start(ranked, unranked)
        }
    }

    fun commit(next: RankSession) {
        session = next
        val rows = next.committedRows
        if (rows.isNotEmpty()) {
            scope.launch { rows.forEach { dao.update(it) } }
        }
    }

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

        seed() // idempotent; runs once per composition lifetime

        val s = session
        when {
            s == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            s.isDone && s.judgedCount == 0 -> {
                // Nothing to rank: pool was empty at seed time.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Nothing to rank",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Every title on your list already has a tier.\n" +
                            "Add something from Explore, then come back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { navController.popBackStack() }) {
                        Text("Back to tiers")
                    }
                }
            }
            s.isDone -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 72.dp),
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
                        text = "${s.judgedCount} title${if (s.judgedCount == 1) "" else "s"} placed into tiers. " +
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
                    // Shouldn't happen (non-done implies a pair) — fail soft.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 64.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProgressHeader(
                            judged = s.judgedCount,
                            remaining = s.remainingCount,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Which did you like more?",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            H2HCard(
                                entry = candidate,
                                modifier = Modifier.weight(1f),
                                onClick = { commit(s.choose(candidateWins = true)) },
                            )
                            H2HCard(
                                entry = opponent,
                                modifier = Modifier.weight(1f),
                                onClick = { commit(s.choose(candidateWins = false)) },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { commit(s.skip()) },
                            enabled = s.remainingCount > 1,
                        ) {
                            Text("Can't decide — skip")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(judged: Int, remaining: Int) {
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
                text = "$remaining to go",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(4.dp))
        val total = judged + remaining
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
 * H2HCard — one side of the comparison. Big poster + title + year/format.
 * The whole card is the tap target; no other affordances (this screen is
 * a decision machine — everything else is friction).
 */
@Composable
private fun H2HCard(entry: com.slippedpenguin.mangolist.data.local.AnimeEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(20.dp),
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
                    .height(200.dp)
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
