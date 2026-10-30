package dev.gomoku.zenyth.feature.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr
import kotlin.math.roundToInt

@Composable
internal fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.tabular(), maxLines = 1)
    }
}
@Composable
internal fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
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
internal fun KeyValue(key: String, content: @Composable () -> Unit) {
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
internal fun evalText(mate: Int?, cp: Int?): String = when {
    mate != null && mate > 0 -> "+M$mate"
    mate != null && mate < 0 -> "-M${-mate}"
    cp != null -> "$cp"
    else -> tr("없음", "None", "无")
}
internal fun dbValueText(stmMate: Int?, stmRate: Double): String =
    if (stmMate != null) {
        (if (stmMate > 0) tr("승", "Win", "胜") else tr("패", "Loss", "负")) + " " + (kotlin.math.abs(stmMate).takeIf { it > 0 }?.toString() ?: "*")
    } else {
        "${(stmRate * 100).roundToInt()}%"
    }
/** Nodes and speed only need their magnitude: 1.23M reads at a glance. */
internal fun compact(n: Long?): String = when {
    n == null -> tr("없음", "None", "无")
    n < 1_000 -> "$n"
    n < 1_000_000 -> "%.${if (n < 10_000) 1 else 0}fK".format(n / 1e3)
    n < 1_000_000_000 -> "%.${if (n < 10_000_000) 2 else 1}fM".format(n / 1e6)
    else -> "%.2fG".format(n / 1e9)
}
internal fun timeText(ms: Long): String =
    if (ms < 60_000) "%.1fs".format(ms / 1000.0) else "${ms / 60_000}:%02d".format((ms / 1000) % 60)
internal fun hexColor(hex: String): Color = Color(0xFF000000L or hex.removePrefix("#").toLong(16))
