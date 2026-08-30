package dev.gomoku.rapfidroid.feature.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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

    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                tr("실전 통계가 아직 없습니다", "No real-game statistics yet"),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                tr(
                    "오프닝 이름과 흑 5수 유불리는 지금도 나옵니다. 실전 빈도·승률·기보 목록만 RenjuNet 대국 DB가 필요하고, 파일 하나만 받으면 기기가 알아서 만듭니다.",
                    "Opening names and the 5th-move grades already work. Only the frequencies, win rates and game lists need the RenjuNet database — download one file and the phone builds the rest.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    if (running != null) tr("진행 상황 보기", "See progress")
                    else tr("대국 데이터 추가", "Add game data"),
                )
            }
        }
    }

    if (showDialog) {
        RifBuildDialog(onDismiss = { showDialog = false }, viewModel = viewModel)
    }
}
