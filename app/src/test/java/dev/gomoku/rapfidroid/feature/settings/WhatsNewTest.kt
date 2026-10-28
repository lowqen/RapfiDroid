package dev.gomoku.rapfidroid.feature.settings

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.gomoku.rapfidroid.BuildConfig
import dev.gomoku.rapfidroid.core.i18n.AppLanguage
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
    fun parsesTheThreeLanguages() {
        val notes = WhatsNew.parse(
            """
            # comment
            [ko]
            - 하나
            - 둘
            stray line
            [en]
            - one
            [zh]
            - 一
            """.trimIndent(),
        )
        assertThat(notes[AppLanguage.KOREAN]).containsExactly("하나", "둘").inOrder()
        assertThat(notes[AppLanguage.ENGLISH]).containsExactly("one")
        assertThat(notes[AppLanguage.CHINESE]).containsExactly("一")
    }

    /** A release without notes would show nothing after the update — make that a decision. */
    @Test
    fun theCurrentVersionHasItsNotesInEveryLanguage() {
        val file = File("src/main/assets/whatsnew/${BuildConfig.VERSION_CODE}.txt")
        assertWithMessage("add ${file.path} for this release").that(file.exists()).isTrue()
        val notes = WhatsNew.parse(file.readText())
        val counts = AppLanguage.entries.map { notes[it].orEmpty().size }
        assertThat(counts.first()).isGreaterThan(0)
        assertWithMessage("every language lists the same items").that(counts.toSet()).hasSize(1)
    }
}
