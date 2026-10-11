package dev.gomoku.rapfidroid.feature.data

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.PackBuildState

/**
 * How to get the opening statistics onto the phone, and the build itself.
 *
 * Three numbered steps rather than a paragraph, because two of them happen
 * outside this app: the file comes from renju.net's own download page, in the
 * browser, and is then picked from storage. A user who is told "import the
 * packs" without being told where they come from has no way to find out.
 *
 * The licence line at the bottom is the reason the flow exists at all — the
 * database is offline non-commercial only, so we can neither ship the data nor
 * host it, and the phone builds it instead.
 */
@Composable
fun RifBuildDialog(
    onDismiss: () -> Unit,
    viewModel: RifBuildViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.onFilePicked(uri) }

    val running = state as? PackBuildState.Running

    AlertDialog(
        onDismissRequest = { if (running == null) onDismiss() },
        confirmButton = {
            when {
                running != null -> TextButton(onClick = viewModel::onCancel) {
                    Text(tr("중단", "Stop"))
                }
                state is PackBuildState.Done -> TextButton(onClick = {
                    viewModel.onAcknowledge()
                    onDismiss()
                }) { Text(tr("완료", "Done")) }
                else -> TextButton(onClick = onDismiss) { Text(tr("닫기", "Close")) }
            }
        },
        title = { Text(tr("대국 데이터 추가", "Add game data")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    tr(
                        "RenjuNet 기보 파일로 오프닝 빈도 · 랭킹을 이 기기에서 만듭니다.",
                        "Builds opening frequencies and rankings from the RenjuNet game file, on this phone.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (val s = state) {
                    is PackBuildState.Running -> Progress(s)

                    is PackBuildState.Done -> Outcome(
                        title = tr("데이터가 준비됐습니다", "The data is ready"),
                        body = tr(
                            "대국 ${s.games}판 · 국면 ${s.positions}개 · ${"%.1f".format(s.elapsedMs / 1000.0)}초" +
                                if (s.skipped > 0) " · 건너뜀 ${s.skipped}" else "",
                            "${s.games} games · ${s.positions} positions · ${"%.1f".format(s.elapsedMs / 1000.0)}s" +
                                if (s.skipped > 0) " · ${s.skipped} skipped" else "",
                        ),
                        tone = MaterialTheme.colorScheme.primary,
                    )

                    is PackBuildState.Failed -> Outcome(
                        title = tr("만들지 못했습니다", "The build did not finish"),
                        body = s.message,
                        tone = MaterialTheme.colorScheme.error,
                    )

                    PackBuildState.Idle -> {
                        Step(
                            number = 1,
                            title = tr("renju.net 에서 받기", "Download from renju.net"),
                            body = tr(
                                "Games 페이지의 «XML» 단추 (약 43MB)",
                                "The XML button on the Games page (about 43 MB)",
                            ),
                            action = {
                                OutlinedButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RENJU_NET_GAMES))
                                    runCatching { context.startActivity(intent) }
                                }) { Text(tr("열기", "Open")) }
                            },
                        )
                        Step(
                            number = 2,
                            title = tr("받은 파일 고르기", "Pick the file"),
                            body = tr(
                                "다운로드 폴더의 .rif 파일",
                                "The .rif file in Downloads",
                            ),
                            action = {
                                Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                                    Text(tr("파일 선택", "Choose"))
                                }
                            },
                        )
                        Step(
                            number = 3,
                            title = tr("기기에서 만들기", "The phone builds it"),
                            body = tr(
                                "몇 초 걸립니다",
                                "Takes a few seconds",
                            ),
                        )
                    }
                }

                Text(
                    tr(
                        "비상업 · 오프라인 전용 자료라 이 기기 밖으로 나가지 않습니다.",
                        "Offline, non-commercial data: it never leaves this phone.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun Step(
    number: Int,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                body,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action?.invoke()
    }
}

@Composable
private fun Progress(state: PackBuildState.Running) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(state.phase.label, style = MaterialTheme.typography.bodyMedium)
        val fraction = state.fraction
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (state.done > 0) {
                Text(
                    tr("${state.done / 1024}MB 읽음", "${state.done / 1024} MB read"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            tr(
                "이 창을 닫아도 계속됩니다.",
                "Closing this window does not stop it.",
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Outcome(title: String, body: String, tone: androidx.compose.ui.graphics.Color) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = tone)
            Text(
                body,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val RENJU_NET_GAMES = "https://www.renju.net/game/"
