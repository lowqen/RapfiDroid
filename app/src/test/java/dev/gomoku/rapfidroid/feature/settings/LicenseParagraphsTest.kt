package dev.gomoku.rapfidroid.feature.settings

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** The licence dialog's reflow: every word of every shipped licence, in paragraphs small enough to draw. */
class LicenseParagraphsTest {

    private val shipped = File("src/main/assets/licenses").listFiles().orEmpty().sortedBy { it.name }

    @Test
    fun blankLinesSplitAndHardWrapsJoin() {
        val text = "  TITLE\r\n   Version 3\r\n\r\nFirst line\r\nsecond line.\r\n \r\nLast."
        assertThat(licenseParagraphs(text)).containsExactly("TITLE Version 3", "First line second line.", "Last.").inOrder()
    }

    @Test
    fun noWordOfAShippedLicenceIsLost() {
        assertThat(shipped).isNotEmpty()
        for (file in shipped) {
            val text = file.readText()
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val shown = licenseParagraphs(text).flatMap { it.split(' ') }.filter { it.isNotEmpty() }
            assertWithMessage(file.name).that(shown).containsExactlyElementsIn(words).inOrder()
        }
    }

    /** The GPL cut off as one 35 KB `Text`; no single paragraph may come near that. */
    @Test
    fun everyParagraphIsSmallEnoughToDraw() {
        for (file in shipped) {
            val paragraphs = licenseParagraphs(file.readText())
            assertThat(paragraphs.size).isGreaterThan(1)
            assertThat(paragraphs.maxOf { it.length }).isLessThan(4000)
        }
    }
}
