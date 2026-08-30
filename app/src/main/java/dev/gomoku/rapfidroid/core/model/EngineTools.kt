package dev.gomoku.rapfidroid.core.model

/**
 * Client-side state of the engine tools — the desktop's loose globals
 * (`boardblock`, `pos_stack`, `commandmode`, `callback_*`) gathered into one
 * value.
 *
 * The three auto flags (`hashautoclear`, `blockautoreset`, `blockpathautoreset`)
 * are **not** here: they live in `settings.txt` lines 25/32/33 and are already
 * modelled by `AppSettings`, so the console commands that toggle them write
 * there instead of keeping a second copy that could drift.
 */
data class ToolsState(
    /** Raw passthrough: every console line goes to the engine unparsed. */
    val commandMode: Boolean = false,

    /** Points the engine has been told to ignore, drawn on the board. */
    val blocked: Set<Move> = emptySet(),

    /** Ten position slots (`pushpos` / `poppos`), null when empty. */
    val stack: List<String?> = List(STACK_SLOTS) { null },

    val callbacks: CallbackConfig = CallbackConfig(),

    /** `callback off` — suspended without losing the configuration. */
    val callbacksSuspended: Boolean = false,
) {
    val callbacksActive: Boolean get() = callbacks.enabled && !callbacksSuspended

    companion object {
        /** `pos_stack[10]` (main.c:11425 rejects anything outside 0..9). */
        const val STACK_SLOTS = 10
    }
}

/**
 * Scripts the desktop runs on its own when the engine reports something
 * (main.c:13795-13982). Each is a console script in the same language the user
 * types, so a callback can do anything a toolbar button can.
 */
data class CallbackConfig(
    val enabled: Boolean = false,

    /** Consecutive 50 % evaluations that count as a draw (`callback_draw_count`). */
    val drawCount: Int = 10,

    /** Below this ply, [onMoveMinPly] runs instead of [onMove]. */
    val minPly: Int = 1,

    /** At or above this ply, [onMoveMaxPly] runs instead. */
    val maxPly: Int = MAX_PLY,

    /** The side to move has a forced win. */
    val onMate: String = "",

    /** The side to move is being mated. */
    val onMated: String = "",

    /** [drawCount] evaluations in a row came back at 50 %. */
    val onDraw: String = "",

    val onMove: String = "",
    val onMoveMinPly: String = "",
    val onMoveMaxPly: String = "",
) {
    /** Which script a move at [ply] stones triggers (main.c:13976-13983). */
    fun moveScript(ply: Int): String = when {
        ply <= minPly -> onMoveMinPly
        ply >= maxPly -> onMoveMaxPly
        else -> onMove
    }

    companion object {
        /** `MAX_SIZE * MAX_SIZE` on the desktop. */
        const val MAX_PLY = 225
    }
}

/** What a console line produced, for the log. */
data class ToolsOutcome(val text: String, val isError: Boolean = false)
