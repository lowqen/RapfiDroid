package dev.gomoku.rapfidroid.domain.repository

import android.net.Uri
import dev.gomoku.rapfidroid.core.model.FunctionScripts
import dev.gomoku.rapfidroid.core.model.LngTable
import kotlinx.coroutines.flow.StateFlow

/**
 * The desktop's `function/` and `language/` folders: user-defined toolbar
 * buttons, hotkeys, and the label table their numeric ids point into.
 *
 * These are plain text files the user already owns next to Yixin.exe, so they
 * come in through the Storage Access Framework the way the explorer packs do —
 * pick the deployment folder once and everything under it is read. Until then
 * the desktop's own defaults are used, which is what a fresh install of Yixin
 * shows too.
 */
interface AppearanceRepository {

    val toolbar: StateFlow<List<FunctionScripts.ToolbarItem>>
    val hotkeys: StateFlow<List<FunctionScripts.HotkeyItem>>

    /** Labels for the toolbar's numeric ids; [LngTable.EMPTY] until imported. */
    val language: StateFlow<LngTable>

    /** Where the current toolbar/hotkeys came from, for the settings screen. */
    val source: StateFlow<String?>

    /**
     * Read `function/toolbar<n>.txt` and `language/<n>.lng` under a picked folder.
     * Returns what was found, or a failure naming what was missing.
     */
    suspend fun importFrom(tree: Uri, languageIndex: Int): Result<String>

    /** Go back to the desktop's built-in defaults and forget the import. */
    suspend fun reset()

    /** Restore the persisted definitions (call once at startup). */
    suspend fun restore()
}
