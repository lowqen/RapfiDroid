package dev.gomoku.yixindroid.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.designsystem.theme.MonoStyle
import dev.gomoku.yixindroid.core.designsystem.theme.WinGreen
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.ConsoleLine

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
        onDraftChange = viewModel::onDraftChange,
        onConnect = viewModel::onConnect,
        onDisconnect = viewModel::onDisconnect,
        onSend = viewModel::onSend,
        onClear = viewModel::onClearConsole,
    )
}

@Composable
private fun ConnectionContent(
    ui: ConnectionUiState,
    modifier: Modifier = Modifier,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = ui.host,
                onValueChange = onHostChange,
                label = { Text("서버 (Tailscale)") },
                singleLine = true,
                enabled = ui.canConnect,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = ui.port,
                onValueChange = onPortChange,
                label = { Text("포트") },
                singleLine = true,
                enabled = ui.canConnect,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                modifier = Modifier.width(96.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(ui.state)
            Column(modifier = Modifier.weight(1f)) {}
            if (ui.canConnect) {
                Button(onClick = onConnect) { Text("연결") }
            } else {
                OutlinedButton(onClick = onDisconnect) { Text("연결 해제") }
            }
        }

        // settings.txt line 13 ("show log") hides the console entirely, like the
        // desktop's View ▸ Log toggle; line 37 scales its text.
        if (ui.showLog) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "piskvork 콘솔",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Delete, contentDescription = "콘솔 지우기")
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
                "로그 표시가 꺼져 있습니다 (설정 ▸ 표시 ▸ 로그)",
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
                label = { Text("명령 (예: ABOUT, START 15, TURN 7,7)") },
                singleLine = true,
                textStyle = MonoStyle,
                enabled = ui.state.isLive,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSend, enabled = ui.canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송")
            }
        }
    }
}

@Composable
private fun StatusChip(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.Disconnected -> "연결 안 됨" to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionState.Connecting -> "연결 중" to MaterialTheme.colorScheme.secondary
        ConnectionState.Handshaking -> "핸드셰이크" to MaterialTheme.colorScheme.secondary
        ConnectionState.Ready -> "준비됨" to MaterialTheme.colorScheme.primary
        ConnectionState.Thinking -> "분석 중" to MaterialTheme.colorScheme.primary
        is ConnectionState.Error -> "오류" to MaterialTheme.colorScheme.error
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
        isCoordinate(line.text) -> WinGreen
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

private fun isCoordinate(text: String): Boolean =
    Regex("""^\s*\d+\s*,\s*\d+\s*$""").matches(text)
