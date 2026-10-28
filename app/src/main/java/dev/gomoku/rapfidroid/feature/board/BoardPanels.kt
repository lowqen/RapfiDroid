package dev.gomoku.rapfidroid.feature.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.designsystem.component.CalmCard
import dev.gomoku.rapfidroid.core.designsystem.component.CardTitle
import dev.gomoku.rapfidroid.core.designsystem.component.ChoiceChips
import dev.gomoku.rapfidroid.core.designsystem.component.DisclosureCard
import dev.gomoku.rapfidroid.core.designsystem.component.FieldRow
import dev.gomoku.rapfidroid.core.designsystem.component.GradeMark
import dev.gomoku.rapfidroid.core.designsystem.component.Hint
import dev.gomoku.rapfidroid.core.designsystem.component.LocalShellChrome
import dev.gomoku.rapfidroid.core.designsystem.component.LocalUiPrefs
import dev.gomoku.rapfidroid.core.designsystem.component.StatusDot
import dev.gomoku.rapfidroid.core.designsystem.component.TagPalette
import dev.gomoku.rapfidroid.core.designsystem.component.TimePicker
import dev.gomoku.rapfidroid.core.designsystem.component.TextOutline
import dev.gomoku.rapfidroid.core.designsystem.component.engineWord
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiTheme
import dev.gomoku.rapfidroid.core.designsystem.theme.tabular
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.BoardShift
import dev.gomoku.rapfidroid.core.model.BoardSymmetry
import dev.gomoku.rapfidroid.core.model.CellTag
import dev.gomoku.rapfidroid.core.model.ClockSide
import dev.gomoku.rapfidroid.core.model.ComputerSide
import dev.gomoku.rapfidroid.core.model.ConnectionState
import dev.gomoku.rapfidroid.core.model.Duration
import dev.gomoku.rapfidroid.core.model.GameFileFormat
import dev.gomoku.rapfidroid.core.model.PvSnapshot
import dev.gomoku.rapfidroid.core.model.TagKind
import kotlin.math.roundToInt

// ---- position ---------------------------------------------------------------

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
    ui: BoardUiState,
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

