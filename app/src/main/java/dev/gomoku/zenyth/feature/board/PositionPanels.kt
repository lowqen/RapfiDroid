package dev.gomoku.zenyth.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import dev.gomoku.zenyth.core.designsystem.component.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.component.CalmCard
import dev.gomoku.zenyth.core.designsystem.component.CardTitle
import dev.gomoku.zenyth.core.designsystem.component.GradeMark
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.GameFileFormat

/** What the 기보 menu can do; the screen owns the pickers. */
internal class GameFileActions(
    val open: () -> Unit,
    val save: (GameFileFormat) -> Unit,
    val copyLink: () -> Unit,
    val positionString: () -> Unit,
    val saveImage: () -> Unit,
)
/**
 * The position card: whose move, how many, the last one; the opening name chain
 * with its 흑 5수 grade; the last move's grade; the database's value and
 * comment; the file this line came from. File work is behind one «기보» menu.
 * On the game tab, the two buttons that end a game.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PositionCard(
    ui: PositionCardUi,
    files: GameFileActions,
    onResign: () -> Unit,
    onDraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CalmCard(modifier) {
        CardTitle(tr("국면", "Position", "局面")) {
            Text(
                ruleLabel(ui.rule),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            GameFileMenu(ui, files)
        }
        ui.game.result?.let {
            Text(
                it.describe(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(tr("둘 차례", "To move", "轮到"), ui.sideToMove.label, Modifier.weight(1f))
            Tile(
                tr("수", "Moves", "手数"),
                if (ui.futureCount > 0) "${ui.moveCount} / ${ui.moveCount + ui.futureCount}" else "${ui.moveCount}",
                Modifier.weight(1f),
            )
            Tile(
                tr("마지막", "Last", "最后一手"),
                ui.render.lastMove?.label(ui.render.size) ?: tr("없음", "None", "无"),
                Modifier.weight(1f),
            )
        }
        if (ui.openingNames.isNotEmpty()) {
            KeyValue(tr("주형", "Opening", "开局")) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(ui.openingNames.joinToString(" › "), style = MaterialTheme.typography.bodyMedium)
                    ui.openingGrade?.let { GradeMark(it) }
                }
            }
        }
        ui.moveGrade?.let { q ->
            KeyValue(tr("평가", "Grade", "评级")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(hexColor(q.colorHex)),
                        contentAlignment = Alignment.Center,
                    ) { Text(q.symbol, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    Text(q.display, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (ui.analysisTab && ui.db.enabled && ui.dbActive) {
            KeyValue("DB") {
                val v = ui.dbValue
                Text(
                    (v?.let { dbValueText(it.stmMate, it.stmWinRate) + " (${ui.sideToMove.label})" }
                        ?: tr("값 없음", "No value", "无估值")) +
                        tr(" · 기록 ${ui.db.snapshot.cells.size}", " · ${ui.db.snapshot.cells.size} records", " · 记录 ${ui.db.snapshot.cells.size} 条"),
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                )
            }
        }
        if (ui.db.snapshot.comment.isNotBlank()) {
            KeyValue(tr("주석", "Note", "注释")) {
                val base = MaterialTheme.typography.bodyMedium
                Text(ui.db.snapshot.comment, style = base.copy(fontSize = base.fontSize * ui.dbCommentFont.scale))
            }
        }
        ui.gameName?.let { name ->
            KeyValue(tr("기보", "Game", "棋谱")) { Text(name, style = MaterialTheme.typography.bodyMedium) }
        }
        if (!ui.analysisTab) {
            if (ui.game.offeringFifth) {
                Hint(tr("5수 후보 ${ui.game.fifthCount}개를 놓으세요", "Place ${ui.game.fifthCount} fifth-move candidates", "请放置 ${ui.game.fifthCount} 个第5手打点"))
            }
            val playing = ui.game.result == null && ui.moveCount > 0
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onResign, enabled = playing) { Text(tr("기권", "Resign", "认输")) }
                OutlinedButton(onClick = onDraw, enabled = playing && ui.connection.isLive) { Text(tr("무승부", "Draw", "和棋")) }
            }
        }
    }
}
// The same card from the whole state — for callers that have nothing else,
// the screenshot tests among them. The screen hands it the slice.
@Composable
internal fun PositionCard(
    ui: BoardUiState,
    files: GameFileActions,
    onResign: () -> Unit,
    onDraw: () -> Unit,
    modifier: Modifier = Modifier,
) = PositionCard(ui.positionCardUi(), files, onResign, onDraw, modifier)
@Composable
private fun GameFileMenu(ui: PositionCardUi, files: GameFileActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(tr("기보", "Game", "棋谱"))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun item(label: String, enabled: Boolean = true, action: () -> Unit) = Triple(label, enabled, action)
            listOf(
                item(tr("기보 열기", "Open game", "打开棋谱"), action = files.open),
                item(tr(".psq 저장", "Save .psq", "保存 .psq")) { files.save(GameFileFormat.PSQ) },
                item(tr(".sav 저장", "Save .sav", "保存 .sav")) { files.save(GameFileFormat.SAV) },
                item(tr("링크 복사", "Copy link", "复制链接"), enabled = ui.moveCount > 0, action = files.copyLink),
                item(tr("국면 문자열", "Position string", "局面代码"), action = files.positionString),
                item(tr("이미지 저장", "Save image", "保存图片"), action = files.saveImage),
            ).forEach { (label, enabled, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    enabled = enabled,
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}
/**
 * The position in one line, for narrow screens where the card would push the
 * game controls off the bottom: whose move, count and last, name and grade.
 */
@Composable
internal fun PositionStrip(ui: PositionStripUi, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val skin = ZenythTheme.board
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(if (ui.sideToMove.name == "BLACK") skin.blackLow else skin.whiteHigh),
            )
            Text(
                ui.game.result?.describe() ?: tr("${ui.sideToMove.label} 차례", "${ui.sideToMove.label} to move", "轮到${ui.sideToMove.label}"),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                tr("${ui.moveCount}수", "${ui.moveCount} moves", "${ui.moveCount} 手") +
                    (ui.render.lastMove?.let { " · " + it.label(ui.render.size) } ?: ""),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            ui.openingNames.lastOrNull()?.let {
                Text(
                    it,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ui.openingGrade?.let { GradeMark(it, withName = false) }
        }
    }
}
// The same card from the whole state — for callers that have nothing else,
// the screenshot tests among them. The screen hands it the slice.
@Composable
internal fun PositionStrip(ui: BoardUiState, modifier: Modifier = Modifier) = PositionStrip(ui.positionStripUi(), modifier)
/** «링크로 연 판» — the board a link replaced, until the user decides. */
@Composable
internal fun LinkBackupCard(onRestore: () -> Unit, onDrop: () -> Unit) {
    CalmCard {
        Text(tr("링크의 국면을 열었습니다", "Opened the linked position", "已打开链接中的局面"), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRestore) { Text(tr("두던 판으로 돌아가기", "Back to my board", "回到原来的棋盘")) }
            TextButton(onClick = onDrop) { Text(tr("버리기", "Discard", "丢弃")) }
        }
    }
}
