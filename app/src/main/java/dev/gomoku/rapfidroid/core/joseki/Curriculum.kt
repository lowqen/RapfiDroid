package dev.gomoku.rapfidroid.core.joseki

import dev.gomoku.rapfidroid.core.i18n.AppLanguage
import dev.gomoku.rapfidroid.core.i18n.appLanguage
import dev.gomoku.rapfidroid.core.joseki.JosekiJson.obj
import dev.gomoku.rapfidroid.core.joseki.JosekiJson.str
import dev.gomoku.rapfidroid.core.model.Move
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The official joseki curriculum (RapfiWeb `core/curriculum/schema.ts`): courses
 * of lessons, each lesson a joseki tree read-only, learned by browsing or by
 * guessing the moves. The file is the same `curriculum.json` the web app
 * serves; the app ships a copy in its assets.
 *
 * Comments may come in two languages; the tree is resolved to the app's
 * language when the file is read, so every screen works on plain [JosekiNode]s.
 * Validation follows the joseki import: bad branches are dropped, ids must be
 * well-formed and unique, and a lesson the learner has no move in is dropped.
 */
enum class CourseLevel(val key: String, val ko: String, val en: String, val zh: String) {
    BEGINNER("beginner", "입문", "Beginner", "入门"),
    INTERMEDIATE("intermediate", "중급", "Intermediate", "中级"),
    ADVANCED("advanced", "고급", "Advanced", "高级"),
}

data class Lesson(
    val id: String,
    val title: String,
    val summary: String,
    /** The colour the learner plays in learn mode. */
    val blackLearner: Boolean,
    val root: JosekiNode,
)

data class Course(
    val id: String,
    val title: String,
    val level: CourseLevel,
    val summary: String,
    val lessons: List<Lesson>,
)

data class Curriculum(val version: String, val courses: List<Course>, val problems: List<String> = emptyList()) {
    fun findLesson(id: String): Pair<Course, Lesson>? {
        for (c in courses) c.lessons.firstOrNull { it.id == id }?.let { return c to it }
        return null
    }
}

object CurriculumJson {
    const val FORMAT = "rapfiweb-curriculum"
    private const val MAX_COMMENT = 20_000
    private const val MAX_TITLE = 200
    private const val MAX_SUMMARY = 4_000
    private val ID = Regex("^[a-z0-9][a-z0-9-]{0,63}$")

    /**
     * A text in the app's language. The file carries Korean and English, and may
     * carry Chinese (`zh`); a language that is missing falls back to English,
     * then to Korean — a Chinese reader is better served by English than by Korean.
     */
    fun text(e: JsonElement?, max: Int, lang: AppLanguage = appLanguage()): String {
        if (e is JsonPrimitive) return e.str(max)
        val o = e.obj() ?: return ""
        val ko = o["ko"].str(max)
        val en = o["en"].str(max)
        val zh = o["zh"].str(max)
        return when (lang) {
            AppLanguage.KOREAN -> ko
            AppLanguage.CHINESE -> zh.ifEmpty { en.ifEmpty { ko } }
            AppLanguage.ENGLISH -> en.ifEmpty { ko }
        }
    }

    /** Learner moves on the main line. */
    fun learnerMoves(root: JosekiNode, black: Boolean): Int {
        var n = root
        var ply = 0
        var count = 0
        while (n.children.isNotEmpty()) {
            if ((ply % 2 == 0) == black) count++
            n = n.children[0]
            ply++
        }
        return count
    }

    fun parse(text: String, lang: AppLanguage = appLanguage()): Curriculum? {
        val raw = runCatching { Json.parseToJsonElement(text) }.getOrNull().obj() ?: return null
        if (raw["format"].str(64) != FORMAT) return null
        if ((raw["schema"] as? JsonPrimitive)?.intOrNull != 1) return null
        val problems = ArrayList<String>()
        val seenCourse = HashSet<String>()
        val seenLesson = HashSet<String>()
        val courses = ArrayList<Course>()
        for (rc in (raw["courses"] as? JsonArray).orEmpty().take(100)) {
            val c = rc.obj() ?: continue
            val id = c["id"].str(64)
            if (!ID.matches(id) || !seenCourse.add(id)) {
                problems.add("course $id: bad or duplicate id")
                continue
            }
            val level = CourseLevel.entries.firstOrNull { it.key == c["level"].str(20) } ?: CourseLevel.BEGINNER
            val lessons = ArrayList<Lesson>()
            for (rl in (c["lessons"] as? JsonArray).orEmpty().take(200)) {
                val l = rl.obj() ?: continue
                val lid = l["id"].str(64)
                if (!ID.matches(lid) || lid in seenLesson) {
                    problems.add("lesson $lid: bad or duplicate id")
                    continue
                }
                val black = (l["quiz"].obj()?.get("side")).str(8) != "white"
                val drop = IntArray(1)
                val root = JosekiJson.cleanNode(l["root"], HashSet<Move>(), Move.DEFAULT_SIZE, drop, 0) {
                    text(it, MAX_COMMENT, lang)
                }
                if (drop[0] > 0) problems.add("lesson $lid: ${drop[0]} branches dropped")
                if (learnerMoves(root, black) == 0) {
                    problems.add("lesson $lid: no learner move on the main line")
                    continue
                }
                seenLesson.add(lid)
                lessons.add(
                    Lesson(
                        id = lid,
                        title = text(l["title"], MAX_TITLE, lang),
                        summary = text(l["summary"], MAX_SUMMARY, lang),
                        blackLearner = black,
                        root = root,
                    ),
                )
            }
            courses.add(Course(id, text(c["title"], MAX_TITLE, lang), level, text(c["summary"], MAX_SUMMARY, lang), lessons))
        }
        return Curriculum(raw["version"].str(40), courses, problems)
    }

    @Suppress("unused")
    private fun JsonObject?.orEmptyObject(): JsonObject = this ?: JsonObject(emptyMap())
}
