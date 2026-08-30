package dev.gomoku.rapfidroid.core.model

import android.view.KeyEvent

/**
 * Android key event → the desktop's hotkey index.
 *
 * The desktop stores the *position in its combo box* rather than a key code
 * (main.c:4104, `hotkeykeylist` at main.c:218), so the index is the contract
 * between the file and both programs. This maps a real key press onto that same
 * table: F1-F12 are 1-12, Ctrl + arrows 13-16, Ctrl + digits 17-26, Ctrl +
 * letters 27-52, Escape 53 — position for position with [FunctionScripts.KEY_NAMES].
 *
 * Phones rarely have these keys; tablets with a keyboard case, DeX and Chrome OS
 * do, and on those the six defaults (undo/redo/stop/toggle) are worth having.
 */
object HotkeyMap {

    /** The index for a press, or 0 — the desktop's "unbound" slot — for anything else. */
    fun indexFor(keyCode: Int, ctrlPressed: Boolean): Int {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) return 53
        if (keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
            return keyCode - KeyEvent.KEYCODE_F1 + 1
        }
        if (!ctrlPressed) return 0
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> 13
            KeyEvent.KEYCODE_DPAD_DOWN -> 14
            KeyEvent.KEYCODE_DPAD_LEFT -> 15
            KeyEvent.KEYCODE_DPAD_RIGHT -> 16
            // The desktop lists 1..9 then 0, so 0 is last rather than first.
            KeyEvent.KEYCODE_0 -> 26
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_1 + 17
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> keyCode - KeyEvent.KEYCODE_A + 27
            else -> 0
        }
    }

    /**
     * The script bound to a press, or null. Index 0 is never matched: it is the
     * desktop's blank entry, and a hotkey left unbound must not fire on
     * every unrecognised key.
     */
    fun scriptFor(
        hotkeys: List<FunctionScripts.HotkeyItem>,
        keyCode: Int,
        ctrlPressed: Boolean,
    ): String? {
        val index = indexFor(keyCode, ctrlPressed)
        if (index == 0) return null
        return hotkeys.firstOrNull { it.keyIndex == index && it.script.isNotBlank() }?.script
    }
}
