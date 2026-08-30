package dev.gomoku.rapfidroid.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * How the board is painted — the one thing on screen allowed to have colour.
 *
 * Kept out of the `ColorScheme` because none of it is a Material role: wood,
 * grid, two stones and four markers are this app's own vocabulary. Kept out of
 * `BoardRender` because a render is built by a view model, which has no theme;
 * the skin is handed to `drawBoard` at paint time instead, so the same position
 * can be drawn light on screen and dark in an export without the model knowing.
 *
 * **Dark mode gets its own wood.** The desktop's gold is right on paper and
 * wrong at night, where it made the board the brightest object on the screen —
 * brighter than the text, and far brighter than the value chips it is supposed
 * to be a background for. The dark skin is the same wood in shadow: same hue
 * family ([Clay]), a third of the lightness.
 */
data class BoardSkin(
    /** The plate itself, and the slightly darker edge it fades to. */
    val wood: Color,
    val woodEdge: Color,
    /** Grid lines and star points. */
    val line: Color,
    /** Coordinate labels — deliberately *not* the grid's colour (they are not
     *  part of the grid, and at the grid's tone they read as another line). */
    val label: Color,
    /** Black stone: lit face → shadowed face → rim. */
    val blackHigh: Color,
    val blackLow: Color,
    val blackRim: Color,
    /** White stone, same three. */
    val whiteHigh: Color,
    val whiteLow: Color,
    val whiteRim: Color,
    /** The shadow a stone casts on the wood. */
    val stoneShadow: Color,
    /** Ring on the stone just played. */
    val lastMove: Color,
    /** Ring on the engine's best move, and the dark halo that keeps it visible
     *  over both wood and a value chip. */
    val best: Color,
    val bestHalo: Color,
    /** Live search candidate (`POS`). */
    val candidate: Color,
    /** Forbidden point (렌주 금수) and the realtime `LOSE` stroke. */
    val forbid: Color,
    /** Points the engine was told to ignore. */
    val blocked: Color,
    /**
     * A ring around each value chip, or null for no ring.
     *
     * **This is the price of the dark board, and it is not optional.** The value
     * ladder (`TagPalette`) runs from luminance 0.03 to 0.165 and was designed
     * to stand off gold wood at 0.54; a dark wood lands *inside* that range, so
     * the chips in the middle of the ladder — where most candidate moves sit —
     * would be the same lightness as the board they are drawn on. The ladder is
     * not up for redesign (its poles are what a Yixin user has memorised), and
     * no wood colour exists that clears every chip while still being dark. So on
     * the dark board the separation comes from a ring instead of from the fill,
     * which is why the light board still has none: there it would be a hundred
     * outlines solving a problem that does not exist.
     */
    val chipRim: Color?,
) {
    companion object {
        /**
         * Paper daylight: the desktop's gold kept, because a user who has looked
         * at that board for years should recognise it.
         */
        val Light = BoardSkin(
            wood = Clay.tone(76),
            woodEdge = Clay.tone(68),
            line = Clay.tone(42),
            label = Clay.tone(32),
            blackHigh = Ink.tone(28),
            blackLow = Ink.tone(6),
            blackRim = Ink.tone(2),
            whiteHigh = Neutral.tone(99),
            whiteLow = NeutralVariant.tone(84),
            whiteRim = NeutralVariant.tone(58),
            stoneShadow = Color(0x40000000),
            lastMove = Sage.tone(48),
            best = Clay.tone(90),
            bestHalo = Color(0x66000000),
            candidate = Sage.tone(42),
            forbid = Signal.tone(48),
            blocked = Ink.tone(30),
            chipRim = null,
        )

        /** The same board by lamplight. */
        val Dark = BoardSkin(
            wood = WoodNight.tone(33),
            woodEdge = WoodNight.tone(26),
            line = WoodNight.tone(52),
            label = WoodNight.tone(70),
            blackHigh = Ink.tone(26),
            blackLow = Ink.tone(4),
            blackRim = Ink.tone(1),
            whiteHigh = Neutral.tone(96),
            whiteLow = NeutralVariant.tone(78),
            whiteRim = NeutralVariant.tone(48),
            stoneShadow = Color(0x59000000),
            lastMove = Sage.tone(72),
            best = Clay.tone(88),
            bestHalo = Color(0x73000000),
            candidate = Sage.tone(68),
            forbid = Signal.tone(66),
            blocked = Neutral.tone(72),
            chipRim = Neutral.tone(88),
        )
    }
}
