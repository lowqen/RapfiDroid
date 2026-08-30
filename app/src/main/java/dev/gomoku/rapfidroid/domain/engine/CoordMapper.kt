package dev.gomoku.rapfidroid.domain.engine

import dev.gomoku.rapfidroid.core.model.Move

/**
 * The **single** place board coordinates cross the wire (plan §2.5 — the flip-Y
 * trap). Confirmed from the desktop main.c (`iochannelout_watch` / `send_board`):
 * the Rapfi wire format is **"row,col" = "y,x"** in both directions, where the
 * board index is `cell = y * size + x` (`y` = top-down row, `x` = column).
 *
 * `flipY = false` mirrors the verified desktop proxy path
 * (`coord_conversion_mode = "none"`). Flipping reflects the **row** (matching
 * the server's `--flip-y`). Any change here MUST be re-checked with a live
 * round-trip against the same position the desktop GUI shows.
 */
class CoordMapper(
    val size: Int = Move.DEFAULT_SIZE,
    val flipY: Boolean = false,
) {
    /** Board move -> "y,x" (row,col) for a command. */
    fun toWire(move: Move): String {
        val row = if (flipY) size - 1 - move.y else move.y
        return "$row,${move.x}"
    }

    /** Wire pair (row, col) -> board move. */
    fun fromWire(row: Int, col: Int): Move =
        Move(x = col, y = if (flipY) size - 1 - row else row)

    /** Parse a "row,col" token (e.g. "7,8") to a board move, or null. */
    fun parsePair(token: String): Move? {
        val parts = token.trim().split(',')
        if (parts.size != 2) return null
        val row = parts[0].trim().toIntOrNull() ?: return null
        val col = parts[1].trim().toIntOrNull() ?: return null
        return fromWire(row, col)
    }
}
