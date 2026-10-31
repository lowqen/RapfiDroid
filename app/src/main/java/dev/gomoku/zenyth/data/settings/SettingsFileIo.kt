package dev.gomoku.zenyth.data.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.zenyth.core.common.IoDispatcher
import dev.gomoku.zenyth.core.common.ReadLimits
import dev.gomoku.zenyth.core.common.readAtMost
import dev.gomoku.zenyth.core.i18n.tr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads/writes a user-picked document (Storage Access Framework) so the phone and
 * the PC can exchange `settings.txt` / `settings_dev.txt` as-is. No permanent
 * grant is taken: this is a one-shot copy in either direction.
 */
@Singleton
class SettingsFileIo @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun read(uri: Uri): String = withContext(io) {
        val stream = context.contentResolver.openInputStream(uri) ?: error(tr("열 수 없습니다: $uri", "Cannot open: $uri", "无法打开: $uri"))
        val bytes = stream.use { it.readAtMost(ReadLimits.TEXT) }
            ?: error(tr("설정 파일이라기엔 너무 큽니다", "Too large to be a settings file", "文件太大，不是设置文件"))
        bytes.toString(Charsets.UTF_8)
    }

    suspend fun write(uri: Uri, text: String) = withContext(io) {
        // "wt" truncates: without it a shorter file would keep the old tail.
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: error(tr("쓸 수 없습니다: $uri", "Cannot write: $uri", "无法写入: $uri"))
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}
