package dev.gomoku.rapfidroid.data.joseki

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.rapfidroid.core.common.IoDispatcher
import dev.gomoku.rapfidroid.core.joseki.Curriculum
import dev.gomoku.rapfidroid.core.joseki.CurriculumJson
import dev.gomoku.rapfidroid.core.joseki.JosekiJson
import dev.gomoku.rapfidroid.core.joseki.JosekiTree
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.PosKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Best score and number of finished runs of one lesson. */
data class LessonProgress(val best: Int, val runs: Int, val completedAt: Long)

/** A joseki node that reaches the board's position (symmetries included). */
data class JosekiHit(val tree: JosekiTree, val path: List<Int>, val ply: Int)

/**
 * Joseki on the device: the official curriculum shipped in assets
 * (`joseki/curriculum.json`, the file RapfiWeb serves), the user's progress
 * through it, and the user's own joseki (`files/joseki.json`, RapfiWeb's
 * export format so it moves between the two apps as is).
 */
@Singleton
class JosekiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val prefs = context.getSharedPreferences("joseki", Context.MODE_PRIVATE)
    private val file: File get() = File(context.filesDir, "joseki.json")

    private val _curriculum = MutableStateFlow<Curriculum?>(null)
    val curriculum: StateFlow<Curriculum?> = _curriculum.asStateFlow()

    private val _progress = MutableStateFlow(readProgress())
    val progress: StateFlow<Map<String, LessonProgress>> = _progress.asStateFlow()

    private val _trees = MutableStateFlow<List<JosekiTree>>(emptyList())
    val trees: StateFlow<List<JosekiTree>> = _trees.asStateFlow()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        scope.launch {
            _curriculum.value = runCatching {
                context.assets.open("joseki/curriculum.json").use { it.readBytes().decodeToString() }
            }.getOrNull()?.let { CurriculumJson.parse(it) }
            _trees.value = runCatching {
                if (file.isFile) (JosekiJson.import(file.readText()) as? JosekiJson.Import.Ok)?.trees.orEmpty() else emptyList()
            }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }
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

    fun resetProgress() {
        _progress.value = emptyMap()
        prefs.edit().remove(PROGRESS).apply()
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
    }
}
