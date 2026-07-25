package dev.gomoku.yixindroid.domain.settings

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.DesktopSettings
import dev.gomoku.yixindroid.core.model.EngineCapabilities
import dev.gomoku.yixindroid.core.model.SettingEditor
import dev.gomoku.yixindroid.core.model.SettingsFile
import org.junit.Test

/**
 * P4 locks the settings inventory to the desktop's files. The two constants below
 * are the **deployed** `test-yixin/settings.txt` and `settings_dev.txt`, byte for
 * byte: the desktop parses both by position, so a dropped, added or reordered
 * line silently shifts every setting after it. If this test fails, the app and
 * the PC no longer agree on what line 37 means.
 */
class SettingsCodecTest {

    // ---- inventory ----

    @Test
    fun bothFilesKeepTheDesktopLineCount() {
        assertThat(DesktopSettings.MAIN).hasSize(47)
        assertThat(DesktopSettings.DEV).hasSize(20)
        assertThat(DesktopSettings.ALL).hasSize(67)
    }

    @Test
    fun lineNumbersAreSequentialAndIdsUnique() {
        SettingsFile.entries.forEach { file ->
            DesktopSettings.of(file).forEachIndexed { index, spec ->
                assertThat(spec.line).isEqualTo(index + 1)
                assertThat(spec.file).isEqualTo(file)
            }
        }
        val ids = DesktopSettings.ALL.map { it.id }
        assertThat(ids).containsNoDuplicates()
        assertThat(DesktopSettings.ALL.map { it.label }.filter { it.isBlank() }).isEmpty()
    }

    @Test
    fun everySpecHasAUsableEditor() {
        val defaults = AppSettings()
        DesktopSettings.ALL.forEach { spec ->
            when (val editor = spec.editor) {
                is SettingEditor.Number -> assertThat(editor.min).isAtMost(editor.max)
                is SettingEditor.Choice -> {
                    assertThat(editor.options).isNotEmpty()
                    // the shipped default must be one of the offered values
                    assertThat(editor.options.map { it.value.toString() })
                        .contains(spec.read(defaults))
                }
                else -> Unit
            }
        }
    }

    // ---- the deployed files, round trip ----

    @Test
    fun defaultsRenderTheDeployedFilesExactly() {
        assertThat(SettingsCodec.render(AppSettings(), SettingsFile.MAIN)).isEqualTo(MAIN_TEXT)
        assertThat(SettingsCodec.render(AppSettings(), SettingsFile.DEV)).isEqualTo(DEV_TEXT)
    }

    @Test
    fun deployedFilesParseBackToTheDefaults() {
        // A different starting point proves the files, not the defaults, decide.
        val other = AppSettings(rule = 0, threadNum = 1, hashSizeMb = 256, darkMode = false)
        assertThat(SettingsCodec.parseAll(MAIN_TEXT, DEV_TEXT, other)).isEqualTo(AppSettings())
    }

    @Test
    fun everyEditedValueSurvivesARoundTrip() {
        val edited = AppSettings(
            boardSize = 20,
            rule = 6,
            level = 1,
            timeoutTurnSec = 42,
            maxNode = 123_456_000,
            style = 5,
            threadNum = 2,
            hashSizeMb = 1024,
            multiPv = 8,
            darkMode = false,
            incrementMs = 5_000,
            databaseReadonly = true,
            logScale = 100,
            winSaturation = 12,
            boardTextFont = "Noto Sans KR 14",
            dbCommentFont2 = "Consolas 11",
            boardZoomPercent = 175,
            reservedFitBoard = 3,
            reviewDepth = 20,
            devDefaults = false,
        )
        val main = SettingsCodec.render(edited, SettingsFile.MAIN)
        val dev = SettingsCodec.render(edited, SettingsFile.DEV)
        assertThat(SettingsCodec.parseAll(main, dev, AppSettings())).isEqualTo(edited)
    }

    @Test
    fun aShortFileKeepsTheRemainingValues() {
        // The desktop's `feof` fallback: missing lines are simply not applied.
        val base = AppSettings(threadNum = 7, hashSizeMb = 999)
        val parsed = SettingsCodec.parse("9\t;board size\n1\t;language\n", SettingsFile.MAIN, base)
        assertThat(parsed.boardSize).isEqualTo(9)
        assertThat(parsed.language).isEqualTo(1)
        assertThat(parsed.threadNum).isEqualTo(7)
        assertThat(parsed.hashSizeMb).isEqualTo(999)
    }

