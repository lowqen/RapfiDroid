package dev.gomoku.rapfidroid.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The page's and the cards' light: a gradient you should notice only when it
 * is gone (user request, 2026-10-29 — Mornye's surfaces have depth, flat fills
 * read as flat).
 *
 * Every stop is a mix of two of the scheme's own neighbouring tones, so the
 * whole thing moves a couple of L* steps at most and every text pair the theme
 * test holds at AA still clears it at both ends (`ThemeContrastTest`):
 *
 *  - **page** — a shade brighter at the top than at the foot, as if lit from
 *    above. AMOLED stays pure black top to bottom: a lit pixel is the cost it
 *    exists to avoid.
 *  - **card** — the face a touch lighter at the top, and a **rim** that is
 *    lighter along the top edge than the bottom, the way a raised edge catches
 *    light.
 *
 * Never inside the board: the wood, the grid and the stones are drawn on their
 * own canvas and keep their flat, readable fills.
 */
@Immutable
data class SurfaceStops(
    val pageTop: Color,
    val pageBottom: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val rimTop: Color,
    val rimBottom: Color,
) {
    val page: Brush get() = Brush.verticalGradient(listOf(pageTop, pageBottom))
    val card: Brush get() = Brush.verticalGradient(listOf(cardTop, cardBottom))
    val rim: Brush get() = Brush.verticalGradient(listOf(rimTop, rimBottom))
}

internal fun surfaceStops(s: ColorScheme): SurfaceStops {
    val dark = s.surface.luminance() < 0.5f
    if (!dark) {
        return SurfaceStops(
            pageTop = s.surfaceBright,
            pageBottom = mixColors(s.surface, s.surfaceContainerLow, 0.6f),
            cardTop = s.surfaceContainerLowest,
            cardBottom = mixColors(s.surfaceContainerLowest, s.surfaceContainerLow, 0.45f),
            rimTop = mixColors(s.outlineVariant, s.surfaceContainerLowest, 0.45f),
            rimBottom = s.outlineVariant,
        )
    }
    val amoled = s.surface.luminance() < 0.0005f
    return SurfaceStops(
        pageTop = if (amoled) s.surface else mixColors(s.surface, s.surfaceContainerLow, 0.8f),
        pageBottom = if (amoled) s.surface else mixColors(s.surface, s.surfaceDim, 0.6f),
        cardTop = mixColors(s.surfaceContainer, s.surfaceContainerHigh, 0.55f),
        cardBottom = s.surfaceContainer,
        rimTop = mixColors(s.outlineVariant, s.onSurface, 0.18f),
        rimBottom = s.outlineVariant,
    )
}

/** Provided by `RapfiDroidTheme`; read as `RapfiTheme.surfaces`. */
val LocalSurfaces = staticCompositionLocalOf { surfaceStops(colorSchemeFor(dark = false, amoled = false, chromeHue = null)) }
