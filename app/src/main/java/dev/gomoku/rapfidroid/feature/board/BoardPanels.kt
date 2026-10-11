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
        CardTitle(tr("국면", "Position")) {
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
            Tile(tr("둘 차례", "To move"), ui.sideToMove.label, Modifier.weight(1f))
            Tile(
                tr("수", "Moves"),
                if (ui.futureCount > 0) "${ui.moveCount} / ${ui.moveCount + ui.futureCount}" else "${ui.moveCount}",
                Modifier.weight(1f),
            )
            Tile(
                tr("마지막", "Last"),
                ui.render.lastMove?.label(ui.render.size) ?: tr("없음", "None"),
                Modifier.weight(1f),
            )
        }
        if (ui.openingNames.isNotEmpty()) {
            KeyValue(tr("주형", "Opening")) {
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
            KeyValue(tr("평가", "Grade")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(hexColor(q.colorHex)),
                        contentAlignment = Alignment.Center,
                    ) { Text(q.symbol, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    Text(tr(q.korean, q.label), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (ui.analysisTab && ui.db.enabled && ui.dbActive) {
            KeyValue("DB") {
                val v = ui.dbValue
                Text(
                    (v?.let { dbValueText(it.stmMate, it.stmWinRate) + " (${ui.sideToMove.label})" }
                        ?: tr("값 없음", "No value")) +
                        tr(" · 기록 ${ui.db.snapshot.cells.size}", " · ${ui.db.snapshot.cells.size} records"),
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                )
            }
        }
        if (ui.db.snapshot.comment.isNotBlank()) {
            KeyValue(tr("주석", "Note")) {
                val base = MaterialTheme.typography.bodyMedium
                Text(ui.db.snapshot.comment, style = base.copy(fontSize = base.fontSize * ui.dbCommentFont.scale))
            }
        }
        ui.gameName?.let { name ->
            KeyValue(tr("기보", "Game")) { Text(name, style = MaterialTheme.typography.bodyMedium) }
        }
        if (!ui.analysisTab) {
            if (ui.game.offeringFifth) {
                Hint(tr("5수 후보 ${ui.game.fifthCount}개를 놓으세요", "Place ${ui.game.fifthCount} fifth-move candidates"))
            }
            val playing = ui.game.result == null && ui.moveCount > 0
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onResign, enabled = playing) { Text(tr("기권", "Resign")) }
                OutlinedButton(onClick = onDraw, enabled = playing && ui.connection.isLive) { Text(tr("무승부", "Draw")) }
            }
        }
    }
}

@Composable
private fun GameFileMenu(ui: BoardUiState, files: GameFileActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(tr("기보", "Game"))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun item(label: String, enabled: Boolean = true, action: () -> Unit) = Triple(label, enabled, action)
            listOf(
                item(tr("기보 열기", "Open game"), action = files.open),
                item(tr(".psq 저장", "Save .psq")) { files.save(GameFileFormat.PSQ) },
                item(tr(".sav 저장", "Save .sav")) { files.save(GameFileFormat.SAV) },
                item(tr("링크 복사", "Copy link"), enabled = ui.moveCount > 0, action = files.copyLink),
                item(tr("국면 문자열", "Position string"), action = files.positionString),
                item(tr("이미지 저장", "Save image"), action = files.saveImage),
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
                ui.game.result?.describe() ?: tr("${ui.sideToMove.label} 차례", "${ui.sideToMove.label} to move"),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                tr("${ui.moveCount}수", "${ui.moveCount} moves") +
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
        Text(tr("링크의 국면을 열었습니다", "Opened the linked position"), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRestore) { Text(tr("두던 판으로 돌아가기", "Back to my board")) }
            TextButton(onClick = onDrop) { Text(tr("버리기", "Discard")) }
        }
    }
}

// ---- analysis ---------------------------------------------------------------

