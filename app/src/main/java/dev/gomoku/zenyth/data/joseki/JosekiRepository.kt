package dev.gomoku.zenyth.data.joseki

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.zenyth.core.common.IoDispatcher
import dev.gomoku.zenyth.core.joseki.Curriculum
import dev.gomoku.zenyth.core.joseki.CurriculumJson
import dev.gomoku.zenyth.core.joseki.JosekiJson
import dev.gomoku.zenyth.core.joseki.JosekiTree
import dev.gomoku.zenyth.core.joseki.LessonLink
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.PosKey
import dev.gomoku.zenyth.data.prefs.UiPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Best score and number of finished runs of one lesson. */
data class LessonProgress(val best: Int, val runs: Int, val completedAt: Long)

/**
 * Where the curriculum on screen came from. [ONLINE] means it was checked
 * against Zenyth Web just now (fetched, or unchanged since the last fetch).
 */
enum class CurriculumSource { BUNDLED, CACHED, ONLINE }

data class CurriculumSync(
    val source: CurriculumSource = CurriculumSource.BUNDLED,
    val checking: Boolean = false,
    /** The last check could not reach Zenyth Web. */
    val offline: Boolean = false,
)

/** A joseki node that reaches the board's position (symmetries included). */
data class JosekiHit(val tree: JosekiTree, val path: List<Int>, val ply: Int)

/**
 * Joseki on the device: the official curriculum, the user's progress through
 * it, and the user's own joseki (`files/joseki.json`, Zenyth Web's export format
 * so it moves between the two apps as is).
 *
 * **The curriculum is Zenyth Web's.** The maintainer publishes one file,
 * `https://rapfiweb.pages.dev/joseki/curriculum.json` (Zenyth Web
 * `docs/공식-정석-관리.md`), and this app reads that same file: on start it shows
 * the last copy it fetched (`files/curriculum.json`) or, before the first
 * fetch, the one in the APK's assets, then asks the site for a newer one. The
 * request carries the last ETag, so an unchanged curriculum costs one empty
 * reply. A file that does not parse, or has no lessons, is never kept.
 *
 * That request is the app's only automatic use of the network, and Settings
 * turns it off ([UiPrefs.curriculumAutoUpdate]); the refresh button and a
 * lesson link still fetch, because the user asked.
 */
