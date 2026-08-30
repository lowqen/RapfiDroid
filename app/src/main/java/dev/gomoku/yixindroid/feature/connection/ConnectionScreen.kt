package dev.gomoku.yixindroid.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.designsystem.component.WideLayoutMin
import dev.gomoku.yixindroid.core.designsystem.theme.MonoStyle
import dev.gomoku.yixindroid.core.designsystem.theme.YixinTheme
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine
import dev.gomoku.yixindroid.core.model.LinkHealth

@Composable
fun ConnectionScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectionViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectionContent(
        ui = ui,
        modifier = modifier,
        onHostChange = viewModel::onHostChange,
        onPortChange = viewModel::onPortChange,
        onLocalModeChange = viewModel::onLocalModeChange,
        onDraftChange = viewModel::onDraftChange,
        onConnect = viewModel::onConnect,
        onDisconnect = viewModel::onDisconnect,
        onSend = viewModel::onSend,
        onClear = viewModel::onClearConsole,
        onRetryNow = viewModel::onRetryNow,
    )
}

@Composable
private fun ConnectionContent(
    ui: ConnectionUiState,
    modifier: Modifier = Modifier,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onLocalModeChange: (Boolean) -> Unit,
    onDraftChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onRetryNow: () -> Unit,
) {
    // Where the engine is and whether it is answering. Every row here is a
    // fixed height.
    val endpointPane: @Composable ColumnScope.() -> Unit = {
        // Which engine. The two are not the same tool: on-device is always
        // there but holds a small hash and no database, the server is the deep
        // one. Choosing is only allowed while nothing is connected — swapping
        // engines mid-session would leave the board talking to the wrong one.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = !ui.localMode,
                onClick = { onLocalModeChange(false) },
                enabled = ui.canConnect,
                label = { Text(tr("서버", "Server")) },
            )
            FilterChip(
                selected = ui.localMode,
                onClick = { onLocalModeChange(true) },
                enabled = ui.canConnect,
                label = { Text(tr("기기 내 엔진", "On-device")) },
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = ui.host,
                onValueChange = onHostChange,
                label = { Text(tr("서버 (Tailscale)", "Server (Tailscale)")) },
                singleLine = true,
                enabled = ui.canConnect && !ui.localMode,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = ui.port,
                onValueChange = onPortChange,
                label = { Text(tr("포트", "Port")) },
                singleLine = true,
                enabled = ui.canConnect && !ui.localMode,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(ui.state)
            Spacer(Modifier.weight(1f))
            if (ui.canConnect) {
                Button(onClick = onConnect) { Text(tr("연결", "Connect")) }
            } else {
                OutlinedButton(onClick = onDisconnect) { Text(tr("연결 해제", "Disconnect")) }
            }
        }

        LinkHealthRow(ui.health, onRetryNow)
    }

    // The console and the line you type into it, in that order and never apart:
    // a command is written while its answer is being read. This is the pane that
    // wants whatever height is left over.
    val consolePane: @Composable ColumnScope.() -> Unit = {
        // settings.txt line 13 ("show log") hides the console entirely, like the
        // desktop's View ▸ Log toggle; line 37 scales its text.
        if (ui.showLog) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("piskvork 콘솔", "piskvork console"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Delete, contentDescription = tr("콘솔 지우기", "Clear the console"))
                }
            }

            Console(
                lines = ui.console,
                scale = ui.logScale,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            Text(
                tr("로그 표시가 꺼져 있습니다 (설정 ▸ 표시 ▸ 로그)", "The log is turned off (Settings ▸ Display ▸ Log)"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = ui.commandDraft,
                onValueChange = onDraftChange,
                label = { Text(tr("명령 (예: ABOUT, START 15, TURN 7,7)", "Command (e.g. ABOUT, START 15, TURN 7,7)")) },
                singleLine = true,
                textStyle = MonoStyle,
                enabled = ui.state.isLive,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSend, enabled = ui.canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr("전송", "Send"))
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= WideLayoutMin) {
            // Two columns from a landscape phone up. Stacked, the endpoint rows,
            // the status row, the health row, the console header and the command
            // line come to about 360dp — more than a landscape phone has — and a
            // Column hands its weighted child only what is left, so the console
            // was measured at zero and the rows it should have made room for
            // spilled off the bottom instead. Side by side, the console gets a
            // full screen height and the controls stop competing for it.
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = endpointPane,
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = consolePane,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                endpointPane()
                consolePane()
            }
        }
    }
}

