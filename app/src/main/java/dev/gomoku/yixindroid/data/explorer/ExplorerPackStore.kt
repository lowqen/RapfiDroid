package dev.gomoku.yixindroid.data.explorer

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.PackInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the user-imported opening-explorer packs.
 *
 * ⚠ **RenjuNet-derived — never bundled and never exported.** The user builds
 * `renju_stats.pack` / `renju_games.pack` from their own `.rif` download with
 * `rifdb/` and picks them here through the Storage Access Framework; the
 * licence is offline non-commercial only (rifdb/README.md).
 *
 * The picked documents are copied once into app-private storage and then
 * memory-mapped, which is what the desktop does with `g_mapped_file_new`: 33 MB
 * of pack must not sit on the heap, and a lookup touches only a few pages.
 * Copying (rather than mapping the content URI) also means a revoked SAF grant
 * cannot take the data away mid-session.
 */
@Singleton
class ExplorerPackStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dir: File get() = File(context.filesDir, "packs")
    private val statsFile: File get() = File(dir, "renju_stats.pack")
    private val gamesFile: File get() = File(dir, "renju_games.pack")

    private val _packs = MutableStateFlow<Packs?>(null)
    val packs: StateFlow<Packs?> = _packs.asStateFlow()

    /** Both packs plus their header numbers; the explorer needs the pair, like
     *  the desktop's `rj_load` which only reports success when both map. */
    class Packs(val stats: RjStatsPack, val games: RjGamesPack, val info: PackInfo)

    /** Map the previously imported copies, if they are still there. */
    suspend fun restore() {
        if (_packs.value != null) return
        withContext(io) { runCatching { mapBoth() }.getOrNull() }
            ?.let { _packs.value = it }
    }

    /**
     * Import freshly picked documents. Either order, and either one or both:
     * the pack kind is read from its magic, so the user can pick both files in
     * one go without being asked which is which.
     *
     * Returns a message describing what loaded, or a failure explaining what is
     * still missing.
     */
    suspend fun import(uris: List<Uri>): Result<String> = withContext(io) {
        runCatching {
            require(uris.isNotEmpty()) { "선택된 파일이 없습니다" }
            dir.mkdirs()
            var wroteStats = false
            var wroteGames = false
            for (uri in uris) {
                when (magicOf(uri)) {
                    RjStatsPack.MAGIC -> {
                        copy(uri, statsFile); wroteStats = true
                    }
                    RjGamesPack.MAGIC -> {
                        copy(uri, gamesFile); wroteGames = true
                    }
                    else -> error(
                        "오프닝 익스플로러 팩이 아닙니다 — rifdb/rif_pack.py 가 만든 " +
                            "renju_stats.pack / renju_games.pack 을 고르세요",
                    )
                }
            }
            val loaded = runCatching { mapBoth() }.getOrNull()
                ?: error(
                    when {
                        !statsFile.exists() -> "renju_stats.pack 이 아직 없습니다"
                        !gamesFile.exists() -> "renju_games.pack 이 아직 없습니다"
                        else -> "팩을 읽을 수 없습니다(형식 또는 버전 불일치 — v2 팩이 필요합니다)"
                    },
                )
            _packs.value = loaded
            val picked = listOfNotNull(
                "renju_stats.pack".takeIf { wroteStats },
                "renju_games.pack".takeIf { wroteGames },
            ).joinToString(" + ")
            "$picked 불러옴 — 대국 ${loaded.info.totalGames}판 · 국면 ${loaded.info.positions}개"
        }
    }

    /** Forget the imported packs and delete the private copies. */
    suspend fun clear() = withContext(io) {
        _packs.value = null
        statsFile.delete()
        gamesFile.delete()
        Unit
    }

    private fun mapBoth(): Packs? {
        val s = RjStatsPack.open(map(statsFile) ?: return null) ?: return null
        val g = RjGamesPack.open(map(gamesFile) ?: return null) ?: return null
        return Packs(
            s, g,
            PackInfo(
                totalGames = s.totalGames,
                maxPlies = s.maxPlies,
                minGames = s.minGames,
                date = s.date,
                positions = s.positions,
                gameRecords = g.gameCount,
            ),
        )
    }

    private fun map(file: File) =
        if (!file.isFile || file.length() < 64) null
        else RandomAccessFile(file, "r").use { raf ->
            // The mapping outlives the channel, which is the point: no fd is
            // held open for the life of the app.
            raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
        }

    private fun magicOf(uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(4)
            if (input.read(head) != 4) null else String(head, Charsets.US_ASCII)
        }

    private fun copy(uri: Uri, target: File) {
        val tmp = File(target.parentFile, target.name + ".part")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it, DEFAULT_BUFFER_SIZE) }
        } ?: error("파일을 열 수 없습니다: $uri")
        // An existing copy may still be mapped; unlinking it first keeps the
        // live mapping valid (same inode) and makes the rename unambiguous.
        target.delete()
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("팩을 저장하지 못했습니다: ${target.name}")
        }
    }
}
