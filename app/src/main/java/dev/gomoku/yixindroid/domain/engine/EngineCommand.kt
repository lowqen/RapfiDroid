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

    data object YxShowForbid : EngineCommand {
        override fun serialize(coord: CoordMapper) = "YXSHOWFORBID"
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
