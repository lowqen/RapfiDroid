package dev.gomoku.rapfidroid.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.designsystem.theme.MOTION_QUICK
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiTheme
import dev.gomoku.rapfidroid.core.designsystem.theme.Spacing
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.ConnectionState

/**
 * The one snackbar in the app.
 *
 * There used to be three ways of saying something went wrong: a hand-built
 * `Snackbar` composable with its own `delay(3000)` on the board, a real
 * `SnackbarHost` in the explorer, and inline banners everywhere else — which
 * meant a notice could appear in three different places depending on which
 * screen produced it, and the board's version scrolled away with the page.
 *
 * The host lives on the app's scaffold and is reached from anywhere:
 *
 *     val snackbar = LocalSnackbarHostState.current
 *     LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it) } }
 */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/**
 * What the top bar shows on its right, on every screen: the engine's state in a
 * word, and the theme switch. Provided once by the app shell, so no screen has
 * to know the engine exists to report on it (RapfiWeb `TopBar`).
 */
data class ShellChrome(
    val connection: ConnectionState,
    val dark: Boolean,
    val onStartEngine: () -> Unit,
    val onToggleTheme: () -> Unit,
    /** Threads the on-device engine is held to by heat or battery saver (T1), or null. */
    val thermalThreads: Int? = null,
)

val LocalShellChrome = compositionLocalOf<ShellChrome?> { null }

/**
 * Every screen's header — one line.
 *
 * The subtitle under the title is gone (RapfiWeb's calm redesign): it repeated
 * what the tab already said and took a line of height from the board. On the
 * right, the engine chip and the theme switch, whichever screen this is.
 *
 * [title] null drops the title too — the board's header, where the selected tab
 * already says «보드» and the line is better spent on the board (UI overhaul P2).
 * [leading] then takes the left end: the board puts its 분석 | 대국 switch there.
 *
 * Insets are zero on purpose: the app scaffold has already inset its content
 * below the status bar.
 */
@Composable
fun RapfiTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (title != null) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                leading?.invoke(this)
                Spacer(Modifier.weight(1f))
            }
            actions()
            ShellActions()
        }
    }
}

/**
 * The header of a screen that is one long list — settings, the database, the
 * joseki courses. Tall while the list is at its top — a 34sp ExtraBold title
 * under a lot of air, as Mornye's pages open (M2) — and folded
 * to the ordinary 52dp bar as soon as the list moves (Mornye's collapsing
 * header, decision P2 ③).
 *
 * Only for screens that are a list. A screen with a board on it keeps the
 * one-line bar: a header that only folds when something scrolls would sit tall
 * above a board that does not, and take its height for good.
 *
 * The screen hands [scrollBehavior]'s connection to its own column:
 *
 *     Column(Modifier.nestedScroll(behavior.nestedScrollConnection)) {
 *         RapfiCollapsingTopBar(title, behavior)
 *         LazyColumn { … }
 *     }
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RapfiCollapsingTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        LargeTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            actions = {
                ShellActions()
                // Material's bar ends its actions 4dp from the edge, the app's
                // header 8dp: without this the chip jumps 4dp between tabs.
                Spacer(Modifier.width(4.dp))
            },
            collapsedHeight = 52.dp,
            // M2: Mornye's page titles sit low under a lot of air (~150dp).
            expandedHeight = 152.dp,
            windowInsets = WindowInsets(0),
            // No surface of its own, folded or not: the page runs on under it,
            // as Mornye's headers sit on the page.
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            scrollBehavior = scrollBehavior,
        )
    }
}

/**
 * The engine chip and the theme switch — the right end of every header, and on
 * its own where a screen has no header (the board turned sideways, whose whole
 * height goes to the board).
 */
