package dev.gomoku.yixindroid.core.model

/**
 * A board cell. Internal convention matches the desktop Yixin-Board core:
 * `x` = column 0..size-1 (a..o), `y` = row measured **top-down** (0 = top).
 * Human labels count rows bottom-up, so the label row is `size - y`.
 */
data class Move(val x: Int, val y: Int) {

    fun label(size: Int = DEFAULT_SIZE): String = "${'A' + x}${size - y}"

    fun isInside(size: Int = DEFAULT_SIZE): Boolean =
        x in 0 until size && y in 0 until size

    companion object {
        const val DEFAULT_SIZE = 15

        /** Parse "H8" / "h8" -> Move. Returns null on malformed input. */
        fun fromLabel(text: String, size: Int = DEFAULT_SIZE): Move? {
            val t = text.trim()
            if (t.length < 2 || !t[0].isLetter()) return null
            val x = t[0].uppercaseChar() - 'A'
            val row = t.substring(1).toIntOrNull() ?: return null
            val m = Move(x, size - row)
            return if (m.isInside(size)) m else null
        }
    }
}
