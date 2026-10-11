package dev.gomoku.rapfidroid.core.i18n

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.DesktopSettings
import dev.gomoku.rapfidroid.core.model.MoveQuality
import dev.gomoku.rapfidroid.core.model.Opening26
import org.junit.After
import org.junit.Test
import java.util.Locale

/**
 * The app ships three languages and picks between them by locale. These check the
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
        assertThat(tr("보드", "Board", "棋盘")).isEqualTo("보드")
        assertThat(isKorean()).isTrue()
    }

    /** Simplified and Traditional Chinese, and any other zh locale, get the Chinese. */
    @Test
    fun chineseIsWhatAChineseDeviceGets() {
        listOf(Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE, Locale.CHINESE, Locale.forLanguageTag("zh-HK")).forEach {
            Locale.setDefault(it)
            assertThat(tr("보드", "Board", "棋盘")).isEqualTo("棋盘")
            assertThat(isChinese()).isTrue()
            assertThat(isKorean()).isFalse()
        }
    }

    /** Every other language falls back to English. */
    @Test
    fun everythingElseGetsEnglish() {
        listOf(Locale.ENGLISH, Locale.JAPANESE, Locale.FRANCE, Locale.GERMANY).forEach {
            Locale.setDefault(it)
            assertThat(tr("보드", "Board", "棋盘")).isEqualTo("Board")
            assertThat(appLanguage()).isEqualTo(AppLanguage.ENGLISH)
        }
    }

    @Test
    fun theSettingsTableFollowsTheLanguage() {
        Locale.setDefault(Locale.KOREAN)
        assertThat(DesktopSettings.spec("boardSize")?.label).isEqualTo("보드 크기")

        Locale.setDefault(Locale.ENGLISH)
        assertThat(DesktopSettings.spec("boardSize")?.label).isEqualTo("Board Size")

        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        assertThat(DesktopSettings.spec("boardSize")?.label).isEqualTo("棋盘大小")

        // …and back, because the cache has to notice a change in every direction.
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

        // Chinese players use the characters: 寒星, 花月, 浦月 …
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        assertThat(MoveQuality.BLUNDER.display).isEqualTo("大失误")
        assertThat(Opening26.name(0)).isEqualTo("寒星")
        assertThat(Opening26.name(3)).isEqualTo("花月")
        assertThat(Opening26.name(19)).isEqualTo("浦月")
    }

    /** One name per opening, in the same order as the Korean and romaji tables. */
    @Test
    fun theChineseOpeningTableLinesUp() {
        assertThat(Opening26.chinese).hasSize(Opening26.COUNT)
        assertThat(Opening26.chinese.toSet()).hasSize(Opening26.COUNT)
        // spot checks against romaji: Kagetsu 花月, Hogetsu 浦月, Suisei 彗星
        assertThat(Opening26.chinese[Opening26.romaji.indexOf("Kagetsu")]).isEqualTo("花月")
        assertThat(Opening26.chinese[Opening26.romaji.indexOf("Hogetsu")]).isEqualTo("浦月")
        assertThat(Opening26.chinese[Opening26.romaji.indexOf("Suisei")]).isEqualTo("彗星")
        assertThat(Opening26.chinese[Opening26.romaji.indexOf("Myojo")]).isEqualTo("明星")
    }

    /** A Chinese-typed time reads back: «10秒», «2分钟», «不限时». */
    @Test
    fun chineseTimeUnitsParse() {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        val spec = dev.gomoku.rapfidroid.core.model.Duration.MOVE_TIME
        assertThat(dev.gomoku.rapfidroid.core.model.Duration.parse("10秒", spec).ms).isEqualTo(10_000L)
        assertThat(dev.gomoku.rapfidroid.core.model.Duration.parse("2分钟", spec).ms).isEqualTo(120_000L)
    }
}
