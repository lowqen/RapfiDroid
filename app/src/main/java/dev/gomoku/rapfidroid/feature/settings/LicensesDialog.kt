package dev.gomoku.rapfidroid.feature.settings

import dev.gomoku.rapfidroid.core.designsystem.component.ChipRow
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiFilterChip
import dev.gomoku.rapfidroid.core.designsystem.component.rememberCalmFling
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.i18n.tr

/**
 * The licence texts themselves, read out of the APK.
 *
 * This is not a courtesy screen. The engine in this app is GPL-3.0, and GPLv3 §4
 * says a copy of the licence must reach everyone who receives the program — a
 * link in a README does not reach someone holding only the APK. The BSD notice
 * for Yixin-Board has the same shape of requirement for binary distribution:
 * reproduce it "in the documentation and/or other materials provided with the
 * distribution". So both texts ship inside the APK, and this is what shows them.
 *
 * **One `Text` per paragraph, never one for the file** (fixed 2026-10-29). The
 * GPL is 35 KB; as a single `Text` in a scroll it laid out taller than the
 * largest layer the renderer will draw, and everything past that height came
 * out blank — the licence was cut off. The files are also hard-wrapped at 80
 * columns for a terminal, which in monospace on a phone wrapped a second time
 * mid-line; [licenseParagraphs] joins each paragraph back into one line so it
 * wraps once, at the dialog's width.
 */
@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val files = remember {
        runCatching { context.assets.list(LICENSE_DIR)?.sorted().orEmpty() }.getOrDefault(emptyList())
    }
    var selected by remember { mutableStateOf(files.firstOrNull()) }
    val paragraphs = remember(selected) {
        val name = selected ?: return@remember emptyList()
        runCatching {
            licenseParagraphs(context.assets.open("$LICENSE_DIR/$name").use { it.readBytes() }.decodeToString())
        }.getOrElse { e -> listOf(tr("읽지 못했습니다: ${e.message}", "Could not read it: ${e.message}", "无法读取: ${e.message}")) }
    }
    // A new file starts at its top.
    val scroll = remember(selected) { ScrollState(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("닫기", "Close", "关闭")) } },
        title = { Text(tr("라이선스 전문", "Licence texts", "许可证全文")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (files.isEmpty()) {
                    Text(
                        tr(
                            "라이선스 파일을 찾지 못했습니다 (assets/licenses).",
                            "No licence files found (assets/licenses).",
                            "未找到许可证文件 (assets/licenses)。",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    // Three names do not fit across a dialog; the row scrolls rather than clip one.
                    ChipRow {
                        files.forEach { name ->
                            RapfiFilterChip(
                                selected = name == selected,
                                onClick = { selected = name },
                                label = { Text(name.removeSuffix(".txt"), maxLines = 1) },
                            )
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(scroll, flingBehavior = rememberCalmFling()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
    )
}

/**
 * A licence file as paragraphs: blank lines split them, and the lines inside
 * one — hard-wrapped at 80 columns, some indented to centre a title — are
 * joined with single spaces. CRLF and LF files alike.
 */
internal fun licenseParagraphs(text: String): List<String> =
    text.replace("\r\n", "\n").replace('\r', '\n')
        .split(Regex("\n[ \t]*\n"))
        .map { block -> block.lines().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotEmpty() }

private const val LICENSE_DIR = "licenses"
