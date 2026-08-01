package dev.gomoku.yixindroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Against the four font lines of the deployed settings.txt (lines 44-47). */
class FontSpecTest {

    @Test
    fun theBoardTextFontIsAFamilyListAndASize() {
        val spec = FontSpec.parse("SimHei, sans-serif 12;Board Text Font")
        assertThat(spec.families).isEqualTo("SimHei, sans-serif")
        assertThat(spec.pointSize).isEqualTo(12)
        assertThat(spec.monospace).isFalse()
        assertThat(spec.scale).isWithin(0.001f).of(1.2f)
    }

    @Test
    fun aTrailingMonospaceGenericMakesItMonospaced() {
        val spec = FontSpec.parse(
            "Sarasa Mono SC, Cascadia Mono, SimHei, monospace 9;Text Log Font",
        )
        assertThat(spec.pointSize).isEqualTo(9)
        assertThat(spec.monospace).isTrue()
    }

    @Test
    fun aFamilyWithMonoInItsNameCountsAsMonospaced() {
        assertThat(FontSpec.parse("Courier New, monospace 10;Database Comment Font").monospace)
            .isTrue()
    }

    @Test
    fun anEmptyOrBrokenLineFallsBackRatherThanThrowing() {
        assertThat(FontSpec.parse("")).isEqualTo(FontSpec.DEFAULT)
        assertThat(FontSpec.parse(";Board Text Font")).isEqualTo(FontSpec.DEFAULT)
        assertThat(FontSpec.parse("SimHei;Board Text Font").pointSize)
            .isEqualTo(FontSpec.DEFAULT.pointSize)
    }

    /** A size nobody can read, or one that would break the layout, is clamped. */
    @Test
    fun theScaleIsClampedToSomethingUsable() {
        assertThat(FontSpec.parse("sans 1").scale).isWithin(0.001f).of(0.6f)
        assertThat(FontSpec.parse("sans 400").scale).isWithin(0.001f).of(2.4f)
    }
}
