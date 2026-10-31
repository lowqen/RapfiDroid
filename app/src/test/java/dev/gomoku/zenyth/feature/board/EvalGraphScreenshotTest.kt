package dev.gomoku.zenyth.feature.board

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The evaluation graph (docs/보드-연구-개선-계획.md, R3), as the board sheet
 * draws it — in the board's wood colours — light and dark, at 1.0 and 1.3.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ko-rKR-w420dp-h900dp-xhdpi")
class EvalGraphScreenshotTest(private val variant: Variant) {

    data class Variant(val dark: Boolean, val fontScale: Float) {
        val tag: String get() = (if (dark) "dark" else "light") + "_" + (fontScale * 100).toInt()
        override fun toString(): String = tag
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = listOf(
            Variant(dark = false, fontScale = 1f),
            Variant(dark = true, fontScale = 1f),
            Variant(dark = false, fontScale = 1.3f),
            Variant(dark = true, fontScale = 1.3f),
        ).map { arrayOf(it) }
    }

    /** Black edging ahead over a 30-move game, the last move on the board. */
    @Test
    fun blackAhead() = shot("eval_graph_black") {
        val history = List(31) { i -> 0.5 + 0.012 * i + if (i % 2 == 0) 0.02 else -0.01 }
        EvalGraphCard(history, currentPly = 30, lineLength = 30, onJump = {})
    }

    /**
     * A game that turns: Black ahead, one move throws it to White (the swing
     * point), a stretch never read (the dashed thread), the cursor mid-game
     * with moves still to redo after it.
     */
    @Test
    fun reversal() = shot("eval_graph_reversal") {
        val history: List<Double?> = listOf(
            0.5, 0.55, 0.58, 0.62, 0.66, 0.64, 0.70, 0.72, 0.40, 0.36,
            0.33, null, null, null, 0.30, 0.26, 0.22, 0.25, 0.18, 0.10,
        )
        EvalGraphCard(history, currentPly = 9, lineLength = 24, onJump = {})
    }

    /** Two moves read: the plot still spans the line, the header has no change yet. */
    @Test
    fun opening() = shot("eval_graph_opening") {
        EvalGraphCard(listOf(0.5, 0.53), currentPly = 1, lineLength = 6, onJump = {})
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/${name}_${variant.tag}.png") {
            ZenythTheme(darkTheme = variant.dark) {
                WoodTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, variant.fontScale)) {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.width(411.dp).padding(12.dp)) { content() }
                        }
                    }
                }
            }
        }
    }
}