/**
 * What happened to the link and what is being done about it. Hidden while the
 * link is healthy — a status line that is always there is a status line nobody
 * reads — and it says the attempt number and the countdown, because "재연결
 * 중…" with no numbers is indistinguishable from a hang.
 */
@Composable
private fun LinkHealthRow(health: LinkHealth, onRetryNow: () -> Unit) {
    if (health.idle) return
    Surface(
        color = if (health.reconnecting) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        health.reconnecting && health.retryInSeconds > 0 ->
                            tr("연결이 끊겼습니다 — ${health.retryInSeconds}초 후 재시도 ", "Connection lost — retrying in ${health.retryInSeconds}s") +
                                tr("(${health.attempt}회째)", "(attempt ${health.attempt})")
                        health.reconnecting -> tr("재연결하는 중… (${health.attempt}회째)", "Reconnecting… (attempt ${health.attempt})")
                        else -> tr("연결이 끊겼습니다", "Connection lost")
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                health.lastError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (health.recovered > 0) {
                    Text(
                        tr("이 세션에서 ${health.recovered}회 복구됨", "Recovered ${health.recovered} times this session"),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (health.reconnecting) {
                TextButton(onClick = onRetryNow) { Text(tr("지금 재시도", "Retry now")) }
            }
        }
    }
}

@Composable
private fun StatusChip(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.Disconnected -> tr("연결 안 됨", "Not connected") to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionState.Connecting -> tr("연결 중", "Connecting") to MaterialTheme.colorScheme.secondary
        ConnectionState.Handshaking -> tr("핸드셰이크", "Handshake") to MaterialTheme.colorScheme.secondary
        ConnectionState.Ready -> tr("준비됨", "Ready") to MaterialTheme.colorScheme.primary
        ConnectionState.Thinking -> tr("분석 중", "Searching") to MaterialTheme.colorScheme.primary
        is ConnectionState.Error -> tr("오류", "Error") to MaterialTheme.colorScheme.error
    }
    Surface(
        color = color.copy(alpha = 0.18f),
        contentColor = color,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Console(lines: List<ConsoleLine>, scale: Float, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            items(lines) { line -> ConsoleRow(line, scale) }
        }
    }
}

@Composable
private fun ConsoleRow(line: ConsoleLine, scale: Float) {
    val color = when {
        line.outbound -> MaterialTheme.colorScheme.secondary
        line.text.startsWith("ERROR", ignoreCase = true) -> MaterialTheme.colorScheme.error
        isCoordinate(line.text) -> YixinTheme.colors.positive
        else -> MaterialTheme.colorScheme.onSurface
    }
    val prefix = if (line.outbound) "» " else "  "
    Text(
        text = prefix + line.text,
        style = MonoStyle.copy(
            fontSize = MonoStyle.fontSize * scale,
            lineHeight = MonoStyle.lineHeight * scale,
        ),
        color = color,
    )
}

/**
 * Hoisted out of [isCoordinate]: `Regex(…)` compiles when it is constructed, and
 * this is asked about every console line on every recomposition — a search fills
 * the console faster than that pattern is worth rebuilding.
 */
private val coordinateLine = Regex("""^\s*\d+\s*,\s*\d+\s*$""")

private fun isCoordinate(text: String): Boolean = coordinateLine.matches(text)