    @Test
    fun outOfRangeValuesAreClampedAndBadChoicesIgnored() {
        val spec = { id: String -> DesktopSettings.spec(id)!! }
        val base = AppSettings()
        assertThat(spec("boardSize").write(base, "99").boardSize).isEqualTo(22)
        assertThat(spec("boardSize").write(base, "1").boardSize).isEqualTo(5)
        assertThat(spec("winSaturation").write(base, "500").winSaturation).isEqualTo(100)
        // rule 9 does not exist -> the previous value stays
        assertThat(spec("rule").write(base, "9").rule).isEqualTo(base.rule)
        // anything non-zero is "on", as main.c reads booleans
        assertThat(spec("darkMode").write(base, "7").darkMode).isTrue()
        assertThat(spec("darkMode").write(base, "0").darkMode).isFalse()
        // garbage leaves the value untouched instead of zeroing it
        assertThat(spec("threadNum").write(base, "abc").threadNum).isEqualTo(base.threadNum)
    }

    @Test
    fun theReservedSlotStaysOnItsOwnLine() {
        val reserved = DesktopSettings.DEV[8]
        assertThat(reserved.id).isEqualTo("reservedFitBoard")
        assertThat(reserved.line).isEqualTo(9)
        val text = SettingsCodec.render(AppSettings(reservedFitBoard = 7), SettingsFile.DEV)
        assertThat(text.lines()[8]).isEqualTo("7\t;reserved (was: fit board to window)")
        // and it must not swallow the next setting
        assertThat(SettingsCodec.parse(text, SettingsFile.DEV).dbAutoSave).isTrue()
    }

    // ---- engine mapping ----

    @Test
    fun engineParamsConvertTheFileUnits() {
        val params = AppSettings().toEngineParams()
        // lines 7/8 are seconds on disk, milliseconds on the wire
        assertThat(params.timeoutTurnMs).isEqualTo(2_000_000)
        assertThat(params.timeoutMatchMs).isEqualTo(100_000_000)
        // line 29 is already milliseconds
        assertThat(AppSettings(incrementMs = 5_000).toEngineParams().incrementMs).isEqualTo(5_000)
        // line 19 is MB; set_hashsize shifts to KB
        assertThat(params.infoPairs().toMap()["hash_size"]).isEqualTo((8192L shl 10).toString())
    }

    @Test
    fun openingRulesFallBackToTheirBaseRule() {
        // main.c load_setting decodes 3/4/5/6 into (inforule, specialrule)
        assertThat(AppSettings(rule = 0).engineRule).isEqualTo(0)
        assertThat(AppSettings(rule = 1).engineRule).isEqualTo(1)
        assertThat(AppSettings(rule = 2).engineRule).isEqualTo(2)
        assertThat(AppSettings(rule = 3).engineRule).isEqualTo(0) // swap after 1st
        assertThat(AppSettings(rule = 4).engineRule).isEqualTo(2) // Yamaguchi
        assertThat(AppSettings(rule = 5).engineRule).isEqualTo(2) // Soosorv-8
        assertThat(AppSettings(rule = 6).engineRule).isEqualTo(1) // swap-2
        assertThat(AppSettings(rule = 2).isRenju).isTrue()
        assertThat(AppSettings(rule = 0).isRenju).isFalse()
    }

    @Test
    fun databaseAndSymmetryFlagsArePushed() {
        val map = AppSettings(useDatabase = false, databaseReadonly = true, nbestSym = true)
            .toEngineParams().infoPairs().toMap()
        assertThat(map["usedatabase"]).isEqualTo("0")
        assertThat(map["database_readonly"]).isEqualTo("1")
        assertThat(map["nbestsym"]).isEqualTo("1")
    }

    @Test
    fun everyEngineKeyReachesTheEngine() {
        val sent = AppSettings().toEngineParams().infoPairs().map { it.first }.toSet()
        val declared = DesktopSettings.ALL.mapNotNull { it.engineKey }.toSet()
        // yxnbest is a command argument, not an INFO key; everything else is sent
        assertThat(sent).containsAtLeastElementsIn(declared - "yxnbest")
    }

    @Test
    fun boardSizeChangeForcesARestart() {
        val base = AppSettings().toEngineParams()
        assertThat(AppSettings(boardSize = 20).toEngineParams().needsRestart(base)).isTrue()
        assertThat(AppSettings(rule = 0).toEngineParams().needsRestart(base)).isTrue()
        assertThat(AppSettings(threadNum = 8).toEngineParams().needsRestart(base)).isFalse()
    }

    @Test
    fun capabilitiesFoldTheEngineReports() {
        var caps = EngineCapabilities()
        assertThat(caps.maxThreadNum).isNull()
        caps = caps.with("MAX_THREAD_NUM", "8")
        assertThat(caps.maxThreadNum).isEqualTo(8)
        // the engine reports log2(bytes); main.c converts the same way
        assertThat(caps.with("MAX_HASH_SIZE", "26").maxHashSizeMb).isEqualTo(65_536)
        assertThat(caps.with("MAX_HASH_SIZE", "10").maxHashSizeMb).isEqualTo(1)
        assertThat(caps.with("SOMETHING_ELSE", "3")).isEqualTo(caps)
    }

