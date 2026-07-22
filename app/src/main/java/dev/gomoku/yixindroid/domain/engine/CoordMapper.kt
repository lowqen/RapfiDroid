package dev.gomoku.yixindroid.domain.engine

import dev.gomoku.yixindroid.core.model.Move

/**
 * The **single** place board coordinates cross the wire (see plan §2.5 — the
 * flip-Y trap). Board `y` is top-down; the protocol may or may not flip it
 * depending on the server's `coord_conversion_mode`.
 *
 * Default `flipY = false` mirrors the desktop proxy path, which is verified
 * with `coord_conversion_mode = "none"`. Any change here MUST be re-checked by
 * a live round-trip (send a known position, confirm the returned move lands on
 * the same cell as the desktop GUI).
 */
class CoordMapper(
    val size: Int = Move.DEFAULT_SIZE,
    val flipY: Boolean = false,
) {
    /** Board move -> "x,y" for a command. */
    fun toWire(move: Move): String {
        val y = if (flipY) size - 1 - move.y else move.y
        return "${move.x},$y"
    }

    /** Wire "x,y" -> board move. */
    fun fromWire(x: Int, y: Int): Move =
        Move(x, if (flipY) size - 1 - y else y)
}