@Composable
fun ShellActions(modifier: Modifier = Modifier) {
    val chrome = LocalShellChrome.current ?: return
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EngineChip(chrome.connection, chrome.onStartEngine, thermalThreads = chrome.thermalThreads)
        IconButton(onClick = chrome.onToggleTheme) {
            Icon(
                if (chrome.dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = if (chrome.dark) tr("밝은 테마", "Light theme", "浅色主题") else tr("어두운 테마", "Dark theme", "深色主题"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The engine in one word — 준비 · 탐색 중 · 준비 중 · 꺼짐 · 오류 — with the
 * one action that state calls for (켜기 · 다시 시도). The address and the
 * variant are the engine screen's business, not every screen's.
 */
@Composable
fun EngineChip(
    state: ConnectionState,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    thermalThreads: Int? = null,
) {
    val (word, color) = engineWord(state)
    val heat = thermalThreads?.let {
        tr("발열·절전으로 ${it} 스레드", "$it threads (heat / saver)", "因发热/省电 $it 线程")
    }
    val action = when (state) {
        ConnectionState.Disconnected -> tr("켜기", "Start", "启动")
        is ConnectionState.Error -> tr("다시 시도", "Retry", "重试")
        else -> null
    }
    Surface(
        modifier = modifier.semantics { contentDescription = if (heat == null) word else "$word, $heat" },
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = { if (action != null) onStart() },
        enabled = action != null,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusDot(color)
            Text(
                word,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            // Held back by heat or battery saver: a thermometer and the threads in use —
            // short enough for the top bar, spelled out for the screen reader above.
            if (thermalThreads != null) {
                Icon(
                    Icons.Filled.Thermostat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = RapfiTheme.colors.caution,
                )
                Text(
                    thermalThreads.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = RapfiTheme.colors.caution,
                    maxLines = 1,
                )
            }
            if (action != null) {
                Text(
                    action,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The word and the dot colour for an engine state — the chip and the tab badge agree. */
@Composable
fun engineWord(state: ConnectionState): Pair<String, Color> = when (state) {
    ConnectionState.Ready -> tr("엔진 준비", "Ready", "引擎就绪") to RapfiTheme.colors.positive
    ConnectionState.Thinking -> tr("탐색 중", "Searching", "搜索中") to RapfiTheme.colors.caution
    ConnectionState.Connecting, ConnectionState.Handshaking ->
        tr("준비 중", "Starting", "启动中") to RapfiTheme.colors.caution
    is ConnectionState.Error -> tr("엔진 오류", "Engine error", "引擎错误") to MaterialTheme.colorScheme.error
    ConnectionState.Disconnected -> tr("엔진 꺼짐", "Engine off", "引擎关闭") to MaterialTheme.colorScheme.outline
}

/**
 * A crossfade between two things that occupy the same place — the app's only
 * screen transition.
 *
 * Never a slide: three of the four things that swap in this app are boards, and
 * sliding one board out while another arrives is motion sickness rather than
 * polish. 150 ms, so it is felt and not watched.
 */
@Composable
fun <T> QuietSwitch(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    Crossfade(
        targetState = target,
        animationSpec = tween(MOTION_QUICK),
        label = "quietSwitch",
        modifier = modifier,
        content = content,
    )
}

/**
 * How wide one column of reading is allowed to get.
 *
 * Two of the research screens are a *sequence*, not a set of tiles: the position,
 * then its continuations, then the games that reached it. Splitting that into
 * columns would break the order it is read in, so on a wide screen they stay one
 * column and centre themselves instead of stretching a "h8 · 12 games · ▮▮▮" row
 * across a thousand points of tablet.
 *
 *     Modifier.fillMaxHeight().wrapContentWidth().widthIn(max = ReadingWidth)
 */
val ReadingWidth = 640.dp

/**
 * Wide enough for two columns. 640dp is a landscape phone; below it a split
 * would squeeze each half to something unreadable.
 *
 * The board screen has always branched on this. The connection screen now does
 * too — it stacks a console under four rows of controls, which in landscape
 * left the console measured at nothing — so the number lives here rather than
 * being copied, and the two screens turn into two columns at the same moment.
 */
val WideLayoutMin = 640.dp

/**
 * Wide enough for a navigation rail instead of the bottom bar: Material's
 * compact/medium boundary. A phone on its side is past it, and there the six
 * tabs move to the side, so the board's height is no longer shared with a bar
 * along the bottom (UI overhaul P2).
 */
val RailLayoutMin = 600.dp

/**
 * What a screen shows when it has nothing to show.
 *
 * Four screens had four answers to that — a paragraph, a shorter paragraph, a
 * line of grey text, and nothing — none of which told the reader what to do
 * about it. One shape, and the thing to do about it goes in [action].
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(40.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}
