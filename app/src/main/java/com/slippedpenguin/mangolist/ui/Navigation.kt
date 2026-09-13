package com.slippedpenguin.mangolist.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.slippedpenguin.mangolist.ui.screens.AnimeTabScreen
import com.slippedpenguin.mangolist.ui.screens.DetailScreen
import com.slippedpenguin.mangolist.ui.screens.HomeScreen
import com.slippedpenguin.mangolist.ui.screens.MangaTabScreen
import com.slippedpenguin.mangolist.ui.screens.ProfileScreen
import com.slippedpenguin.mangolist.ui.screens.RankHeadToHeadScreen
import com.slippedpenguin.mangolist.ui.screens.TiersScreen
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.Accent2
import com.slippedpenguin.mangolist.ui.theme.BorderSubtle
import com.slippedpenguin.mangolist.ui.theme.SurfaceContainer
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextSecondary

/*
 * v1.8 navigation shell — the redesign's structural change.
 *
 *   - The flat, full-width NavigationBar is replaced by a **floating glass
 *     dock**: a rounded, translucent surface that hovers over content near
 *     the bottom edge, the way modern tracker apps (and the JS prototype's
 *     original "floats over content" intent) behave.
 *   - The generic TopAppBar is gone. Top-level screens own their identity:
 *     big Bebas screen titles render above each tab's content, Home gets
 *     its hero greeting. This kills the "settings app" header.
 *   - Dock icons get an animated gradient pill behind the selected tab —
 *     the brand gradient (periwinkle→violet) from Theme.kt.
 *
 * Routes, tabs, and motion are unchanged from v1.5.0.
 */
sealed class BottomDest(
    val route:  String,
    val label:  String,
    val icon:   ImageVector,
) {
    data object Home    : BottomDest("home",    "Home",    Icons.Outlined.Home)
    data object Anime   : BottomDest("anime",   "Anime",   Icons.Outlined.PlayCircle)
    data object Manga   : BottomDest("manga",   "Manga",   Icons.Outlined.MenuBook)
    data object Profile : BottomDest("profile", "Profile", Icons.Outlined.Person)
}

private val bottomDestinations = listOf(
    BottomDest.Home,
    BottomDest.Anime,
    BottomDest.Manga,
    BottomDest.Profile,
)

private val dockShape = RoundedCornerShape(28.dp)

@Composable
fun MangoNavRoot(navController: NavHostController = rememberNavController()) {

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isOnBottomNav = bottomDestinations.any { dest ->
        currentRoute == dest.route || currentRoute?.startsWith("${dest.route}?") == true
    }
    val screenTitle = bottomDestinations.firstOrNull { dest ->
        currentRoute == dest.route || currentRoute?.startsWith("${dest.route}?") == true
    }?.label

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = BottomDest.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    slideInHorizontally(animationSpec = tween(220)) { it / 24 }
            },
            exitTransition = { fadeOut(animationSpec = tween(160)) },
            popEnterTransition = { fadeIn(animationSpec = tween(220)) },
            popExitTransition = {
                fadeOut(animationSpec = tween(160)) +
                    slideOutHorizontally(animationSpec = tween(160)) { it / 24 }
            },
        ) {
            composable(BottomDest.Home.route)    { HomeScreen(navController) }
            composable(
                route = "${BottomDest.Anime.route}?tab={tab}",
                arguments = listOf(
                    navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { entry ->
                TitledScreen(title = screenTitle) {
                    AnimeTabScreen(
                        navController = navController,
                        initialTab = entry.arguments?.getInt("tab") ?: 0,
                    )
                }
            }
            composable(BottomDest.Manga.route) {
                TitledScreen(title = screenTitle) { MangaTabScreen(navController) }
            }
            composable(BottomDest.Profile.route) {
                TitledScreen(title = screenTitle) { ProfileScreen(navController) }
            }
            composable("tiers")                   { TiersScreen(navController) }
            // v1.7: head-to-head batch-ranking of unranked titles.
            composable("rank_h2h")                { RankHeadToHeadScreen(navController) }
            composable(
                route = "detail/{mediaType}/{anilistId}",
                arguments = listOf(
                    navArgument("mediaType") { type = NavType.StringType; defaultValue = "ANIME" },
                    navArgument("anilistId") { type = NavType.IntType },
                ),
            ) { entry ->
                val mediaType = entry.arguments?.getString("mediaType") ?: "ANIME"
                val id = entry.arguments?.getInt("anilistId") ?: 0
                DetailScreen(navController, anilistId = id, initialMediaType = mediaType)
            }
        }

        // The floating dock — only on top-level destinations.
        if (isOnBottomNav) {
            FloatingDock(
                currentRoute = currentRoute,
                onSelect = { dest ->
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

/**
 * Wrapper for pill-tab screens: a big Bebas screen title above the screen's
 * own content, with clearance for the floating dock (content pads its own
 * lists, so the dock clearance lives in each screen's bottom padding).
 */
@Composable
private fun TitledScreen(title: String?, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // v1.9: replacing the TopAppBar dropped its built-in status-bar
            // inset — content rode up behind the camera cutout on tall
            // devices (S25 Ultra). Restore it here.
            .statusBarsPadding(),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
            )
        }
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

/*
 * FloatingDock — translucent rounded bar, brand-gradient pill behind the
 * selected icon, gentle animated tint/scale on selection. Kept deliberately
 * small: 4 icons, no labels (labels live in the screen titles now).
 */
@Composable
private fun FloatingDock(
    currentRoute: String?,
    onSelect: (BottomDest) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = dockShape,
        color = SurfaceContainer.copy(alpha = 0.88f),
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomDestinations.forEach { dest ->
                DockItem(
                    dest = dest,
                    selected = currentRoute == dest.route ||
                        currentRoute?.startsWith("${dest.route}?") == true,
                    onClick = { onSelect(dest) },
                )
            }
        }
    }
}

@Composable
private fun DockItem(
    dest: BottomDest,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else TextSecondary,
        animationSpec = tween(220),
        label = "dockTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(220),
        label = "dockScale",
    )
    // Gradient pill alpha — Brush can't animate directly, so animate the
    // layer alpha of a permanently-painted pill.
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(260),
        label = "dockPill",
    )
    val pill: Brush = Brush.linearGradient(listOf(Accent, Accent2))
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = pillAlpha }
                .clip(RoundedCornerShape(20.dp))
                .background(pill),
        )
        Icon(
            imageVector = dest.icon,
            contentDescription = dest.label,
            tint = if (selected) tint else TextMuted,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}
