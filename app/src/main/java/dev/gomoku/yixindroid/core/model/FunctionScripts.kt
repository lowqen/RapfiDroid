package dev.gomoku.yixindroid.core.model

/**
 * The desktop's user-definable buttons and keys — `function/toolbar<n>.txt` and
 * `function/hotkey<n>.txt` (main.c:14281-14320 reads them, main.c:11698-11714
 * writes them back).
 *
 * Both files are the same shape: a leading integer, then a script in the console
 * command language P10 already speaks. Nothing here interprets the script; it
 * goes to `ConsoleCommand.script()` exactly as the desktop hands it to
 * `custom_function`. That is the whole point of having ported the language
 * first — a button is a name and a script, not a special case.
 */
object FunctionScripts {

    /**
     * One toolbar button. [lngId] indexes the language table rather than holding
     * a label, because that is what the file stores; [icon] is a GTK icon name,
     * kept verbatim so a file written here still opens on the desktop.
     */
    data class ToolbarItem(val lngId: Int, val icon: String, val script: String)

    /** One hotkey. [keyIndex] indexes [KEY_NAMES] — the desktop stores the
     *  combo-box position, not a key code (main.c:4104). */
    data class HotkeyItem(val keyIndex: Int, val script: String)

    /**
     * `fscanf("%d")` then `fscanf("%255s")` then every remaining line, with each
     * line's CR/LF collapsed to one `\n` — so blank lines vanish and the icon
     * token ends at whitespace. Reproduced rather than simplified: the shipped
     * files have a blank line after the icon and after the script, and a reader
     * that split on newlines would take the blank as the command.
     */
    fun parseToolbar(text: String): ToolbarItem? {
        val tokens = text.trimStart().split(Regex("\\s+"), limit = 3)
        if (tokens.size < 2) return null
        val lng = tokens[0].toIntOrNull() ?: return null
        val icon = tokens[1]
        val script = tokens.getOrElse(2) { "" }.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return ToolbarItem(lng, icon, script)
    }

    /** Same shape without the icon token. */
    fun parseHotkey(text: String): HotkeyItem? {
        val trimmed = text.trimStart()
        val head = trimmed.takeWhile { !it.isWhitespace() }
        val key = head.toIntOrNull() ?: return null
        val script = trimmed.drop(head.length).lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return HotkeyItem(key, script)
    }

    /** The exact bytes main.c writes, so a round trip through the app is a no-op. */
    fun writeToolbar(item: ToolbarItem): String =
        "${item.lngId}\n${item.icon}\n${item.script}\n"

    fun writeHotkey(item: HotkeyItem): String = "${item.keyIndex}\n${item.script}\n"

    /** `toolbarlng` / `toolbaricon` / `toolbarcommand` (main.c:183-193). */
    val DEFAULT_TOOLBAR: List<ToolbarItem> = listOf(
        ToolbarItem(48, "go-first", "undo all"),
        ToolbarItem(46, "go-previous", "undo one"),
        ToolbarItem(47, "go-next", "redo one"),
        ToolbarItem(49, "go-last", "redo all"),
        ToolbarItem(45, "media-playback-stop", "thinking stop"),
        ToolbarItem(44, "media-playback-start", "thinking start"),
    )

    /** `hotkeykey` / `hotkeycommand` (main.c:197-204). */
    val DEFAULT_HOTKEYS: List<HotkeyItem> = listOf(
        HotkeyItem(13, "undo all"),
        HotkeyItem(14, "redo all"),
        HotkeyItem(15, "undo one"),
        HotkeyItem(16, "redo one"),
        HotkeyItem(53, "thinking stop"),
        HotkeyItem(1, "thinking toggle"),
    )

    /** `hotkeynamelist` (main.c:206-216). Position is the stored value. */
    val KEY_NAMES: List<String> = buildList {
        add("")
        for (i in 1..12) add("F$i")
        addAll(listOf("Ctrl + Up", "Ctrl + Down", "Ctrl + Left", "Ctrl + Right"))
        for (d in 1..9) add("Ctrl + $d")
        add("Ctrl + 0")
        for (c in 'A'..'Z') add("Ctrl + $c")
        add("Escape")
    }

    /** main.c caps both lists; the files past the cap are simply not read. */
    const val MAX_TOOLBAR_ITEMS = 40
    const val MAX_HOTKEY_ITEMS = 20

    /** `function/toolbar<n>.txt`, 1-based like the desktop. */
    fun toolbarFileName(index: Int) = "toolbar${index + 1}.txt"

    fun hotkeyFileName(index: Int) = "hotkey${index + 1}.txt"
}
