package dev.gomoku.rapfidroid.data.explorer

import dev.gomoku.rapfidroid.core.model.ExplorerGameRow
import dev.gomoku.rapfidroid.core.model.ExplorerGames
import dev.gomoku.rapfidroid.core.model.ExplorerNext
import dev.gomoku.rapfidroid.core.model.ExplorerPosition
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.Opening26
import dev.gomoku.rapfidroid.core.model.OpeningEval
import dev.gomoku.rapfidroid.core.model.OpeningName
import dev.gomoku.rapfidroid.core.model.OpeningTables
import dev.gomoku.rapfidroid.core.model.PosKey
import dev.gomoku.rapfidroid.core.model.Position
import dev.gomoku.rapfidroid.core.model.RjGame

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
        val parent = if (pos.moves.isEmpty()) null else
            stats.lookup(PosKey.of(pos.moves.dropLast(1), pos.size).key)
        return Result(
            ExplorerPosition(
                key = id.key,
                line = lineText(pos),
                games = stat.games,
                blackWins = stat.blackWins,
                draws = stat.draws,
                whiteWins = stat.whiteWins,
                next = nextRows(pos, stat.nextMoves().map { n ->
                    ExplorerNext(
                        move = id.toBoard(Move(n.x, n.y), pos.size),
                        games = n.games,
                        blackWins = n.blackWins,
                        draws = n.draws,
                        whiteWins = n.whiteWins,
                    )
                }),
                gameCount = stat.gameCount,
                parentGames = parent?.games ?: 0,
                grade = OpeningName.gradeAt(pos.moves, pos.size),
            ),
            stat,
        )
    }

    /**
     * The next-move table: what was played UNION what is graded.
     *
     * Grades are a fact about the position, so every empty point is asked —
     * 678 of the shipped shapes never reach the packs' two-game floor and would
     * otherwise have nowhere to appear. 225 key builds is nothing next to the
     * pack lookup that precedes it.
     *
     * Order: most played first, then theory best-for-black first. Both halves
     * read "what matters most" downwards.
     */
    fun nextRows(pos: Position, played: List<ExplorerNext>): List<ExplorerNext> {
        val ply = pos.moves.size + 1
        val rows = ArrayList(played.map {
            it.copy(
                name = OpeningName.nameAt(ply, pos.moves + it.move, pos.size),
                grade = OpeningName.gradeAt(pos.moves + it.move, pos.size),
            )
        })
        if (ply in OpeningEval.PLIES && OpeningTables.evals.isNotEmpty()) {
            val taken = HashSet(pos.moves)
            val listed = played.mapTo(HashSet()) { it.move }
            for (y in 0 until pos.size) for (x in 0 until pos.size) {
                val m = Move(x, y)
                if (m in taken || m in listed) continue
                val g = OpeningName.gradeAt(pos.moves + m, pos.size) ?: continue
                rows.add(
                    ExplorerNext(
                        move = m, games = 0, blackWins = 0, draws = 0, whiteWins = 0,
                        name = OpeningName.nameAt(ply, pos.moves + m, pos.size),
                        grade = g,
                    )
                )
            }
        }
        rows.sortWith(
            compareByDescending<ExplorerNext> { it.games }
                .thenByDescending { it.grade?.code ?: Int.MIN_VALUE }
                .thenBy { it.move.y * pos.size + it.move.x }
        )
        return rows
    }

    /** Rows for a position the packs have never seen: theory only. Called
     *  where the desktop calls `rjexp_next_fill(NULL)` — a grade is just as
     *  true with no games behind it. */
    fun theoryOnly(pos: Position): ExplorerPosition? {
        if (pos.size != RjGame.PACK_SIZE) return null
        val rows = nextRows(pos, emptyList())
        val grade = OpeningName.gradeAt(pos.moves, pos.size)
        if (rows.isEmpty() && grade == null) return null
        return ExplorerPosition(
            key = PosKey.of(pos.moves, pos.size).key,
            line = lineText(pos),
            games = 0, blackWins = 0, draws = 0, whiteWins = 0,
            next = rows, gameCount = 0, parentGames = 0, grade = grade,
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

    /* The position used to be labelled with whatever its most prominent game
       was filed under. It is now labelled with its own computed name chain
       ([OpeningName]), which needs no games at all — so the RIF filing stays
       where it is a fact about a game, in the per-game detail below. */

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
