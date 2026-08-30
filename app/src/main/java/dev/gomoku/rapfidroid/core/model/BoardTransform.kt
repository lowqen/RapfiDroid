package dev.gomoku.rapfidroid.core.model

/**
 * The eight symmetries of the square, as the desktop's `rotate [90,180,270]`
 * and `flip [-,|,\]` console commands (main.c:10194-10266).
 *
 * One deliberate divergence: main.c's `flip /` maps (y,x) -> (size-1-y, size-1-x),
 * which is a 180° rotation and not a mirror about the anti-diagonal — `rotate 180`
 * already does that. [MIRROR_ANTI_DIAGONAL] is the real anti-diagonal mirror, so
 * the app can reach every symmetry; the other six match the desktop exactly.
 */
enum class BoardSymmetry {
    ROTATE_90,
    ROTATE_180,
    ROTATE_270,
    MIRROR_UP_DOWN,      // desktop `flip -`
    MIRROR_LEFT_RIGHT,   // desktop `flip |`
    MIRROR_DIAGONAL,     // desktop `flip \` (transpose)
    MIRROR_ANTI_DIAGONAL,
}

/** Directions of the desktop's `move [^,v,<,>]` (main.c:10267). */
enum class BoardShift { UP, DOWN, LEFT, RIGHT }

/**
 * Whole-shape transforms on a move path.
 *
 * The desktop transforms `movepath` and then replays it from an empty board, so
 * the move order — and with it the colours and the numbering — survives, while
 * the redo tail does not (`new_game`). Callers here mirror that: transform the
 * played moves, drop the future.
 */
object BoardTransform {

    fun symmetry(moves: List<Move>, size: Int, symmetry: BoardSymmetry): List<Move> =
        moves.map { map(it, size, symmetry) }

    private fun map(m: Move, size: Int, symmetry: BoardSymmetry): Move {
        val last = size - 1
        return when (symmetry) {
            // main.c applies its 90° step k times; these are the closed forms.
            BoardSymmetry.ROTATE_90 -> Move(x = last - m.y, y = m.x)
            BoardSymmetry.ROTATE_180 -> Move(x = last - m.x, y = last - m.y)
            BoardSymmetry.ROTATE_270 -> Move(x = m.y, y = last - m.x)
            BoardSymmetry.MIRROR_UP_DOWN -> Move(x = m.x, y = last - m.y)
            BoardSymmetry.MIRROR_LEFT_RIGHT -> Move(x = last - m.x, y = m.y)
            BoardSymmetry.MIRROR_DIAGONAL -> Move(x = m.y, y = m.x)
            BoardSymmetry.MIRROR_ANTI_DIAGONAL -> Move(x = last - m.y, y = last - m.x)
        }
    }

    /**
     * Shift every stone one point. Returns null when any stone would leave the
     * board — the desktop checks the whole path first and then shifts nothing
     * (main.c:10304, flag `f`), rather than clipping part of the shape.
     */
    fun shift(moves: List<Move>, size: Int, direction: BoardShift): List<Move>? {
        val dx = when (direction) {
            BoardShift.LEFT -> -1
            BoardShift.RIGHT -> 1
            else -> 0
        }
        val dy = when (direction) {
            BoardShift.UP -> -1
            BoardShift.DOWN -> 1
            else -> 0
        }
        val shifted = moves.map { Move(it.x + dx, it.y + dy) }
        return if (shifted.all { it.isInside(size) }) shifted else null
    }

    /**
     * The desktop's `getpos` / clipboard format: lower-case column letter plus
     * the 1-based row counted from the bottom, concatenated with no separator
     * (main.c:10399 `"%c%d"`, e.g. `h8i9`).
     */
    fun toPositionString(moves: List<Move>, size: Int): String =
        moves.joinToString("") { "${'a' + it.x}${size - it.y}" }

    /**
     * `putpos` parsing, ported from main.c:10374: one letter, then one digit and
     * a **second digit if there is one** (greedy), so `h11` is row 11 and never
     * `h1` + stray `1`. Stops — keeping what it has — at the first token that is
     * malformed, off-board, or lands on an occupied point.
     */
    fun fromPositionString(text: String, size: Int): List<Move> {
        val out = ArrayList<Move>()
        val taken = HashSet<Move>()
        var i = 0
        while (i < text.length) {
            val letter = text[i].uppercaseChar()
            if (letter !in 'A'..'Z') break
            val x = letter - 'A'
            i++
            var row = (text.getOrNull(i)?.takeIf { it.isDigit() } ?: break) - '0'
            i++
            text.getOrNull(i)?.takeIf { it.isDigit() }?.let {
                row = row * 10 + (it - '0')
                i++
            }
            val move = Move(x = x, y = size - row)
            if (!move.isInside(size) || !taken.add(move)) break
            out.add(move)
        }
        return out
    }
}
