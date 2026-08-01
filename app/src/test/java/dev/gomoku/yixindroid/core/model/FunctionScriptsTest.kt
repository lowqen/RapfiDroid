package dev.gomoku.yixindroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Against the shipped `function/` and `language/` files, whose exact shape
 * main.c's `fscanf`/`fgets` reader defines (main.c:14281-14320).
 */
class FunctionScriptsTest {

    /** The bytes of the deployed `function/toolbar1.txt`, CRLF and all. */
    private val toolbar1 = "48\r\ngo-first\r\n\r\nundo all\r\n\r\n"
    private val hotkey1 = "13\r\n\r\nundo all\r\n\r\n"

    @Test
    fun theBlankLinesAroundTheScriptAreNotTheScript() {
        val item = FunctionScripts.parseToolbar(toolbar1)!!
        assertThat(item.lngId).isEqualTo(48)
        assertThat(item.icon).isEqualTo("go-first")
        assertThat(item.script).isEqualTo("undo all")
    }

    @Test
    fun aHotkeyIsTheSameFileWithoutTheIcon() {
        val item = FunctionScripts.parseHotkey(hotkey1)!!
        assertThat(item.keyIndex).isEqualTo(13)
        assertThat(item.script).isEqualTo("undo all")
    }

    /** toolbar33: `command on` wrapping, so the script really is multi-line. */
    @Test
    fun aMultiLineScriptSurvivesIntact() {
        val text = "330\r\nsystem-run\r\n\r\ncommand on\r\nbench\r\ncommand off\r\n\r\n"
        val item = FunctionScripts.parseToolbar(text)!!
        assertThat(item.script).isEqualTo("command on\nbench\ncommand off")
        assertThat(ConsoleCommand.script(item.script)).hasSize(3)
    }

    @Test
    fun aFileWithoutAnIconTokenIsRejectedRatherThanGuessed() {
        assertThat(FunctionScripts.parseToolbar("48")).isNull()
        assertThat(FunctionScripts.parseToolbar("")).isNull()
        assertThat(FunctionScripts.parseHotkey("not-a-number\nundo all")).isNull()
    }

    /** Writing then reading must land on the same item, or a save loses a button. */
    @Test
    fun everyDefaultToolbarItemRoundTrips() {
        for (item in FunctionScripts.DEFAULT_TOOLBAR) {
            assertThat(FunctionScripts.parseToolbar(FunctionScripts.writeToolbar(item)))
                .isEqualTo(item)
        }
    }

    @Test
    fun everyDefaultHotkeyRoundTrips() {
        for (item in FunctionScripts.DEFAULT_HOTKEYS) {
            assertThat(FunctionScripts.parseHotkey(FunctionScripts.writeHotkey(item)))
                .isEqualTo(item)
        }
    }

    /** Every default script has to be a command the app can actually run. */
    @Test
    fun theDefaultScriptsAllParseAsConsoleCommands() {
        val all = FunctionScripts.DEFAULT_TOOLBAR.map { it.script } +
            FunctionScripts.DEFAULT_HOTKEYS.map { it.script }
        for (script in all) {
            assertThat(ConsoleCommand.script(script)).isNotEmpty()
        }
    }

    /** `hotkeynamelist` has 54 entries: blank, F1-F12, 4 arrows, 10 digits, 26 letters, Escape. */
    @Test
    fun theKeyNamesMatchTheDesktopTablePositionForPosition() {
        assertThat(FunctionScripts.KEY_NAMES).hasSize(54)
        assertThat(FunctionScripts.KEY_NAMES[13]).isEqualTo("Ctrl + Up")
        assertThat(FunctionScripts.KEY_NAMES[16]).isEqualTo("Ctrl + Right")
        assertThat(FunctionScripts.KEY_NAMES[26]).isEqualTo("Ctrl + 0")
        assertThat(FunctionScripts.KEY_NAMES[27]).isEqualTo("Ctrl + A")
        assertThat(FunctionScripts.KEY_NAMES[52]).isEqualTo("Ctrl + Z")
        assertThat(FunctionScripts.KEY_NAMES[53]).isEqualTo("Escape")
    }

    @Test
    fun theFileNamesAreOneBasedLikeTheDesktop() {
        assertThat(FunctionScripts.toolbarFileName(0)).isEqualTo("toolbar1.txt")
        assertThat(FunctionScripts.hotkeyFileName(5)).isEqualTo("hotkey6.txt")
    }
}

class LngTableTest {

    @Test
    fun entriesAreIdEqualsText() {
        val table = LngTable.parse("0=Best Line\n1=Evaluation\n84=한국어\n")
        assertThat(table.label(0)).isEqualTo("Best Line")
        assertThat(table.label(1)).isEqualTo("Evaluation")
        assertThat(table.languageName).isEqualTo("한국어")
    }

    /** The shipped files carry a trailing comment block; it is not entries. */
    @Test
    fun parsingStopsAtTheFirstBlankLineOrSemicolon() {
        val table = LngTable.parse("0=a\n1=b\n\n2=never read\n")
        assertThat(table.size).isEqualTo(2)
        assertThat(table.label(2, "fallback")).isEqualTo("fallback")

        val commented = LngTable.parse("0=a\n;note\n1=b\n")
        assertThat(commented.size).isEqualTo(1)
    }

    /** `TL(idx, "default")`: a missing id falls through to the desktop's own text. */
    @Test
    fun anUndefinedIdFallsThroughToTheCallersText() {
        val table = LngTable.parse("0=a\n")
        assertThat(table.label(999, "Undo All")).isEqualTo("Undo All")
        assertThat(LngTable.EMPTY.label(48, "Undo All")).isEqualTo("Undo All")
    }

    @Test
    fun anEmptyValueIsTreatedAsUndefined() {
        val table = LngTable.parse("48=\n")
        assertThat(table.label(48, "Undo All")).isEqualTo("Undo All")
    }

    @Test
    fun textAfterTheFirstEqualsSignIsKeptWhole() {
        val table = LngTable.parse("5=a = b = c\n")
        assertThat(table.label(5)).isEqualTo("a = b = c")
    }
}
