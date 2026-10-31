package dev.gomoku.zenyth.feature.board

import dev.gomoku.zenyth.core.designsystem.component.CappedFontScale
import dev.gomoku.zenyth.core.designsystem.component.PillBadge
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.component.StatusDot
import dev.gomoku.zenyth.core.designsystem.component.engineWord
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr

/** Height of [SummaryStrip]: the part of the sheet that is always showing. */
internal val SummaryHeight = 64.dp
/**
 * The line the sheet rests on, under the board — what the board's reader wants
 * without pulling anything up (UI overhaul P3).
 *
 * Analysis: the best move, large and alone. Win rate, evaluation and depth sat
 * under it in a 12sp pill nobody could read; they are the engine card's
 * headline tiles now (user request, 2026-11-01), and the board writes the win
 * rate on the best move's point. At full strength even when it belongs to the
 * position before — the card says so in words. Nothing read yet is one line
 * saying what ▶ does.
 * Game: the result, or whose move it is and who is playing whom.
 */
@Composable
internal fun SummaryStrip(ui: SummaryUi, modifier: Modifier = Modifier) {
    // The strip is the sheet's 64dp peek: its text grows to 1.3 and no further.
    CappedFontScale(1.3f) { SummaryStripBody(ui, modifier) }
}
// The same card from the whole state — for callers that have nothing else,
// the screenshot tests among them. The screen hands it the slice.
@Composable
internal fun SummaryStrip(ui: BoardUiState, modifier: Modifier = Modifier) = SummaryStrip(ui.summaryUi(), modifier)
@Composable
private fun SummaryStripBody(ui: SummaryUi, modifier: Modifier) {
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
            // Centred like the analysis line: the state large, the match in a pill.
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusDot(if (ui.game.thinking) ZenythTheme.colors.caution else color)
                    Text(
                        ui.game.result?.describe()
                            ?: (
                                if (ui.game.thinking) tr("엔진이 생각 중", "Engine thinking", "引擎思考中")
                                else tr("${ui.sideToMove.label} 차례", "${ui.sideToMove.label} to move", "轮到${ui.sideToMove.label}")
                                ),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PillBadge(
                    ui.game.computerSide.label + " · " + tr("${ui.moveCount}수", "move ${ui.moveCount}", "第${ui.moveCount}手") +
                        (if (!ui.connection.isLive) " · $word" else ""),
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
        val best = snap.best?.head ?: snap.realtimeBest
        // Mornye's now-playing title, large and centred (M5): on the left it
        // left the line's right half empty (device review, 2026-10-29).
        Text(
            best?.label(ui.render.size) ?: tr("없음", "None", "无"),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold).tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

    }
}