    private companion object {
        /** Deployed `test-yixin/settings.txt`, verbatim. */
        val MAIN_LINES = listOf(
            "15\t;board size (10 ~ 22)",
            "0\t;language (0: English, 1,2,...: custom)",
            "2\t;rule (0: freestyle, 1: standard, 2: free renju, 3: swap after 1st move, 5: soosorv, 6: swap-2)",
            "1\t;computer play black (0: no, 1: yes)",
            "0\t;computer play white (0: no, 1: yes)",
            "0\t;level (0: unlimited time 1: custom level 2-12: predefined level)",
            "2000\t;time limit (turn)",
            "100000\t;time limit (match)",
            "100\t;max depth",
            "1000000000\t;max node",
            "3\t;style (rash 0 ~ 5 cautious)",
            "2\t;toolbar style (0: only icon, 1: both icon and words, 2: both with horizontally stacked)",
            "1\t;show log (0: no, 1: yes)",
            "1\t;show number (0: no, 1: yes)",
            "1\t;show analysis (0: no, 1: yes)",
            "1\t;show analysis winrate (0: no, 1: yes)",
            "1\t;show warning (0: no, 1: yes)",
            "4\t;number of threads",
            "8192\t;hash size (MB)",
            "3\t;default number of multi-pv",
            "1\t;block autoreset (0: no, 1: yes)",
            "0\t;blockpath autoreset (0: no, 1: yes)",
            "0\t;pondering (0: off, 1: on)",
            "0\t;checkmate in global search (0: no, 1: vct, 2: vc2)",
            "0\t;hash autoclear (0: no, 1: yes)",
            "0\t;toolbar postion (0: left vertical, 1: right horizontal)",
            "1\t;enable dark mode",
            "0\t;show clock (0: no, 1: yes)",
            "0\t;time increment per move",
            "1\t;show forbidden moves",
            "0\t;check timeout",
            "1\t;use database moves (0: no, 1: yes)",
            "0\t;enable database read-only mode (0: no, 1: yes)",
            "1\t;show database baord texts (0: no, 1: yes)",
            "1\t;show database delall confirmation (0: no, 1: yes)",
            "0\t;record debug log",
            "140\t;log area horizontal scale",
            "0\t;symmetric nbest for the 5th moves",
            "0\t;lossing move color saturation (0~100)",
            "83\t;winning move color saturation (0~100)",
            "20\t;min winrate color saturation (0~100)",
            "80\t;max winrate color saturation (0~100)",
            "100\t;value of color (0~100)",
            "SimHei, sans-serif 12;Board Text Font",
            "Sarasa Mono SC, Cascadia Mono, SimHei, monospace 9;Text Log Font",
            "SimHei, sans-serif 12;Database Comment Font",
            "Courier New, monospace 10;Database Comment Font",
        )

        /** Deployed `test-yixin/settings_dev.txt`, verbatim. */
        val DEV_LINES = listOf(
            "1\t;show evaluation bar (0: no, 1: yes)",
            "1\t;show winrate graph (0: no, 1: yes)",
            "1\t;move quality preset (0: strict, 1: default, 2: lenient)",
            "1\t;show move badges on stones (0: no, 1: yes)",
            "10\t;prove mode initial budget per node (seconds)",
            "320\t;prove mode budget cap (seconds)",
            "1\t;prove mode attacker candidates (yxnbest k)",
            "100\t;board zoom scale percent (60~300)",
            "0\t;reserved (was: fit board to window)",
            "1\t;auto-save database periodically (0: no, 1: yes)",
            "5\t;database auto-save interval in minutes",
            "1\t;prove best attack move first (0: no, 1: yes)",
            "1\t;prove early probe of strongest defense (0: no, 1: yes)",
            "0\t;review budget unit (0: seconds, 1: depth)",
            "14\t;review fixed depth per move",
            "0\t;prove budget unit (0: seconds, 1: depth)",
            "15\t;prove initial depth per node",
            "30\t;prove depth cap per node",
            "1\t;skip grading/search of opening moves 1-5 (0: no, 1: yes)",
            "1\t;one-time dark+korean defaults applied (do not edit)",
        )

        val MAIN_TEXT = MAIN_LINES.joinToString(separator = "\n", postfix = "\n")
        val DEV_TEXT = DEV_LINES.joinToString(separator = "\n", postfix = "\n")
    }
}
