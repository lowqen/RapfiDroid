package dev.gomoku.yixindroid.core.model

/**
 * One of the desktop's four font settings, e.g.
 *
 * ```
 * SimHei, sans-serif 12;Board Text Font
 * Sarasa Mono SC, Cascadia Mono, SimHei, monospace 9;Text Log Font
 * ```
 *
 * These are Pango descriptions: a comma-separated family list with the size on
 * the end. A phone has none of those families, and shipping them is not the
 * point of the setting — what the user is actually adjusting is **how big the
 * text is**, and whether it is monospaced. Both of those transfer.
 *
 * So the family list is kept verbatim (it travels back to the PC untouched) and
 * read only for the last entry, which is the generic that says what kind of face
 * was wanted.
 */
data class FontSpec(val families: String, val pointSize: Int, val monospace: Boolean) {

    /**
     * Scale relative to the desktop's usual 10 pt, clamped so a stray value
     * cannot make the UI unusable. Applied to the app's own type scale rather
     * than as an absolute size: 12 pt on a 96 dpi desktop is not 12 sp here.
     */
    val scale: Float get() = (pointSize.coerceIn(6, 24)) / 10f

    companion object {
        val DEFAULT = FontSpec("sans-serif", 10, monospace = false)

        /**
         * The desktop writes `<families> <size>` with no tab before the `;`
         * (main.c's font lines are the one place that differs), so the size is
         * the trailing integer and everything before it is the family list.
         */
        fun parse(raw: String): FontSpec {
            val text = raw.substringBefore(';').trim()
            if (text.isEmpty()) return DEFAULT
            val size = text.takeLastWhile { it.isDigit() }
            val families = text.dropLast(size.length).trim()
            val generic = families.substringAfterLast(',').trim().lowercase()
            return FontSpec(
                families = families.ifEmpty { DEFAULT.families },
                pointSize = size.toIntOrNull() ?: DEFAULT.pointSize,
                monospace = generic == "monospace" || generic.contains("mono"),
            )
        }
    }
}
