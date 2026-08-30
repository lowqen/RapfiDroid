package dev.gomoku.rapfidroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.LocalEngineProfile
import org.junit.Test
import java.io.File

/**
 * Runs against the **file that actually ships** (`assets/engine/config.toml`, a
 * verbatim copy of the server's), not a fixture. The failure this guards is
 * precisely a shipped config the renderer no longer matches: the engine would
 * then start with the server's 8 GiB table and be killed before it ever
 * answered, and the only symptom on screen is "the local engine won't connect".
 *
 * Working directory is the module dir, as in `RealPackSmokeTest`.
 */
class MobileEngineConfigTest {

    private val shipped = File("src/main/assets/engine/config.toml")

    private fun template(): String {
        assertThat(shipped.exists()).isTrue()
        return shipped.readText()
    }

    @Test
    fun `brings the server config down to phone size`() {
        val out = MobileEngineConfig.render(template(), LocalEngineProfile())

        // 128 MB, expressed the way Rapfi reads it (KiB).
        assertThat(out).contains("default_tt_size_kb = 131072")
        assertThat(out).contains("default_thread_num = 3")
        // The server's 8 GiB must be gone, not merely overridden later: this
        // value is applied when the config is committed, before the app has
        // said a word.
        assertThat(out).doesNotContain("8388608")
    }

    @Test
    fun `changes those two lines and nothing else`() {
        val before = template().lines()
        val after = MobileEngineConfig.render(template(), LocalEngineProfile()).lines()

        assertThat(after).hasSize(before.size)
        val changed = before.indices.filter { before[it] != after[it] }
        assertThat(changed).hasSize(2)
        assertThat(changed.map { before[it].trim().substringBefore(' ') })
            .containsExactly("default_tt_size_kb", "default_thread_num")
    }

    @Test
    fun `profile numbers reach the file`() {
        val profile = LocalEngineProfile(threadNum = 2, hashSizeMb = 64)
        val out = MobileEngineConfig.render(template(), profile)

        assertThat(out).contains("default_tt_size_kb = 65536")
        assertThat(out).contains("default_thread_num = 2")
    }

    @Test
    fun `out-of-range profile values are clamped, not written raw`() {
        // What the desktop settings would hand over if nothing clamped them.
        val profile = LocalEngineProfile(threadNum = 64, hashSizeMb = 8192)
        val out = MobileEngineConfig.render(template(), profile)

        assertThat(out).contains("default_thread_num = ${LocalEngineProfile.MAX_THREADS}")
        assertThat(out).contains(
            "default_tt_size_kb = ${LocalEngineProfile.MAX_HASH_MB.toLong() shl 10}",
        )
    }

    @Test
    fun `refuses a template that lost a key`() {
        val stripped = template().lines()
            .filterNot { it.trimStart().startsWith("default_tt_size_kb") }
            .joinToString("\n")

        val error = runCatching { MobileEngineConfig.render(stripped, LocalEngineProfile()) }
            .exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error!!.message).contains("default_tt_size_kb")
    }

    @Test
    fun `refuses a flipped coordinate mode`() {
        // The app reads engine coordinates as y,x with no flip. A config that
        // converts would put every move and every PV on the transposed square,
        // which is the kind of bug that looks like an engine fault for days.
        val flipped = template().replace(
            "coord_conversion_mode = \"none\"",
            "coord_conversion_mode = \"flipY_X\"",
        )

        val error = runCatching { MobileEngineConfig.render(flipped, LocalEngineProfile()) }
            .exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error!!.message).contains("coord_conversion_mode")
    }
}
