package dev.gomoku.rapfidroid.data.game

import android.content.Context
import android.net.Uri
import dev.gomoku.rapfidroid.core.common.IoDispatcher
import dev.gomoku.rapfidroid.domain.repository.GameFileReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved games and exported reports over the Storage Access Framework — the
 * phone's stand-in for the desktop's file chooser. No storage permission is
 * needed: the user picks the document and the app gets a one-shot grant.
 *
 * Automatic report writes go to the app's own external files directory
 * (`Android/data/…/files/reports`), which mirrors the desktop's `reports/`
 * folder next to Yixin.exe and is reachable from a file manager.
 */
@Singleton
class GameFileIo @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : GameFileReader {

    override suspend fun read(uri: String): ByteArray? = withContext(io) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))!!.use { it.readBytes() }
        }.getOrNull()
    }

    suspend fun write(uri: Uri, bytes: ByteArray) = withContext(io) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("파일을 열 수 없습니다")
    }

    /** The display name of a picked document, for report titles and the queue. */
    suspend fun displayName(uri: Uri): String = withContext(io) {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    /** Writes into the app's own `files/<folder>` and returns the path shown to the user. */
    suspend fun writeLocal(folder: String, name: String, text: String): String = withContext(io) {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, folder)
        dir.mkdirs()
        val file = File(dir, name)
        file.writeText(text)
        file.absolutePath
    }
}
