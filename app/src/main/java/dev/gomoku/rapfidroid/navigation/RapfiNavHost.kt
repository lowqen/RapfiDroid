package dev.gomoku.rapfidroid.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import dev.gomoku.rapfidroid.core.common.IncomingLinks
import dev.gomoku.rapfidroid.core.designsystem.component.LocalShellChrome
import dev.gomoku.rapfidroid.core.designsystem.component.LocalUiPrefs
import dev.gomoku.rapfidroid.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.rapfidroid.core.designsystem.component.RailLayoutMin
import dev.gomoku.rapfidroid.core.designsystem.component.ShellChrome
import dev.gomoku.rapfidroid.core.designsystem.theme.ChromeMotion
import dev.gomoku.rapfidroid.core.designsystem.theme.MOTION_PANEL
import dev.gomoku.rapfidroid.core.designsystem.theme.MOTION_QUICK
import dev.gomoku.rapfidroid.core.designsystem.theme.themeFollowsSystem
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
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    links: IncomingLinks,
    private val endpoints: EndpointStore,
    private val settings: SettingsRepository,
    private val ui: UiPrefs,
) : ViewModel() {
    val state: StateFlow<ConnectionState> = engine.state
    val thermalThreads: StateFlow<Int?> = engine.thermalThreads

    /** A curriculum link waiting: the shell moves to the joseki tab, which opens it. */
    val lessonLink = links.lesson

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
    val thermalThreads by shell.thermalThreads.collectAsStateWithLifecycle()
    val dark by shell.dark.collectAsStateWithLifecycle()
    val lessonLink by shell.lessonLink.collectAsStateWithLifecycle()
    LaunchedEffect(lessonLink) {
        if (lessonLink != null && currentRoute?.route != Destination.Joseki.route) {
            navController.navigate(Destination.Joseki.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    // What is on screen, which is not settings.txt line 27 when the theme
    // follows the phone. The switch picks the opposite of it, and picking is a
    // choice: it leaves «기기 설정».
    val shownDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val prefs = LocalUiPrefs.current
    val chrome = ShellChrome(
        connection = connection,
        dark = shownDark,
        onStartEngine = shell::start,
        onToggleTheme = {
            prefs?.themeFollowsSystem?.set(false)
            if (dark == shownDark) shell.toggleTheme()
        },
        thermalThreads = thermalThreads,
    )

    val selected = Destination.entries.firstOrNull { dest ->
        currentRoute?.hierarchy?.any { it.route == dest.route } == true
    }
    val onSelect: (Destination) -> Unit = { dest ->
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    val threshold = with(LocalDensity.current) { 28.dp.toPx() }
    val hider = remember(threshold) { BarHider(threshold) }
    // The board never folds the bar away, and a new tab starts with it shown.
    LaunchedEffect(selected) {
        hider.enabled = selected != null && selected != Destination.Board
        hider.show()
    }

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
        LocalShellChrome provides chrome,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rail = maxWidth >= RailLayoutMin
            Row(Modifier.fillMaxSize()) {
                if (rail) {
                    RapfiRail(
                        selected = selected,
                        connection = connection,
                        onSelect = onSelect,
                        // Under ~480dp of height (a phone on its side) six
                        // labelled items do not fit; the selected one keeps its.
                        showLabels = maxHeight >= 480.dp,
                    )
                }
                Scaffold(
                    // The rail has taken the side inset; the content must not take it again.
                    modifier = if (rail) {
                        Modifier.consumeWindowInsets(NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start))
                    } else {
                        Modifier
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!rail) {
                            AnimatedVisibility(
                                visible = !hider.hidden,
                                enter = expandVertically(ChromeMotion.spatial(tween(MOTION_PANEL))) +
                                    fadeIn(ChromeMotion.effects(tween(MOTION_PANEL))),
                                exit = shrinkVertically(ChromeMotion.spatial(tween(MOTION_QUICK))) +
                                    fadeOut(ChromeMotion.effects(tween(MOTION_QUICK))),
                            ) {
                                RapfiBottomBar(selected = selected, connection = connection, onSelect = onSelect)
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Destination.Board.route,
                        modifier = Modifier.padding(padding).nestedScroll(hider),
                        // QuietSwitch's rule for every screen change: a crossfade, 150 ms.
                        // Navigation's own default is a 700 ms fade.
                        enterTransition = { fadeIn(tween(MOTION_QUICK)) },
                        exitTransition = { fadeOut(tween(MOTION_QUICK)) },
                        popEnterTransition = { fadeIn(tween(MOTION_QUICK)) },
                        popExitTransition = { fadeOut(tween(MOTION_QUICK)) },
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
    }
}