@Composable
private fun GameFileMenu(ui: BoardUiState, files: GameFileActions) {
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
internal fun PositionStrip(ui: BoardUiState, modifier: Modifier = Modifier) {
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
            val skin = RapfiTheme.board
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

// ---- analysis ---------------------------------------------------------------

/**
 * The engine card of the analysis tab — what the summary line above it has no
 * room for: what the engine is doing, time · nodes · speed under «세부 수치»,
 * and the candidate lines.
 *
 * The best move, win rate, evaluation and depth are the summary line's
 * ([SummaryStrip]), and «분석» / «정지» are the toolbar's ▶: the card used to
 * repeat all of it, one sheet-height under the same numbers. Nothing read yet
 * says nothing here — the summary line says what ▶ does.
 *
 * A result whose position the board has moved on from keeps full strength and
 * says so in words. Dimmed, it was hard to read on exactly the screens where a
 * stopped analysis is being read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnalysisCard(
    ui: BoardUiState,
    onPreview: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = LocalUiPrefs.current
    var detail by remember { mutableStateOf(prefs?.isOpen("engine-detail", false) ?: false) }
    CalmCard(modifier) {
        val (word, color) = engineWord(ui.connection)
        CardTitle(tr("엔진", "Engine", "引擎")) {
            StatusDot(color)
            Text(
                when {
                    ui.analyzing && ui.defending -> tr("방어수 탐색 중", "Searching defences", "正在搜索防守")
                    ui.balancing -> tr("균형점 탐색 중", "Balancing", "正在寻找平衡点")
                    ui.analyzing -> tr("분석 중", "Analysing", "分析中")
                    else -> word
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!ui.connection.isLive) {
            val chrome = LocalShellChrome.current
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint(
                    if (ui.connection is ConnectionState.Error) tr("엔진을 띄우지 못했습니다", "The engine failed to start", "引擎未能启动")
                    else tr("엔진이 꺼져 있습니다", "The engine is off", "引擎已关闭"),
                    Modifier.weight(1f),
                )
                if (ui.connection is ConnectionState.Disconnected || ui.connection is ConnectionState.Error) {
                    FilledTonalButton(onClick = { chrome?.onStartEngine?.invoke() }) { Text(tr("엔진 켜기", "Start engine", "启动引擎")) }
                }
            }
        }
        val snap = ui.shown
        if (ui.stale) {
            Hint(
                if (ui.analyzing) tr("새 국면을 읽는 중 · 직전 국면(${ui.staleAt}수)의 분석", "Reading the new position · showing move ${ui.staleAt}", "正在读取新局面 · 显示第 ${ui.staleAt} 手的分析")
                else tr("정지됨 · 직전 국면(${ui.staleAt}수)의 분석", "Stopped · showing the analysis after move ${ui.staleAt}", "已停止 · 显示第 ${ui.staleAt} 手后的分析"),
            )
        }
        val blank = snap == null || (snap.pvs.isEmpty() && snap.stats.depth == 0)
        if (blank) return@CalmCard
        val s = snap!!.stats
        val size = ui.render.size
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (detail) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(tr("시간", "Time", "用时"), s.timeMs?.let { timeText(it) } ?: tr("없음", "None", "无"), Modifier.weight(1f))
                    Stat(tr("노드", "Nodes", "结点"), compact(s.nodes), Modifier.weight(1f))
                    Stat(tr("속도", "Speed", "速度"), s.speed?.let { compact(it) + "/s" } ?: tr("없음", "None", "无"), Modifier.weight(1f))
                }
            }
            Text(
                if (detail) tr("세부 수치 접기", "Hide details", "收起详细数据") else tr("세부 수치", "Details", "详细数据"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        detail = !detail
                        prefs?.setOpen("engine-detail", detail)
                    }
                    .padding(vertical = 4.dp),
            )
            if (snap.pvs.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                snap.pvs.sortedBy { it.index }.forEach { pv ->
                    PvRow(pv, size, selected = pv.index == ui.previewPv) {
                        onPreview(if (pv.index == ui.previewPv) null else pv.index)
                    }
                }
            }
        }
    }
}

@Composable
private fun PvRow(pv: PvSnapshot, size: Int, selected: Boolean, onClick: () -> Unit) {
    val palette = remember { TagPalette() }
    val tag = when {
        pv.mate != null && pv.mate > 0 -> CellTag("W${pv.mate}", TagKind.WIN, pv.depth)
        pv.mate != null -> CellTag("L${-pv.mate}", TagKind.LOSE, pv.depth)
        else -> CellTag(pv.winRate?.let { "${(it * 100).roundToInt()}%" } ?: "", TagKind.RATE, pv.depth, pv.winRate?.let { (it * 100).roundToInt() })
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.width(52.dp).clip(MaterialTheme.shapes.small).background(TextOutline.copy(alpha = 0.85f))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(tag.label, color = palette.textFor(tag), style = MaterialTheme.typography.labelLarge.tabular())
        }
        Text(
            pv.line.take(8).joinToString(" ") { it.label(size) },
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The analysis settings, folded: time, candidates, and whether a stop plays
 * the best move. The head says the current values, which is all most people read.
 */
@Composable
internal fun AnalysisOptions(
    ui: BoardUiState,
    onAnalysisSec: (Int) -> Unit,
    onMultiPv: (Int) -> Unit,
    onPlayBest: (Boolean) -> Unit,
    onNote: (String) -> Unit,
) {
    val time = if (ui.analysisSec <= 0) tr("무제한", "Unlimited", "不限时") else tr("${ui.analysisSec}초", "${ui.analysisSec}s", "${ui.analysisSec} 秒")
    DisclosureCard(
        id = "analysis-options",
        title = tr("분석 설정", "Analysis settings", "分析设置"),
        summary = "$time · " + tr("후보 ${ui.multiPv}수", "${ui.multiPv} candidates", "候选 ${ui.multiPv} 手"),
    ) {
        FieldRow(tr("분석 시간", "Analysis time", "分析时间")) {
            TimePicker(
                valueMs = ui.analysisSec * 1000L,
                presetsMs = GameLevels.ANALYSIS_SECS.map { it * 1000L },
                spec = Duration.ANALYSIS_TIME,
                onChange = { onAnalysisSec((it / 1000).toInt()) },
                onNote = onNote,
            )
        }
        FieldRow(tr("후보 수", "Candidates", "候选数")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMultiPv(ui.multiPv - 1) }, enabled = ui.multiPv > 1) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text("${ui.multiPv}", style = MaterialTheme.typography.titleMedium.tabular())
                IconButton(onClick = { onMultiPv(ui.multiPv + 1) }, enabled = ui.multiPv < 8) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("정지하면 최선수 두기", "Play the best move on stop", "停止时下出最佳着法"), style = MaterialTheme.typography.labelLarge)
                Hint(tr("빈 판은 H8", "H8 on an empty board", "空棋盘下 H8"))
            }
            Switch(checked = ui.playBestOnStop, onCheckedChange = onPlayBest)
        }
    }
}

