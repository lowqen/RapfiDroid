package dev.gomoku.zenyth.feature.board

import androidx.compose.animation.animateColorAsState
import dev.gomoku.zenyth.core.designsystem.component.ZenythSwitch
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AssistChip
import dev.gomoku.zenyth.core.designsystem.component.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.component.CalmCard
import dev.gomoku.zenyth.core.designsystem.component.CardTitle
import dev.gomoku.zenyth.core.designsystem.component.DisclosureCard
import dev.gomoku.zenyth.core.designsystem.component.FieldRow
import dev.gomoku.zenyth.core.designsystem.component.Hint
import dev.gomoku.zenyth.core.designsystem.component.LocalShellChrome
import dev.gomoku.zenyth.core.designsystem.component.LocalUiPrefs
import dev.gomoku.zenyth.core.designsystem.component.StatusDot
import dev.gomoku.zenyth.core.designsystem.component.TagPalette
import dev.gomoku.zenyth.core.designsystem.component.TimePicker
import dev.gomoku.zenyth.core.designsystem.component.TextOutline
import dev.gomoku.zenyth.core.designsystem.component.engineWord
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.BoardShift
import dev.gomoku.zenyth.core.model.BoardSymmetry
import dev.gomoku.zenyth.core.model.CellTag
import dev.gomoku.zenyth.core.model.ConnectionState
import dev.gomoku.zenyth.core.model.Duration
import dev.gomoku.zenyth.core.model.PvSnapshot
import dev.gomoku.zenyth.core.model.TagKind
import kotlin.math.roundToInt

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
        val s = snap.stats
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
                val lines = snap.pvs.sortedBy { it.index }
                lines.forEachIndexed { i, pv ->
                    // The line being shown on the board reads strong, the rest
                    // fall back — Mornye's lyrics (M5). With none picked, the
                    // engine's first line is the one in front.
                    val strong = if (ui.previewPv != null) pv.index == ui.previewPv else i == 0
                    PvRow(pv, size, strong = strong) {
                        onPreview(if (pv.index == ui.previewPv) null else pv.index)
                    }
                }
            }
        }
    }
}
@Composable
private fun PvRow(pv: PvSnapshot, size: Int, strong: Boolean, onClick: () -> Unit) {
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
        // Only the words fade; the value chip keeps its colour (the colour is the value).
        val ink by animateColorAsState(
            if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            label = "pvInk",
        )
        Text(
            pv.line.take(8).joinToString(" ") { it.label(size) },
            style = (if (strong) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodyMedium).tabular(),
            color = ink,
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
    ui: AnalysisOptionsUi,
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
        FieldRow(tr("후보 수", "Candidates", "候选数"), note = tr("늘리면 수마다 얕게 읽음", "More lines, each read shallower", "越多每条越浅")) {
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
            ZenythSwitch(checked = ui.playBestOnStop, onCheckedChange = onPlayBest)
        }
    }
}
// The same card from the whole state — for callers that have nothing else,
// the screenshot tests among them. The screen hands it the slice.
@Composable
internal fun AnalysisOptions(
    ui: BoardUiState,
    onAnalysisSec: (Int) -> Unit,
    onMultiPv: (Int) -> Unit,
    onPlayBest: (Boolean) -> Unit,
    onNote: (String) -> Unit,
) = AnalysisOptions(ui.analysisOptionsUi(), onAnalysisSec, onMultiPv, onPlayBest, onNote)
/** The search tools that are not «analyse»: defences, balance, shape moves, the user's toolbar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolsCard(
    ui: ToolsCardUi,
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
// The same card from the whole state — for callers that have nothing else,
// the screenshot tests among them. The screen hands it the slice.
@Composable
internal fun ToolsCard(
    ui: BoardUiState,
    onDefend: () -> Unit,
    onBalance: () -> Unit,
    onSymmetry: (BoardSymmetry) -> Unit,
    onShift: (BoardShift) -> Unit,
    onToggleForbidden: () -> Unit,
    onRunScript: (String) -> Unit,
) = ToolsCard(ui.toolsCardUi(), onDefend, onBalance, onSymmetry, onShift, onToggleForbidden, onRunScript)
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
    val line = ZenythTheme.colors.resultBlack
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
