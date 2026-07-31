package dev.gomoku.yixindroid.data.explorer

import dev.gomoku.yixindroid.core.model.ExplorerGameRow
import dev.gomoku.yixindroid.core.model.ExplorerGames
import dev.gomoku.yixindroid.core.model.ExplorerNext
import dev.gomoku.yixindroid.core.model.ExplorerPosition
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Opening26
import dev.gomoku.yixindroid.core.model.PosKey
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.RjGame

/**
 * The pure half of the explorer: position → key → record → rows. Split out of
 * the repository because the risky step has no IO in it at all — the stored
 * next moves live in the key's **canonical frame** and must be mapped back
 * through the inverse symmetry (`web_tf(web_tf_inv(webtf), …)`, main.c:5364).
 * Get that wrong and the statistics are right but drawn on the wrong points,
 * which nothing downstream would notice.
 */
object ExplorerLookup {

    data class Result(val position: ExplorerPosition, val stat: RjStat)

    fun lookup(
        stats: RjStatsPack,
        games: RjGamesPack,
        pos: Position,
    ): Result? {
        if (pos.size != RjGame.PACK_SIZE) return null
        val id = PosKey.of(pos.moves, pos.size)
        val stat = stats.lookup(id.key) ?: return null
        return Result(
            ExplorerPosition(
                key = id.key,
                line = lineText(pos),
                games = stat.games,
                blackWins = stat.blackWins,
                draws = stat.draws,
                whiteWins = stat.whiteWins,
                next = stat.nextMoves().map {
                    ExplorerNext(
                        move = id.toBoard(Move(it.x, it.y), pos.size),
                        games = it.games,
                        blackWins = it.blackWins,
                        draws = it.draws,
                        whiteWins = it.whiteWins,
                    )
                },
                openingLabel = openingLabel(games, stat, pos.moves.size),
                gameCount = stat.gameCount,
            ),
            stat,
        )
    }

    /** "H8, I9, F6" — the current line in played order (`rjexp_pos_line`). */
    fun lineText(pos: Position): String =
        if (pos.moves.isEmpty()) "시작 국면"
        else pos.moves.joinToString(", ") { it.label(pos.size) }

    /**
     * Games through the position, narrowed by a case-insensitive player-name
     * substring (`rj_name_match` — RIF names are latin).
     *
     * Display is capped at [ExplorerGames.MAX_ROWS] while [ExplorerGames.matched]
     * stays exact, like the desktop. With no filter every game matches, so the
     * count comes straight off the record instead of walking 118k of them.
     */
    fun gameList(stat: RjStat, games: RjGamesPack, filter: String): ExplorerGames {
        val needle = filter.trim()
        val rows = ArrayList<ExplorerGameRow>(minOf(stat.gameCount, ExplorerGames.MAX_ROWS))
        if (needle.isEmpty()) {
            for (i in 0 until minOf(stat.gameCount, ExplorerGames.MAX_ROWS)) {
                games.game(stat.gameIdAt(i))?.let { rows.add(it.toRow()) }
            }
            return ExplorerGames(rows, stat.gameCount)
        }
        var matched = 0
        for (i in 0 until stat.gameCount) {
            val g = games.game(stat.gameIdAt(i)) ?: continue
            if (!g.black.contains(needle, true) && !g.white.contains(needle, true)) continue
            matched++
            if (rows.size < ExplorerGames.MAX_ROWS) rows.add(g.toRow())
        }
        return ExplorerGames(rows, matched)
    }

    /**
     * The opening of the position = the one its most prominent game was filed
     * under. Meaningful in the opening phase, harmless later — the desktop
     * shows it only up to move 8 (main.c:5388).
     */
    fun openingLabel(games: RjGamesPack, stat: RjStat, plies: Int): String? {
        if (stat.gameCount <= 0 || plies !in 1..8) return null
        val g = games.game(stat.gameIdAt(0)) ?: return null
        return gameOpeningLabel(games, g.opening) { Opening26.korean[it] }
    }

    /** Label for a game's filed opening: the real 주형 name with the RIF
     *  abbreviation in parentheses (`rj_opening_idx` + `mo_opening26_name`). */
    fun gameOpeningLabel(
        games: RjGamesPack,
        opening: Int,
        name: (Int) -> String = { Opening26.label(it) },
    ): String? {
        val (abbr, rifName) = games.openingName(opening) ?: return null
        val idx = openingIndex(abbr)
        return if (idx != null) "${name(idx)} ($abbr)" else "$abbr ($rifName)"
    }

    /** RIF stores "d4" / "i7"; map to the [Opening26] index (`rj_opening_idx`). */
    fun openingIndex(abbr: String): Int? {
        if (abbr.isEmpty()) return null
        val n = abbr.drop(1).takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        if (n !in 1..13) return null
        return when (abbr[0].lowercaseChar()) {
            'd' -> n - 1
            'i' -> 13 + n - 1
            else -> null
        }
    }

    private fun RjGame.toRow() =
        ExplorerGameRow(id, year, black, white, resultText, tournament)
}
