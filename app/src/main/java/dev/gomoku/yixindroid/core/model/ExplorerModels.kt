package dev.gomoku.yixindroid.core.model

/**
 * Models for the opening explorer — the app side of the desktop's `rjexp_*`
 * window (main.c:4968-5724), fed by the RenjuNet statistics packs.
 *
 * ⚠ The packs are RenjuNet-derived: **user-imported, never bundled, never
 * exported** (rifdb/README.md). Nothing here reaches the network.
 */

/** One next-move row of a position record, in the key's canonical frame. */
data class RjNext(
    val x: Int,
    val y: Int,
    val games: Int,
    val blackWins: Int,
    val draws: Int,
    val whiteWins: Int,
)

/** A game record of `renju_games.pack`. */
data class RjGame(
    val id: Int,
    val year: Int,
    /** 0 = black won, 1 = draw, 2 = white won. */
    val result: Int,
    val rule: Int,
    val opening: Int,
    val rated: Boolean,
    val black: String,
    val white: String,
    val tournament: String,
    val round: String,
    val swap: String,
    val alt: String,
    val info: String,
    val blackCountry: String,
    val whiteCountry: String,
    val tourStart: String,
    val tourEnd: String,
    val tourCountry: String,
    /** Move cells as `y * 15 + x`, the key generator's frame. */
    val cells: List<Int>,
) {
    val resultText: String get() = when (result) {
        0 -> "1-0"
        1 -> "½-½"
        else -> "0-1"
    }

    fun moves(size: Int = Move.DEFAULT_SIZE): List<Move> =
        cells.map { Move(it % PACK_SIZE, it / PACK_SIZE) }.filter { it.isInside(size) }

    companion object {
        /** The packs are 15×15 only — `rif_pack.py` stores `y * 15 + x`. */
        const val PACK_SIZE = 15
    }
}

/**
 * A next-move candidate: what the games say, what the table says, or both.
 *
 * Statistics and theory answer different questions and neither contains the
 * other — 678 graded shapes never reach the packs' 2-game floor, and most
 * played moves have no grade — so a row exists when *either* source knows the
 * move, with [games] at 0 where only the table does.
 */
data class ExplorerNext(
    val move: Move,
    val games: Int,
    val blackWins: Int,
    val draws: Int,
    val whiteWins: Int,
    /**
     * The opening name this move would *make* ([OpeningName]), or null when it
     * makes none. Browsing a 3-move position is therefore browsing the 4th-move
     * names in the order they are actually played.
     */
    val name: String? = null,
    /** The 흑 5수 유불리 grade this move would reach, or null. */
    val grade: OpeningEval.Grade? = null,
) {
    val decided: Int get() = blackWins + draws + whiteWins

    /** Black's score rate, (승 + 무/2) / 결정판 — the same definition the
     *  frequency dashboard uses, so the two screens cannot contradict. */
    val blackScore: Double?
        get() = if (decided > 0) 100.0 * (blackWins + 0.5 * draws) / decided else null
}

/** Why the explorer has nothing to show, or that it does. */
enum class ExplorerStatus {
    /** No packs imported yet — the how-to notice. */
    NO_PACKS,

    /** The packs only cover 15×15. */
    WRONG_SIZE,

    /** Covered range is 20 plies / 2+ games; outside it "no statistics" is normal. */
    NO_STATS,

    OK,
}

/** Header numbers of the loaded packs. */
data class PackInfo(
    val totalGames: Int,
    val maxPlies: Int,
    val minGames: Int,
    /** Build date as `YYYYMMDD`. */
    val date: Int,
    val positions: Int,
    val gameRecords: Int,
) {
    val dateText: String
        get() = if (date < 10000101) "?" else
            "${date / 10000}-%02d-%02d".format((date / 100) % 100, date % 100)
}

/** Everything the explorer shows for one position. */
data class ExplorerPosition(
    val key: String,
    /** "H8, I9, F6" — the current line in played order. */
    val line: String,
    val games: Int,
    val blackWins: Int,
    val draws: Int,
    val whiteWins: Int,
    val next: List<ExplorerNext>,
    val gameCount: Int,
    /** Games through the position one move back, for "how often was this
     *  reached". 0 when unknown (start position, or outside the packs). */
    val parentGames: Int = 0,
    /** This position's own 흑 5수 유불리 grade, or null. */
    val grade: OpeningEval.Grade? = null,
) {
    /** Share of the games that reached here — the RESULT split. Not the same
     *  question as [shareOfParent], which is how often it was reached at all. */
    fun percent(part: Int): Double = if (games > 0) 100.0 * part / games else 0.0

    val shareOfParent: Double?
        get() = if (parentGames > 0 && games > 0) 100.0 * games / parentGames else null

    fun shareOfAll(total: Int): Double? =
        if (total > 0 && games > 0) 100.0 * games / total else null
}

/** One row of the games pane. */
data class ExplorerGameRow(
    val id: Int,
    val year: Int,
    val black: String,
    val white: String,
    val result: String,
    val tournament: String,
)

/**
 * A page of the games pane. Display is capped so the empty-board list (every
 * game in the database) stays snappy; [matched] stays exact, like the desktop.
 */
data class ExplorerGames(
    val rows: List<ExplorerGameRow> = emptyList(),
    val matched: Int = 0,
) {
    val shown: Int get() = rows.size

    companion object {
        /** `RJEXP_MAXROWS`. */
        const val MAX_ROWS = 1000
    }
}
