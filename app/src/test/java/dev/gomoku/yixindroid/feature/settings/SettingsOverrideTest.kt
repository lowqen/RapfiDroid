package dev.gomoku.yixindroid.feature.settings

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.DesktopSettings
import dev.gomoku.yixindroid.core.model.LocalEngineProfile
import org.junit.Test

/**
 * The settings screen shows 67 desktop values, and while the on-device engine is
 * connected three of them are not what the engine received. A screen that
 * reports 8192 MB to a user whose engine runs on 128 is worse than one that
 * admits the difference — this is the line that admits it.
 */
class SettingsOverrideTest {

    private fun spec(id: String) = DesktopSettings.ALL.first { it.id == id }

    private val local = SettingsUiState(
        localMode = true,
        localProfile = LocalEngineProfile(threadNum = 3, hashSizeMb = 128, useDatabase = true),
    )

    @Test
    fun `server mode overrides nothing`() {
        val server = local.copy(localMode = false)

        assertThat(server.overrideFor(spec("threadNum"))).isNull()
        assertThat(server.overrideFor(spec("hashSizeMb"))).isNull()
        assertThat(server.overrideFor(spec("useDatabase"))).isNull()
    }

    @Test
    fun `on-device mode names the value the engine will actually get`() {
        assertThat(local.overrideFor(spec("threadNum"))).contains("3")
        assertThat(local.overrideFor(spec("hashSizeMb"))).contains("128")
        assertThat(local.overrideFor(spec("useDatabase"))).isNotNull()
    }

    @Test
    fun `the override follows the profile`() {
        val bigger = local.copy(
            localProfile = LocalEngineProfile(threadNum = 6, hashSizeMb = 256),
        )

        assertThat(bigger.overrideFor(spec("threadNum"))).contains("6")
        assertThat(bigger.overrideFor(spec("hashSizeMb"))).contains("256")
    }

    @Test
    fun `an out-of-range profile is reported as clamped, not as typed`() {
        val silly = local.copy(
            localProfile = LocalEngineProfile(threadNum = 99, hashSizeMb = 8192),
        )

        assertThat(silly.overrideFor(spec("threadNum")))
            .contains(LocalEngineProfile.MAX_THREADS.toString())
        assertThat(silly.overrideFor(spec("hashSizeMb")))
            .contains(LocalEngineProfile.MAX_HASH_MB.toString())
    }

    @Test
    fun `everything else is untouched`() {
        // Rule, level, caution factor and the rest reach both engines alike —
        // that is what keeps the phone and the server agreeing on a position.
        for (id in listOf("rule", "level", "style", "multiPv", "pondering")) {
            assertThat(local.overrideFor(spec(id))).isNull()
        }
    }
}