/** The search tools that are not «analyse»: defences, balance, shape moves, the user's toolbar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolsCard(
    ui: BoardUiState,
    onDefend: () -> Unit,
    onBalance: () -> Unit,
    onSymmetry: (BoardSymmetry) -> Unit,
    onShift: (BoardShift) -> Unit,
    onToggleForbidden: () -> Unit,
    onRunScript: (String) -> Unit,
) {
    DisclosureCard(id = "board-tools", title = tr("도구", "Tools", "工具")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = onDefend, enabled = ui.canAnalyze && !ui.busy, label = { Text(tr("모든 방어수", "All defences", "所有防守点")) })
            AssistChip(onClick = onBalance, enabled = ui.canAnalyze && !ui.busy, label = { Text(tr("균형점 찾기", "Balance", "平衡点")) })
            if (ui.isRenju) {
                AssistChip(
                    onClick = onToggleForbidden,
                    label = { Text(if (ui.showForbidden) tr("금수 숨기기", "Hide forbidden", "隐藏禁手") else tr("금수 표시", "Show forbidden", "显示禁手")) },
                )
            }
        }
        FieldRow(tr("모양 대칭", "Rotate / flip", "旋转 / 翻转")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "90°" to BoardSymmetry.ROTATE_90,
                    "180°" to BoardSymmetry.ROTATE_180,
                    "270°" to BoardSymmetry.ROTATE_270,
                    tr("좌우", "Mirror ↔", "左右") to BoardSymmetry.MIRROR_LEFT_RIGHT,
                    tr("상하", "Mirror ↕", "上下") to BoardSymmetry.MIRROR_UP_DOWN,
                    tr("대각 ＼", "Diagonal ＼", "对角 ＼") to BoardSymmetry.MIRROR_DIAGONAL,
                    tr("대각 ／", "Diagonal ／", "对角 ／") to BoardSymmetry.MIRROR_ANTI_DIAGONAL,
                ).forEach { (label, sym) ->
                    AssistChip(onClick = { onSymmetry(sym) }, enabled = ui.canTransform, label = { Text(label) })
                }
            }
        }
        FieldRow(tr("수 이동", "Shift", "平移")) {
            Row {
                listOf(
                    Icons.Filled.ArrowUpward to BoardShift.UP,
                    Icons.Filled.ArrowDownward to BoardShift.DOWN,
                    Icons.AutoMirrored.Filled.ArrowBack to BoardShift.LEFT,
                    Icons.AutoMirrored.Filled.ArrowForward to BoardShift.RIGHT,
                ).forEach { (icon, dir) ->
                    IconButton(onClick = { onShift(dir) }, enabled = ui.canTransform) {
                        Icon(icon, contentDescription = dir.name)
                    }
                }
            }
        }
        if (ui.toolbar.isNotEmpty()) {
            UserToolbar(
                items = ui.toolbar,
                language = ui.language,
                style = ui.toolbarStyle,
                enabled = ui.connection.isLive,
                onRun = onRunScript,
            )
        }
    }
}

/**
 * Black's win rate move by move. Gaps (positions never read) are not joined.
 */
