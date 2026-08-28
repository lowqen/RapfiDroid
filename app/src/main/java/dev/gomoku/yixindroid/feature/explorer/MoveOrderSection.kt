package dev.gomoku.yixindroid.feature.explorer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.designsystem.component.BoardGeometry
import dev.gomoku.yixindroid.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.yixindroid.core.designsystem.component.ReadingWidth
import dev.gomoku.yixindroid.core.designsystem.theme.BoardSkin
import dev.gomoku.yixindroid.core.designsystem.theme.YixinTheme
import dev.gomoku.yixindroid.core.designsystem.theme.tabular
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.core.model.MoveOrderFormat
import kotlin.math.roundToInt

/**
 * 수순 탐색기 — the DAG of move orders that end in exactly these stones
 * (main.c:5726-6700). Orders explode as `b! · w!`, so nothing is listed: one
 * ply of the DAG at a time, with the number of complete orders behind each
 * branch, and you drill.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveOrderSection(
    modifier: Modifier = Modifier,
    viewModel: MoveOrderViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current

    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.onNoticeShown()
        }
    }

    ui.applyPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissApplyPrompt,
            title = { Text(tr("돌 위치가 바뀝니다", "The stones will move")) },
            text = { Text(prompt) },
            confirmButton = {
                TextButton(onClick = { viewModel.onApply(confirmed = true) }) { Text(tr("놓기", "Place")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissApplyPrompt) { Text(tr("취소", "Cancel")) }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxHeight().wrapContentWidth().widthIn(max = ReadingWidth),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(ui.headline, style = MaterialTheme.typography.titleMedium)
                    if (ui.note.isNotEmpty()) {
                        Text(ui.note, style = MaterialTheme.typography.bodySmall)
                    }
                    ui.openingLabel?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (ui.computing) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }

        item { OptionChips(ui, viewModel::onOptionsChange) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        ui.breadcrumb,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    ui.orientation?.let {
                        Text("[$it]", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = viewModel::onBack, enabled = ui.canBack) {
                            Text(tr("한 수 뒤로", "Back one"))
                        }
                        OutlinedButton(onClick = viewModel::onRoot, enabled = ui.canBack) {
                            Text(tr("처음으로", "To start"))
                        }
                        Button(onClick = { viewModel.onApply() }, enabled = ui.canApply) {
                            Text(tr("보드에 놓기", "Put on the board"))
                        }
                    }
                }
            }
        }

        item {
            MiniBoard(
                ui = ui,
                onTapCell = { cell ->
                    if (ui.rows.any { it.cell == cell }) viewModel.onDrill(cell)
                },
            )
        }

        if (ui.rows.isNotEmpty()) {
            item {
                Text(
                    tr("다음 수 후보 ${ui.rows.size}가지 — 누르면 그 갈래로 들어갑니다", "${ui.rows.size} continuations — tap one to follow it"),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(ui.rows, key = { it.cell }) { row ->
                CandidateRow(
                    row = row,
                    selected = ui.selected == row.cell,
                    onSelect = { viewModel.onSelect(row.cell) },
                    onDrill = { viewModel.onDrill(row.cell) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionChips(ui: MoveOrderUiState, onChange: (MoveOrderOptions) -> Unit) {
    val o = ui.options
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = o.openingRule,
            onClick = { onChange(o.copy(openingRule = !o.openingRule)) },
            label = { Text(tr("오프닝 규칙", "Opening rule")) },
        )
        FilterChip(
            selected = o.move2Fix,
            // H9/I9 is a sub-rule of the opening rule (main.c:6140)
            enabled = o.openingRule,
            onClick = { onChange(o.copy(move2Fix = !o.move2Fix)) },
            label = { Text(tr("2수 H9/I9", "Move 2 at H9/I9")) },
        )
        FilterChip(
            selected = o.noFive,
            onClick = { onChange(o.copy(noFive = !o.noFive)) },
            label = { Text(tr("중간 오목 금지", "No five before the end")) },
        )
        FilterChip(
            selected = o.symmetry,
            onClick = { onChange(o.copy(symmetry = !o.symmetry)) },
            label = { Text(tr("환원(회전·반전)", "Fold rotations and mirrors")) },
        )
    }
}

@Composable
private fun CandidateRow(
    row: MoveOrderRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onDrill: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable { onSelect(); onDrill() }) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val skin = YixinTheme.board
            Box(
                Modifier.width(30.dp).height(30.dp)
                    .background(if (row.isBlack) skin.blackLow else skin.whiteHigh, CircleShape),
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    row.label + if (row.actual) tr("  ← 실제 대국", "  ← this game") else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    tr("수순 ${row.countText}가지 · ${row.sharePercent}%", "${row.countText} orders · ${row.sharePercent}%"),
                    style = MaterialTheme.typography.labelSmall.tabular(),
                )
            }
            Box(
                Modifier.width(60.dp).height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall),
            ) {
                Box(
                    Modifier.fillMaxWidth(row.sharePercent / 100f).height(8.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
                )
            }
        }
    }
}

/**
 * The at-a-glance board (`mo_board_draw`): the drilled prefix as solid numbered
 * stones, the remaining stones as ghosts carrying their possible move numbers,
 * and the candidates as translucent stones ringed by their share.
 */
