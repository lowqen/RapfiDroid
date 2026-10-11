package dev.gomoku.rapfidroid.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.rapfidroid.core.designsystem.component.LocalShellChrome
import dev.gomoku.rapfidroid.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.rapfidroid.core.designsystem.component.ShellChrome
import dev.gomoku.rapfidroid.core.designsystem.component.engineWord
import dev.gomoku.rapfidroid.core.model.ConnectionState
import dev.gomoku.rapfidroid.core.model.EngineTarget
import dev.gomoku.rapfidroid.data.prefs.EndpointStore
import dev.gomoku.rapfidroid.data.prefs.UiPrefs
import dev.gomoku.rapfidroid.domain.repository.EngineRepository
import dev.gomoku.rapfidroid.domain.repository.SettingsRepository
import dev.gomoku.rapfidroid.feature.board.BoardScreen
import dev.gomoku.rapfidroid.feature.database.DatabaseScreen
import dev.gomoku.rapfidroid.feature.joseki.JosekiScreen
import dev.gomoku.rapfidroid.feature.settings.SettingsScreen
import dev.gomoku.rapfidroid.feature.tools.EngineScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The shell's own state: the engine for the top-bar chip and the tab dot, and
 * the theme switch.
 *
 * **The engine starts with the app** (RapfiWeb's auto boot). It used to wait
 * for a tap on the engine tab, so the first thing a new user met was a board
 * that did nothing when asked to analyse. It connects to whatever was used last
 * — the engine inside the app unless the server was chosen — and the switch in
 * Settings turns this off.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val engine: EngineRepository,
    private val endpoints: EndpointStore,
    private val settings: SettingsRepository,
    private val ui: UiPrefs,
) : ViewModel() {
    val state: StateFlow<ConnectionState> = engine.state

    val dark: StateFlow<Boolean> = settings.settings.map { it.darkMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.settings.value.darkMode)

    init {
        if (ui.autoStartEngine.value && engine.state.value == ConnectionState.Disconnected) start()
    }

    fun start() {
        viewModelScope.launch {
            if (engine.state.value is ConnectionState.Error) {
                runCatching { engine.retryNow() }
                return@launch
            }
            val local = endpoints.localMode.first()
            val target = if (local) EngineTarget.Local else EngineTarget.Remote(endpoints.endpoint.first())
            runCatching { engine.connect(target) }
        }
    }

    fun toggleTheme() {
        viewModelScope.launch { settings.set("darkMode", if (dark.value) "0" else "1") }
    }
}

@Composable
fun RapfiApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val shell: ShellViewModel = hiltViewModel()
    val connection by shell.state.collectAsStateWithLifecycle()
    val dark by shell.dark.collectAsStateWithLifecycle()
    val chrome = ShellChrome(
        connection = connection,
        dark = dark,
        onStartEngine = shell::start,
        onToggleTheme = shell::toggleTheme,
    )

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
        LocalShellChrome provides chrome,
    ) {
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
                                // The dot rides the tab that leads to the answer.
                                if (dest == Destination.Connect) {
                                    val (word, color) = engineWord(connection)
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = color,
                                                modifier = Modifier.semantics { contentDescription = word },
                                            )
                                        },
                                    ) {
                                        Icon(dest.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(dest.icon, contentDescription = null)
                                }
                            },
                            label = {
                                Text(
                                    stringResource(dest.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destination.Board.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Destination.Board.route) { BoardScreen() }
                composable(Destination.Research.route) { ResearchScreen() }
                composable(Destination.Joseki.route) { JosekiScreen() }
                composable(Destination.Database.route) { DatabaseScreen() }
                composable(Destination.Settings.route) { SettingsScreen() }
                composable(Destination.Connect.route) { EngineScreen() }
            }
        }
    }
}
