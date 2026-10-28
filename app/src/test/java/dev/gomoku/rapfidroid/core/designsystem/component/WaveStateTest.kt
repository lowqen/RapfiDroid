package dev.gomoku.rapfidroid.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The wave's head between engine reports (2026-10-29, «뚝뚝 끊김»): it runs on
 * by itself, absorbs a late or early report without jumping or stepping back,
 * and stays a straight line until [WaveFrom].
 */
class WaveStateTest {

    private val frame = 1f / 60

    /** An 8-second analysis, reported twice a second with the engine's own time. */
    @Test
    fun theHeadMovesEveryFrameAndNeverJumps() {
        val rate = 1f / 8
        val wave = WaveState(0f)
        var anchor = 0f
        var since = 0f
        var previous = 0f
        for (i in 1..(60 * 6)) {
            val t = i * frame
            if (i % 30 == 0) {
                // Reports arrive a little off the clock, as real ones do.
                anchor = (t + if (i % 60 == 0) -0.15f else 0.12f) * rate
                since = 0f
            }
            since += frame
            wave.advance(anchor + rate * since, 1f, frame, rate, reduced = false)
            val step = wave.head - previous
            assertThat(step).isAtLeast(0f)
            assertThat(step).isAtMost(rate * frame * 3.01f)
            assertThat(step).isGreaterThan(0f)
            previous = wave.head
        }
        assertThat(wave.head).isWithin(0.03f).of(6f * rate)
    }

    @Test
    fun aStraightLineUntilATenthThenAWave() {
        val rate = 1f / 10
        val wave = WaveState(0f)
        var t = 0f
        while (wave.head < WaveFrom - 0.005f) {
            t += frame
            wave.advance(t * rate, 1f, frame, rate, reduced = false)
            assertThat(wave.swell).isEqualTo(0f)
        }
        repeat(120) {
            t += frame
            wave.advance(t * rate, 1f, frame, rate, reduced = false)
        }
        assertThat(wave.head).isGreaterThan(WaveFrom)
        assertThat(wave.swell).isGreaterThan(0.95f)
    }

    /** A review waits at the next position's start rather than running into it. */
    @Test
    fun theCeilingHolds() {
        val wave = WaveState(0.2f)
        repeat(600) { wave.advance(1f, 0.25f, frame, 0.1f, reduced = false) }
        assertThat(wave.head).isWithin(1e-6f).of(0.25f)
    }

    /** Without a pace (a review by depth) it eases to each report instead. */
    @Test
    fun withoutAPaceItEasesToTheReport() {
        val wave = WaveState(0.2f)
        var previous = wave.head
        repeat(120) {
            wave.advance(0.4f, 1f, frame, 0f, reduced = false)
            assertThat(wave.head - previous).isAtMost(0.2f * frame / 0.25f + 1e-4f)
            previous = wave.head
        }
        assertThat(wave.head).isWithin(0.001f).of(0.4f)
    }

    @Test
    fun reducedMotionFollowsTheClockAndStillsTheWave() {
        val wave = WaveState(0.5f)
        wave.advance(0.6f, 1f, frame, 0.1f, reduced = true)
        assertThat(wave.head).isEqualTo(0.6f)
        assertThat(wave.phase).isEqualTo(0f)
    }
}
