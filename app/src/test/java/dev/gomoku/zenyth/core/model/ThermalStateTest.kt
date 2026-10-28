package dev.gomoku.zenyth.core.model

import com.google.common.truth.Truth.assertThat
import dev.gomoku.zenyth.core.model.ThermalState.Companion.STATUS_CRITICAL
import dev.gomoku.zenyth.core.model.ThermalState.Companion.STATUS_LIGHT
import dev.gomoku.zenyth.core.model.ThermalState.Companion.STATUS_MODERATE
import dev.gomoku.zenyth.core.model.ThermalState.Companion.STATUS_NONE
import dev.gomoku.zenyth.core.model.ThermalState.Companion.STATUS_SEVERE
import org.junit.Test

/**
 * The thermal cap (T1) only ever takes threads away, only from what is sent to the
 * engine, and gives them back the moment the phone reports it has cooled.
 */
class ThermalStateTest {

    @Test
    fun `a cool phone keeps every thread`() {
        assertThat(ThermalState(STATUS_NONE).threadCap(8)).isNull()
        assertThat(ThermalState(STATUS_LIGHT).threadCap(8)).isNull()
    }

    @Test
    fun `moderate halves the threads but keeps the two big cores busy`() {
        assertThat(ThermalState(STATUS_MODERATE).threadCap(8)).isEqualTo(4)
        assertThat(ThermalState(STATUS_MODERATE).threadCap(3)).isEqualTo(2)
        // Nothing to take from a profile already at or below the floor.
        assertThat(ThermalState(STATUS_MODERATE).threadCap(2)).isNull()
        assertThat(ThermalState(STATUS_MODERATE).threadCap(1)).isNull()
    }

    @Test
    fun `severe and above leave one thread`() {
        assertThat(ThermalState(STATUS_SEVERE).threadCap(8)).isEqualTo(1)
        assertThat(ThermalState(STATUS_CRITICAL).threadCap(4)).isEqualTo(1)
        assertThat(ThermalState(STATUS_SEVERE).threadCap(1)).isNull()
    }

    @Test
    fun `battery saver holds two threads whatever the temperature`() {
        assertThat(ThermalState(STATUS_NONE, powerSave = true).threadCap(8)).isEqualTo(2)
        // The stricter of the two wins.
        assertThat(ThermalState(STATUS_MODERATE, powerSave = true).threadCap(8)).isEqualTo(2)
        assertThat(ThermalState(STATUS_SEVERE, powerSave = true).threadCap(8)).isEqualTo(1)
    }

    @Test
    fun `the cap changes only the thread count of the profile`() {
        val stored = LocalEngineProfile(threadNum = 8, hashSizeMb = 256, useDatabase = true)
        val capped = stored.capThreads(ThermalState(STATUS_MODERATE).threadCap(stored.threads))
        assertThat(capped).isEqualTo(stored.copy(threadNum = 4))
        assertThat(stored.capThreads(null)).isSameInstanceAs(stored)
        assertThat(stored.capThreads(12)).isSameInstanceAs(stored)
    }

    @Test
    fun `the capped profile is what reaches the engine`() {
        val stored = LocalEngineProfile(threadNum = 8, hashSizeMb = 256)
        val sent = stored.capThreads(ThermalState(STATUS_SEVERE).threadCap(stored.threads)).clamp(EngineParams())
        assertThat(sent.threadNum).isEqualTo(1)
        assertThat(sent.hashSizeMb).isEqualTo(256)
    }
}
