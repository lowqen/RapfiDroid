package dev.gomoku.rapfidroid.feature.settings

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
import dev.gomoku.rapfidroid.BuildConfig
import dev.gomoku.rapfidroid.core.designsystem.theme.tabular
import dev.gomoku.rapfidroid.core.i18n.tr

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
fun AboutDialog(
    onDismiss: () -> Unit,
    onShowLicenses: () -> Unit,
    onReplayWelcome: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("확인", "OK")) } },
        // Name and version are one block, the way an About screen reads: the
        // version used to be the first line of the body, above the first
        // heading, where it looked like a section of its own.
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("RapfiDroid", style = MaterialTheme.typography.titleLarge)
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
                    tr("PC용 Yixin-Board 분석 강화판을 안드로이드로 옮긴 것입니다. ", "An Android port of the analysis build of Yixin-Board for the PC. ") +
                        tr("엔진(Rapfi)이 앱 안에 들어 있어 기기에서 그대로 돌아갑니다 — ", "The engine (Rapfi) is inside the app and runs on the device itself: ") +
                        tr("인터넷도 서버도 필요 없습니다. 직접 운영하는 Rapfi 서버가 있다면 ", "no server and no internet. If you run a Rapfi server of your own, ") +
                        tr("설정 ▸ 고급에서 «서버 엔진 사용»을 켜서 그쪽으로 붙일 수도 있습니다.", "settings ▸ advanced can point the app at it instead."),
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
                    tr("국면 증명과 분석 결과는 데이터베이스에 기록됩니다 — 기기 내 엔진이면 ", "Proofs and search results are written to a database: the on-device engine keeps ") +
                        tr("이 기기의 rapfi.db, 서버 엔진이면 서버의 공용 yixindb 입니다. ", "its own rapfi.db here, a server engine writes to the shared yixindb there. ") +
                        tr("지우는 명령은 기본적으로 잠겨 있고, 되돌릴 수 없습니다.", "The commands that delete are locked by default, and nothing can be undone."),
                )
                Section(
                    tr("저작권 · 라이선스", "Copyright and licences"),
                    tr("엔진은 Rapfi(dhbloo) — GPL-3.0 입니다. 이 앱을 남에게 전달하려면 ", "The engine is Rapfi (dhbloo), under GPL-3.0. Passing this app on means ") +
                        tr("엔진의 대응 소스를 함께 제공해야 합니다(저장소 README 참고). ", "you must also make the engine's corresponding source available (see the repository README). ") +
                        tr("GUI 는 Yixin-Board(© 2009-2017 Kai Sun, accreator / dhbloo 수정판)를 ", "The GUI is ported from Yixin-Board (© 2009-2017 Kai Sun, via accreator and dhbloo) ") +
                        tr("이식한 것으로 BSD 2-Clause 이고, 신경망 가중치는 CC0 입니다. ", "under the BSD 2-Clause licence; the network weights are CC0. ") +
                        tr("Yixin 엔진 자체는 이 앱에 들어 있지 않습니다.", "The Yixin engine itself is not part of this app."),
                )
                TextButton(onClick = onReplayWelcome) {
                    Text(tr("처음 안내 다시 보기", "Show the welcome guide again"))
                }
                TextButton(onClick = onShowLicenses) {
                    Text(tr("라이선스 전문 보기", "Read the licence texts"))
                }
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
