package com.slippedpenguin.mangolist.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.slippedpenguin.mangolist.ui.screens.AddScreen
import com.slippedpenguin.mangolist.ui.screens.AiringScreen
import com.slippedpenguin.mangolist.ui.screens.DetailScreen
import com.slippedpenguin.mangolist.ui.screens.ProfileScreen
import com.slippedpenguin.mangolist.ui.screens.TiersScreen
import com.slippedpenguin.mangolist.ui.screens.WatchlistScreen

/*
 * Sealed family of bottom-bar destinations. Compared to a flat List<String>
 * this keeps the icon + label + route colocated so adding a new tab is one
 * line + one `composable` call.
 */
sealed class BottomDest(
    val route:  String,
    val label:  String,
    val icon:   ImageVector,
) {
    data object Watchlist : BottomDest("watchlist", "Watch",  Icons.Outlined.Visibility)
    data object Add       : BottomDest("add",       "Add",    Icons.Outlined.Add)
    data object Tiers     : BottomDest("tiers",     "Tiers",  Icons.Outlined.Leaderboard)
    data object Airing    : BottomDest("airing",    "Airing", Icons.Outlined.CalendarMonth)
    data object Profile   : BottomDest("profile",   "Profile", Icons.Outlined.Person)
}

private val bottomDestinations = listOf(
    BottomDest.Watchlist,
    BottomDest.Add,
    BottomDest.Tiers,
    BottomDest.Airing,
    BottomDest.Profile,
)

/*
 * Root Scaffold + NavHost.
 *   - Bottom bar shows only when we're on a top-level route (Detail hides it
 *     to give the hero image real estate).
 *   - `restoreState = true` keeps each tab's scroll position when switching.
 */
@Composable
fun MangoNavRoot(navController: NavHostController = rememberNavController()) {

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isOnBottomNav = bottomDestinations.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isOnBottomNav) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
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
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = BottomDest.Watchlist.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(BottomDest.Watchlist.route) { WatchlistScreen(navController) }
            composable(BottomDest.Add.route)       { AddScreen(navController) }
            composable(BottomDest.Tiers.route)     { TiersScreen(navController) }
            composable(BottomDest.Airing.route)    { AiringScreen() }
            composable(BottomDest.Profile.route)   { ProfileScreen(navController) }
            composable("detail/{anilistId}") { entry ->
                val id = entry.arguments?.getString("anilistId")?.toIntOrNull() ?: 0
                DetailScreen(navController, anilistId = id)
            }
        }
    }
}
