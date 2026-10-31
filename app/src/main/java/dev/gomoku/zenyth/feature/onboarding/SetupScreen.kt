package dev.gomoku.zenyth.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.designsystem.component.Button
import dev.gomoku.zenyth.core.designsystem.component.ZenythMark
import dev.gomoku.zenyth.core.i18n.tr

/**
 * The first-run setup. For now its first step alone and a way through — the
 * five steps come in O2 of the plan.
 */
@Composable
fun SetupScreen(onFinish: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ZenythMark(Modifier.size(96.dp))
                Text("Zenyth", style = MaterialTheme.typography.headlineMedium)
                Text(
                    tr(
                        "폰 안에서 도는 진짜 오목 · 렌주 엔진",
                        "A real gomoku & renju engine, on your phone",
                        "在手机里运行的真正五子棋 · 连珠引擎",
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Button(onClick = onFinish, modifier = Modifier.align(Alignment.BottomEnd)) {
                Text(tr("시작하기", "Get started", "开始使用"))
            }
        }
    }
}
