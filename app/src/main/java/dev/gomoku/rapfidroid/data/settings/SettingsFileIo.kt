package dev.gomoku.rapfidroid.data.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.rapfidroid.core.common.IoDispatcher
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
        val stream = context.contentResolver.openInputStream(uri) ?: error("열 수 없습니다: $uri")
        stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    suspend fun write(uri: Uri, text: String) = withContext(io) {
        // "wt" truncates: without it a shorter file would keep the old tail.
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("쓸 수 없습니다: $uri")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}