@Composable
private fun MiniBoard(ui: MoveOrderUiState, onTapCell: (Int) -> Unit) {
    val size = ui.boardSize
    val onSurface = MaterialTheme.colorScheme.onSurface
    // The explorer's board is the same wood as the real one: two boards on the
    // same screen in different materials looked like two programs.
    val skin: BoardSkin = YixinTheme.board
    val plies = MaterialTheme.colorScheme.error
    val selectedRing = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .background(skin.wood, MaterialTheme.shapes.medium)
            .pointerInput(size, ui.rows) {
                detectTapGestures { offset ->
                    val g = BoardGeometry(kotlin.math.min(this.size.width, this.size.height).toFloat(), size)
                    val x = ((offset.x - g.origin) / g.step).roundToInt()
                    val y = ((offset.y - g.origin) / g.step).roundToInt()
                    if (x in 0 until size && y in 0 until size) onTapCell(y * size + x)
                }
            },
    ) {
        val g = BoardGeometry(kotlin.math.min(this.size.width, this.size.height), size)
        // Widths scale with the grid, like the main board: fixed pixels are what
        // made every line here a hairline on a tablet.
        val hair = (g.step / 45f).coerceAtLeast(1f)
        val grid = skin.line
        for (i in 0 until size) {
            drawLine(grid, Offset(g.origin, g.cy(i)), Offset(g.cx(size - 1), g.cy(i)), hair)
            drawLine(grid, Offset(g.cx(i), g.origin), Offset(g.cx(i), g.cy(size - 1)), hair)
        }
        if (size == 15) {
            for ((sy, sx) in listOf(3 to 3, 3 to 11, 7 to 7, 11 to 3, 11 to 11)) {
                drawCircle(grid, g.step * 0.10f, Offset(g.cx(sx), g.cy(sy)))
            }
        }

        val candidates = ui.rows.associateBy { it.cell }
        val inPrefix = ui.prefix.toSet()

        // ghosts: stones still to be placed
        for (ghost in ui.ghosts) {
            if (ghost.cell in candidates || ghost.cell in inPrefix) continue
            val cx = g.cx(ghost.cell % size)
            val cy = g.cy(ghost.cell / size)
            if (!ghost.common) {
                drawCircle(skin.line, g.step * 0.13f, Offset(cx, cy), alpha = 0.55f)
                continue
            }
            drawCircle(
                if (ghost.color == 1) skin.blackLow else skin.whiteHigh,
                g.radius, Offset(cx, cy), alpha = if (ghost.color == 1) 0.35f else 0.5f,
            )
            drawCircle(skin.blackRim, g.radius, Offset(cx, cy), alpha = 0.4f, style = Stroke(hair))
            val text = MoveOrderFormat.plies(ghost.plies)
            if (text.isNotEmpty()) label(text, cx, cy, g.step * 0.30f, plies)
        }

        // the drilled prefix: solid stones with their move numbers
        for ((i, cell) in ui.prefix.withIndex()) {
            val black = i % 2 == 0
            val cx = g.cx(cell % size)
            val cy = g.cy(cell / size)
            drawCircle(if (black) skin.blackLow else skin.whiteHigh, g.radius, Offset(cx, cy))
            drawCircle(
                if (black) skin.blackRim else skin.whiteRim,
                g.radius, Offset(cx, cy), alpha = 0.8f, style = Stroke(hair),
            )
            label("${i + 1}", cx, cy, g.step * 0.42f, if (black) skin.whiteHigh else skin.blackLow)
        }

        // candidates: translucent next stone + a ring graded by share
        for (row in ui.rows) {
            val cx = g.cx(row.cell % size)
            val cy = g.cy(row.cell / size)
            val share = row.sharePercent / 100f
            drawCircle(
                if (row.isBlack) skin.blackLow else skin.whiteHigh,
                g.radius, Offset(cx, cy), alpha = if (row.isBlack) 0.6f else 0.7f,
            )
            val ring = when {
                ui.selected == row.cell -> selectedRing
                row.actual -> skin.lastMove
                else -> skin.forbid.copy(alpha = 0.35f + 0.65f * share)
            }
            drawCircle(
                ring, g.radius * 0.9f, Offset(cx, cy),
                style = Stroke(hair * (1.5f + 3.5f * share)),
            )
            label(
                "${row.sharePercent}%", cx, cy, g.step * 0.30f,
                if (row.isBlack) skin.whiteHigh else skin.blackLow,
            )
        }
        if (ui.rows.isEmpty() && ui.prefix.isEmpty() && ui.ghosts.isEmpty()) {
            label("—", g.cx(size / 2), g.cy(size / 2), g.step, onSurface)
        }
    }
}

private fun DrawScope.label(text: String, cx: Float, cy: Float, px: Float, color: Color) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            this.color = color.toArgb()
            textSize = px
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawText(text, cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
    }
}
