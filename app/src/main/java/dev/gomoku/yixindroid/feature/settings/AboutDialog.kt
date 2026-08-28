package dev.gomoku.yixindroid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gomoku.yixindroid.BuildConfig
import dev.gomoku.yixindroid.core.designsystem.theme.tabular
import dev.gomoku.yixindroid.core.i18n.tr

/**
 * The desktop's Help ▸ About, plus the two things a phone user cannot see for
 * themselves: what this app actually is (a client, not an engine) and where the
 * data it uses may and may not go.
 *
 * The licence lines are not decoration. RenjuNet's database is non-commercial
 * and offline-only, which is why the explorer packs and the frequency data are
 * imported by the user and never shipped — someone reading this screen is
 * exactly the person who might otherwise share them.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("확인", "OK")) } },
        // Name and version are one block, the way an About screen reads: the
        // version used to be the first line of the body, above the first
        // heading, where it looked like a section of its own.
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("YixinDroid", style = MaterialTheme.typography.titleLarge)
                Text(
                    tr("버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Section(
                    tr("이 앱은 무엇인가", "What this app is"),
                    tr("PC용 Yixin-Board 분석 강화판을 안드로이드로 옮긴 것입니다. ", "An Android port of the analysis build of Yixin-Board for the PC.") +
                        tr("엔진은 들어 있지 않습니다 — piskvork 프로토콜로 원격 Rapfi 에 접속하는 ", "There is no engine inside it: this is a client that speaks piskvork to a") +
                        tr("클라이언트이고, PC의 engine.exe(투명 TCP 릴레이)가 하던 일을 ", "remote Rapfi, doing itself what engine.exe (a transparent TCP relay) does") +
                        tr("앱이 직접 합니다. 그래서 서버가 꺼져 있으면 분석·데이터베이스 기능은 ", "on the PC. With the server down, analysis and the database are") +
                        tr("쓸 수 없습니다.", "simply unavailable."),
                )
                Section(
                    tr("PC와 파일 주고받기", "Moving files to and from the PC"),
                    tr("설정은 PC와 같은 settings.txt / settings_dev.txt 형식이라 그대로 ", "Settings are the PC's own settings.txt / settings_dev.txt, so they load") +
                        tr("불러오고 내보낼 수 있습니다. 툴바·핫키·언어는 Yixin 폴더의 ", "and export as they are. Toolbar, hotkeys and language come from") +
                        tr("function/ 과 language/ 를 읽습니다. 기보는 .psq/.sav/.pos, ", "function/ and language/ in the Yixin folder. Game files are .psq/.sav/.pos,") +
                        tr("리포트는 PC와 같은 HTML 입니다.", "and a report is the same HTML the PC writes."),
                )
                Section(
                    tr("데이터 · 라이선스", "Data and licence"),
                    tr("오프닝 익스플로러 팩과 실전 빈도 데이터는 RenjuNet 대국 DB에서 ", "The explorer packs and the frequency data come from the RenjuNet game") +
                        tr("나온 것이라 앱에 넣지 않습니다 — 사용자가 직접 만들어 기기로 ", "database and are not shipped: you build them yourself and bring them to") +
                        tr("가져오며, 비상업·오프라인 사용만 허용됩니다. 웹에 올리거나 ", "the device. Non-commercial, offline use only — do not upload them or") +
                        tr("재배포하지 마세요.", "pass them on."),
                )
                Section(
                    tr("오프닝 이름 · 흑 5수 유불리", "Opening names and 5th-move grades"),
                    tr("1~3수 이름은 계산이고, 4수 이름은 사용자가 채운 표입니다. ", "Names for moves 1-3 are computed; the 4th-move names are a table the user filled in.") +
                        tr("흑 5수 유불리 11등급은 Renju Atlas(렌주 아틀라스)의 자료를 ", "The eleven 5th-move grades come from Renju Atlas, used under") +
                        tr("CC0 1.0 로 가져와 씁니다 — 공개 자료라 앱에 함께 넣었습니다. ", "CC0 1.0: a public domain dedication, so they ship with the app.") +
                        tr("표시가 없는 국면은 «값이 없는 것»이지 «호각»이 아닙니다.", "A position with no mark has no value recorded — that is not the same as balanced."),
                )
                Section(
                    tr("데이터베이스에 쓸 때", "Writing to the database"),
                    tr("국면 증명과 분석 결과는 서버의 공용 yixindb 에 기록됩니다. ", "Proofs and search results are written to the shared yixindb on the server.") +
                        tr("지우는 명령은 기본적으로 잠겨 있고, 되돌릴 수 없습니다.", "The commands that delete are locked by default, and nothing can be undone."),
                )
            }
        },
    )
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
