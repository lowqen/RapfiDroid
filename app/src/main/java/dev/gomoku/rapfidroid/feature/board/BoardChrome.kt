package dev.gomoku.rapfidroid.feature.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import dev.gomoku.rapfidroid.core.designsystem.theme.MOTION_VALUE
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiTheme
import dev.gomoku.rapfidroid.core.designsystem.theme.tabular
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.BoardMode
import dev.gomoku.rapfidroid.core.model.ComputerSide
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The strip above the cards: the [분석] | [대국] tabs, «새 판», and on the game
 * tab the side swap with who has which colour. Nothing that the move bar
 * already does — two sets of the same buttons make people ask which to press.
 */
@Composable
internal fun StageBar(
    ui: BoardUiState,
    onMode: (BoardMode) -> Unit,
    onNewBoard: () -> Unit,
    onSwapSides: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeTabs(ui.mode, onMode)
        OutlinedButton(
            onClick = onNewBoard,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(40.dp),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" " + tr("새 판", "New"), maxLines = 1)
        }
        if (!ui.analysisTab) {
            val side = ui.game.computerSide
            val canSwap = (side == ComputerSide.BLACK || side == ComputerSide.WHITE) &&
                !ui.game.thinking && ui.game.prompt == null
            IconButton(onClick = onSwapSides, enabled = canSwap) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = tr("진영 전환", "Swap sides"))
            }
        }
    }
}

/** Analysis first — it is what a first visit opens on. */
@Composable
private fun ModeTabs(mode: BoardMode, onMode: (BoardMode) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.height(40.dp),
    ) {
        Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                BoardMode.ANALYSIS to tr("분석", "Analysis"),
                BoardMode.GAME to tr("대국", "Play"),
            ).forEach { (m, label) ->
                val on = m == mode
                Surface(
                    onClick = { onMode(m) },
                    shape = CircleShape,
                    color = if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (on) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxHeight()
                        .semantics {
                            role = Role.Tab
                            selected = on
                        },
                ) {
                    Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * The move bar — `|<` `<` `▶` `>` `>|`, and only those. ▶ lives on the
 * analysis tab alone: on the game tab the engine moves on its own turns.
 */
@Composable
internal fun MoveTransport(
    ui: BoardUiState,
    onFirst: () -> Unit,
    onUndo: () -> Unit,
    onToggle: () -> Unit,
    onRedo: () -> Unit,
    onLast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocked = ui.game.thinking || ui.game.prompt != null
    val canBack = ui.canUndo
    val canForward = ui.canRedo && !blocked
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.Filled.FirstPage, tr("처음으로", "To start"), canBack, onFirst)
        BarButton(Icons.Filled.ChevronLeft, tr("한 수 뒤로", "Back one"), canBack, onUndo)
        if (ui.analysisTab) {
            val running = ui.analyzing || ui.balancing
            FilledIconButton(
                onClick = onToggle,
                enabled = running || ui.canAnalyze,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (running) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (running) tr("엔진 멈춤", "Pause engine") else tr("엔진 분석", "Analyse"),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        BarButton(Icons.Filled.ChevronRight, tr("한 수 앞으로", "Forward one"), canForward, onRedo)
        BarButton(Icons.AutoMirrored.Filled.LastPage, tr("끝으로", "To end"), canForward, onLast)
    }
}

@Composable
private fun BarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
    }
}

/**
 * The eval bar — chess.com's vertical gauge, Black on top. It fills from the
 * top by Black's win rate and writes the leading side's number at its end
 * (`M5` for a mate). Only the fill moves (200 ms); numbers are never animated.
 * A database value says «DB» under it.
 *
 * Narrow ([compact], a phone in portrait) it is a little slimmer and shows only
 * the leading side's number.
 */
@Composable
internal fun VerticalEvalBar(eval: EvalView?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val skin = RapfiTheme.board
    val black = (eval?.blackPct ?: 50.0).coerceIn(0.0, 100.0).toFloat()
    val frac by animateFloatAsState(black / 100f, tween(MOTION_VALUE), label = "evalBar")
    val (main, sub) = labels(eval)
    val description = describe(eval)
    Box(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(if (compact) 3.dp else 6.dp))
            .background(skin.whiteHigh)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(if (compact) 3.dp else 6.dp))
            .semantics { contentDescription = description },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(maxHeight * frac)
                    .background(skin.blackLow),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(0.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            )
            if (main.isNotEmpty()) {
                val blackLeads = black >= 50f
                Column(
                    Modifier
                        .align(if (blackLeads) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val ink = if (blackLeads) skin.whiteHigh else skin.blackLow
                    Text(main, color = ink, fontSize = if (compact) 9.sp else 11.sp, maxLines = 1, style = MaterialTheme.typography.labelSmall.tabular(), overflow = TextOverflow.Clip)
                    if (!compact) sub?.let { Text(it, color = ink.copy(alpha = 0.8f), fontSize = 9.sp, maxLines = 1, style = MaterialTheme.typography.labelSmall.tabular()) }
                }
            }
        }
    }
}

private fun labels(v: EvalView?): Pair<String, String?> {
    if (v == null) return "" to null
    v.mate?.let { return "M${abs(it)}" to if (v.fromDb) "DB" else null }
    val lead = maxOf(v.blackPct, 100 - v.blackPct).roundToInt()
    if (v.fromDb) return "$lead" to "DB"
    return "$lead" to v.evalCp?.let { abs(it).toString() }
}

private fun describe(v: EvalView?): String {
    if (v == null) return tr("평가 없음", "No evaluation")
    val src = if (v.fromDb) tr(" · DB 기록", " · database") else ""
    v.mate?.let {
        return (if (it > 0) tr("흑 ${abs(it)}수 외통", "Black mates in ${abs(it)}")
        else tr("백 ${abs(it)}수 외통", "White mates in ${abs(it)}")) + src
    }
    val b = v.blackPct.roundToInt()
    return tr("흑 $b% · 백 ${100 - b}%", "Black $b% · White ${100 - b}%") + src
}

/** Width of the eval bar beside the board. */
internal fun evalBarWidth(compact: Boolean): Dp = if (compact) 20.dp else 30.dp
