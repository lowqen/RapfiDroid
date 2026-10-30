package dev.gomoku.zenyth.feature.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import dev.gomoku.zenyth.core.designsystem.component.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.component.CalmCard
import dev.gomoku.zenyth.core.designsystem.component.CardTitle
import dev.gomoku.zenyth.core.designsystem.component.ChoiceChips
import dev.gomoku.zenyth.core.designsystem.component.FieldRow
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.designsystem.component.StatusDot
import dev.gomoku.zenyth.core.designsystem.component.TimePicker
import dev.gomoku.zenyth.core.designsystem.component.engineWord
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.ClockSide
import dev.gomoku.zenyth.core.model.ComputerSide
import dev.gomoku.zenyth.core.model.GameClock
import dev.gomoku.zenyth.core.model.Duration

/**
 * The game tab's card: what the engine is doing, «지금 두기» when it thinks
 * until told, and the game's settings — colours, time, strength, rules. No
 * depth, no lines: watching the opponent think is the analysis tab's job.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameCard(
    ui: GameCardUi,
    clock: () -> GameClock,
    onComputerSide: (ComputerSide) -> Unit,
    onMoveTime: (Long) -> Unit,
    onStrength: (Int) -> Unit,
    onRule: (Int) -> Unit,
    onMoveNow: () -> Unit,
    onEngineMove: () -> Unit,
    onNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CalmCard(modifier) {
        val (word, color) = engineWord(ui.connection)
        CardTitle(tr("대국", "Game", "对局")) {
            StatusDot(if (ui.game.thinking) ZenythTheme.colors.caution else color)
            Text(
                if (ui.game.thinking) tr("엔진이 생각 중", "Engine thinking", "引擎思考中") else word,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val level = GameLevels.all.first { it.level == ui.strength }
        Hint(ui.game.computerSide.label + " · " + tr("레벨 ${level.ko}", "Level ${level.en}", "棋力 ${level.zh}"))
        if (ui.game.thinking && ui.unlimitedTurn) {
            Button(onClick = onMoveNow, modifier = Modifier.fillMaxWidth()) { Text(tr("지금 두기", "Move now", "立即落子")) }
        } else if (ui.engineOnMove) {
            Button(onClick = onEngineMove, modifier = Modifier.fillMaxWidth()) { Text(tr("엔진 착수", "Engine move", "引擎落子")) }
        }
        if (!ui.connection.isLive) {
            Hint(tr("엔진이 켜져야 컴퓨터가 둡니다", "The computer moves once the engine is on", "引擎开启后计算机才会落子"))
        }
        FieldRow(tr("흑 / 백", "Black / White", "黑 / 白"), note = tr("엔진 차례에 판을 누르면 엔진이 둡니다", "Tap the board on the engine's turn", "轮到引擎时点击棋盘，引擎即落子")) {
            ChoiceChips(
                options = ComputerSide.entries.map { it to it.label },
                selected = ui.game.computerSide,
                onSelect = onComputerSide,
            )
        }
        FieldRow(tr("시간 제어", "Time control", "时间控制")) {
            TimePicker(
                valueMs = ui.moveTimeMs,
                presetsMs = GameLevels.MOVE_TIMES,
                spec = Duration.MOVE_TIME.copy(minMs = 1_000, stepMs = 1_000),
                onChange = onMoveTime,
                onNote = onNote,
            )
        }
        FieldRow(tr("레벨", "Level", "棋力"), note = tr("한 수에 읽을 노드 수", "Nodes per move", "每步搜索结点数")) {
            ChoiceChips(
                options = GameLevels.all.map { it.level to tr(it.ko, it.en, it.zh) },
                selected = ui.strength,
                onSelect = onStrength,
            )
        }
        FieldRow(tr("룰셋", "Rules", "规则"), note = tr("바꾸면 새 대국", "Changing starts a new game", "更改后开始新对局")) {
            ChoiceChips(
                options = (0..6).map { it to ruleLabel(it) },
                selected = ui.rule,
                onSelect = onRule,
            )
        }
        if (ui.openingNeedsOddSize) {
            Hint(tr("오프닝 규칙은 홀수 크기 판이 필요합니다", "An opening rule needs an odd board size", "开局规则需要奇数大小的棋盘"), color = MaterialTheme.colorScheme.error)
        }
        if (ui.showClock && ui.game.active) ClockRow(clock)
        ui.game.log.lastOrNull()?.let { Hint(it) }
    }
}
// The same card from the whole state — for callers that have nothing else,
// the screenshot tests among them. The screen hands it the slice.
@Composable
internal fun GameCard(
    ui: BoardUiState,
    onComputerSide: (ComputerSide) -> Unit,
    onMoveTime: (Long) -> Unit,
    onStrength: (Int) -> Unit,
    onRule: (Int) -> Unit,
    onMoveNow: () -> Unit,
    onEngineMove: () -> Unit,
    onNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) = GameCard(
    ui.gameCardUi(), { ui.game.clock }, onComputerSide, onMoveTime, onStrength, onRule, onMoveNow, onEngineMove, onNote, modifier,
)
/** Read here and nowhere else, so a tick composes this row and not the card around it. */
@Composable
private fun ClockRow(current: () -> GameClock) {
    val clock = current()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(ClockSide.COMPUTER to tr("컴퓨터", "Computer", "计算机"), ClockSide.HUMAN to tr("사람", "Human", "人")).forEach { (side, name) ->
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.labelMedium)
                    if (clock.running == side) StatusDot(ZenythTheme.colors.positive, size = 6.dp)
                }
                Text(clock.label(side), style = MaterialTheme.typography.titleMedium.tabular())
                Text(
                    tr("사용 ${clock.usedLabel(side)}", "used ${clock.usedLabel(side)}", "已用 ${clock.usedLabel(side)}"),
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
