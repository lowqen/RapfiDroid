package dev.gomoku.zenyth.feature.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.zenyth.core.designsystem.component.MiniBoard
import dev.gomoku.zenyth.core.designsystem.component.TileShape
import dev.gomoku.zenyth.core.designsystem.component.WavyProgress
import dev.gomoku.zenyth.core.designsystem.theme.expandFadeIn
import dev.gomoku.zenyth.core.designsystem.theme.shrinkFadeOut
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr

/**
 * The board's engine, seen from the other tabs — Mornye's mini player (UI
 * overhaul 2, M5). While an analysis runs or the engine thinks on its move in
 * a game, a slim bar sits over the tab bar with the position in small, the
 * best move, the win rate and ⏸; a tap goes back to the board.
 *
 * It reads the board's own [BoardViewModel] — the one the board tab made, which
 * lives as long as the app does because the board is the first destination —
 * and asks it nothing but to stop.
 */
@Composable
fun MiniAnalysisBar(viewModel: BoardViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val live = ui.analyzing || ui.balancing || ui.game.thinking
    AnimatedVisibility(live, modifier = modifier, enter = expandFadeIn, exit = shrinkFadeOut) {
        MiniAnalysisContent(ui, onOpen = onOpen, onPause = viewModel::onStopAnalyze)
    }
}

/** The bar itself; see [MiniAnalysisBar]. */
@Composable
internal fun MiniAnalysisContent(ui: BoardUiState, onOpen: () -> Unit, onPause: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val snap = ui.shown
    val stats = snap?.stats
    val best = snap?.best?.head ?: snap?.realtimeBest
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHigh,
    ) {
        Column {
            val run = ui.timedRun()
            val timed = run != null
            if (run != null) {
                TimedWave(run, Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp))
            }
            Row(
                Modifier.padding(start = 10.dp, end = 10.dp, top = if (timed) 4.dp else 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiniBoard(stones = ui.render.stones, modifier = Modifier.size(44.dp).clip(TileShape), showNumbers = false)
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            ui.game.thinking -> tr("엔진이 생각 중", "Engine thinking", "引擎思考中")
                            best != null -> best.label(ui.render.size)
                            else -> tr("읽는 중", "Reading", "读取中")
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            stats?.winRatePct?.let { tr("승률 $it%", "Win $it%", "胜率 $it%") },
                            stats?.depth?.takeIf { it > 0 }?.let { tr("깊이 $it", "Depth $it", "深度 $it") },
                            tr("${ui.moveCount}수", "move ${ui.moveCount}", "第${ui.moveCount}手"),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (ui.analyzing || ui.balancing) {
                    Surface(
                        onClick = onPause,
                        modifier = Modifier.size(width = 52.dp, height = 44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Pause, contentDescription = tr("엔진 멈춤", "Pause engine", "暂停引擎"))
                        }
                    }
                }
            }
        }
    }
}

/**
 * A game review or a proof at work, in the same bar (2026-10-31): it took over
 * from the coloured badge that sat on the board. The board shows what the
 * engine is doing; this says how far it has got, wherever the user is — the
 * job and its place, the engine's reading, the position in small, the wave,
 * and ⏹. A tap goes to the research tab, where the run's own card is.
 */
@Composable
fun ResearchMiniBar(viewModel: BoardViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val run = ui.research
    // The last run stays drawn while the bar folds away.
    var last by remember { mutableStateOf<ResearchRun?>(null) }
    SideEffect { if (run != null) last = run }
    AnimatedVisibility(run != null, modifier = modifier, enter = expandFadeIn, exit = shrinkFadeOut) {
        (run ?: last)?.let { ResearchMiniContent(it, onOpen = onOpen, onStop = viewModel::onStopResearch) }
    }
}

/** The bar itself; see [ResearchMiniBar]. */
@Composable
internal fun ResearchMiniContent(run: ResearchRun, onOpen: () -> Unit, onStop: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHigh,
    ) {
        Column {
            key(run.wave) {
                WavyProgress(
                    fraction = run.fraction,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
                    perSecond = run.perSecond,
                    ceiling = run.ceiling,
                )
            }
            Row(
                Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiniBoard(stones = run.stones, modifier = Modifier.size(44.dp).clip(TileShape), showNumbers = false)
                Column(Modifier.weight(1f)) {
                    Text(
                        run.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold).tabular(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        run.detail,
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    onClick = onStop,
                    modifier = Modifier.size(width = 52.dp, height = 44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.primary,
                    contentColor = scheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = if (run.prove) tr("증명 중지", "Stop the proof", "停止证明") else tr("리뷰 중지", "Stop the review", "停止复盘"),
                        )
                    }
                }
            }
        }
    }
}
