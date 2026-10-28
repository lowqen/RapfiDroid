package dev.gomoku.zenyth.core.joseki

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Where a curriculum link points: a lesson (in browse or learn mode) or a course. */
data class LessonTarget(val lessonId: String?, val courseId: String?, val learn: Boolean)

/**
 * Zenyth Web's curriculum links (`core/curriculum/link.ts`):
 * `https://rapfiweb.pages.dev/joseki?lesson=<id>` (`&mode=learn` to open in
 * learn mode) and `…/joseki?course=<id>`. The app writes the same addresses,
 * so a lesson shared from the phone opens on the web and the other way round,
 * and the published curriculum both read lives at the same site.
 */
object LessonLink {
    const val SITE = "https://rapfiweb.pages.dev"

    /** The published curriculum — the file the web app serves and the app syncs. */
    const val CURRICULUM_URL = "$SITE/joseki/curriculum.json"

    fun url(lessonId: String, learn: Boolean): String {
        val id = URLEncoder.encode(lessonId, "UTF-8")
        return "$SITE/joseki?lesson=$id" + if (learn) "&mode=learn" else ""
    }

    /** A curriculum link, or null when the address is not one (a board link, say). */
    fun parse(text: String): LessonTarget? {
        val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
        if (uri.path?.trimEnd('/')?.endsWith("/joseki") != true) return null
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to URLDecoder.decode(part.substring(i + 1), "UTF-8")
        }.toMap()
        val lesson = query["lesson"]?.takeIf { it.isNotBlank() }
        val course = query["course"]?.takeIf { it.isNotBlank() }
        if (lesson == null && course == null) return null
        return LessonTarget(lesson, course, learn = lesson != null && query["mode"] == "learn")
    }
}
