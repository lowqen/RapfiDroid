package dev.gomoku.yixindroid.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's colour, in one place and derived from six numbers.
 *
 * **먹과 한지.** The chrome — cards, bars, chips, buttons — is warm and almost
 * colourless; the saturated colour on screen belongs to the board: the value
 * chips of an analysis, the wood, the grade marks. That is not a style
 * preference but a working rule, because in this app *colour is a value*: a
 * chip's hue says how good a move is, and a UI that also shouts in colour makes
 * the reader disambiguate the two. So the UI recedes and the board speaks.
 *
 * Every shade below is a [TonalRamp] tone. The ramps:
 *
 * | ramp | hue | what it is |
 * |---|---|---|
 * | [Ink] | 74° | the near-neutral warm charcoal that buttons and emphasis use |
 * | [Neutral] / [NeutralVariant] | 74° | backgrounds, cards, outlines — the paper |
 * | [Sage] | 148° | the desktop's board green, quietened; "good/alive" |
 * | [Clay] | 82° | the desktop's gold; the wood, and "attention, not error" |
 * | [WoodNight] | 82° | the same wood in shadow, for the dark board |
 * | [Signal] | 27° | errors and forbidden points |
 *
 * The two result ramps ([ResultBlue], [ResultGreen]) are separate on purpose:
 * "black won / white won / drawn" is a **third** vocabulary that has to stay
 * legible next to both of the others, and it is shared with the desktop's
 * statistics tables, so it does not follow a brand change.
 *
 * Nothing here is a raw hex value except the seeds. Re-tinting the app means
 * editing a hue.
 */

/** Warm charcoal → the emphasis family. Barely chromatic: it must read as ink. */
val Ink = TonalRamp(hue = 74f, chroma = 0.020f)

/** The paper: backgrounds and surfaces. Warm enough to not read as blue-grey. */
val Neutral = TonalRamp(hue = 74f, chroma = 0.007f)

/** Outlines and the dimmer text — the same paper with a little more body. */
val NeutralVariant = TonalRamp(hue = 74f, chroma = 0.016f)

/** The board green, quietened from the desktop's `#81B64C`. */
val Sage = TonalRamp(hue = 148f, chroma = 0.060f)

/**
 * The board gold's family, used for the wood and for "attention, not error".
 *
 * The hue and chroma are chosen so that tone 76 lands on the desktop's own
 * board (`#DCB35C` → `#DAB673`) and tone 42 on its grid (`#7A5A2B` →
 * `#7B5E22`). The board a Yixin user has looked at for years is not something
 * to redesign by accident; it is reproduced, and only then derived from.
 */
val Clay = TonalRamp(hue = 82f, chroma = 0.095f)

/**
 * The same wood by lamplight — its own ramp because it is a *material* change,
 * not a tone change: at the dark board's lightness the daylight chroma reads as
 * orange plastic rather than as wood in shadow.
 */
val WoodNight = TonalRamp(hue = 82f, chroma = 0.050f)

/** Errors, forbidden points, losses of material — the one loud family. */
val Signal = TonalRamp(hue = 27f, chroma = 0.130f)

/** "Black won" in every statistics table, desktop and app alike. */
val ResultBlue = TonalRamp(hue = 245f, chroma = 0.085f)

/** "White won". Green rather than white, because white on paper is nothing. */
val ResultGreen = TonalRamp(hue = 155f, chroma = 0.085f)

/**
 * The colours Material has no role for.
 *
 * Everything Material *does* have a role for lives in the `ColorScheme` and is
 * reached through `MaterialTheme.colorScheme`; this holds only what the app
 * means and Material does not — the three result colours, two states that are
 * neither "error" nor "primary", and the board's own skin.
 *
 * Reached through `YixinTheme.colors`, never constructed by a screen.
 */
data class YixinColors(
    /** Black won — bars, KPI figures, the win-rate curve. */
    val resultBlack: Color,
    /** White won. */
    val resultWhite: Color,
    /** Drawn. Neutral by design: a draw is not a weak win. */
    val resultDraw: Color,
    /** Text drawn *on* one of the three result colours — a count inside its own
     *  bar. It has to flip with the theme: the light scheme's bars are dark
     *  enough for white, the dark scheme's are light enough for ink. */
    val onResult: Color,
    /** Connected, proven, healthy. */
    val positive: Color,
    /** Reconnecting, stale, "look at this" — short of an error. */
    val caution: Color,
    /** How the board is painted in this theme. */
    val board: BoardSkin,
) {
    companion object {
        val Light = YixinColors(
            resultBlack = ResultBlue.tone(45),
            resultWhite = ResultGreen.tone(40),
            resultDraw = NeutralVariant.tone(43),
            onResult = Neutral.tone(100),
            positive = Sage.tone(38),
            caution = Clay.tone(40),
            board = BoardSkin.Light,
        )

        val Dark = YixinColors(
            resultBlack = ResultBlue.tone(76),
            resultWhite = ResultGreen.tone(74),
            resultDraw = NeutralVariant.tone(74),
            onResult = Neutral.tone(10),
            positive = Sage.tone(78),
            caution = Clay.tone(76),
            board = BoardSkin.Dark,
        )
    }
}
