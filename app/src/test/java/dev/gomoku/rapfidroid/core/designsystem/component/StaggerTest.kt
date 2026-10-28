package dev.gomoku.rapfidroid.core.designsystem.component

import android.app.Application
import android.os.SystemClock
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Who rises in, and who simply appears (UI overhaul P5). */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app that starts the engine watcher.
@Config(sdk = [35], application = Application::class)
class StaggerTest {

    @Test
    fun `the first ten items of a fresh list rise in`() {
        val s = Stagger(active = true, bornAt = SystemClock.uptimeMillis())
        assertThat((0 until Stagger.MaxStaggered).all { s.plays(it) }).isTrue()
        assertThat(s.plays(Stagger.MaxStaggered)).isFalse()
    }

    @Test
    fun `a list seen before, or under reduced motion, does not`() {
        val s = Stagger(active = false, bornAt = SystemClock.uptimeMillis())
        assertThat(s.plays(0)).isFalse()
    }

    @Test
    fun `an item scrolled into view later just appears`() {
        val s = Stagger(active = true, bornAt = SystemClock.uptimeMillis() - Stagger.Window - 1)
        assertThat(s.plays(0)).isFalse()
    }
}
