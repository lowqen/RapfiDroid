package dev.gomoku.rapfidroid.data.explorer

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.rapfidroid.core.common.IoDispatcher
import dev.gomoku.rapfidroid.core.model.OpeningTables
import dev.gomoku.rapfidroid.core.model.PackInfo
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
    private val namesFile: File get() = File(dir, NAMES)
    private val evalsFile: File get() = File(dir, EVALS)

    private val _packs = MutableStateFlow<Packs?>(null)
    val packs: StateFlow<Packs?> = _packs.asStateFlow()

    /**
     * Bumped whenever the opening tables change, so the explorer re-syncs after
     * a late import. The tables themselves live in [OpeningTables] — a name is
     * a property of a position, not something every caller carries.
     */
    private val _tables = MutableStateFlow(0)
    val tables: StateFlow<Int> = _tables.asStateFlow()

    /** What the two tables hold right now, for the notice on screen. */
    @Volatile
    var tableNote: String = ""
        private set

    /** Both packs plus their header numbers; the explorer needs the pair, like
     *  the desktop's `rj_load` which only reports success when both map. */
    class Packs(val stats: RjStatsPack, val games: RjGamesPack, val info: PackInfo)

    /** Map the previously imported copies, if they are still there. */
    suspend fun restore() {
        loadTables()
        if (_packs.value != null) return
        withContext(io) { runCatching { mapBoth() }.getOrNull() }
            ?.let { _packs.value = it }
    }

    /**
     * Read the two opening tables into [OpeningTables].
     *
     * Both ship in assets. `opening_names.txt` is the user's own work;
     * `opening_evals.txt` comes from Renju Atlas under **CC0 1.0**, a public
     * domain dedication, so bundling and redistributing it is unencumbered —
     * unlike the RenjuNet packs above, which is why only those are imported.
     * The source is credited in the file header and the about box.
     *
     * An imported copy of either still wins over the bundled one, which is what
     * lets a user refresh a table without waiting for a new build.
     */
    suspend fun loadTables() = withContext(io) {
        val nameText = readTable(namesFile, NAMES)
        val evalText = readTable(evalsFile, EVALS)
        val names = OpeningTables.parseNames(nameText.orEmpty())
        val evals = OpeningTables.parseEvals(evalText.orEmpty())
        OpeningTables.names = names.rows
        OpeningTables.evals = evals.rows
        tableNote = buildString {
            append("이름 ${names.rows.size}개 · 유불리 ${evals.rows.size}개")
            val bad = names.bad + evals.bad
            if (bad > 0) append(" (읽지 못한 줄 ${bad}개)")
        }
        _tables.value = _tables.value + 1
    }

    /** The imported copy if there is one, else the bundled asset, else null. */
    private fun readTable(file: File, asset: String): String? =
        runCatching { if (file.isFile) file.readText() else null }.getOrNull()
            ?: runCatching {
                context.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()

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
                        val target = when (kind) {
                            RjStatsPack.MAGIC -> statsFile
                            RjGamesPack.MAGIC -> gamesFile
                            NAMES -> namesFile
                            else -> evalsFile
                        }
                        target.delete()
                        if (!staged.renameTo(target)) {
                            staged.delete()
                            error("파일을 저장하지 못했습니다: ${target.name}")
                        }
                        took.add(target.name)
                    }
                }
            }

            if (took.any { it == NAMES || it == EVALS }) loadTables()
            val loaded = runCatching { mapBoth() }.getOrNull()
            if (loaded != null) _packs.value = loaded

            val missing = listOfNotNull(
                "renju_stats.pack".takeIf { !statsFile.isFile },
                "renju_games.pack".takeIf { !gamesFile.isFile },
            )
            val onlyTables = took.isNotEmpty() && took.all { it == NAMES || it == EVALS }
            when {
                rejected.isNotEmpty() -> error(
                    "읽지 못한 파일이 있습니다 —\n" + rejected.joinToString("\n") +
                        "\nrifdb/rif_pack.py 가 만든 v2 팩이거나, " +
                        "$NAMES / $EVALS 여야 합니다.",
                )
                onlyTables -> "${took.joinToString(" + ")} 불러옴 — $tableNote"
                loaded != null ->
                    "${took.joinToString(" + ")} 불러옴 — 대국 ${loaded.info.totalGames}판 · " +
                        "국면 ${loaded.info.positions}개"
                missing.isNotEmpty() ->
                    "${took.joinToString(" + ")} 저장함 — ${missing.joinToString(", ")} 도 불러오세요"
                else -> error("팩을 열 수 없습니다(형식 또는 버전 불일치 — v2 팩이 필요합니다)")
            }
        }
    }

    /** What a staged file is, or null when it is none of the four. */
    private fun classify(file: File): String? {
        val buf = map(file)
        if (buf != null) {
            if (RjStatsPack.open(buf) != null) return RjStatsPack.MAGIC
            if (RjGamesPack.open(buf) != null) return RjGamesPack.MAGIC
        }
        return classifyTable(file)
    }

    /**
     * Names or grades — told apart by the third column, exactly as
     * `name_sheet.py --check` and the browser input tool do it: a grade is a
     * signed integer in −5..+5 and a name never looks like that. Whichever
     * reading yields rows without rejects wins, so a file that is neither is
     * still refused.
     */
    private fun classifyTable(file: File): String? {
        if (file.length() > MAX_TABLE_BYTES) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val names = OpeningTables.parseNames(text)
        val evals = OpeningTables.parseEvals(text)
        return when {
            evals.rows.isNotEmpty() && evals.bad == 0 -> EVALS
            names.rows.isNotEmpty() && names.bad == 0 -> NAMES
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

    /**
     * Put packs the device just built into service.
     *
     * Separate from [import] because nothing needs checking: these came out of
     * `PackWriter` a moment ago, in this process, with the magic and version it
     * wrote. What is shared is the part that matters — the same two filenames in
     * the same directory, so a built pack and an imported one are afterwards the
     * same thing to every reader.
     *
     * The old packs are only replaced once both new ones are in hand, so a build
     * that dies half way leaves the previous data working.
     */
    suspend fun adopt(newStats: File, newGames: File): Result<Packs> = withContext(io) {
        runCatching {
            dir.mkdirs()
            require(newStats.isFile && newGames.isFile) { "생성된 팩 파일이 없습니다" }
            statsFile.delete()
            gamesFile.delete()
            check(newStats.renameTo(statsFile) && newGames.renameTo(gamesFile)) {
                "생성한 데이터를 저장하지 못했습니다"
            }
            val loaded = mapBoth() ?: error("생성한 데이터를 읽지 못했습니다")
            _packs.value = loaded
            loaded
        }
    }

    /** Where a build should put its temporary output — same filesystem as the
     *  final home, so the swap above is a rename and not a copy. */
    val workDir: File get() = File(dir, "build").also { it.mkdirs() }

    /** Forget the imported packs and delete the private copies. The opening
     *  tables are not RenjuNet-derived and are left alone — clearing here is
     *  about the licence, not about tidying. */
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

    companion object {
        const val NAMES = "opening_names.txt"
        const val EVALS = "opening_evals.txt"

        /** Both tables are tens of kilobytes; anything far larger is not one,
         *  and reading it whole to find that out would be the bug. */
        private const val MAX_TABLE_BYTES = 4L shl 20
    }
}
