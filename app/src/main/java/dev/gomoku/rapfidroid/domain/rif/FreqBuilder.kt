package dev.gomoku.rapfidroid.domain.rif

import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.Opening26
import dev.gomoku.rapfidroid.core.model.PosKey

/**
 * The rankings dataset, built from the same `.rif` the explorer packs come from
 * — the port of `rifdb/freq35.py`'s emit step.
 *
 * The explorer answers "what happens from *this* position"; the rankings answer
 * "which openings do people actually play, and how do they score". Both are the
 * same games counted differently, so building one without the other left half
 * the app looking broken with a full database on the device.
 *
 * A game row is `[blackIdx, whiteIdx, ruleIdx, o3, k5, res]`, which is what
 * [dev.gomoku.rapfidroid.domain.rankings.FreqAnalyzer] reads:
 *  - `o3` is the 3-move opening, 0..25 or [Opening26.NONSTD]
 *  - `k5` indexes a distinct 5-move **shape**, or -1 when the game is shorter
 *  - `res` is 2 black win / 1 draw / 0 white win
 */
class FreqBuilder(private val boardSize: Int = Move.DEFAULT_SIZE) {

    class Result(
        val players: List<List<String>>,
        val rules: List<String>,
        val shapes: List<String>,
        val games: List<IntArray>,
    )

    fun build(db: RifDatabase): Result {
        val playerIndex = HashMap<Int, Int>()
        val players = ArrayList<List<String>>()
        val ruleIndex = HashMap<Int, Int>()
        val rules = ArrayList<String>()
        // Shapes are keyed by the position key of the first five stones, so the
        // eight symmetries of one shape are one entry. The representative move
        // order is the first game that reached it — which order is arbitrary,
        // but it has to be stable, and first-seen is stable for a given file.
        val shapeIndex = HashMap<String, Int>()
        val shapes = ArrayList<String>()
        val games = ArrayList<IntArray>(db.games.size)

        for (game in db.games) {
            val moves = ArrayList<Move>(minOf(game.cells.size, SHAPE_PLIES))
            for (i in 0 until minOf(game.cells.size, SHAPE_PLIES)) {
                val cell = game.cells[i]
                moves += Move(cell % boardSize, cell / boardSize)
            }

            val black = playerIndex.getOrPut(game.black) {
                val p = db.players[game.black]
                players += listOf(p?.display ?: "?", db.countryOf(p?.country))
                players.size - 1
            }
            val white = playerIndex.getOrPut(game.white) {
                val p = db.players[game.white]
                players += listOf(p?.display ?: "?", db.countryOf(p?.country))
                players.size - 1
            }
            val rule = ruleIndex.getOrPut(game.rule) {
                rules += db.rules.firstOrNull { it.id == game.rule }?.name ?: "?"
                rules.size - 1
            }

            val o3 = Opening26.classify(moves.take(3))
            val k5 = if (moves.size >= SHAPE_PLIES) {
                shapeIndex.getOrPut(PosKey.keyOf(moves, boardSize)) {
                    shapes += moves.joinToString(" ") { it.label(boardSize) }
                    shapes.size - 1
                }
            } else {
                -1
            }

            games += intArrayOf(black, white, rule, o3, k5, RESULT[game.resultIndex])
        }

        return Result(players, rules, shapes, games)
    }

    private companion object {
        /** The rankings are about the 5-move shape; nothing past it is read. */
        const val SHAPE_PLIES = 5

        /** Parser order (0 black win, 1 draw, 2 white win) to freq35's (2, 1, 0). */
        val RESULT = intArrayOf(2, 1, 0)
    }
}
