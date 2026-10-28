package dev.gomoku.zenyth.navigation

import dev.gomoku.zenyth.feature.board.MiniAnalysisBar
import dev.gomoku.zenyth.feature.board.BoardViewModel
import androidx.navigation.NavHostController
import androidx.compose.foundation.layout.Column
import dev.gomoku.zenyth.core.designsystem.theme.LocalProMode
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import dev.gomoku.zenyth.core.common.IncomingLinks
import dev.gomoku.zenyth.core.designsystem.component.LocalShellChrome
import dev.gomoku.zenyth.core.designsystem.component.LocalUiPrefs
import dev.gomoku.zenyth.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.zenyth.core.designsystem.component.RailLayoutMin
import dev.gomoku.zenyth.core.designsystem.component.ShellChrome
import dev.gomoku.zenyth.core.designsystem.theme.MOTION_QUICK
import dev.gomoku.zenyth.core.designsystem.theme.themeFollowsSystem
import dev.gomoku.zenyth.core.model.ConnectionState
import dev.gomoku.zenyth.core.model.EngineTarget
import dev.gomoku.zenyth.data.prefs.EndpointStore
import dev.gomoku.zenyth.data.prefs.UiPrefs
import dev.gomoku.zenyth.domain.repository.EngineRepository
import dev.gomoku.zenyth.domain.repository.SettingsRepository
import dev.gomoku.zenyth.feature.board.BoardScreen
import dev.gomoku.zenyth.feature.database.DatabaseScreen
import dev.gomoku.zenyth.feature.joseki.JosekiScreen
import dev.gomoku.zenyth.feature.settings.SettingsScreen
import dev.gomoku.zenyth.feature.tools.EngineScreen
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
 * **The engine starts with the app** (Zenyth Web's auto boot). It used to wait
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
    // The DB tab is an expert surface: shown in «고급 사용자 모드» only, and
    // left for the board if the mode goes off while it is open.
    val pro = LocalProMode.current
    val destinations = Destination.entries.filter { pro || it != Destination.Database }
    LaunchedEffect(pro, selected) {
        if (!pro && selected == Destination.Database) onSelect(Destination.Board)
    }

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
        LocalShellChrome provides chrome,
    ) {
        // One flat page behind every screen (M1: Mornye's pages are flat).
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val rail = maxWidth >= RailLayoutMin
            // Six labelled items need ~480dp; a phone on its side has less.
            val railLabels = maxHeight >= 480.dp
            Row(Modifier.fillMaxSize()) {
                if (rail) {
                    RapfiRail(
                        selected = selected,
                        connection = connection,
                        onSelect = onSelect,
                        showLabels = railLabels,
                        destinations = destinations,
                    )
                }
                Scaffold(
                    // The rail has taken the side inset; the content must not take it again.
                    modifier = if (rail) {
                        Modifier.consumeWindowInsets(NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start))
                    } else {
                        Modifier
                    },
                    containerColor = Color.Transparent,
                    // Said outright: Material reads no content colour off a
                    // transparent container and hands down the default black,
                    // which every bare text on a dark page then took (the
                    // research and engine titles, 2026-10-29).
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    snackbarHost = {
                        SnackbarHost(snackbarHostState) { data ->
                            Snackbar(data, shape = MaterialTheme.shapes.medium)
                        }
                    },
                    bottomBar = {
                        // Always there (user request, 2026-10-29: the bar folding away
                        // on scroll was unwanted).
                        if (!rail) {
                            Column {
                                // The board's engine at work, seen from the other tabs (M5).
                                if (selected != Destination.Board) {
                                    BoardMiniBar(navController, onOpen = { onSelect(Destination.Board) })
                                }
                                RapfiBottomBar(selected = selected, connection = connection, onSelect = onSelect, destinations = destinations)
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Destination.Board.route,
                        modifier = Modifier.padding(padding),
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

/**
 * [MiniAnalysisBar] for the board tab's own view model. The board is the start
 * destination, so its entry — and the view model the board screen made — stays
 * in the back stack whichever tab is open.
 */
@Composable
private fun BoardMiniBar(navController: NavHostController, onOpen: () -> Unit) {
    val backStack by navController.currentBackStackEntryAsState()
    val entry = remember(backStack) {
        runCatching { navController.getBackStackEntry(Destination.Board.route) }.getOrNull()
    } ?: return
    val board: BoardViewModel = hiltViewModel(entry)
    MiniAnalysisBar(board, onOpen)
}
