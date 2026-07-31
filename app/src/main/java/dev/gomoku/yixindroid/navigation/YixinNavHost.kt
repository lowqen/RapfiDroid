package dev.gomoku.yixindroid.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.gomoku.yixindroid.feature.board.BoardScreen
import dev.gomoku.yixindroid.feature.database.DatabaseScreen
import dev.gomoku.yixindroid.feature.settings.SettingsScreen
import dev.gomoku.yixindroid.feature.tools.EngineScreen

@Composable
fun YixinApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { dest ->
                    val selected = currentRoute?.hierarchy?.any { it.route == dest.route } == true
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
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Connect.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Board.route) {
                BoardScreen()
            }
            composable(Destination.Research.route) {
                ResearchScreen()
            }
            composable(Destination.Database.route) {
                DatabaseScreen()
            }
            composable(Destination.Settings.route) {
                SettingsScreen()
            }
            composable(Destination.Connect.route) {
                EngineScreen()
            }
        }
    }
}
