package dev.gomoku.rapfidroid.core.model

import dev.gomoku.rapfidroid.core.i18n.tr

/**
 * Which colours the engine plays — the desktop's `computerside` bitmask
 * (main.c:74, persisted as settings.txt lines 4 and 5).
 */
enum class ComputerSide(val bits: Int, val label: String) {
    NONE(0, tr("사람 대 사람", "Human vs human")),
    BLACK(1, tr("컴퓨터 흑", "Computer Black")),
    WHITE(2, tr("컴퓨터 백", "Computer White")),
    BOTH(3, tr("컴퓨터 양쪽", "Computer both")),
    ;

    fun plays(color: StoneColor): Boolean = when (color) {
        StoneColor.BLACK -> bits and 1 != 0
        StoneColor.WHITE -> bits and 2 != 0
    }

    /** Swapping sides, as the desktop's paired `change_side_menu` calls do. */
    fun swapped(): ComputerSide = when (this) {
        WHITE -> BLACK
        BLACK -> WHITE
        // The desktop's else-branch turns black off and white on, so "both" and
        // "none" also land on WHITE / BLACK respectively (main.c:2365).
        BOTH -> WHITE
        NONE -> WHITE
    }

    companion object {
        fun of(black: Boolean, white: Boolean): ComputerSide =
            entries.first { it.bits == (if (black) 1 else 0) or (if (white) 2 else 0) }

        fun of(bits: Int): ComputerSide = entries.first { it.bits == (bits and 3) }
    }
}

/**
 * The desktop's `specialrule`: an opening negotiation layered on a base rule.
 * Decoded from settings.txt line 3 exactly like `load_setting` (main.c:14070) —
 * note the menu callback `change_rule` maps 3 and 4 the other way round, a
 * desktop inconsistency; the file is what both sides persist, so the file wins.
 */
enum class OpeningProtocol(val label: String) {
    NONE(tr("없음", "None")),
    /** rule 4: the first three moves are entered by hand, near the centre. */
    RIF(tr("RIF 오프닝", "RIF opening")),
    /** rule 3: opponent may take over after move 1. */
    SWAP_FIRST(tr("첫 수 이후 교환", "Swap after the first move")),
    /** rule 5: the full Soosorv-8 negotiation, including N fifth moves. */
    SOOSORV("Soosorv-8"),
    /** rule 6: three stones, swap or add two more. */
    SWAP2(tr("스왑2", "Swap2")),
    ;

    /** Openings where the engine must not be asked to move before move 3. */
    val handEnteredOpening: Boolean get() = this == RIF || this == SOOSORV
}

/** How a game ended. The winner is null for a draw. */
enum class GameEnd(val label: String) {
    FIVE(tr("5목 완성", "five in a row")),
    BOARD_FULL(tr("판이 다 찼습니다", "the board is full")),
    RESIGNED(tr("기권", "resignation")),
    DRAW_AGREED(tr("무승부 합의", "draw agreed")),
    TIMEOUT(tr("시간 초과", "time out")),
}

data class GameResult(val end: GameEnd, val winner: StoneColor?) {
    fun describe(): String = when {
        winner == null -> end.label
        winner == StoneColor.BLACK -> tr("흑 승 · ${end.label}", "Black wins · ${end.label}")
        else -> tr("백 승 · ${end.label}", "White wins · ${end.label}")
    }
}

/** Choices of the desktop's three-button Swap2 dialog (main.c:2347). */
enum class Swap2Choice { STAY_WHITE, SWAP, ADD_TWO }

/**
 * Something the game needs from the user, or wants to tell them. Each entry is
 * one desktop dialog.
 */
sealed interface GamePrompt {
    /** `show_dialog_swap_query` — yes/no, with N shown during Soosorv move 5. */
    data class Swap(val fifthCount: Int? = null) : GamePrompt

    /** `show_dialog_swap_query2` — stay / swap / add two more stones. */
    data object Swap2 : GamePrompt

    /** `show_dialog_move5N` — how many fifth moves to offer. */
    data object FifthCount : GamePrompt

    /** `show_dialog_swap_info` — the engine took the other colour. */
    data object SwapInfo : GamePrompt

    /** `show_dialog_illegal_opening` — the board was reset. */
    data object IllegalOpening : GamePrompt

    /** `show_dialog_forbidden_info` — a renju forbidden point was tapped. */
    data class Forbidden(val cell: Move) : GamePrompt

    /** `show_dialog_timeout` — the clock ran out (settings.txt line 31). */
    data object Timeout : GamePrompt

    data class Info(val text: String) : GamePrompt
}

/** What happened to a tap on the board. */
sealed interface TapResult {
    data object Placed : TapResult
    data object Ignored : TapResult
    data class Rejected(val reason: String) : TapResult
}

/**
 * Everything about the game that is not the stones themselves.
 *
 * [offeringFifth] and [swapDone] are the desktop's `refreshboardflag` and
 * `swap2done`; [needsRestart] is `isneedrestart`, which decides whether the next
 * engine turn is pushed as a whole board or as a single `TURN`.
 */
data class GameState(
    val computerSide: ComputerSide = ComputerSide.NONE,
    val opening: OpeningProtocol = OpeningProtocol.NONE,
    /** A game search is running (the desktop's `isthinking`). */
    val thinking: Boolean = false,
    val result: GameResult? = null,
    val clock: GameClock = GameClock(),
    val prompt: GamePrompt? = null,
    /** Soosorv `move5N`: how many fifth moves are offered. */
    val fifthCount: Int = 1,
    /** Fifth-move candidates are on the board and one must be chosen. */
    val offeringFifth: Boolean = false,
    /** The fifth-move stage is over (the desktop's `refreshboardflag2`). */
    val fifthStageDone: Boolean = false,
    val swapDone: Boolean = false,
    val needsRestart: Boolean = true,
    val log: List<String> = emptyList(),
) {
    val over: Boolean get() = result != null

    /** A game is being played when the engine has a colour or an opening is negotiated. */
    val active: Boolean get() = computerSide != ComputerSide.NONE

    fun engineOwns(color: StoneColor): Boolean = computerSide.plays(color)
}
