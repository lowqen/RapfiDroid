package dev.gomoku.rapfidroid.feature.onboarding

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.rapfidroid.core.i18n.tr

/**
 * The first launch, answered in one screen.
 *
 * Four things a new user cannot discover by looking: that the engine is inside
 * the app (so nothing needs setting up), that a game starts by tapping the
 * board, where analysis lives, and that the real-game statistics are an
 * optional extra they have to fetch themselves. Everything else the app can
 * explain in place; these four it cannot.
 *
 * Shown once. It can be brought back from Settings ▸ ⓘ, which is where someone
 * goes looking for "what was that thing it said at the start".
 */
@Composable
fun WelcomeDialog(
    onDismiss: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            TextButton(onClick = {
                viewModel.onSeen()
                onDismiss()
            }) { Text(tr("시작하기", "Get started")) }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tr("RapfiDroid 에 오신 것을 환영합니다", "Welcome to RapfiDroid"))
                Text(
                    tr(
                        "오목·렌주 엔진이 이 앱 안에서 돌아갑니다",
                        "A gomoku and renju engine, running inside the app",
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        tr(
                            "인터넷도, 서버도, 계정도 필요 없습니다. 엔진(Rapfi)이 기기 안에 들어 있어 비행기 안에서도 그대로 동작합니다.",
                            "No internet, no server, no account. The engine (Rapfi) ships inside the app and works just as well in flight mode.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                Item(
                    icon = Icons.Filled.PlayArrow,
                    title = tr("두기", "Playing"),
                    body = tr(
                        "«연결» 탭에서 한 번 연결하면 엔진이 깨어납니다. 그다음 «보드» 에서 판을 탭해 돌을 놓으세요.",
                        "Connect once on the Connection tab to wake the engine, then tap the board to place a stone.",
                    ),
                )
                Item(
                    icon = Icons.Filled.Search,
                    title = tr("분석", "Analysis"),
                    body = tr(
                        "보드 화면의 «분석» 을 켜면 최선 수순과 승률이 실시간으로 따라옵니다. 금수도 함께 표시됩니다.",
                        "Turn on Analysis on the board and the best lines and win rate follow along live, forbidden points included.",
                    ),
                )
                Item(
                    icon = Icons.Filled.Settings,
                    title = tr("설정", "Settings"),
                    body = tr(
                        "PC판 Yixin-Board 의 설정을 그대로 옮겨 왔습니다. 기기 성능에 맞춘 스레드·해시는 «기기 내 엔진» 카드에 있습니다.",
                        "The desktop Yixin-Board settings came across intact. Threads and hash for this phone are in the On-device engine card.",
                    ),
                )
                Item(
                    icon = Icons.Filled.Info,
                    title = tr("실전 통계 (선택)", "Real-game statistics (optional)"),
                    body = tr(
                        "오프닝 이름과 흑 5수 유불리는 지금 바로 나옵니다. 실전 빈도·승률만 RenjuNet 자료가 필요하고, 설정 ▸ «대국 데이터 추가» 에서 안내합니다.",
                        "Opening names and the 5th-move grades work right now. Only the real-game frequencies need the RenjuNet database — Settings ▸ Add game data walks you through it.",
                    ),
                )
            }
        },
    )
}

@Composable
private fun Item(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
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
    }
}

/** Host for [WelcomeDialog]: shows it once, the first time the app opens. */
@Composable
fun WelcomeGate(viewModel: WelcomeViewModel = hiltViewModel()) {
    val seen by viewModel.seen.collectAsStateWithLifecycle()
    // null = not read yet. Deciding before the answer arrives would flash the
    // dialog at everyone who has already dismissed it.
    if (seen == false) {
        WelcomeDialog(onDismiss = { }, viewModel = viewModel)
    }
}