/**
 * The engine card of the analysis tab. Best move first, then win rate,
 * evaluation and depth; time, nodes and speed only under «세부 수치». Nothing
 * read yet is one line, not seven «없음» tiles. When the board has moved on,
 * the last result stays — dimmed, and labelled as the position it belongs to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnalysisCard(
    ui: BoardUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPreview: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = LocalUiPrefs.current
    var detail by remember { mutableStateOf(prefs?.isOpen("engine-detail", false) ?: false) }
    val running = ui.analyzing || ui.balancing
    CalmCard(modifier) {
        val (word, color) = engineWord(ui.connection)
        CardTitle(tr("엔진", "Engine")) {
            StatusDot(color)
            Text(
                when {
                    ui.analyzing && ui.defending -> tr("방어수 탐색 중", "Searching defences")
                    ui.balancing -> tr("균형점 탐색 중", "Balancing")
                    ui.analyzing -> tr("분석 중", "Analysing")
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
                    if (ui.connection is ConnectionState.Error) tr("엔진을 띄우지 못했습니다", "The engine failed to start")
                    else tr("엔진이 꺼져 있습니다", "The engine is off"),
                    Modifier.weight(1f),
                )
                if (ui.connection is ConnectionState.Disconnected || ui.connection is ConnectionState.Error) {
                    FilledTonalButton(onClick = { chrome?.onStartEngine?.invoke() }) { Text(tr("엔진 켜기", "Start engine")) }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onStart, enabled = !running && ui.canAnalyze, modifier = Modifier.weight(1f)) {
                    Text(tr("분석", "Analyse"))
                }
                OutlinedButton(onClick = onStop, enabled = running, modifier = Modifier.weight(1f)) {
                    Text(tr("정지", "Stop"))
                }
            }
        }
        val snap = ui.shown
        if (ui.stale) {
            Hint(
                if (ui.analyzing) tr("새 국면을 읽는 중 · 직전 국면(${ui.staleAt}수)의 분석", "Reading the new position · showing move ${ui.staleAt}")
                else tr("정지됨 · 직전 국면(${ui.staleAt}수)의 분석", "Stopped · showing the analysis after move ${ui.staleAt}"),
            )
        }
        val blank = snap == null || (snap.pvs.isEmpty() && snap.stats.depth == 0)
        if (blank) {
            if (ui.connection.isLive && !running) {
                Hint(tr("«분석»을 누르면 엔진이 이 국면의 최선수를 찾습니다", "Press Analyse and the engine looks for the best move here"))
            }
            return@CalmCard
        }
        val s = snap!!.stats
        val alpha = if (ui.stale) 0.55f else 1f
        val size = ui.render.size
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            val best = snap.best?.head ?: snap.realtimeBest
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("최선수", "Best move"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    best?.label(size) ?: tr("없음", "None"),
                    style = MaterialTheme.typography.headlineSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(tr("승률", "Win rate"), s.winRatePct?.let { "$it%" } ?: tr("없음", "None"), alpha, Modifier.weight(1f))
                Stat(tr("평가", "Eval"), evalText(s.mate, s.evalCp), alpha, Modifier.weight(1f))
                Stat(
                    tr("깊이", "Depth"),
                    if (s.depth > 0) (if (s.selDepth > 0) "${s.depth}/${s.selDepth}" else "${s.depth}") else tr("없음", "None"),
                    alpha,
                    Modifier.weight(1f),
                )
            }
            if (detail) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(tr("시간", "Time"), s.timeMs?.let { timeText(it) } ?: tr("없음", "None"), alpha, Modifier.weight(1f))
                    Stat(tr("노드", "Nodes"), compact(s.nodes), alpha, Modifier.weight(1f))
                    Stat(tr("속도", "Speed"), s.speed?.let { compact(it) + "/s" } ?: tr("없음", "None"), alpha, Modifier.weight(1f))
                }
            }
            Text(
                if (detail) tr("세부 수치 접기", "Hide details") else tr("세부 수치", "Details"),
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
                    PvRow(pv, size, selected = pv.index == ui.previewPv, alpha = alpha) {
                        onPreview(if (pv.index == ui.previewPv) null else pv.index)
                    }
                }
            }
        }
    }
}

@Composable
private fun PvRow(pv: PvSnapshot, size: Int, selected: Boolean, alpha: Float, onClick: () -> Unit) {
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
            Modifier.width(52.dp).clip(MaterialTheme.shapes.small).background(TextOutline.copy(alpha = 0.85f * alpha))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(tag.label, color = palette.textFor(tag).copy(alpha = alpha), style = MaterialTheme.typography.labelLarge.tabular())
        }
        Text(
            pv.line.take(8).joinToString(" ") { it.label(size) },
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
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
    val time = if (ui.analysisSec <= 0) tr("무제한", "Unlimited") else tr("${ui.analysisSec}초", "${ui.analysisSec}s")
    DisclosureCard(
        id = "analysis-options",
        title = tr("분석 설정", "Analysis settings"),
        summary = "$time · " + tr("후보 ${ui.multiPv}수", "${ui.multiPv} candidates"),
    ) {
        FieldRow(tr("분석 시간", "Analysis time")) {
            TimePicker(
                valueMs = ui.analysisSec * 1000L,
                presetsMs = GameLevels.ANALYSIS_SECS.map { it * 1000L },
                spec = Duration.ANALYSIS_TIME,
                onChange = { onAnalysisSec((it / 1000).toInt()) },
                onNote = onNote,
            )
        }
        FieldRow(tr("후보 수", "Candidates")) {
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
                Text(tr("정지하면 최선수 두기", "Play the best move on stop"), style = MaterialTheme.typography.labelLarge)
                Hint(tr("빈 판은 H8", "H8 on an empty board"))
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
    DisclosureCard(id = "board-tools", title = tr("도구", "Tools")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = onDefend, enabled = ui.canAnalyze && !ui.busy, label = { Text(tr("모든 방어수", "All defences")) })
            AssistChip(onClick = onBalance, enabled = ui.canAnalyze && !ui.busy, label = { Text(tr("균형점 찾기", "Balance")) })
            if (ui.isRenju) {
                AssistChip(
                    onClick = onToggleForbidden,
                    label = { Text(if (ui.showForbidden) tr("금수 숨기기", "Hide forbidden") else tr("금수 표시", "Show forbidden")) },
                )
            }
        }
        FieldRow(tr("모양 대칭", "Rotate / flip")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "90°" to BoardSymmetry.ROTATE_90,
                    "180°" to BoardSymmetry.ROTATE_180,
                    "270°" to BoardSymmetry.ROTATE_270,
                    tr("좌우", "Mirror ↔") to BoardSymmetry.MIRROR_LEFT_RIGHT,
                    tr("상하", "Mirror ↕") to BoardSymmetry.MIRROR_UP_DOWN,
                    tr("대각 ＼", "Diagonal ＼") to BoardSymmetry.MIRROR_DIAGONAL,
                    tr("대각 ／", "Diagonal ／") to BoardSymmetry.MIRROR_ANTI_DIAGONAL,
                ).forEach { (label, sym) ->
                    AssistChip(onClick = { onSymmetry(sym) }, enabled = ui.canTransform, label = { Text(label) })
                }
            }
        }
        FieldRow(tr("수 이동", "Shift")) {
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
        CardTitle(tr("형세 그래프", "Evaluation graph")) {
            Text(
                latest?.let { tr("흑 ${(it * 100).roundToInt()}%", "Black ${(it * 100).roundToInt()}%") }.orEmpty(),
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
        CardTitle(tr("대국", "Game")) {
            StatusDot(if (ui.game.thinking) RapfiTheme.colors.caution else color)
            Text(
                if (ui.game.thinking) tr("엔진이 생각 중", "Engine thinking") else word,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val level = GameLevels.all.first { it.level == ui.strength }
        Hint(ui.game.computerSide.label + " · " + tr("레벨 ${level.ko}", "Level ${level.en}"))
        if (ui.game.thinking && ui.unlimitedTurn) {
            Button(onClick = onMoveNow, modifier = Modifier.fillMaxWidth()) { Text(tr("지금 두기", "Move now")) }
        } else if (ui.engineOnMove) {
            Button(onClick = onEngineMove, modifier = Modifier.fillMaxWidth()) { Text(tr("엔진 착수", "Engine move")) }
        }
        if (!ui.connection.isLive) {
            Hint(tr("엔진이 켜져야 컴퓨터가 둡니다", "The computer moves once the engine is on"))
        }
        FieldRow(tr("흑 / 백", "Black / White"), note = tr("엔진 차례에 판을 누르면 엔진이 둡니다", "Tap the board on the engine's turn")) {
            ChoiceChips(
                options = ComputerSide.entries.map { it to it.label },
                selected = ui.game.computerSide,
                onSelect = onComputerSide,
            )
        }
        FieldRow(tr("시간 제어", "Time control")) {
            TimePicker(
                valueMs = ui.moveTimeMs,
                presetsMs = GameLevels.MOVE_TIMES,
                spec = Duration.MOVE_TIME.copy(minMs = 1_000, stepMs = 1_000),
                onChange = onMoveTime,
                onNote = onNote,
            )
        }
        FieldRow(tr("레벨", "Level"), note = tr("한 수에 읽을 노드 수", "Nodes per move")) {
            ChoiceChips(
                options = GameLevels.all.map { it.level to tr(it.ko, it.en) },
                selected = ui.strength,
                onSelect = onStrength,
            )
        }
        FieldRow(tr("룰셋", "Rules"), note = tr("바꾸면 새 대국", "Changing starts a new game")) {
            ChoiceChips(
                options = (0..6).map { it to ruleLabel(it) },
                selected = ui.rule,
                onSelect = onRule,
            )
        }
        if (ui.openingNeedsOddSize) {
            Hint(tr("오프닝 규칙은 홀수 크기 판이 필요합니다", "An opening rule needs an odd board size"), color = MaterialTheme.colorScheme.error)
        }
        if (ui.showClock && ui.game.active) ClockRow(ui)
        ui.game.log.lastOrNull()?.let { Hint(it) }
    }
}

@Composable
private fun ClockRow(ui: BoardUiState) {
    val clock = ui.game.clock
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(ClockSide.COMPUTER to tr("컴퓨터", "Computer"), ClockSide.HUMAN to tr("사람", "Human")).forEach { (side, name) ->
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.labelMedium)
                    if (clock.running == side) StatusDot(RapfiTheme.colors.positive, size = 6.dp)
                }
                Text(clock.label(side), style = MaterialTheme.typography.titleMedium.tabular())
                Text(
                    tr("사용 ${clock.usedLabel(side)}", "used ${clock.usedLabel(side)}"),
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
private fun Stat(label: String, value: String, alpha: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
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
    0 -> tr("자유 오목", "Freestyle")
    1 -> tr("표준 오목", "Standard")
    2 -> tr("렌주", "Renju")
    3 -> tr("첫 수 교환", "Swap after 1st")
    4 -> tr("렌주 RIF", "Renju RIF")
    5 -> "Soosorv-8"
    6 -> tr("스왑2", "Swap2")
    else -> "?"
}

private fun evalText(mate: Int?, cp: Int?): String = when {
    mate != null && mate > 0 -> "+M$mate"
    mate != null && mate < 0 -> "-M${-mate}"
    cp != null -> "$cp"
    else -> tr("없음", "None")
}

private fun dbValueText(stmMate: Int?, stmRate: Double): String =
    if (stmMate != null) {
        (if (stmMate > 0) tr("승", "Win") else tr("패", "Loss")) + " " + (kotlin.math.abs(stmMate).takeIf { it > 0 }?.toString() ?: "*")
    } else {
        "${(stmRate * 100).roundToInt()}%"
    }

/** Nodes and speed only need their magnitude: 1.23M reads at a glance. */
private fun compact(n: Long?): String = when {
    n == null -> tr("없음", "None")
    n < 1_000 -> "$n"
    n < 1_000_000 -> "%.${if (n < 10_000) 1 else 0}fK".format(n / 1e3)
    n < 1_000_000_000 -> "%.${if (n < 10_000_000) 2 else 1}fM".format(n / 1e6)
    else -> "%.2fG".format(n / 1e9)
}

private fun timeText(ms: Long): String =
    if (ms < 60_000) "%.1fs".format(ms / 1000.0) else "${ms / 60_000}:%02d".format((ms / 1000) % 60)

private fun hexColor(hex: String): Color = Color(0xFF000000L or hex.removePrefix("#").toLong(16))
