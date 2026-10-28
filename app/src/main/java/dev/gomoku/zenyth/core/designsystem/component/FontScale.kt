package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Lets the system font scale grow [content] only up to [max] (M7).
 *
 * For the few places whose size is fixed by what they sit in, not by their
 * text: the board sheet's 64dp summary line (its height is the sheet's peek)
 * and the eval bar's number (the bar is a few dp wide). At the largest font
 * (2.0) they clipped — «63» became «6». Everything they say is also in the
 * cards below, which keep the full scale.
 */
@Composable
fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= max) {
        content()
        return
    }
    CompositionLocalProvider(LocalDensity provides Density(density.density, max), content = content)
}
