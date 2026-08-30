package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The clock formulas from `clock_label_refresh` / `clock_timer_update`
 * (main.c:11713 / 11875), including the one the engine actually sees.
 */
class GameClockTest {

    private val limits = GameClock(
        limited = true,
        turnLimitMs = 30_000,
        matchLimitMs = 300_000,
        incrementMs = 5_000,
    )

    @Test
    fun handingOverFoldsTheOtherSidesTurnIntoItsMatch() {
        var clock = limits.start(ClockSide.HUMAN).tick(4_000)
        assertThat(clock.usedTurnMs(ClockSide.HUMAN)).isEqualTo(4_000)

        clock = clock.start(ClockSide.COMPUTER).tick(1_000)
        // The player's 4 s are now part of their match total, and the turn is clear.
        assertThat(clock.usedTurnMs(ClockSide.HUMAN)).isEqualTo(0)
        assertThat(clock.usedMatchMs(ClockSide.HUMAN)).isEqualTo(4_000)
        assertThat(clock.usedTurnMs(ClockSide.COMPUTER)).isEqualTo(1_000)
    }

    @Test
    fun aStoppedClockDoesNotTick() {
        val clock = limits.start(ClockSide.HUMAN).tick(1_000).stop().tick(9_000)
        assertThat(clock.usedTurnMs(ClockSide.HUMAN)).isEqualTo(1_000)
    }

    /** left(match) = limit - used + increments; left(turn) is capped by it. */
    @Test
    fun leftFollowsTheDesktopFormula() {
        val clock = limits.start(ClockSide.HUMAN).tick(10_000).addIncrement(ClockSide.HUMAN)
        assertThat(clock.leftMatchMs(ClockSide.HUMAN)).isEqualTo(300_000 - 10_000 + 5_000)
        assertThat(clock.leftTurnMs(ClockSide.HUMAN)).isEqualTo(30_000 - 10_000)
    }

    @Test
    fun theTurnLeftCanNeverExceedTheMatchLeft() {
        val nearlyOut = limits.copy(matchLimitMs = 12_000).start(ClockSide.HUMAN).tick(1_000)
        assertThat(nearlyOut.leftTurnMs(ClockSide.HUMAN)).isEqualTo(11_000)
    }

    /**
     * `timeoutmatch - timercomputermatch + timercomputerincrement` — the turn in
     * progress is deliberately not subtracted (main.c:2742).
     */
    @Test
    fun theEngineIsToldTheMatchBudgetOnly() {
        val clock = limits
            .start(ClockSide.COMPUTER).tick(7_000)
            .start(ClockSide.HUMAN)              // folds 7 s into the match total
            .addIncrement(ClockSide.COMPUTER)
            .start(ClockSide.COMPUTER).tick(3_000)
        assertThat(clock.engineTimeLeftMs()).isEqualTo(300_000 - 7_000 + 5_000)
    }

    @Test
    fun theWarningFiresOnlyForThePlayerAndOnlyWhenLimited() {
        val out = limits.start(ClockSide.HUMAN).tick(31_000)
        assertThat(out.playerTimedOut()).isTrue()

        val unlimited = out.copy(limited = false)
        assertThat(unlimited.playerTimedOut()).isFalse()

        val engineOverTime = limits.start(ClockSide.COMPUTER).tick(400_000)
        assertThat(engineOverTime.playerTimedOut()).isFalse()
    }

    @Test
    fun labelsUseTheDesktopFormatAndCap() {
        assertThat(GameClock.format(0)).isEqualTo("00:00:00")
        assertThat(GameClock.format(3_661_000)).isEqualTo("01:01:01")
        assertThat(GameClock.format(-5)).isEqualTo("00:00:00")
        assertThat(GameClock.format(100L * 3_600_000)).isEqualTo("99:59:59")
        // Unlimited levels show the desktop's placeholder on both fields.
        assertThat(GameClock().label(ClockSide.HUMAN)).isEqualTo("99:59:59 / 99:59:59")
    }

    @Test
    fun limitsComeFromTheCustomLevelOnly() {
        val custom = GameClock.fromSettings(
            AppSettings(level = 1, timeoutTurnSec = 15, timeoutMatchSec = 600, incrementMs = 2_000),
        )
        assertThat(custom.limited).isTrue()
        assertThat(custom.turnLimitMs).isEqualTo(15_000)
        assertThat(custom.matchLimitMs).isEqualTo(600_000)
        assertThat(custom.incrementMs).isEqualTo(2_000)

        assertThat(GameClock.fromSettings(AppSettings(level = 0)).limited).isFalse()
    }

    @Test
    fun newLimitsKeepWhatWasAlreadyUsed() {
        val used = limits.start(ClockSide.HUMAN).tick(9_000)
        val relimited = used.withLimits(limits.copy(turnLimitMs = 60_000))
        assertThat(relimited.usedTurnMs(ClockSide.HUMAN)).isEqualTo(9_000)
        assertThat(relimited.turnLimitMs).isEqualTo(60_000)
    }
}
