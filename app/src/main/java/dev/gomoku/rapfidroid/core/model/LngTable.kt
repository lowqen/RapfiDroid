package dev.gomoku.rapfidroid.core.model

/**
 * A desktop language file, `language/<n>.lng` (main.c:12755-12779 for the
 * language *name*, `TL(idx, fallback)` for every other lookup).
 *
 * Lines are `<id>=<text>`. Parsing stops at the first blank line or a line
 * starting with `;`, which is how the shipped files mark their end — a rule
 * worth keeping, since the files carry a trailing comment block that would
 * otherwise be read as entries.
 *
 * Entry **84 is the language's own name** in that language, which is what the
 * desktop lists in its menu, and the only string the app needs before the user
 * has chosen anything.
 *
 * The app does not translate its own UI through this table — Android has
 * resources for that. What it needs the table for is the strings that are *data*
 * rather than UI: a toolbar button stores a numeric label id, so without the
 * table an imported desktop toolbar has buttons with no names.
 */
class LngTable(private val entries: Map<Int, String>) {

    val size: Int get() = entries.size

    /** The label for [id], or [fallback] when this file never defined it. */
    fun label(id: Int, fallback: String = ""): String =
        entries[id]?.takeIf { it.isNotEmpty() } ?: fallback

    /** Entry 84 — the language's name, written in itself. */
    val languageName: String get() = label(NAME_ID)

    /** The raw entries, for persisting an imported table. */
    fun entriesForStorage(): Map<Int, String> = entries

    companion object {
        const val NAME_ID = 84

        val EMPTY = LngTable(emptyMap())

        fun parse(text: String): LngTable {
            val entries = LinkedHashMap<Int, String>()
            for (raw in text.lineSequence()) {
                val line = raw.trimEnd('\r', '\n')
                if (line.isEmpty() || line.startsWith(';')) break
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                val id = line.substring(0, eq).trim().toIntOrNull() ?: continue
                entries[id] = line.substring(eq + 1)
            }
            return LngTable(entries)
        }

        /**
         * Just the name, without keeping the rest — the language picker needs one
         * string per file and there are up to fifteen of them.
         */
        fun nameOf(text: String): String = parse(text).languageName
    }
}
