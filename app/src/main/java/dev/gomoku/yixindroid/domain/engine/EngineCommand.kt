package dev.gomoku.yixindroid.domain.engine

import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.Placement

/**
 * piskvork / Gomocup commands plus the Rapfi extensions the desktop uses.
 * [serialize] returns the full command text; multi-line commands (BOARD) embed
 * '\n' and the transport writes each physical line.
 */
sealed interface EngineCommand {
    fun serialize(coord: CoordMapper): String

    data class Start(val size: Int = Move.DEFAULT_SIZE) : EngineCommand {
        override fun serialize(coord: CoordMapper) = "START $size"
    }

    data object Begin : EngineCommand {
        override fun serialize(coord: CoordMapper) = "BEGIN"
    }

    data object Restart : EngineCommand {
        override fun serialize(coord: CoordMapper) = "RESTART"
    }

    data class Turn(val move: Move) : EngineCommand {
        override fun serialize(coord: CoordMapper) = "TURN ${coord.toWire(move)}"
    }

    data class Board(val stones: List<Placement>) : EngineCommand {
        override fun serialize(coord: CoordMapper) = boardBlock("BOARD", stones, coord)
    }

    /** Rapfi: set an analysis board without committing a move. */
    data class YxBoard(val stones: List<Placement>) : EngineCommand {
        override fun serialize(coord: CoordMapper) = boardBlock("YXBOARD", stones, coord)
    }

    /** Rapfi multi-PV: report the N best moves. */
    data class YxNbest(val n: Int) : EngineCommand {
        override fun serialize(coord: CoordMapper) = "YXNBEST $n"
    }

    data class Info(val key: String, val value: String) : EngineCommand {
        override fun serialize(coord: CoordMapper) = "INFO $key $value"
    }

    /**
     * Rapfi balance search — the desktop's `balance1` / `balance2` console
     * commands (main.c:10854): `yxbalanceone <bias>` looks for the single move
     * that balances the position, `yxbalancetwo <bias>` for the move *pair*.
     * [bias] is in the engine's value units and defaults to 0 (dead even), like
     * the desktop's `sscanf` fallback.
     */
    data class YxBalance(val two: Boolean, val bias: Int = 0) : EngineCommand {
        override fun serialize(coord: CoordMapper) =
            "yxbalance${if (two) "two" else "one"} $bias"
    }

    /**
     * Switch Rapfi into the **detailed (Yixin) output mode**. Without this the
     * engine only prints human-readable `MESSAGE Depth 2-3 | Eval 814 | …` lines
     * and never the `INFO PV/DEPTH/EVAL/WINRATE/BESTLINE` blocks the analysis UI
     * is built on. The desktop sends exactly this pair in `init_engine()`
     * (main.c:14465) — spelling and case mirrored deliberately.
     */
    data class ShowDetail(val level: Int = 3) : EngineCommand {
        override fun serialize(coord: CoordMapper) = "info show_detail $level"
    }

    data object YxShowInfo : EngineCommand {
        override fun serialize(coord: CoordMapper) = "yxshowinfo"
    }

    /**
     * Push the client's read-only state instead of inheriting the engine's own
     * config. main.c:14467 documents the trap: a server-side `readonly = true`
     * silently discards every search result and DB edit while the UI shows
     * read-only off.
     */
    data class DatabaseReadonly(val on: Boolean) : EngineCommand {
        override fun serialize(coord: CoordMapper) = "info database_readonly ${if (on) 1 else 0}"
    }

    data object YxShowForbid : EngineCommand {
        override fun serialize(coord: CoordMapper) = "YXSHOWFORBID"
    }

    /**
     * The remaining match budget, pushed before every engine turn — the desktop
     * sends this in front of each `BOARD`/`TURN` (main.c:2742).
     */
    data class InfoTimeLeft(val ms: Long) : EngineCommand {
        override fun serialize(coord: CoordMapper) = "INFO time_left ${ms.coerceAtLeast(0)}"
    }

    /** Desktop console `draw` / `resign` (main.c:11212). */
    data object YxDraw : EngineCommand {
        override fun serialize(coord: CoordMapper) = "yxdraw"
    }

    data object YxResign : EngineCommand {
        override fun serialize(coord: CoordMapper) = "yxresign"
    }

    /**
     * Swap2 negotiation steps. Step 1 asks the engine to open with three stones,
     * steps 2 and 3 ask it to judge the position the user just built — the board
     * is pushed separately with `YXBOARD` first, as the desktop does.
     */
    data class YxSwap2Step(val step: Int) : EngineCommand {
        override fun serialize(coord: CoordMapper) = "yxswap2step$step"
    }

    /**
     * Soosorv-8 negotiation steps. Unlike Swap2 these carry the move list
     * themselves: `yxsoosorvstep2` + `y,x` lines + `done` (main.c:2807), and
     * steps 4/5 take the number of fifth moves.
     */
    data class YxSoosorvStep(
        val step: Int,
        val fifthCount: Int? = null,
        val moves: List<Move> = emptyList(),
    ) : EngineCommand {
        override fun serialize(coord: CoordMapper): String = buildString {
            append("yxsoosorvstep")
            append(step)
            if (fifthCount != null) {
                append(' ')
                append(fifthCount)
            }
            if (moves.isNotEmpty()) {
                for (m in moves) {
                    append('\n')
                    append(coord.toWire(m))
                }
                append("\ndone")
            }
        }
    }

    /**
     * Rapfi: search this position for **every defense** instead of the best move
     * (`yxsearchdefend`, main.c:9495). One PV per playable defense comes back, so
     * the prove pipeline reads the whole AND-node fan-out from a single search.
     */
    data object YxSearchDefend : EngineCommand {
        override fun serialize(coord: CoordMapper) = "yxsearchdefend"
    }

    /** Drop the transposition table (settings.txt line 25, "hash autoclear"). */
    data object YxHashClear : EngineCommand {
        override fun serialize(coord: CoordMapper) = "yxhashclear"
    }

    data object TakeBack : EngineCommand {
        override fun serialize(coord: CoordMapper) = "TAKEBACK"
    }

    data object YxStop : EngineCommand {
        override fun serialize(coord: CoordMapper) = "YXSTOP"
    }

    data object About : EngineCommand {
        override fun serialize(coord: CoordMapper) = "ABOUT"
    }

    data object End : EngineCommand {
        override fun serialize(coord: CoordMapper) = "END"
    }

    /** Whatever the user types into the debug console. */
    data class Raw(val line: String) : EngineCommand {
        override fun serialize(coord: CoordMapper) = line
    }

    companion object {
        private fun boardBlock(
            head: String,
            stones: List<Placement>,
            coord: CoordMapper,
        ): String = buildString {
            append(head)
            for (p in stones) {
                append('\n')
                append(coord.toWire(p.move))
                append(if (p.own) ",1" else ",2")
            }
            append("\nDONE")
        }
    }
}