@Singleton
class JosekiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val ui: UiPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val prefs = context.getSharedPreferences("joseki", Context.MODE_PRIVATE)
    private val file: File get() = File(context.filesDir, "joseki.json")

    private val _curriculum = MutableStateFlow<Curriculum?>(null)
    val curriculum: StateFlow<Curriculum?> = _curriculum.asStateFlow()

    private val _sync = MutableStateFlow(CurriculumSync())
    val sync: StateFlow<CurriculumSync> = _sync.asStateFlow()

    private val cacheFile: File get() = File(context.filesDir, "curriculum.json")
    private var lastCheck = 0L

    private val _progress = MutableStateFlow(readProgress())
    val progress: StateFlow<Map<String, LessonProgress>> = _progress.asStateFlow()

    private val _trees = MutableStateFlow<List<JosekiTree>>(emptyList())
    val trees: StateFlow<List<JosekiTree>> = _trees.asStateFlow()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        scope.launch {
            val cached = runCatching { if (cacheFile.isFile) usable(cacheFile.readText()) else null }.getOrNull()
            if (cached != null) {
                _curriculum.value = cached
                _sync.value = CurriculumSync(source = CurriculumSource.CACHED)
            } else {
                _curriculum.value = runCatching {
                    context.assets.open("joseki/curriculum.json").use { it.readBytes().decodeToString() }
                }.getOrNull()?.let { CurriculumJson.parse(it) }
            }
            _trees.value = runCatching {
                if (file.isFile) (JosekiJson.import(file.readText()) as? JosekiJson.Import.Ok)?.trees.orEmpty() else emptyList()
            }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }
            if (ui.curriculumAutoUpdate.value) refreshCurriculum()
        }
    }

    /** A curriculum worth showing: it parses and has at least one lesson. */
    private fun usable(text: String): Curriculum? =
        CurriculumJson.parse(text)?.takeIf { c -> c.courses.any { it.lessons.isNotEmpty() } }

    /**
     * Asks Zenyth Web for the published curriculum. At most once every few
     * minutes unless [force] (the refresh button); a failure keeps whatever is
     * on screen and only marks the state offline.
     */
    fun refreshCurriculum(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (_sync.value.checking || (!force && now - lastCheck < RECHECK_MS)) return
        lastCheck = now
        _sync.value = _sync.value.copy(checking = true)
        scope.launch {
            val got = runCatching { fetchCurriculum() }.getOrNull()
            if (got == null) {
                _sync.value = _sync.value.copy(checking = false, offline = true)
            } else {
                if (got !== _curriculum.value) _curriculum.value = got
                _sync.value = CurriculumSync(source = CurriculumSource.ONLINE)
            }
        }
    }

    /** The published file, or the one on screen when it has not changed; null when it cannot be had. */
    private fun fetchCurriculum(): Curriculum? {
        val conn = URL(LessonLink.CURRICULUM_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = NET_TIMEOUT_MS
            conn.readTimeout = NET_TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            val etag = prefs.getString(ETAG, null)
            // Only trust the ETag while the file it names is still here.
            if (etag != null && cacheFile.isFile) conn.setRequestProperty("If-None-Match", etag)
            when (conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> return _curriculum.value
                HttpURLConnection.HTTP_OK -> Unit
                else -> return null
            }
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_CURRICULUM_BYTES) return null
                }
            }
            val text = out.toByteArray().decodeToString()
            val parsed = usable(text) ?: return null
            val tmp = File(context.filesDir, "curriculum.json.part")
            tmp.writeText(text)
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.writeText(text)
                tmp.delete()
            }
            prefs.edit().putString(ETAG, conn.getHeaderField("ETag")).apply()
            return parsed
        } finally {
            conn.disconnect()
        }
    }

    // ---- progress --------------------------------------------------------------

    fun record(lessonId: String, score: Int, now: Long = System.currentTimeMillis()) {
        val prev = _progress.value[lessonId]
        val next = _progress.value + (lessonId to LessonProgress(maxOf(prev?.best ?: 0, score), (prev?.runs ?: 0) + 1, now))
        _progress.value = next
        val o = JSONObject()
        next.forEach { (k, p) ->
            o.put(k, JSONObject().put("best", p.best).put("runs", p.runs).put("completedAt", p.completedAt))
        }
        prefs.edit().putString(PROGRESS, o.toString()).apply()
    }

    private fun readProgress(): Map<String, LessonProgress> = runCatching {
        val o = JSONObject(prefs.getString(PROGRESS, null) ?: return emptyMap())
        o.keys().asSequence().associateWith { k ->
            val p = o.getJSONObject(k)
            LessonProgress(p.optInt("best").coerceIn(0, 100), p.optInt("runs"), p.optLong("completedAt"))
        }
    }.getOrDefault(emptyMap())

    // ---- my joseki ---------------------------------------------------------------

    fun save(tree: JosekiTree) {
        val stamped = tree.copy(updatedAt = System.currentTimeMillis())
        _trees.value = (listOf(stamped) + _trees.value.filter { it.id != tree.id })
        persist()
    }

    fun delete(id: String) {
        _trees.value = _trees.value.filter { it.id != id }
        persist()
    }

    /** Adds imported trees; one with an id already present gets a new id. */
    fun addAll(trees: List<JosekiTree>) {
        val ids = _trees.value.map { it.id }.toHashSet()
        val fresh = trees.map { if (it.id in ids) it.copy(id = java.util.UUID.randomUUID().toString()) else it }
        _trees.value = fresh + _trees.value
        persist()
    }

    fun exportAll(): String = JosekiJson.export(_trees.value)

    /** Joseki that pass through this position, in any of its eight orientations. */
    fun search(moves: List<Move>, size: Int): List<JosekiHit> {
        if (moves.isEmpty()) return emptyList()
        val key = PosKey.keyOf(moves, size)
        return _trees.value.mapNotNull { t ->
            JosekiJson.indexKeys(t).firstOrNull { it.key == key }?.let { JosekiHit(t, it.path, it.ply) }
        }
    }

    private var persistJob: kotlinx.coroutines.Job? = null

    /** Written half a second after the last edit, so typing a comment is one write. */
    private fun persist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            kotlinx.coroutines.delay(500)
            val text = JosekiJson.export(_trees.value)
            withContext(io) {
                val tmp = File(context.filesDir, "joseki.json.part")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    file.writeText(text)
                    tmp.delete()
                }
            }
        }
    }

    private companion object {
        const val PROGRESS = "curriculum.progress"
        const val ETAG = "curriculum.etag"
        const val RECHECK_MS = 10 * 60_000L
        const val NET_TIMEOUT_MS = 10_000
        const val MAX_CURRICULUM_BYTES = 4 * 1024 * 1024
    }
}
