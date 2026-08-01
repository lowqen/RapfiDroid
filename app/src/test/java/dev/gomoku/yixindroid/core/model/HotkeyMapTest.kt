package dev.gomoku.yixindroid.core.model

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The index is the contract between `function/hotkey<n>.txt` and both programs,
 * so every mapping here is checked against `hotkeykeylist` (main.c:218-277).
 */
class HotkeyMapTest {

    @Test
    fun theFunctionKeysAreOneThroughTwelve() {
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_F1, false)).isEqualTo(1)
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_F12, false)).isEqualTo(12)
    }

    @Test
    fun theArrowsNeedControlAndSitAtThirteenToSixteen() {
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_DPAD_UP, true)).isEqualTo(13)
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_DPAD_DOWN, true)).isEqualTo(14)
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_DPAD_LEFT, true)).isEqualTo(15)
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_DPAD_RIGHT, true)).isEqualTo(16)
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_DPAD_UP, false)).isEqualTo(0)
    }

    /** The desktop lists 1..9 and then 0, so zero is 26 rather than 17. */
    @Test
    fun theDigitsRunOneToNineThenZero() {
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_1, true)).isEqualTo(17)
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_9, true)).isEqualTo(25)
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_0, true)).isEqualTo(26)
    }

    @Test
    fun theLettersRunAToZ() {
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_A, true)).isEqualTo(27)
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_Z, true)).isEqualTo(52)
    }

    @Test
    fun escapeIsFiftyThreeWithOrWithoutControl() {
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_ESCAPE, false)).isEqualTo(53)
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_ESCAPE, true)).isEqualTo(53)
    }

    /** Every index this produces must name a key in the desktop's own list. */
    @Test
    fun everyMappedIndexIsInsideTheDesktopKeyTable() {
        val codes = listOf(
            KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F12, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_ESCAPE,
        )
        for (code in codes) {
            val index = HotkeyMap.indexFor(code, true)
            assertThat(index).isIn(1 until FunctionScripts.KEY_NAMES.size)
        }
    }

    @Test
    fun anUnboundKeyRunsNothing() {
        assertThat(HotkeyMap.indexFor(KeyEvent.KEYCODE_SPACE, true)).isEqualTo(0)
        assertThat(
            HotkeyMap.scriptFor(
                FunctionScripts.DEFAULT_HOTKEYS, KeyEvent.KEYCODE_SPACE, true,
            ),
        ).isNull()
    }

    /** The desktop's six defaults, pressed. */
    @Test
    fun theDefaultBindingsResolveToTheirScripts() {
        val keys = FunctionScripts.DEFAULT_HOTKEYS
        assertThat(HotkeyMap.scriptFor(keys, KeyEvent.KEYCODE_DPAD_UP, true)).isEqualTo("undo all")
        assertThat(HotkeyMap.scriptFor(keys, KeyEvent.KEYCODE_DPAD_DOWN, true)).isEqualTo("redo all")
        assertThat(HotkeyMap.scriptFor(keys, KeyEvent.KEYCODE_DPAD_LEFT, true)).isEqualTo("undo one")
        assertThat(HotkeyMap.scriptFor(keys, KeyEvent.KEYCODE_DPAD_RIGHT, true)).isEqualTo("redo one")
        assertThat(HotkeyMap.scriptFor(keys, KeyEvent.KEYCODE_ESCAPE, false))
            .isEqualTo("thinking stop")
        assertThat(HotkeyMap.scriptFor(keys, KeyEvent.KEYCODE_F1, false))
            .isEqualTo("thinking toggle")
    }

    /** An entry left blank in the file must not swallow the key. */
    @Test
    fun aBoundKeyWithAnEmptyScriptIsIgnored() {
        val keys = listOf(FunctionScripts.HotkeyItem(53, "   "))
        assertThat(HotkeyMap.scriptFor(keys, KeyEvent.KEYCODE_ESCAPE, false)).isNull()
    }
}
