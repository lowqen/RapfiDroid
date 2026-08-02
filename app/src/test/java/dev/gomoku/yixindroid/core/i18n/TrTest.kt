package dev.gomoku.yixindroid.core.i18n

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.DesktopSettings
import dev.gomoku.yixindroid.core.model.MoveQuality
import dev.gomoku.yixindroid.core.model.Opening26
import org.junit.After
import org.junit.Test
import java.util.Locale

/**
 * The app ships two languages and picks between them by locale. These check the
 * picking, and that nothing froze the choice at class-initialisation time — the
 * settings table and the enums used to be built once, which would have left them
 * speaking whichever language the process started in.
 */
class TrTest {

    private val original: Locale = Locale.getDefault()

    @After
    fun restore() {
        Locale.setDefault(original)
    }

    @Test
    fun koreanIsWhatAKoreanDeviceGets() {
        Locale.setDefault(Locale.KOREAN)
        assertThat(tr("보드", "Board")).isEqualTo("보드")
        assertThat(isKorean()).isTrue()
    }

    /** Every other language falls back to English — there is no third table. */
    @Test
    fun everythingElseGetsEnglish() {
        listOf(Locale.ENGLISH, Locale.JAPANESE, Locale.FRANCE, Locale.CHINESE).forEach {
            Locale.setDefault(it)
            assertThat(tr("보드", "Board")).isEqualTo("Board")
            assertThat(isKorean()).isFalse()
        }
    }

    @Test
    fun theSettingsTableFollowsTheLanguage() {
        Locale.setDefault(Locale.KOREAN)
        assertThat(DesktopSettings.spec("boardSize")?.label).isEqualTo("보드 크기")

        Locale.setDefault(Locale.ENGLISH)
        assertThat(DesktopSettings.spec("boardSize")?.label).isEqualTo("Board Size")

        // …and back, because the cache has to notice a change in both directions.
        Locale.setDefault(Locale.KOREAN)
        assertThat(DesktopSettings.spec("boardSize")?.label).isEqualTo("보드 크기")
    }

    /** A rebuilt table is still the desktop's file layout, not just its words. */
    @Test
    fun rebuildingTheTableKeepsTheFileLayout() {
        Locale.setDefault(Locale.ENGLISH)
        assertThat(DesktopSettings.MAIN).hasSize(47)
        assertThat(DesktopSettings.DEV).hasSize(20)
        assertThat(DesktopSettings.MAIN.first().line).isEqualTo(1)
    }

    @Test
    fun gradeAndOpeningNamesFollowTheLanguageToo() {
        Locale.setDefault(Locale.KOREAN)
        assertThat(MoveQuality.BLUNDER.display).isEqualTo("대실수")
        assertThat(Opening26.name(0)).isEqualTo("한성")

        Locale.setDefault(Locale.ENGLISH)
        assertThat(MoveQuality.BLUNDER.display).isEqualTo("Blunder")
        // Openings have no English names anywhere in this world; romaji is what
        // the RIF tables and every non-Korean source use.
        assertThat(Opening26.name(0)).isEqualTo("Kansei")
    }
}
