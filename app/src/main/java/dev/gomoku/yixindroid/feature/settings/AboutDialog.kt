package dev.gomoku.yixindroid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.gomoku.yixindroid.BuildConfig

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
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
        title = { Text("YixinDroid") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )
                Section(
                    "이 앱은 무엇인가",
                    "PC용 Yixin-Board 분석 강화판을 안드로이드로 옮긴 것입니다. " +
                        "엔진은 들어 있지 않습니다 — piskvork 프로토콜로 원격 Rapfi 에 접속하는 " +
                        "클라이언트이고, PC의 engine.exe(투명 TCP 릴레이)가 하던 일을 " +
                        "앱이 직접 합니다. 그래서 서버가 꺼져 있으면 분석·데이터베이스 기능은 " +
                        "쓸 수 없습니다.",
                )
                Section(
                    "PC와 파일 주고받기",
                    "설정은 PC와 같은 settings.txt / settings_dev.txt 형식이라 그대로 " +
                        "불러오고 내보낼 수 있습니다. 툴바·핫키·언어는 Yixin 폴더의 " +
                        "function/ 과 language/ 를 읽습니다. 기보는 .psq/.sav/.pos, " +
                        "리포트는 PC와 같은 HTML 입니다.",
                )
                Section(
                    "데이터 · 라이선스",
                    "오프닝 랭킹(rank5)은 앱에 들어 있습니다. " +
                        "오프닝 익스플로러 팩과 실전 빈도 데이터는 RenjuNet 대국 DB에서 " +
                        "나온 것이라 앱에 넣지 않습니다 — 사용자가 직접 만들어 기기로 " +
                        "가져오며, 비상업·오프라인 사용만 허용됩니다. 웹에 올리거나 " +
                        "재배포하지 마세요.",
                )
                Section(
                    "데이터베이스에 쓸 때",
                    "국면 증명과 분석 결과는 서버의 공용 yixindb 에 기록됩니다. " +
                        "지우는 명령은 기본적으로 잠겨 있고, 되돌릴 수 없습니다.",
                )
            }
        },
    )
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
