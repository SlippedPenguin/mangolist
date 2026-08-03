package com.slippedpenguin.mangolist.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.slippedpenguin.mangolist.ui.screens.TiersScreen
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.Border
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextSecondary

/*
 * v1.4: Anihyou-style bottom navigation.
 *
 *   - Home    — watchlist overview + activity + tier access
 *   - Anime   — anime watchlist, explore, and airing schedule (mediaType=ANIME)
 *   - Manga   — manga watchlist and explore (mediaType=MANGA)
 *   - Profile — sign-in, stats, sync, score-scale toggle
 *
 * The standalone Watchlist / Explore / Tiers / Airing tabs from v1.3 are
 * absorbed into the Home / Anime / Manga tabs. Each screen is a standalone
 * composable that owns its own sub-tab row.
 *
 * v1.5.0 (motion + depth): the bar sits on a layered surface container
 * with a 1dp top hairline, the selected item animates its icon scale and
 * tint, and the Material 3 pill indicator slides between tabs. NavHost
 * gets a subtle fade + slide so tab switches feel continuous instead of
 * snapping.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangoNavRoot(navController: NavHostController = rememberNavController()) {

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isOnBottomNav = bottomDestinations.any { dest ->
        currentRoute == dest.route || currentRoute?.startsWith("${dest.route}?") == true
    }
    val currentTitle = bottomDestinations.firstOrNull { dest ->
        currentRoute == dest.route || currentRoute?.startsWith("${dest.route}?") == true
    }?.label

    Scaffold(
        topBar = {
            if (isOnBottomNav && currentTitle != null) {
                TopAppBar(
                    title = { Text(text = currentTitle) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
        },
        bottomBar = {
            if (isOnBottomNav) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // 1dp hairline separating the nav bar from content.
                            drawLine(
                                color = Border,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx(),
                            )
                        },
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        bottomDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route || currentRoute?.startsWith("${dest.route}?") == true
                            // v1.5.0: animated icon scale + tint so the
                            // selected tab visibly "pops" under the pill.
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.12f else 1f,
                                animationSpec = tween(durationMillis = 220),
                                label = "navIconScale",
                            )
                            val iconTint by animateColorAsState(
                                targetValue = if (selected) Accent else TextSecondary,
                                animationSpec = tween(durationMillis = 220),
                                label = "navIconTint",
                            )
                            val labelColor by animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.onSurface else TextMuted,
                                animationSpec = tween(durationMillis = 220),
                                label = "navLabelColor",
                            )
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        dest.icon,
                                        contentDescription = dest.label,
                                        tint = iconTint,
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        },
                                    )
                                },
                                label = { Text(dest.label, color = labelColor) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = Accent.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextMuted,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = BottomDest.Home.route,
            modifier = Modifier.padding(padding),
            // v1.5.0: fade + slight horizontal drift between top-level
            // destinations. Detail/Tiers push screens inherit the same
            // enter so the motion language stays consistent.
            enterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    slideInHorizontally(animationSpec = tween(220)) { it / 24 }
            },
            exitTransition = {
                fadeOut(animationSpec = tween(160))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(220))
            },
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
                AnimeTabScreen(
                    navController = navController,
                    initialTab = entry.arguments?.getInt("tab") ?: 0,
                )
            }
            composable(BottomDest.Manga.route)   { MangaTabScreen(navController) }
            composable(BottomDest.Profile.route) { ProfileScreen(navController) }
            composable("tiers")                  { TiersScreen(navController) }
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
    }
}
