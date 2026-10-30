package dev.gomoku.zenyth.core.common

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

/*
 * Plain text on the clipboard, as the deprecated ClipboardManager put and read
 * it, for its successor [Clipboard] (Phase 7-4 of
 * docs/리팩토링-성능-위생-계획.md): what is copied and what a paste reads are
 * unchanged.
 */

/** What `ClipboardManager.setText` put on the clipboard: [text], labelled "plain text". */
internal fun plainTextClip(text: String): ClipEntry = ClipEntry(ClipData.newPlainText("plain text", text))

/** What `ClipboardManager.getText` read: the first item's text, or null. */
internal suspend fun Clipboard.plainText(): String? =
    getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
