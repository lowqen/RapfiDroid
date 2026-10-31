package dev.gomoku.zenyth.feature.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The first-run setup, photographed (docs/온보딩-계획.md, O2): each screen at a
 * Galaxy A16's size, light and dark, at the default font scale and at 1.3 —
 * the same variants and the same rules as `DesignSystemScreenshotTest`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ko-rKR-w420dp-h900dp-xhdpi")
class OnboardingScreenshotTest(private val variant: Variant) {

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

    private val steps = listOf(SetupStep.Welcome, SetupStep.Notifications)

    /** 1/2: the mark, the welcome, three things the app does; the language pill up top. */
    @Test
    fun welcome() = screen("setup_welcome") { setup(index = 0) }

    /** 2/2 waiting for an answer: the two buttons, «시작하기» greyed. */
    @Test
    fun notificationsAsk() = screen("setup_notifications") { setup(index = 1) }

    /** 2/2 granted: the card in place of the buttons, «시작하기» live. */
    @Test
    fun notificationsOn() = screen("setup_notifications_on") { setup(index = 1, on = true) }

    /** Refused twice: the button goes to the system settings. */
    @Test
    fun notificationsBlocked() = screen("setup_notifications_blocked") { setup(index = 1, blocked = true) }

    /** The language sheet's body: «기기 설정» chosen. */
    @Test
    fun languageList() = screen("setup_language", height = null) {
        Column(Modifier.width(420.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LanguageList(current = "", onChoose = {})
        }
    }

    /** The tour's four pages (O3): rows, the two pictured parts, dots, the pill. */
    @Test
    fun tourBoard() = screen("tour_board") { tour(TourPage.Board) }

    @Test
    fun tourResearch() = screen("tour_research") { tour(TourPage.Research) }

    @Test
    fun tourJoseki() = screen("tour_joseki") { tour(TourPage.Joseki) }

    /** The last page: no «건너뛰기», «완료» in the pill. */
    @Test
    fun tourEngine() = screen("tour_engine") { tour(TourPage.Engine) }

    @Composable
    private fun tour(page: TourPage) {
        val pages = TourPage.entries
        TourContent(
            pages = pages,
            pager = rememberPagerState(initialPage = page.ordinal) { pages.size },
            onBack = {},
            onNext = {},
            onSelect = {},
            onSkip = {},
        )
    }

    @Composable
    private fun setup(index: Int, on: Boolean = false, blocked: Boolean = false) = SetupContent(
        steps = steps,
        index = index,
        languageLabel = "한국어",
        notificationsOn = on,
        notificationsBlocked = blocked,
        onLanguage = {},
        onBack = {},
        onNext = {},
        onAskNotifications = {},
        onSkipNotifications = {},
    )

    /** A phone screen under the status bar: 411 × 860dp, or the content's own height. */
    private fun screen(name: String, height: Int? = 860, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/${name}_${variant.tag}.png") {
            ZenythTheme(darkTheme = variant.dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, variant.fontScale),
                    LocalWaveFrames provides false,
                ) {
                    val size = if (height != null) Modifier.size(411.dp, height.dp) else Modifier
                    Surface(size, color = MaterialTheme.colorScheme.surface) { content() }
                }
            }
        }
    }
}
