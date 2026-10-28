package dev.gomoku.zenyth.feature.settings

import dev.gomoku.zenyth.core.designsystem.component.rememberCalmFling
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
import dev.gomoku.zenyth.core.designsystem.component.ZenythMark
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.BuildConfig
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr

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
    onShowWhatsNew: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("확인", "OK", "确定")) } },
        // Name and version are one block, the way an About screen reads: the
        // version used to be the first line of the body, above the first
        // heading, where it looked like a section of its own.
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ZenythMark(Modifier.size(44.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Zenyth", style = MaterialTheme.typography.titleLarge)
                Text(
                    tr("오목 · 렌주 분석 · Rapfi 엔진 사용", "Gomoku & Renju analysis · powered by Rapfi", "五子棋 · 连珠分析 · 由 Rapfi 引擎驱动"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    tr("버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    tr("Rapfi 엔진을 기기 안에서 돌리는 오목 · 렌주 분석반입니다. RapfiWeb 의 안드로이드판이며, 2026년 10월까지의 이름은 RapfiDroid 였습니다.",
                        "A gomoku and renju analysis board with the Rapfi engine on the device. The Android sibling of RapfiWeb, called RapfiDroid until October 2026.",
                        "在本设备上运行 Rapfi 引擎的五子棋 · 连珠分析棋盘。RapfiWeb 的安卓版，2026 年 10 月前名为 RapfiDroid。"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Section(
                    tr("자료", "Data", "数据"),
                    tr("RenjuNet 자료는 앱에 들어 있지 않고, 기기에서 만들어 기기 밖으로 내보내지 않습니다(비상업 · 오프라인 전용).",
                        "RenjuNet data is not shipped; the phone builds it and never sends it anywhere (offline, non-commercial only).",
                        "RenjuNet 数据不随应用附带，由本机生成且不会发送到任何地方(仅限非商业、离线使用)。"),
                )
                Section(
                    tr("라이선스", "Licences", "许可证"),
                    tr("엔진 Rapfi(dhbloo) GPL-3.0 · GUI 는 Yixin-Board(© 2009-2017 Kai Sun) BSD 2-Clause 에서 이식 · 가중치 CC0 · 글꼴 Pretendard OFL-1.1.",
                        "Engine Rapfi (dhbloo) GPL-3.0 · GUI ported from Yixin-Board (© 2009-2017 Kai Sun) BSD 2-Clause · weights CC0 · font Pretendard OFL-1.1.",
                        "引擎 Rapfi(dhbloo) GPL-3.0 · 界面移植自 Yixin-Board(© 2009-2017 Kai Sun) BSD 2-Clause · 权重 CC0 · 字体 Pretendard OFL-1.1。"),
                )
                // The notes shown once after an update ([WhatsNewOnUpdate]), again on request.
                if (onShowWhatsNew != null) {
                    TextButton(onClick = onShowWhatsNew) {
                        Text(tr("이번 버전에서 바뀐 점", "What's new in this version", "本版本的新功能"))
                    }
                }
                TextButton(onClick = onShowLicenses) {
                    Text(tr("라이선스 전문 보기", "Read the licence texts", "查看许可证全文"))
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
