package dev.gomoku.zenyth.feature.board

import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import dev.gomoku.zenyth.core.designsystem.component.RapfiSegmented
import dev.gomoku.zenyth.core.designsystem.component.rememberRapfiHaptics
import dev.gomoku.zenyth.core.designsystem.theme.MOTION_VALUE
import dev.gomoku.zenyth.core.designsystem.theme.RapfiTheme
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.BoardMode
import dev.gomoku.zenyth.core.model.ComputerSide
import kotlin.math.abs
import kotlin.math.roundToInt

/** 분석 | 대국 — analysis first, it is what a first visit opens on. */
@Composable
internal fun BoardModeSwitch(mode: BoardMode, onMode: (BoardMode) -> Unit, modifier: Modifier = Modifier) {
    RapfiSegmented(
        options = listOf(
            BoardMode.ANALYSIS to tr("분석", "Analysis", "分析"),
            BoardMode.GAME to tr("대국", "Play", "对局"),
        ),
        selection = mode,
        onSelect = onMode,
        modifier = modifier,
    )
}

/** Finger travel along the toolbar that steps one move when scrubbing. */
private val ScrubStep = 28.dp

/**
 * The move toolbar under the board, as Mornye's player lays out its controls
 * (UI overhaul 2, M5): a button group with no pill behind it. In the middle
 * the one primary action, ▶ — a wide rounded square in the strong accent; on
 * either side `<` `>`, keys in the pale accent; at the ends `|<` `>|`, and
 * further out «새 판» and, on the game tab, the side swap (where Mornye keeps
 * shuffle and repeat), as smaller round keys on a quiet face. Every button has
 * a face — bare icons beside boxed keys left the ends floating (device review)
 * — and the order of importance is told by size and colour. On the game tab
 * there is no ▶: the engine moves on its own turns.
 *
 * The keys answer a press by squaring their corners a little and springing
 * back; ▶ is rounder while the engine reads, so the state shows in its shape
 * as well as its icon.
 *
 * **Scrubbing.** A finger drawn along the toolbar steps through the game, one
 * move per [ScrubStep], with a tick per move — the quick way from move 40 back
 * to move 12. It goes through the same undo and redo as the buttons, and stops
 * where they would stop (the start, the end, an engine thinking on its move).
 * A tap is still a tap: the drag only begins past the touch slop.
 *
 * On a narrow column (a small phone on its side) the whole group scales down
 * rather than overflow.
 */
@Composable
internal fun MoveToolbar(
    ui: BoardUiState,
    onNewBoard: () -> Unit,
    onFirst: () -> Unit,
    onUndo: () -> Unit,
    onToggle: () -> Unit,
    onRedo: () -> Unit,
    onLast: () -> Unit,
    onSwapSides: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocked = ui.game.thinking || ui.game.prompt != null
    val canBack = ui.canUndo
    val canForward = ui.canRedo && !blocked
    val haptics = rememberRapfiHaptics()
    val back by rememberUpdatedState(canBack)
    val forward by rememberUpdatedState(canForward)
    val undo by rememberUpdatedState(onUndo)
    val redo by rememberUpdatedState(onRedo)
    val step = with(LocalDensity.current) { ScrubStep.toPx() }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // The group's width at full size: four bare icons, two keys, ▶, the gaps.
        val full = BareSize * 4 + KeySize * 2 + PlayWidth + 6.dp * 6
        val k = ((maxWidth - 16.dp) / full).coerceIn(0.7f, 1f)
        Row(
            // One group for TalkBack (M7): its keys are read left to right as a
            // set, and the drag below is a shortcut for the ‹ › keys it holds.
            Modifier.semantics { isTraversalGroup = true }.pointerInput(step) {
                var travel = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travel = 0f },
                ) { change, dx ->
                    change.consume()
                    travel += dx
                    while (travel >= step) {
                        travel -= step
                        if (forward) {
                            redo()
                            haptics.scrubTick()
                        }
                    }
                    while (travel <= -step) {
                        travel += step
                        if (back) {
                            undo()
                            haptics.scrubTick()
                        }
                    }
                }
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp * k),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EndKey(Icons.Filled.Refresh, tr("새 판", "New board", "新建"), true, onNewBoard, BareSize * k)
            EndKey(Icons.Filled.FirstPage, tr("처음으로", "To start", "到开头"), canBack, onFirst, BareSize * k)
            ToolKey(Icons.Filled.ChevronLeft, tr("한 수 뒤로", "Back one", "后退一步"), canBack, onUndo, KeySize * k, KeySize * k)
            if (ui.analysisTab) {
                val running = ui.analyzing || ui.balancing
                ToolKey(
                    icon = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    label = if (running) tr("엔진 멈춤", "Pause engine", "暂停引擎") else tr("엔진 분석", "Analyse", "引擎分析"),
                    enabled = running || ui.canAnalyze,
                    onClick = onToggle,
                    width = PlayWidth * k,
                    height = KeySize * k,
                    tone = KeyTone.Strong,
                    round = running,
                )
            }
            ToolKey(Icons.Filled.ChevronRight, tr("한 수 앞으로", "Forward one", "前进一步"), canForward, onRedo, KeySize * k, KeySize * k)
            EndKey(Icons.AutoMirrored.Filled.LastPage, tr("끝으로", "To end", "到末尾"), canForward, onLast, BareSize * k)
            if (!ui.analysisTab) {
                val side = ui.game.computerSide
                val canSwap = (side == ComputerSide.BLACK || side == ComputerSide.WHITE) &&
                    !ui.game.thinking && ui.game.prompt == null
                EndKey(Icons.Filled.SwapHoriz, tr("진영 전환", "Swap sides", "交换执子"), canSwap, onSwapSides, BareSize * k)
            } else {
                // The swap's place stays, so the group does not shift between the tabs.
                Spacer(Modifier.size(BareSize * k))
            }
        }
    }
}

