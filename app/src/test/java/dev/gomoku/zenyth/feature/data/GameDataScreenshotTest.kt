package dev.gomoku.zenyth.feature.data

import android.app.Application
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
import dev.gomoku.zenyth.core.designsystem.component.LocalWaveFrames
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.model.PackBuildState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game-data prompt of the research tabs (docs/보드-연구-개선-계획.md, R2):
 * the card waiting, building and done, and the banner it folds into — light
 * and dark, at 1.0 and 1.3, like the other screenshot tests.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ko-rKR-w420dp-h900dp-xhdpi")
class GameDataScreenshotTest(private val variant: Variant) {

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

    @Test
    fun cardWaiting() = shot("data_card") { card(PackBuildState.Idle) }

    @Test
    fun cardBuilding() = shot("data_card_building") {
        card(PackBuildState.Running(PackBuildState.Phase.Aggregating, 62, 100))
    }

    @Test
    fun cardDone() = shot("data_card_done") { card(PackBuildState.Done(games = 151_204, positions = 2_400_000, skipped = 0)) }

    @Test
    fun banner() = shot("data_banner") { GameDataBanner(onExpand = {}) }

    @Composable
    private fun card(build: PackBuildState) = GameDataCard(
        build = build,
        onPick = {},
        onOpenSite = {},
        onLater = {},
        onCancel = {},
        onAcknowledge = {},
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/${name}_${variant.tag}.png") {
            ZenythTheme(darkTheme = variant.dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, variant.fontScale),
                    LocalWaveFrames provides false,
                ) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        androidx.compose.foundation.layout.Box(Modifier.width(411.dp).padding(12.dp)) { content() }
                    }
                }
            }
        }
    }
}
