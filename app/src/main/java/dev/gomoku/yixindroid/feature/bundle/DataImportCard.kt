package dev.gomoku.yixindroid.feature.bundle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.designsystem.theme.YixinTheme
import dev.gomoku.yixindroid.core.designsystem.theme.expandFadeIn
import dev.gomoku.yixindroid.core.designsystem.theme.shrinkFadeOut
import dev.gomoku.yixindroid.core.designsystem.theme.tabular
import dev.gomoku.yixindroid.core.i18n.tr

/**
 * The one place data gets into the app: pick the transfer folder, everything in
 * it is read.
 *
 * It appears wherever missing data is felt — the settings screen, and the empty
 * states of the explorer and the rankings — but it is one composable backed by
 * one importer, so all three offer exactly the same thing and none of them can
 * drift into a different set of steps.
 */
@Composable
fun DataImportCard(
    modifier: Modifier = Modifier,
    viewModel: BundleViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    // rememberSaveable: the settings screen scrolls this card, so a plain
    // `remember` would drop an expanded report the moment it left the viewport.
    var showDetail by rememberSaveable { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> viewModel.onPickFolder(uri) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DriveFolderUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    tr("  자료 반입", "  Data"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${ui.ready}/3",
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    // "done" is a state, not an emphasis: it gets the app's
                    // positive colour rather than the button colour.
                    color = if (ui.ready == 3) YixinTheme.colors.positive
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ReadyLine(
                on = ui.packsLoaded,
                title = tr("오프닝 익스플로러", "Opening explorer"),
                detail = if (ui.packsLoaded) {
                    tr("대국 %,d판 · 국면 %,d개", "%,d games · %,d positions")
                        .format(ui.packGames, ui.packPositions)
                } else {
                    tr("renju_stats.pack · renju_games.pack", "renju_stats.pack · renju_games.pack")
                },
            )
            ReadyLine(
                on = ui.freqLoaded,
                title = tr("랭킹 실전 데이터", "Ranking game data"),
                detail = if (ui.freqLoaded) {
                    tr("%,d판", "%,d games").format(ui.freqGames)
                } else {
                    "freq_data.json"
                },
            )
            ReadyLine(
                on = ui.appearance != null,
                title = tr("설정 · 툴바 · 언어", "Settings · toolbar · language"),
                detail = ui.appearance
                    ?: "settings.txt · settings_dev.txt · function/ · language/",
            )

            if (ui.running) LinearProgressIndicator(Modifier.fillMaxWidth())

            Button(
                onClick = { pick.launch(null) },
                enabled = !ui.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (ui.ready == 0) tr("폴더에서 한 번에 반입", "Import a folder")
                    else tr("폴더에서 다시 반입", "Import again"),
                )
            }
            Text(
                tr(
                    "PC 의 «앱_반입» 폴더(또는 Yixin.exe 가 있는 폴더)를 휴대폰에 복사한 뒤 그 폴더를 고르세요. " +
                        "안에 있는 팩·실전 데이터·설정·툴바를 한 번에 읽습니다.",
                    "Copy the PC's transfer folder (or the one holding Yixin.exe) to the phone and pick it. " +
                        "Packs, game data, settings and toolbar are read in one go.",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ui.report?.let { report ->
                TextButton(onClick = { showDetail = !showDetail }) {
                    Text(
                        if (showDetail) tr("자세히 접기", "Hide details")
                        else tr("${report.folder} 반입 결과 보기", "What ${report.folder} contained"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                AnimatedVisibility(showDetail, enter = expandFadeIn, exit = shrinkFadeOut) {
                    Column {
                        report.found.forEach { Detail("✓ $it", ok = true) }
                        report.missing.forEach {
                            Detail(tr("— $it 없음", "— $it not found"), ok = false)
                        }
                        report.failed.forEach { Detail("✕ $it", ok = false) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyLine(on: Boolean, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (on) YixinTheme.colors.positive
            else MaterialTheme.colorScheme.outline,
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Detail(text: String, ok: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 4.dp),
    )
}
