package dev.gomoku.rapfidroid.core.model

/** The two clocks the desktop shows: the engine's and the player's. */
enum class ClockSide { COMPUTER, HUMAN }

/**
 * The desktop's four timers (`timercomputerturn/match`, `timerhumanturn/match`)
 * plus the two increment credits, ported from `clock_timer_update` and
 * `clock_label_refresh` (main.c:11875 / 11713).
 *
 * Only the custom level (settings.txt line 6 = 1) has real limits; every other
 * level runs the engine on a node budget and the desktop then shows 99:59:59.
 * "Used" is *this* turn / the whole match; "left" is what the match allows minus
 * what has gone, plus the increments earned so far.
 */
data class GameClock(
    val limited: Boolean = false,
    val turnLimitMs: Long = 0,
    val matchLimitMs: Long = 0,
    val incrementMs: Long = 0,
    /** Whose turn is being timed; null while the clock is stopped. */
    val running: ClockSide? = null,
    val computerMatchMs: Long = 0,
    val computerTurnMs: Long = 0,
    val humanMatchMs: Long = 0,
    val humanTurnMs: Long = 0,
    val computerIncrementMs: Long = 0,
    val humanIncrementMs: Long = 0,
    /** Set once the player has been warned, like the desktop's `timeoutflag`. */
    val timedOutNotified: Boolean = false,
) {
    /** Adopt new limits from the settings, keeping what has already been used. */
    fun withLimits(limits: GameClock): GameClock = copy(
        limited = limits.limited,
        turnLimitMs = limits.turnLimitMs,
        matchLimitMs = limits.matchLimitMs,
        incrementMs = limits.incrementMs,
    )

    /**
     * Hand the clock to [side]. The other side's part-finished turn is folded
     * into its match total, exactly as the desktop's tick does when the status
     * changes.
     */
    fun start(side: ClockSide): GameClock = when (side) {
        ClockSide.COMPUTER -> copy(
            running = side,
            humanMatchMs = humanMatchMs + humanTurnMs,
            humanTurnMs = 0,
            computerTurnMs = 0,
        )
        ClockSide.HUMAN -> copy(
            running = side,
            computerMatchMs = computerMatchMs + computerTurnMs,
            computerTurnMs = 0,
            humanTurnMs = 0,
        )
    }

    fun stop(): GameClock = copy(running = null)

    fun tick(deltaMs: Long): GameClock = when (running) {
        ClockSide.COMPUTER -> copy(computerTurnMs = computerTurnMs + deltaMs)
        ClockSide.HUMAN -> copy(humanTurnMs = humanTurnMs + deltaMs)
        null -> this
    }

    /** One completed move earns the increment (`timer*increment += increment`). */
    fun addIncrement(side: ClockSide): GameClock = when (side) {
        ClockSide.COMPUTER -> copy(computerIncrementMs = computerIncrementMs + incrementMs)
        ClockSide.HUMAN -> copy(humanIncrementMs = humanIncrementMs + incrementMs)
    }

    fun usedTurnMs(side: ClockSide): Long =
        if (side == ClockSide.COMPUTER) computerTurnMs else humanTurnMs

    fun usedMatchMs(side: ClockSide): Long =
        if (side == ClockSide.COMPUTER) computerMatchMs + computerTurnMs else humanMatchMs + humanTurnMs

    fun leftMatchMs(side: ClockSide): Long {
        val credit = if (side == ClockSide.COMPUTER) computerIncrementMs else humanIncrementMs
        return (matchLimitMs - usedMatchMs(side) + credit).coerceAtLeast(0)
    }

    fun leftTurnMs(side: ClockSide): Long =
        minOf(turnLimitMs - usedTurnMs(side), leftMatchMs(side)).coerceAtLeast(0)

    /**
     * `INFO time_left` as the desktop computes it before every engine turn:
     * the match budget minus the engine's **completed** turns, plus its credits.
     * The turn in progress is deliberately not subtracted (main.c:2742).
     */
    fun engineTimeLeftMs(): Long =
        (matchLimitMs - computerMatchMs + computerIncrementMs).coerceAtLeast(0)

    /**
     * The desktop warns on the *player's* clock only: the timeout check runs
     * after the human labels are computed (main.c:11866).
     */
    fun playerTimedOut(): Boolean =
        limited && (leftTurnMs(ClockSide.HUMAN) <= 0L || leftMatchMs(ClockSide.HUMAN) <= 0L)

    fun label(side: ClockSide): String =
        if (!limited) "$UNLIMITED / $UNLIMITED"
        else "${format(leftTurnMs(side))} / ${format(leftMatchMs(side))}"

    fun usedLabel(side: ClockSide): String =
        "${format(usedTurnMs(side))} / ${format(usedMatchMs(side))}"

    companion object {
        private const val UNLIMITED = "99:59:59"

        /** `hh:mm:ss`, capped at 99:59:59 like the desktop's labels. */
        fun format(ms: Long): String {
            val safe = ms.coerceAtLeast(0)
            if (safe / 3_600_000 >= 100) return UNLIMITED
            val h = safe / 3_600_000
            val m = safe / 60_000 % 60
            val s = safe / 1_000 % 60
            return "%02d:%02d:%02d".format(h, m, s)
        }

        /** Limits from the user's settings (seconds in the file, ms on the wire). */
        fun fromSettings(settings: AppSettings): GameClock = GameClock(
            limited = settings.level == 1,
            turnLimitMs = settings.timeoutTurnSec.toLong() * 1000,
            matchLimitMs = settings.timeoutMatchSec.toLong() * 1000,
            incrementMs = settings.incrementMs.toLong(),
        )
    }
}
