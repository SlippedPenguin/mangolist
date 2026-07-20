package com.slippedpenguin.mangolist.ui

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navArgument
import com.slippedpenguin.mangolist.ui.screens.AnimeTabScreen
import com.slippedpenguin.mangolist.ui.screens.DetailScreen
import com.slippedpenguin.mangolist.ui.screens.HomeScreen
import com.slippedpenguin.mangolist.ui.screens.MangaTabScreen
import com.slippedpenguin.mangolist.ui.screens.ProfileScreen
import com.slippedpenguin.mangolist.ui.screens.TiersScreen

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
    val isOnBottomNav = bottomDestinations.any { it.route == currentRoute }
    val currentTitle = bottomDestinations.firstOrNull { it.route == currentRoute }?.label

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
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    bottomDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
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
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = BottomDest.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(BottomDest.Home.route)    { HomeScreen(navController) }
            composable(BottomDest.Anime.route)   { AnimeTabScreen(navController) }
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
