package dev.gomoku.yixindroid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.gomoku.yixindroid.core.i18n.tr

/**
 * The licence texts themselves, read out of the APK.
 *
 * This is not a courtesy screen. The engine in this app is GPL-3.0, and GPLv3 §4
 * says a copy of the licence must reach everyone who receives the program — a
 * link in a README does not reach someone holding only the APK. The BSD notice
 * for Yixin-Board has the same shape of requirement for binary distribution:
 * reproduce it "in the documentation and/or other materials provided with the
 * distribution". So both texts ship inside the APK, and this is what shows them.
 */
@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val files = remember {
        runCatching { context.assets.list(LICENSE_DIR)?.sorted().orEmpty() }.getOrDefault(emptyList())
    }
    var selected by remember { mutableStateOf(files.firstOrNull()) }
    val body = remember(selected) {
        val name = selected ?: return@remember ""
        runCatching {
            context.assets.open("$LICENSE_DIR/$name").use { it.readBytes() }.decodeToString()
        }.getOrElse { e -> tr("읽지 못했습니다: ${e.message}", "Could not read it: ${e.message}") }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("닫기", "Close")) } },
        title = { Text(tr("라이선스 전문", "Licence texts")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (files.isEmpty()) {
                    Text(
                        tr(
                            "라이선스 파일을 찾지 못했습니다 (assets/licenses).",
                            "No licence files found (assets/licenses).",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        files.forEach { name ->
                            FilterChip(
                                selected = name == selected,
                                onClick = { selected = name },
                                label = { Text(name.removeSuffix(".txt").take(18)) },
                            )
                        }
                    }
                    Text(
                        body,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
    )
}

private const val LICENSE_DIR = "licenses"
