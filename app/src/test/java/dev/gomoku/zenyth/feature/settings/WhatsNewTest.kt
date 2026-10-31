package dev.gomoku.zenyth.feature.settings

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.gomoku.zenyth.BuildConfig
import dev.gomoku.zenyth.core.i18n.AppLanguage
import java.io.File
import org.junit.Test

/** The notes shown once after an update. */
class WhatsNewTest {

    @Test
    fun onlyAfterAnUpdateAndOnlyOnce() {
        // A fresh install has nothing to compare with.
        assertThat(WhatsNew.shouldShow(seen = 0, current = 22, freshInstall = true)).isFalse()
        // An update from before this feature existed.
        assertThat(WhatsNew.shouldShow(seen = 0, current = 22, freshInstall = false)).isTrue()
        // An update from a version that already recorded its notes.
        assertThat(WhatsNew.shouldShow(seen = 21, current = 22, freshInstall = false)).isTrue()
        assertThat(WhatsNew.shouldShow(seen = 21, current = 22, freshInstall = true)).isTrue()
        // Seen: never again.
        assertThat(WhatsNew.shouldShow(seen = 22, current = 22, freshInstall = false)).isFalse()
    }

    @Test
    fun parsesIntroGroupsAndItems() {
        val notes = WhatsNew.parse(
            """
            # comment
            [ko]
            > 환영합니다.
            - 제목 없는 하나
            ## 엔진
            - 빨라짐
            - 시원해짐
            stray line
            [en]
            - one
            """.trimIndent(),
        )
        val ko = notes.getValue(AppLanguage.KOREAN)
        assertThat(ko.intro).isEqualTo("환영합니다.")
        assertThat(ko.groups.map { it.title }).containsExactly(null, "엔진").inOrder()
        assertThat(ko.groups[1].items).containsExactly("빨라짐", "시원해짐").inOrder()
        assertThat(notes.getValue(AppLanguage.ENGLISH).groups.single().items).containsExactly("one")
        assertThat(notes[AppLanguage.CHINESE]).isNull()
    }

    /** A release without notes would show nothing after the update — make that a decision. */
    @Test
    fun theCurrentVersionHasTheSameNotesInEveryLanguage() {
        val file = File("src/main/assets/whatsnew/${BuildConfig.VERSION_CODE}.txt")
        assertWithMessage("add ${file.path} for this release").that(file.exists()).isTrue()
        val notes = WhatsNew.parse(file.readText())
        val shapes = AppLanguage.entries.map { lang ->
            val n = notes[lang]
            assertWithMessage("$lang has notes").that(n).isNotNull()
            assertWithMessage("$lang has a welcome").that(n!!.intro).isNotNull()
            n.groups.map { it.items.size }
        }
        assertThat(shapes.first()).isNotEmpty()
        assertWithMessage("every language has the same groups and items").that(shapes.toSet()).hasSize(1)
    }

    /**
     * Every notes file, a draft for the next release included: the three
     * languages say the same things, so a line added in one is not forgotten
     * in the others until the release that ships it.
     */
    @Test
    fun everyNotesFileHasTheSameShapeInEveryLanguage() {
        val files = File("src/main/assets/whatsnew").listFiles { f -> f.name.endsWith(".txt") }.orEmpty()
        assertThat(files).isNotEmpty()
        files.forEach { file ->
            val notes = WhatsNew.parse(file.readText())
            val shapes = AppLanguage.entries.map { lang ->
                val n = notes[lang]
                assertWithMessage("${file.name}: $lang has notes").that(n).isNotNull()
                n!!.groups.map { it.items.size } to (n.intro != null)
            }
            assertWithMessage("${file.name}: every language has the same groups, items and welcome").that(shapes.toSet()).hasSize(1)
        }
    }
}
