package dev.gomoku.zenyth.feature.board

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.component.CalmCard
import dev.gomoku.zenyth.core.designsystem.component.CardTitle
import dev.gomoku.zenyth.core.designsystem.component.GradeMark
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.designsystem.theme.LocalReducedMotion
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
 * The position card: the opening name chain with its 흑 5수 grade; the last
 * move's grade; the database's value and comment; the file this line came
 * from; and at its foot the file work as four labelled keys. On the game tab,
 * the two buttons that end a game.
 *
 * Whose move, how many and the last one were three tiles here; they went
 * (user request, 2026-10-31) — the board, the one-line strip and the sheet's
 * head already say them.
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
        }
        ui.game.result?.let {
            Text(
                it.describe(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
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
        GameFileKeys(ui, files)
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
/**
 * The file work as four labelled keys (2026-10-31). It sat behind a small
 * «기보 ⌄» beside the title, which read as a label rather than a menu, so
 * people never found «열기» or «저장». Mornye's icon-button group — the shape
 * of Settings ▸ 디자인's theme keys. «저장» and «공유» each open a small menu
 * for the one choice they hide (the format; a link or the text), so four keys
 * put everything one tap away and the card stays calm.
 */
@Composable
private fun GameFileKeys(ui: PositionCardUi, files: GameFileActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FileKey(Icons.Outlined.FolderOpen, tr("열기", "Open", "打开"), Modifier.weight(1f), onClick = files.open)
        FileKey(
            Icons.Outlined.Save,
            tr("저장", "Save", "保存"),
            Modifier.weight(1f),
            menu = listOf(
                FileChoice(tr(".psq 로 저장", "As .psq", "保存为 .psq")) { files.save(GameFileFormat.PSQ) },
                FileChoice(tr(".sav 로 저장", "As .sav", "保存为 .sav")) { files.save(GameFileFormat.SAV) },
            ),
        )
        FileKey(
            Icons.Outlined.Share,
            tr("공유", "Share", "分享"),
            Modifier.weight(1f),
            menu = listOf(
                FileChoice(tr("링크 복사", "Copy link", "复制链接"), enabled = ui.moveCount > 0, action = files.copyLink),
                FileChoice(tr("국면 문자열", "Position string", "局面代码"), action = files.positionString),
            ),
        )
        FileKey(Icons.Outlined.Image, tr("이미지", "Image", "图片"), Modifier.weight(1f), onClick = files.saveImage)
    }
}

/** One entry of a [FileKey]'s menu. */
private class FileChoice(val label: String, val enabled: Boolean = true, val action: () -> Unit)

/**
 * A key of [GameFileKeys]: icon over label on a quiet face, its corners
 * squaring a little under the finger like every key in the app. With a
 * [menu] it opens that instead of acting.
 */
@Composable
private fun FileKey(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    menu: List<FileChoice> = emptyList(),
    onClick: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val corner by animateIntAsState(
        if (pressed && !reduced) 18 else 26,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "fileKey",
    )
    val scheme = MaterialTheme.colorScheme
    Box(modifier) {
        Surface(
            onClick = { if (menu.isEmpty()) onClick() else open = true },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(corner),
            color = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
            interactionSource = interaction,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = scheme.primary)
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        if (menu.isNotEmpty()) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                menu.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.label) },
                        enabled = choice.enabled,
                        onClick = {
                            open = false
                            choice.action()
                        },
                    )
                }
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