@Composable
internal fun EvalGraphCard(history: List<Double?>, currentPly: Int) {
    val samples = history.count { it != null }
    if (samples < 1) return
    CalmCard {
        val latest = history.getOrNull(currentPly) ?: history.lastOrNull { it != null }
        CardTitle(tr("형세 그래프", "Evaluation graph", "形势曲线")) {
            Text(
                latest?.let { tr("흑 ${(it * 100).roundToInt()}%", "Black ${(it * 100).roundToInt()}%", "黑 ${(it * 100).roundToInt()}%") }.orEmpty(),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WinRateGraph(history, currentPly)
    }
}

@Composable
private fun WinRateGraph(history: List<Double?>, currentPly: Int) {
    val line = RapfiTheme.colors.resultBlack
    val fill = line.copy(alpha = 0.18f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val midGrid = MaterialTheme.colorScheme.outline
    val marker = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val padY = size.height * 0.06f
        val plotH = size.height - padY * 2
        val lastIdx = (history.size - 1).coerceAtLeast(1)
        val stepX = size.width / lastIdx
        fun px(i: Int) = if (history.size == 1) size.width / 2 else i * stepX
        fun py(v: Double) = padY + plotH * (1f - v.toFloat().coerceIn(0f, 1f))
        listOf(0f, 0.5f, 1f).forEach { frac ->
            val y = padY + plotH * (1f - frac)
            drawLine(if (frac == 0.5f) midGrid else grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        var segment = mutableListOf<Offset>()
        fun flush() {
            if (segment.size >= 2) {
                val area = Path().apply {
                    moveTo(segment.first().x, padY + plotH)
                    segment.forEach { lineTo(it.x, it.y) }
                    lineTo(segment.last().x, padY + plotH)
                    close()
                }
                drawPath(area, fill)
                val stroke = Path().apply {
                    moveTo(segment.first().x, segment.first().y)
                    segment.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(stroke, line, style = Stroke(width = 3f))
            }
            segment.forEach { drawCircle(line, radius = 3f, center = it) }
            segment = mutableListOf()
        }
        history.forEachIndexed { i, v -> if (v == null) flush() else segment.add(Offset(px(i), py(v))) }
        flush()
        history.getOrNull(currentPly)?.let { v ->
            val c = Offset(px(currentPly), py(v))
            drawCircle(marker, radius = 6f, center = c)
            drawCircle(line, radius = 6f, center = c, style = Stroke(width = 2f))
        }
    }
}

// ---- game -------------------------------------------------------------------

/**
 * The game tab's card: what the engine is doing, «지금 두기» when it thinks
 * until told, and the game's settings — colours, time, strength, rules. No
 * depth, no lines: watching the opponent think is the analysis tab's job.
 */
@OptIn(ExperimentalLayoutApi::class)
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
) {
    CalmCard(modifier) {
        val (word, color) = engineWord(ui.connection)
        CardTitle(tr("대국", "Game", "对局")) {
            StatusDot(if (ui.game.thinking) RapfiTheme.colors.caution else color)
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
        if (ui.showClock && ui.game.active) ClockRow(ui)
        ui.game.log.lastOrNull()?.let { Hint(it) }
    }
}

@Composable
private fun ClockRow(ui: BoardUiState) {
    val clock = ui.game.clock
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(ClockSide.COMPUTER to tr("컴퓨터", "Computer", "计算机"), ClockSide.HUMAN to tr("사람", "Human", "人")).forEach { (side, name) ->
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.labelMedium)
                    if (clock.running == side) StatusDot(RapfiTheme.colors.positive, size = 6.dp)
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

// ---- small parts ------------------------------------------------------------

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.tabular(), maxLines = 1)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun KeyValue(key: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            key,
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

internal fun ruleLabel(rule: Int): String = when (rule) {
    0 -> tr("자유 오목", "Freestyle", "无禁手")
    1 -> tr("표준 오목", "Standard", "六不赢")
    2 -> tr("렌주", "Renju", "连珠")
    3 -> tr("첫 수 교환", "Swap after 1st", "一手交换")
    4 -> tr("렌주 RIF", "Renju RIF", "连珠 RIF")
    5 -> "Soosorv-8"
    6 -> tr("스왑2", "Swap2", "两次交换")
    else -> "?"
}

private fun evalText(mate: Int?, cp: Int?): String = when {
    mate != null && mate > 0 -> "+M$mate"
    mate != null && mate < 0 -> "-M${-mate}"
    cp != null -> "$cp"
    else -> tr("없음", "None", "无")
}

private fun dbValueText(stmMate: Int?, stmRate: Double): String =
    if (stmMate != null) {
        (if (stmMate > 0) tr("승", "Win", "胜") else tr("패", "Loss", "负")) + " " + (kotlin.math.abs(stmMate).takeIf { it > 0 }?.toString() ?: "*")
    } else {
        "${(stmRate * 100).roundToInt()}%"
    }

/** Nodes and speed only need their magnitude: 1.23M reads at a glance. */
private fun compact(n: Long?): String = when {
    n == null -> tr("없음", "None", "无")
    n < 1_000 -> "$n"
    n < 1_000_000 -> "%.${if (n < 10_000) 1 else 0}fK".format(n / 1e3)
    n < 1_000_000_000 -> "%.${if (n < 10_000_000) 2 else 1}fM".format(n / 1e6)
    else -> "%.2fG".format(n / 1e9)
}

private fun timeText(ms: Long): String =
    if (ms < 60_000) "%.1fs".format(ms / 1000.0) else "${ms / 60_000}:%02d".format((ms / 1000) % 60)

private fun hexColor(hex: String): Color = Color(0xFF000000L or hex.removePrefix("#").toLong(16))

/** Height of [SummaryStrip]: the part of the sheet that is always showing. */
internal val SummaryHeight = 64.dp

/**
 * The line the sheet rests on, under the board — what the board's reader wants
 * without pulling anything up (UI overhaul P3).
 *
 * Analysis: the best move, then win rate, evaluation and depth (`18/27`:
 * depth / selective depth). Tabular, never interpolated, and at full strength
 * even when they belong to the position before — the card says so in words.
 * Nothing read yet is one line saying what ▶ does.
 * Game: the result, or whose move it is and who is playing whom.
 */
@Composable
internal fun SummaryStrip(ui: BoardUiState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(SummaryHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!ui.analysisTab) {
            val (word, color) = engineWord(ui.connection)
            StatusDot(if (ui.game.thinking) RapfiTheme.colors.caution else color)
            Column(Modifier.weight(1f)) {
                Text(
                    ui.game.result?.describe()
                        ?: (
                            if (ui.game.thinking) tr("엔진이 생각 중", "Engine thinking", "引擎思考中")
                            else tr("${ui.sideToMove.label} 차례", "${ui.sideToMove.label} to move", "轮到${ui.sideToMove.label}")
                            ),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ui.game.computerSide.label + " · " + tr("${ui.moveCount}수", "move ${ui.moveCount}", "第${ui.moveCount}手") +
                        (if (!ui.connection.isLive) " · $word" else ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            return@Row
        }
        val snap = ui.shown
        val blank = snap == null || (snap.pvs.isEmpty() && snap.stats.depth == 0)
        if (blank) {
            val (word, color) = engineWord(ui.connection)
            StatusDot(color)
            Text(
                when {
                    ui.analyzing || ui.balancing -> tr("읽는 중", "Reading", "读取中")
                    ui.connection.isLive -> tr("▶ 를 누르면 엔진이 최선수를 찾습니다", "Press ▶ and the engine looks for the best move", "按 ▶，引擎会寻找最佳着法")
                    else -> word
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            return@Row
        }
        val s = snap!!.stats
        val best = snap.best?.head ?: snap.realtimeBest
        Text(
            best?.label(ui.render.size) ?: tr("없음", "None", "无"),
            style = MaterialTheme.typography.headlineSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Stat(tr("승률", "Win rate", "胜率"), s.winRatePct?.let { "$it%" } ?: tr("없음", "None", "无"), Modifier.weight(1f))
        Stat(tr("평가", "Eval", "评价"), evalText(s.mate, s.evalCp), Modifier.weight(1f))
        Stat(
            tr("깊이", "Depth", "深度"),
            if (s.depth > 0) (if (s.selDepth > 0) "${s.depth}/${s.selDepth}" else "${s.depth}") else tr("없음", "None", "无"),
            Modifier.weight(1f),
        )
    }
}
