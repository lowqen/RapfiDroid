package dev.gomoku.zenyth.core.model

/**
 * The position link Zenyth Web shares: `https://rapfiweb.pages.dev/board?p=h8i9j10`.
 *
 * Coordinates run together with no separator — the column letter is the
 * separator — lower case when written, any case, spaces or commas when read,
 * because it is a link people edit by hand. Unlike a game file a link is
 * **all or nothing**: one token off the board or on an occupied point refuses
 * the whole link, since half a position is not the position that was sent.
 */
object PositionLink {
    const val BASE = "https://rapfiweb.pages.dev/board"
    const val PARAM = "p"

    private val SHAPE = Regex("^([A-Za-z]\\d{1,2})*$")
    private val TOKEN = Regex("[A-Za-z]\\d{1,2}")

    /**
     * A full 22 × 22 board is under 1,500 characters. Anything far longer is not
     * a position, and is refused before the shape regex walks it: a link is
     * input any web page or app can send.
     */
    private const val MAX_CHARS = 4096

    fun encode(moves: List<Move>, size: Int): String =
        moves.joinToString("") { it.label(size).lowercase() }

    fun url(moves: List<Move>, size: Int): String = "$BASE?$PARAM=${encode(moves, size)}"

    /** A move string: empty is the empty board, null when it cannot be read. */
    fun parse(text: String, size: Int): List<Move>? {
        val src = text.replace(Regex("[\\s,]"), "")
        if (src.length > MAX_CHARS || !SHAPE.matches(src)) return null
        val out = ArrayList<Move>()
        for (m in TOKEN.findAll(src)) {
            val move = Move.fromLabel(m.value.uppercase(), size) ?: return null
            if (!move.isInside(size) || move in out) return null
            out.add(move)
        }
        return out
    }

    /**
     * The moves of a pasted or opened link: a full URL with `?p=`, or a bare
     * move string. Null when there is no position in it.
     */
    fun fromText(text: String, size: Int): List<Move>? {
        val trimmed = text.trim()
        val q = trimmed.indexOf('?')
        if (q >= 0) {
            val query = trimmed.substring(q + 1).substringBefore('#')
            val value = query.split('&')
                .map { it.split('=', limit = 2) }
                .firstOrNull { it.size == 2 && it[0] == PARAM }
                ?.get(1)
                ?: return null
            // A broken escape (`%zz`) makes URLDecoder throw: no position, not a crash.
            val decoded = runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrNull() ?: return null
            return parse(decoded, size)
        }
        return parse(trimmed, size)?.takeIf { it.isNotEmpty() }
    }
}
