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
            val took = ArrayList<String>()
            val rejected = ArrayList<String>()
            for (uri in uris) {
                // Copy first, then decide what it is by *opening* it. Peeking at
                // the first bytes of a content stream is not reliable — a short
                // read (some providers, cloud-backed documents) looks exactly
                // like a wrong file, which is how a perfectly good pack ends up
                // rejected.
                val staged = File(dir, "import.part")
                val copied = runCatching { copy(uri, staged) }
                val kind = if (copied.isSuccess) classify(staged) else null
                when {
                    copied.isFailure -> {
                        staged.delete()
                        rejected.add(
                            "${name(uri)}: 읽을 수 없음 (${copied.exceptionOrNull()?.message})",
                        )
                    }
                    kind == null -> {
                        rejected.add("${name(uri)}: ${describe(staged, copied.getOrDefault(0L))}")
                        staged.delete()
                    }
                    else -> {
                        val target = if (kind == RjStatsPack.MAGIC) statsFile else gamesFile
                        target.delete()
                        if (!staged.renameTo(target)) {
                            staged.delete()
                            error("팩을 저장하지 못했습니다: ${target.name}")
                        }
                        took.add(target.name)
                    }
                }
            }

            val loaded = runCatching { mapBoth() }.getOrNull()
            if (loaded != null) _packs.value = loaded

            val missing = listOfNotNull(
                "renju_stats.pack".takeIf { !statsFile.isFile },
                "renju_games.pack".takeIf { !gamesFile.isFile },
            )
            when {
                rejected.isNotEmpty() -> error(
                    "팩으로 읽지 못한 파일이 있습니다 —\n" + rejected.joinToString("\n") +
                        "\nrifdb/rif_pack.py 가 만든 v2 팩이어야 합니다.",
                )
                loaded != null ->
                    "${took.joinToString(" + ")} 불러옴 — 대국 ${loaded.info.totalGames}판 · " +
                        "국면 ${loaded.info.positions}개"
                missing.isNotEmpty() ->
                    "${took.joinToString(" + ")} 저장함 — ${missing.joinToString(", ")} 도 불러오세요"
                else -> error("팩을 열 수 없습니다(형식 또는 버전 불일치 — v2 팩이 필요합니다)")
            }
        }
    }

    /** Which pack a staged file is, or null when it is neither. */
    private fun classify(file: File): String? {
        val buf = map(file) ?: return null
        return when {
            RjStatsPack.open(buf) != null -> RjStatsPack.MAGIC
            RjGamesPack.open(buf) != null -> RjGamesPack.MAGIC
            else -> null
        }
    }

    /** Enough detail for the user to tell us what went wrong. */
    private fun describe(file: File, copied: Long): String {
        val head = runCatching {
            file.inputStream().use { input ->
                val b = ByteArray(8)
                val n = input.read(b).coerceAtLeast(0)
                String(b, 0, n, Charsets.US_ASCII).filter { it.isLetterOrDigit() }
            }
        }.getOrDefault("?")
        return "${copied / 1024}KB, 머리글 \"$head\" — 팩이 아니거나 v1 팩입니다"
    }

    private fun name(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/') ?: "$uri"

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

    /** Streams the document into [target]; returns how many bytes landed. */
    private fun copy(uri: Uri, target: File): Long {
        target.delete()
        val input = context.contentResolver.openInputStream(uri)
            ?: error("파일을 열 수 없습니다")
        return input.use { source ->
            target.outputStream().use { source.copyTo(it, DEFAULT_BUFFER_SIZE) }
        }
    }
}
