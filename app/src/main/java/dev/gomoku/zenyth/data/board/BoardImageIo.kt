package dev.gomoku.zenyth.data.board

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.zenyth.core.common.IoDispatcher
import dev.gomoku.zenyth.core.i18n.tr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the exported board PNG to a user-picked location (Storage Access
 * Framework), like [dev.gomoku.zenyth.data.settings.SettingsFileIo] does for
 * the settings files. SAF keeps this permission-free on every supported API
 * level, which MediaStore would not be below API 29.
 */
@Singleton
class BoardImageIo @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun write(uri: Uri, bytes: ByteArray) = withContext(io) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: error(tr("쓸 수 없습니다: $uri", "Cannot write: $uri", "无法写入: $uri"))
        stream.use { it.write(bytes) }
    }
}
