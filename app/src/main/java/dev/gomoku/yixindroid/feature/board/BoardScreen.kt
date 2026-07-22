package dev.gomoku.yixindroid.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.designsystem.component.GomokuBoard
import dev.gomoku.yixindroid.core.designsystem.theme.WinBlue
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.PvSnapshot

private val StoneBlack = Color(0xFF1C1A17)
private val StoneWhite = Color(0xFFEDEAE3)

@Composable
fun BoardScreen(
    modifier: Modifier = Modifier,
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EvalHeader(ui)
        EvalBar(blackWinRate = ui.blackWinRate, mate = ui.blackMate)
        GomokuBoard(render = ui.render, onTap = viewModel::onTap)
        Controls(
            analyzing = ui.analyzing,
            canAnalyze = ui.canAnalyze,
            multiPv = ui.multiPv,
            onUndo = viewModel::onUndo,
            onReset = viewModel::onReset,
            onToggle = viewModel::onToggleAnalyze,
            onMultiPv = viewModel::onMultiPvChange,
        )
        PvList(
            pvs = ui.snapshot?.pvs.orEmpty(),
            size = ui.render.size,
            previewPv = ui.previewPv,
            onPreview = viewModel::onPreviewPv,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun EvalHeader(ui: BoardUiState) {
    val eval = when {
        ui.blackMate != null -> if (ui.blackMate!! > 0) "흑 M${ui.blackMate}" else "백 M${-ui.blackMate!!}"
        ui.blackWinRate != null -> "흑 ${(ui.blackWinRate!! * 100).toInt()}%"
        else -> "—"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$eval  ·  depth ${ui.depth}", style = MaterialTheme.typography.titleLarge)
        Text("${ui.moveCount}수", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EvalBar(blackWinRate: Double?, mate: Int?, modifier: Modifier = Modifier) {
    val frac = (blackWinRate ?: 0.5).coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            Box(Modifier.weight(frac.coerceAtLeast(0.001f)).fillMaxHeight().background(StoneBlack))
            Box(Modifier.weight((1f - frac).coerceAtLeast(0.001f)).fillMaxHeight().background(StoneWhite))
        }
    }
}

@Composable
private fun Controls(
    analyzing: Boolean,
    canAnalyze: Boolean,
    multiPv: Int,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onToggle: () -> Unit,
    onMultiPv: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedIconButton(onClick = onUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "무르기")
        }
        OutlinedIconButton(onClick = onReset) {
            Icon(Icons.Filled.Refresh, contentDescription = "초기화")
        }
        Button(onClick = onToggle, enabled = canAnalyze) {
            Icon(if (analyzing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
            Text(if (analyzing) "  정지" else "  분석")
        }
        Box(Modifier.weight(1f))
        Stepper(label = "PV", value = multiPv, onChange = onMultiPv)
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onChange(value - 1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$value", style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { onChange(value + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun PvList(
    pvs: List<PvSnapshot>,
    size: Int,
    previewPv: Int?,
    onPreview: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pvs.isEmpty()) return
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(pvs, key = { it.index }) { pv ->
            val selected = pv.index == previewPv
            Surface(
                onClick = { onPreview(if (selected) null else pv.index) },
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pvLabel(pv), color = WinBlue, style = MaterialTheme.typography.bodyMedium)
                        Text("d${pv.depth}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        pv.line.take(10).joinToString(" ") { it.label(size) },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun pvLabel(pv: PvSnapshot): String = when {
    pv.mate != null && pv.mate > 0 -> "#${pv.index + 1}  +M${pv.mate}"
    pv.mate != null -> "#${pv.index + 1}  -M${-pv.mate}"
    pv.winRate != null -> "#${pv.index + 1}  ${(pv.winRate * 100).toInt()}%"
    else -> "#${pv.index + 1}"
}
