package dev.gomoku.rapfidroid.feature.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.PackBuildState

/**
 * The "there is no data here yet" card, shown where the absence is felt — the
 * explorer's statistics and the rankings' real-game tables.
 *
 * It replaces a card that asked the user to import files built on a PC. That
 * was the right shape when this was one person's tool and the wrong one for
 * anybody else: the files come from a Python pipeline nobody else has run. The
 * phone builds them now, so the card leads to the build.
 */
@Composable
fun DataSetupCard(
    modifier: Modifier = Modifier,
    viewModel: RifBuildViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val running = state as? PackBuildState.Running

    dev.gomoku.rapfidroid.core.designsystem.component.CalmCard(modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                tr("RenjuNet 기보 파일을 넣으면 실전 빈도 · 승률 · 기보가 나옵니다", "Add the RenjuNet game file for real-game frequencies, scores and games", "加入 RenjuNet 棋谱文件后，可看到实战频率、胜率和棋谱"),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (running != null) {
                Text(running.phase.label, style = MaterialTheme.typography.labelMedium)
                val fraction = running.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Button(onClick = { showDialog = true }) {
                Text(
                    if (running != null) tr("진행 상황 보기", "See progress", "查看进度")
                    else tr("대국 데이터 추가", "Add game data", "添加对局数据"),
                )
            }
        }
    }

    if (showDialog) {
        RifBuildDialog(onDismiss = { showDialog = false }, viewModel = viewModel)
    }
}
