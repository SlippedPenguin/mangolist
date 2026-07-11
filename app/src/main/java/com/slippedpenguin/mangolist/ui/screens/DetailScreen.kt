package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.data.CharacterCard
import com.slippedpenguin.mangolist.data.EloEngine
import com.slippedpenguin.mangolist.data.MediaDetails
import com.slippedpenguin.mangolist.data.RelationCard
import com.slippedpenguin.mangolist.data.local.AnimeDao
import com.slippedpenguin.mangolist.data.local.AnimeEntry
import com.slippedpenguin.mangolist.ui.components.StatusPill
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.BgDeep
import com.slippedpenguin.mangolist.ui.theme.TierC
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextPrimary
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import com.slippedpenguin.mangolist.ui.theme.tierColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * Detail — v0.4.1 AniHyou-style hero + metadata + synopsis + characters +
 * relations + tracking card.
 *
 *   - The top half is populated by a live `GetMediaDetails` GraphQL fetch
 *     triggered in `LaunchedEffect(anilistId)`; no Room columns touched.
 *   - The bottom half (episode +/−, tier picker, Sync to AniList) reads
 *     AND writes the local Room entry. If the entry doesn't exist yet
 *     (e.g. tapped straight from a search result), an "Add to Watchlist"
 *     button persists a fully-formed AnimeEntry built from MediaDetails.
 *   - Add-to-watchlist and Sync callbacks are hoisted into DetailScreen so
 *     TrackingCard stays dumb about both `details` and `app`-level state.
 *   - Sync feedback ("Synced ✓" / "Pick a tier first" / "Sign in first" /
 *     "Sync failed") auto-clears after 4s.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(navController: NavController, anilistId: Int) {
    val context = LocalContext.current
    val app  = remember { context.applicationContext as AnimeApp }
    val dao  = remember { app.database.animeDao() }
    val scope = rememberCoroutineScope()

    val entry by dao.observeById(anilistId).collectAsState(initial = null)
    val token by app.tokenStore.accessToken.collectAsState(initial = null)

    var details        by remember { mutableStateOf<MediaDetails?>(null) }
    var detailsLoaded  by remember { mutableStateOf(false) }
    var showTierSheet  by remember { mutableStateOf(false) }
    var synopsisExpanded by rememberSaveable { mutableStateOf(false) }
    var syncFeedback   by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LaunchedEffect(anilistId) {
        detailsLoaded = false
        details = app.anilistClient.getMediaDetails(anilistId)
        detailsLoaded = true
    }

    // Auto-clear the inline sync feedback toast after 4 seconds.
    LaunchedEffect(syncFeedback) {
        if (syncFeedback != null) {
            delay(4000)
            syncFeedback = null
        }
    }

    // Hoisted callbacks — TrackingCard itself doesn't reach into `details`
    // or `app`/`token`/`scope`. Easier to test, easier to rewire later.
    val addToList: () -> Unit = {
        scope.launch {
            val d = details ?: return@launch
            dao.upsert(buildEntryFromDetails(d))
        }
    }
    val requestSync: () -> Unit = {
        scope.launch {
            val e = entry ?: return@launch
            if (e.tier == null) {
                syncFeedback = "Pick a tier before syncing." to true
                return@launch
            }
            val tok = token
            if (tok.isNullOrBlank()) {
                syncFeedback = "Sign in on the Profile tab first." to true
                return@launch
            }
            val newId = app.anilistClient.saveEntry(tok, e)
            if (newId != null) {
                dao.update(
                    e.copy(
                        listEntryId = newId,
                        syncedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                syncFeedback = "Synced to AniList ✓" to false
            } else {
                syncFeedback = "Sync failed — try again." to true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { HeroSection(details, entry) }
            item { TitleBlock(details, entry, detailsLoaded) }
            item { MetadataFlowRow(details) }
            if (!details?.genres.isNullOrEmpty()) {
                item { GenresRow(details!!.genres) }
            }
            val rawSynopsis = details?.synopsis?.takeIf { it.isNotBlank() }
            if (rawSynopsis != null) {
                item {
                    SynopsisBlock(
                        text = rawSynopsis,
                        expanded = synopsisExpanded,
                        onToggle = { synopsisExpanded = !synopsisExpanded },
                    )
                }
            }
            val studios = details?.studios.orEmpty()
            if (studios.isNotEmpty()) {
                item { StudiosRow(studios) }
            }
            val characters = details?.characters.orEmpty()
            if (characters.isNotEmpty()) {
                item { SectionTitle("Characters") }
                item { CharactersRow(characters) }
            }
            val relations = details?.relations.orEmpty()
            if (relations.isNotEmpty()) {
                item { SectionTitle("Related anime") }
                item { RelationsRow(relations) }
            }
            item {
                TrackingCard(
                    entry       = entry,
                    dao         = dao,
                    scope       = scope,
                    onTierClick = { showTierSheet = true },
                    onAddToList = addToList,
                    onSync      = requestSync,
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }

        // Floating back chip — sits over the banner instead of claiming its own row.
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
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

        // Loading veil while the first fetch is in flight.
        if (!detailsLoaded && details == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDeep.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Loading…",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Inline sync feedback — green for ok, accent-red for failure.
        // Live inside the outer Box's BoxScope so .align(BottomCenter) compiles.
        syncFeedback?.let { (msg, isError) ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isError) Accent.copy(alpha = 0.15f) else TierC.copy(alpha = 0.15f),
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = msg,
                    color = if (isError) Accent else TierC,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (showTierSheet) {
        TierPickerDialog(
            entry = entry,
            dao = dao,
            scope = scope,
            onDismiss = { showTierSheet = false },
        )
    }
}

/* ------------------------------------------------------------------ *
 *  Hero — banner image with a cover overlay + bottom gradient fade    *
 * ------------------------------------------------------------------ */
@Composable
private fun HeroSection(details: MediaDetails?, entry: AnimeEntry?) {
    val bannerUrl = details?.bannerImage ?: entry?.cover
    val fallbackColor = remember(details?.coverColor, entry?.coverColor) {
        val hex = details?.coverColor ?: entry?.coverColor ?: return@remember null
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(fallbackColor ?: BgDeep),
    ) {
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Bottom 1/3 fades to BgDeep so the cover overlay + title read cleanly.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to BgDeep,
                    ),
                ),
        )
        // Cover overlay sits at the bottom-left of the banner (AniHyou pattern).
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 16.dp)
                .size(width = 120.dp, height = 170.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            AsyncImage(
                model = details?.coverLarge ?: entry?.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 *  Title block — English large, romaji/native smaller underneath      *
 * ------------------------------------------------------------------ */
@Composable
private fun TitleBlock(details: MediaDetails?, entry: AnimeEntry?, loaded: Boolean) {
    val displayTitle = details?.titleEnglish?.takeIf { it.isNotBlank() }
        ?: details?.titleRomaji?.takeIf { it.isNotBlank() }
        ?: entry?.title
        ?: if (loaded) "Untitled" else "Loading…"
    val altTitle = when {
        details?.titleRomaji != null && details.titleRomaji != displayTitle -> details.titleRomaji
        details?.titleEnglish != null && details.titleEnglish != displayTitle -> details.titleEnglish
        else -> null
    }
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (altTitle != null) {
            Text(
                text = altTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 *  Metadata — k:v pills laid out with FlowRow                        *
 * ------------------------------------------------------------------ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataFlowRow(details: MediaDetails?) {
    if (details == null) return
    val items = buildList {
        add("Status" to prettyStatus(details.status))
        if (details.format != null) add("Format" to prettyFormat(details.format))
        val seasonStr = prettySeason(details.season, details.year)
        if (seasonStr != null) add("Season" to seasonStr)
        when {
            details.episodes != null -> add("Episodes" to details.episodes.toString())
            details.status == "RELEASING" -> add("Episodes" to "Ongoing")
        }
        if (details.duration != null) add("Duration" to "${details.duration}m")
        if (details.averageScore != null) {
            // Locale.US so French devices don't render "7,5" instead of "7.5".
            add("Score" to String.format(Locale.US, "%.1f", details.averageScore / 10.0))
        }
    }
    if (items.isEmpty()) return
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (label, value) -> MetadataPill(label, value) }
    }
}

@Composable
private fun MetadataPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/* ------------------------------------------------------------------ *
 *  Genres — horizontal chip strip                                     *
 * ------------------------------------------------------------------ */
@Composable
private fun GenresRow(genres: List<String>) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(genres) { g ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = g,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun StudiosRow(studios: List<String>) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "STUDIO",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = studios.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/* ------------------------------------------------------------------ *
 *  Synopsis — collapse/expand on a "Read more" toggle                 *
 * ------------------------------------------------------------------ */
@Composable
private fun SynopsisBlock(text: String, expanded: Boolean, onToggle: () -> Unit) {
    // AniList's `description(asHtml: false)` still leaks <br> + simple inline tags.
    // HtmlCompat in COMPACT mode strips them down to plain text + linebreaks.
    val plain = remember(text) {
        HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
    }
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        SectionHeader("Synopsis")
        Spacer(Modifier.height(6.dp))
        Text(
            text = plain,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
        )
        if (plain.length > 240) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onToggle) {
                    Text(
                        text = if (expanded) "Show less" else "Read more",
                        color = Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 *  Characters + Relations — horizontal scroll rows                   *
 * ------------------------------------------------------------------ */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = TextPrimary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        ),
        color = TextSecondary,
    )
}

@Composable
private fun CharactersRow(characters: List<CharacterCard>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(characters) { c ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(76.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = c.imageLarge,
                        contentDescription = c.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = c.name ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (c.role != null) {
                    Text(
                        text = c.role.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun RelationsRow(relations: List<RelationCard>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(relations) { r ->
            Card(
                modifier = Modifier.width(140.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        AsyncImage(
                            model = r.coverLarge,
                            contentDescription = r.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = r.relationType?.let(::prettyRelation) ?: "Related",
                            style = MaterialTheme.typography.labelLarge,
                            color = Accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = r.title ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (r.format != null) {
                            Text(
                                text = prettyFormat(r.format),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 *  Tracking card — episode +/−, tier picker, sync (or Add to list)    *
 *  Receives prepared onAddToList / onSync / onTierClick callbacks so  *
 *  it doesn't reach into outer state.                                *
 * ------------------------------------------------------------------ */
@Composable
private fun TrackingCard(
    entry: AnimeEntry?,
    dao: AnimeDao,
    scope: CoroutineScope,
    onTierClick: () -> Unit,
    onAddToList: () -> Unit,
    onSync: () -> Unit,
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        SectionHeader("Tracking")
        Spacer(Modifier.height(12.dp))
        val e = entry
        if (e == null) {
            // Search hit that hasn't been added to the list yet.
            Text(
                text = "Not in your watchlist yet.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAddToList,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                Text("Add to Watchlist", fontWeight = FontWeight.SemiBold)
            }
            return@Column
        }
        // In-list path: episode +/−, tier, sync.
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(status = e.status)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Elo ${e.elo}",
                style = MaterialTheme.typography.bodyMedium,
                color = tierColor(e.tier),
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        EpisodeRow(
            current = e.currentEp,
            total = e.episodes,
            onMinus = {
                scope.launch {
                    if (e.currentEp > 0) {
                        dao.update(
                            e.copy(
                                currentEp = e.currentEp - 1,
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                }
            },
            onPlus = {
                scope.launch {
                    val cap = e.episodes ?: Int.MAX_VALUE
                    if (e.currentEp < cap) {
                        dao.update(
                            e.copy(
                                currentEp = e.currentEp + 1,
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onTierClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (e.tier == null) "Add to tier" else "Change tier: ${e.tier}",
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSync,
            modifier = Modifier.fillMaxWidth(),
            enabled = e.tier != null,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
        ) {
            Text("Sync to AniList", fontWeight = FontWeight.SemiBold)
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
            TextButton(onClick = onMinus, enabled = current > 0) {
                Text(text = "−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "$current / ${total ?: "?"}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = onPlus,
                enabled = total?.let { current < it } ?: true,
            ) {
                Text(text = "+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun TierPickerDialog(
    entry: AnimeEntry?,
    dao: AnimeDao,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set tier") },
        text = {
            Column {
                EloEngine.TIERS.forEach { tier ->
                    TextButton(
                        onClick = {
                            entry?.copy(
                                tier = tier,
                                elo = EloEngine.INITIAL_ELO,
                                updatedAt = System.currentTimeMillis(),
                            )?.let { updated ->
                                scope.launch { dao.update(updated) }
                            }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = tier,
                            color = tierColor(tier),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                TextButton(
                    onClick = {
                        entry?.copy(
                            tier = null,
                            elo = EloEngine.INITIAL_ELO,
                            updatedAt = System.currentTimeMillis(),
                        )?.let { updated ->
                            scope.launch { dao.update(updated) }
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Unranked", color = TextSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/* ------------------------------------------------------------------ *
 *  Pretty-printers — AniList enums are uppercase by convention       *
 * ------------------------------------------------------------------ */
private fun prettyFormat(fmt: String?): String = when (fmt) {
    "TV"          -> "TV"
    "TV_SHORT"    -> "TV Short"
    "MOVIE"       -> "Movie"
    "OVA"         -> "OVA"
    "ONA"         -> "ONA"
    "SPECIAL"     -> "Special"
    "MUSIC"       -> "Music"
    "MANGA"       -> "Manga"
    "NOVEL"       -> "Novel"
    "ONE_SHOT"    -> "One-shot"
    null          -> "—"
    else          -> fmt.lowercase().replaceFirstChar { it.uppercase() }
}

private fun prettyStatus(s: String?): String = when (s) {
    "RELEASING"        -> "Airing"
    "FINISHED"         -> "Finished"
    "NOT_YET_RELEASED" -> "Upcoming"
    "CANCELLED"        -> "Cancelled"
    "HIATUS"           -> "Hiatus"
    null               -> "—"
    else               -> s.lowercase().replaceFirstChar { it.uppercase() }
}

private fun prettySeason(season: String?, year: Int?): String? {
    val prettySeason = season?.lowercase()?.replaceFirstChar { it.uppercase() }
    return when {
        prettySeason == null && year == null -> null
        prettySeason == null -> year.toString()
        year == null         -> prettySeason
        else                 -> "$prettySeason $year"
    }
}

private fun prettyRelation(rel: String): String = when (rel) {
    "PREQUEL"     -> "Prequel"
    "SEQUEL"      -> "Sequel"
    "PARENT"      -> "Parent story"
    "SIDE_STORY"  -> "Side story"
    "SPIN_OFF"    -> "Spin-off"
    "ADAPTATION"  -> "Adaptation"
    "ALTERNATIVE" -> "Alt version"
    "CHARACTER"   -> "Shared char"
    "SUMMARY"     -> "Summary"
    "CONTAINS"    -> "Contains"
    "SOURCE"      -> "Source"
    "COMPILATION" -> "Compilation"
    "REQUIRES"    -> "Requires"
    "OTHER"       -> "Related"
    else          -> rel.lowercase().replaceFirstChar { it.uppercase() }
}

/* ------------------------------------------------------------------ *
 *  Builder — convert a fetched MediaDetails into an AnimeEntry        *
 * ------------------------------------------------------------------ */
internal fun buildEntryFromDetails(d: MediaDetails): AnimeEntry {
    val now = System.currentTimeMillis()
    return AnimeEntry(
        anilistId    = d.id,
        title        = d.titleEnglish?.takeIf { it.isNotBlank() }
            ?: d.titleRomaji?.takeIf { it.isNotBlank() }
            ?: "Untitled",
        cover        = d.coverLarge,
        coverColor   = d.coverColor,
        format       = d.format,
        episodes     = d.episodes,
        averageScore = d.averageScore,
        year         = d.year,
        synopsis     = d.synopsis,
        genres       = d.genres.joinToString(", "),
        tier         = null,
        elo          = 1500,
        currentEp    = 0,
        status       = "plan",
        notes        = "",
        listEntryId  = null,
        updatedAt    = now,
        syncedAt     = null,
    )
}
