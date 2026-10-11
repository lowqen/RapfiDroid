package dev.gomoku.rapfidroid.domain.rif

import dev.gomoku.rapfidroid.core.model.Opening26

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
class FreqBuilder {

    class Result(
        val players: List<List<String>>,
        val rules: List<String>,
        val shapes: List<String>,
        val games: List<IntArray>,
    )

    companion object {
        /**
         * The same rows from a device-built pack, whose columns already hold
         * them: dense player and rule indices, the 26주형, the 5-move shape and
         * the result in freq35's code.
         */
        fun fromPack(pack: RenjuPack): Result = Result(
            players = pack.players.indices.map { listOf(pack.players[it], pack.playerCountry[it]) },
            rules = pack.rules.toList(),
            shapes = pack.shapes.toList(),
            games = (0 until pack.gameCount).map { g ->
                intArrayOf(
                    pack.gameBlack[g], pack.gameWhite[g], pack.gameRule[g],
                    pack.gameO3[g].toInt(), pack.gameK5[g], pack.gameRes[g].toInt(),
                )
            },
        )
    }
}