/** The bare icons at the toolbar's ends (touch target, not a face). */
private val BareSize = 44.dp

/** The `<` `>` keys, and the height of ▶. */
private val KeySize = 56.dp

/** ▶ is wider than its neighbours, as Mornye's play button is (~100 × 72 against 68). */
private val PlayWidth = 84.dp

/**
 * A key at the toolbar's ends: a bare icon, no face (user request, 2026-10-29 —
 * the round faces made six boxes of one row). The faces stay on the three keys
 * in the middle, so ‹ ▶ › read as the controls and the ends as tools.
 */
@Composable
private fun EndKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp,
) = ToolKey(icon, label, enabled, onClick, width = size, height = size, tone = KeyTone.Quiet, round = true)

/** How much a [ToolKey] stands out: ▶, the step keys, the ends. */
private enum class KeyTone { Strong, Accent, Quiet }

/**
 * One key of the group: a rounded square, [strong] in the accent (▶) or the
 * pale accent (`<` `>`), or no face at all ([KeyTone.Quiet], the ends). A
 * press squares the corners and springs them back (M5's shape morph); [round]
 * holds them round (▶ while the engine reads).
 */
@Composable
private fun ToolKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    tone: KeyTone = KeyTone.Accent,
    round: Boolean = false,
) {
    val strong = tone == KeyTone.Strong
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = when {
            pressed -> height * 0.24f
            round -> height / 2
            else -> height * 0.42f
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "keyCorner",
    )
    val container = when {
        tone == KeyTone.Quiet -> Color.Transparent
        !enabled -> scheme.onSurface.copy(alpha = 0.08f)
        tone == KeyTone.Strong -> scheme.primary
        else -> scheme.secondaryContainer
    }
    val content = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        tone == KeyTone.Strong -> scheme.onPrimary
        tone == KeyTone.Accent -> scheme.onSecondaryContainer
        else -> scheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(width = width, height = height),
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(height * if (strong) 0.5f else 0.46f))
        }
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
    if (v == null) return tr("평가 없음", "No evaluation", "无评价")
    val src = if (v.fromDb) tr(" · DB 기록", " · database", " · 数据库记录") else ""
    v.mate?.let {
        return (if (it > 0) tr("흑 ${abs(it)}수 외통", "Black mates in ${abs(it)}", "黑 ${abs(it)} 步杀")
        else tr("백 ${abs(it)}수 외통", "White mates in ${abs(it)}", "白 ${abs(it)} 步杀")) + src
    }
    val b = v.blackPct.roundToInt()
    return tr("흑 $b% · 백 ${100 - b}%", "Black $b% · White ${100 - b}%", "黑 $b% · 白 ${100 - b}%") + src
}

/** Width of the eval bar beside the board. */
internal fun evalBarWidth(compact: Boolean): Dp = if (compact) 20.dp else 30.dp
