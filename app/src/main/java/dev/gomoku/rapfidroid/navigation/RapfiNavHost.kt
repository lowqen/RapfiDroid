package dev.gomoku.rapfidroid.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.rapfidroid.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiTheme
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.ConnectionState
import dev.gomoku.rapfidroid.domain.repository.EngineRepository
import dev.gomoku.rapfidroid.feature.board.BoardScreen
import dev.gomoku.rapfidroid.feature.database.DatabaseScreen
import dev.gomoku.rapfidroid.feature.onboarding.WelcomeGate
import dev.gomoku.rapfidroid.feature.settings.SettingsScreen
import dev.gomoku.rapfidroid.feature.tools.EngineScreen
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Just the link's state, for the dot on the 연결 tab. A whole view model for one
 * flow, because the alternative — passing the engine repository down through
 * every screen — is how the connection ends up being reported four different
 * ways again.
 */
@HiltViewModel
class ConnectionBadgeViewModel @Inject constructor(
    engine: EngineRepository,
) : ViewModel() {
    val state: StateFlow<ConnectionState> = engine.state
}

@Composable
fun RapfiApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val badge: ConnectionBadgeViewModel = hiltViewModel()
    val connection by badge.state.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            icon = {
                                // The engine's state used to be visible only on
                                // the screen that owns it, so "why is nothing
                                // happening?" cost a tab change to answer. The
                                // dot rides the tab that leads to the answer.
                                if (dest == Destination.Connect) {
                                    val label = connectionLabel(connection)
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = connectionColor(connection),
                                                modifier = Modifier.semantics {
                                                    contentDescription = label
                                                },
                                            )
                                        },
                                    ) {
                                        Icon(dest.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(dest.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                // The board, not the connection screen. Somebody opening this app
                // wants to look at a position; the link reports itself on the tab
                // bar now, and only asks for attention when it is broken.
                startDestination = Destination.Board.route,
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

        // Over everything, once: the four things a new user cannot find by
        // looking. It gates itself on a stored flag, so this costs a
        // preference read and nothing else on every later launch.
        WelcomeGate()
    }
}

/**
 * Three states worth a colour, and the fourth deliberately quiet: a disconnected
 * engine on an app that is mostly used to read positions is not an error.
 */
@Composable
private fun connectionColor(state: ConnectionState): Color = when (state) {
    ConnectionState.Ready, ConnectionState.Thinking -> RapfiTheme.colors.positive
    ConnectionState.Connecting, ConnectionState.Handshaking -> RapfiTheme.colors.caution
    is ConnectionState.Error -> MaterialTheme.colorScheme.error
    ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
}

/** The dot's meaning, for anyone who cannot see a dot. */
@Composable
private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.Ready -> tr("엔진 연결됨", "Engine connected")
    ConnectionState.Thinking -> tr("엔진 분석 중", "Engine searching")
    ConnectionState.Connecting -> tr("엔진 연결 중", "Engine connecting")
    ConnectionState.Handshaking -> tr("엔진 핸드셰이크 중", "Engine handshaking")
    is ConnectionState.Error -> tr("엔진 오류", "Engine error")
    ConnectionState.Disconnected -> tr("엔진 연결 안 됨", "Engine not connected")
}

