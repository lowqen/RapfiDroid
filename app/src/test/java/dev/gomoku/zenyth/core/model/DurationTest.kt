package dev.gomoku.zenyth.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DurationTest {

    private val move = Duration.MOVE_TIME
    private val analysis = Duration.ANALYSIS_TIME

    @Test
    fun `bare number takes the field unit`() {
        assertThat(Duration.parse("2.5", move)).isEqualTo(Duration.Result.Ok(2_500))
        assertThat(Duration.parse("2,5", move)).isEqualTo(Duration.Result.Ok(2_500))
    }

    @Test
    fun `unit words are read`() {
        assertThat(Duration.parse("1500ms", move).ms).isEqualTo(1_500)
        assertThat(Duration.parse("2분", move).ms).isEqualTo(120_000)
        assertThat(Duration.parse("2 min", move).ms).isEqualTo(120_000)
        assertThat(Duration.parse("3초", move).ms).isEqualTo(3_000)
        assertThat(Duration.parse("1h", move).ms).isEqualTo(3_600_000)
    }

    @Test
    fun `unlimited only where allowed`() {
        assertThat(Duration.parse("무제한", move)).isEqualTo(Duration.Result.Ok(0))
        assertThat(Duration.parse("0", analysis)).isEqualTo(Duration.Result.Ok(0))
    }

    @Test
    fun `out of range is clamped`() {
        val r = Duration.parse("100ms", move)
        assertThat(r).isInstanceOf(Duration.Result.Clamped::class.java)
        assertThat(r.ms).isEqualTo(500)
        assertThat(Duration.parse("5h", move).ms).isEqualTo(3_600_000)
    }

    @Test
    fun `unreadable input falls back to the default`() {
        listOf("", "abc", "-3", "2x").forEach {
            val r = Duration.parse(it, move)
            assertThat(r).isInstanceOf(Duration.Result.Fallback::class.java)
            assertThat(r.ms).isEqualTo(0)
        }
    }

    @Test
    fun `whole seconds are rounded`() {
        assertThat(Duration.parse("2.4", analysis).ms).isEqualTo(2_000)
    }

    @Test
    fun `format and text`() {
        assertThat(Duration.format(0)).isEqualTo("무제한")
        assertThat(Duration.format(800)).isEqualTo("800ms")
        assertThat(Duration.format(2_500)).isEqualTo("2.5초")
        assertThat(Duration.format(600_000)).isEqualTo("10분")
        assertThat(Duration.text(2_500, Duration.Unit.S)).isEqualTo("2.5")
        assertThat(Duration.text(0, Duration.Unit.S)).isEqualTo("")
    }
}
