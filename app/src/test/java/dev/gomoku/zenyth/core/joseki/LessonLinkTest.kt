package dev.gomoku.zenyth.core.joseki

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LessonLinkTest {
    @Test
    fun `writes the addresses Zenyth Web writes`() {
        assertThat(LessonLink.url("first-three-moves", learn = false))
            .isEqualTo("https://rapfiweb.pages.dev/joseki?lesson=first-three-moves")
        assertThat(LessonLink.url("a-b", learn = true))
            .isEqualTo("https://rapfiweb.pages.dev/joseki?lesson=a-b&mode=learn")
        assertThat(LessonLink.CURRICULUM_URL).isEqualTo("https://rapfiweb.pages.dev/joseki/curriculum.json")
    }

    @Test
    fun `reads lesson and course links, in either mode`() {
        assertThat(LessonLink.parse("https://rapfiweb.pages.dev/joseki?lesson=first-three-moves"))
            .isEqualTo(LessonTarget("first-three-moves", null, learn = false))
        assertThat(LessonLink.parse("https://rapfiweb.pages.dev/joseki/?mode=learn&lesson=a-b"))
            .isEqualTo(LessonTarget("a-b", null, learn = true))
        assertThat(LessonLink.parse("https://rapfiweb.pages.dev/joseki?course=renju-opening-basics"))
            .isEqualTo(LessonTarget(null, "renju-opening-basics", learn = false))
        // A round trip through what the app itself copies.
        assertThat(LessonLink.parse(LessonLink.url("x-1", learn = true))).isEqualTo(LessonTarget("x-1", null, true))
    }

    @Test
    fun `anything else is not a lesson link`() {
        assertThat(LessonLink.parse("https://rapfiweb.pages.dev/board?p=h8i9")).isNull()
        assertThat(LessonLink.parse("https://rapfiweb.pages.dev/joseki")).isNull()
        assertThat(LessonLink.parse("https://rapfiweb.pages.dev/joseki?lesson=")).isNull()
        assertThat(LessonLink.parse("not a url at all %%")).isNull()
    }
}
