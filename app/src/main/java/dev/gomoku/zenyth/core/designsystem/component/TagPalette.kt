package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.ui.graphics.Color
import dev.gomoku.zenyth.core.designsystem.theme.linearToSrgb
import dev.gomoku.zenyth.core.designsystem.theme.mixColors
import dev.gomoku.zenyth.core.designsystem.theme.oklch
import dev.gomoku.zenyth.core.designsystem.theme.srgbToLinear
import dev.gomoku.zenyth.core.model.CellTag
import dev.gomoku.zenyth.core.model.DbCellKind
import dev.gomoku.zenyth.core.model.TagKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/**
 * Fill and ink of one value chip. Two colours, not three: a chip that is far
 * enough from the board needs no outline, and an outline multiplied by a hundred
 * cells is noise of its own.
 */
data class TagColors(val fill: Color, val ink: Color, val bold: Boolean = false)
/**
 * Colour rules for the value labels on the board: the analysis tags and the
 * database values, which get the same treatment because they say the same thing
 * — main.c colours both through one function, `winrate2colorstr`.
 *
 * **The desktop's poles, without the desktop's rainbow.** `winrate2colorstr`
 * (main.c:1195) is `hue = (100 - winrate) * 1.8` at full HSV value: winning is
 * red, losing is cyan, and everything between is a hue sweep through green. The
 * two poles are what a Yixin user has memorised, so they are kept exactly —
 * warm means the move wins, cool means it loses. The sweep is not: a hue ramp
 * carries no order (a rainbow's brightest point is its *middle*, which is why
 * the pastel band this replaced made 50 % — the one value that means "no idea"
 * — the loudest thing on the board). It is replaced by the structure the
 * literature recommends for a value that diverges around a neutral midpoint:
 * poles at opposite hues, a neutral centre, and lightness doing the ranking.
 *
 * So: **lightness carries the rank, hue says which side.** The ladder runs from
 * a deep petrol blue (lost) through slate (even) to a vivid vermilion (won),
 * getting steadily lighter as the win rate rises, so the order survives in
 * greyscale — and the poles are near-complementary in [Oklch], which is both the
 * largest hue separation available and, not by accident, a classic harmony.
 *
 * The chips are dark and the text on them is white: at a hundred chips per
 * screen a pale fill sits at the same lightness as the gold wood, so "even" —
 * where most candidates are — became the least readable part of the board.
 *
 * The desktop's five colour settings (settings.txt lines 39–43) survive as
 * *positions on the ladder* rather than raw HSV saturation, so importing a PC
 * `settings.txt` still orders the colours the way that user set them up.
 */
data class TagPalette(
    val losingSaturation: Int = 0,
    val winningSaturation: Int = 83,
    val minRateSaturation: Int = 20,
    val maxRateSaturation: Int = 80,
    val value: Int = 100,
) {
    /** Tag colours: mate wins/losses sit at the ends, rates interpolate. */
    fun colorsFor(tag: CellTag): TagColors = when (tag.kind) {
        TagKind.WIN -> chip(winPosition(), bold = true)
        TagKind.LOSE -> chip(losePosition(), bold = true)
        TagKind.RATE -> chip(ratePosition(tag.winRatePct ?: 50))
    }

    /**
     * Database labels on the same scale (W = 100 %, L = 0 %, `NN%` = the rate).
     * A draw is not half a win, so it steps off the ladder sideways — the same
     * rank as slate, tinted green — and a free-form note is not a value at all,
     * so it gets parchment.
     */
    fun colorsForDb(kind: DbCellKind, ratePct: Int?): TagColors = when (kind) {
        DbCellKind.WIN -> chip(winPosition(), bold = true)
        DbCellKind.LOSS -> chip(losePosition(), bold = true)
        DbCellKind.DRAW -> TagColors(dim(DrawFill), Color.White, bold = true)
        DbCellKind.RATE -> chip(ratePosition(ratePct ?: 50))
        DbCellKind.NOTE -> NoteColors
    }

    /**
     * The colour of a value written **as text only** on the board. Zenyth Web's
     * second redesign took the chip away and wrote the number straight onto the
     * wood, the way the desktop Yixin does. With no chip there is no dark fill
     * to carry the rank, so here the **hue** does: losing blue, even green,
     * yellow and orange, winning red, the desktop's `winrate2colorstr` poles. The
     * order still comes from [ratePosition], so the five colour settings still
     * bend it. A dark outline (`TextOutline`) lifts it off wood, stone and ghost.
     * The colours themselves are [textInkAt]'s.
     */
    fun textFor(tag: CellTag): Color = when (tag.kind) {
        TagKind.WIN -> textInk(0f)
        TagKind.LOSE -> textInk(220f)
        TagKind.RATE -> textInk(textHue(ratePosition(tag.winRatePct ?: 50)))
    }

    /** Database values on the same scale; a free-form note is not a value at all. */
    fun textForDb(kind: DbCellKind, ratePct: Int?): Color = when (kind) {
        DbCellKind.WIN -> textInk(0f)
        DbCellKind.LOSS -> textInk(220f)
        DbCellKind.DRAW -> textInk(textHue(0.5f))
        DbCellKind.RATE -> textInk(textHue(ratePosition(ratePct ?: 50)))
        DbCellKind.NOTE -> NoteText
    }

    /** Ladder position (0 lost, 1 won) to hue, 240 degrees blue down to 0 red. */
    internal fun textHue(position: Float): Float = (1f - position.coerceIn(0f, 1f)) * 240f

    private fun textInk(hue: Float): Color = textInkAt(hue, (value / 100f).coerceIn(0.6f, 1f))

    /** Exposed for the tests: where a win rate sits on the ladder. */
    internal fun positionOf(percent: Int): Float = ratePosition(percent)

    /**
     * Ladder position of a win rate. 0.5 is even; the two rate settings say how
     * fast a value leaves the neutral middle ([minRateSaturation], as the
     * exponent of the ramp) and how far it can get ([maxRateSaturation]).
     *
     * Almost every candidate worth comparing sits within a few points of even,
     * so a ramp that is linear in win rate spends most of its colour on the part
     * of the scale nobody reads. The exponent bends it: the first points away
     * from 50 % move the furthest, and the 90 → 100 % end — where the answer is
     * already known — is the part that gets compressed.
     */
    private fun ratePosition(percent: Int): Float {
        val offset = percent.coerceIn(0, 100) / 100f - 0.5f
        if (offset == 0f) return 0.5f
        val reach = rateReach() * (abs(offset) * 2f).pow(rateGamma())
        return (0.5f + sign(offset) * reach / 2f).coerceIn(0f, 1f)
    }

    /**
     * Steepness of that ramp. This used to be a floor added the instant a
     * position stopped being exactly even, which put a visible seam between
     * 50 % and 51 %; as an exponent it does the same job — spread the crowded
     * middle — continuously. A [minRateSaturation] of 0 leaves the ramp linear
     * in win rate, which is exactly what the desktop's `winrate2colorstr` does.
     */
    private fun rateGamma(): Float {
        val reach = rateReach()
        if (reach <= 0f) return 1f
        return (1f - 2f * (rateFloor() / reach)).coerceIn(0.15f, 1f)
    }

    /**
     * Where a proven win sits. [winningSaturation] deepens it, but it can never
     * end up *inside* the range the percentages use — a fact must not read as
     * weaker than a number.
     */
    private fun winPosition(): Float =
        0.5f + max(winningSaturation.coerceIn(0, 100) / 100f, rateReach()) / 2f

    /** The same at the losing end, driven by [losingSaturation]. */
    private fun losePosition(): Float =
        0.5f - max(losingSaturation.coerceIn(0, 100) / 100f, rateReach()) / 2f

    /** Min/max are read as a span, so a swapped pair cannot invert the ladder. */
    private fun rateFloor(): Float =
        min(minRateSaturation.coerceIn(0, 100), maxRateSaturation.coerceIn(0, 100)) / 100f

    private fun rateReach(): Float =
        max(minRateSaturation.coerceIn(0, 100), maxRateSaturation.coerceIn(0, 100)) / 100f

    private fun chip(position: Float, bold: Boolean = false): TagColors =
        TagColors(dim(ladderColor(position)), Color.White, bold)

    /** `colorValue` (line 43) darkens the whole ladder, exactly as on the PC. */
    private fun dim(color: Color): Color {
        val factor = (value / 100f).coerceIn(0.4f, 1f)
        if (factor >= 1f) return color
        return scaleLinear(color, factor)
    }

    private companion object {
        /**
         * A draw at slate's rank, turned green: its own answer, not half a win.
         * Green is also what the desktop paints a `D` — `winrate2colorstr(50)`
         * lands on hue 90 — so this is the one place the rainbow's middle was
         * saying something true, and it is kept.
         */
        val DrawFill = Color(0xFF2E623B)

        /** Board text is a note, not a value — parchment and ink, off the ladder. */
        val NoteColors = TagColors(Color(0xFFF2E7D3), Color(0xFF57452C))

        /** A note written as text: parchment, under the same dark outline. */
        val NoteText = Color(0xFFF2E7D3)
    }
}
/** The outline under every value written on the board. */
internal val TextOutline = Color(0xFF140F0A)
/**
 * The ink of a value written on the board, for a hue on the desktop's HSL scale
 * (240 blue = lost … 120 green = even … 0 red = won) — [TagPalette.textHue].
 *
 * It was `hsl(hue, 95 %, 62 %)`, Zenyth Web's formula, and on the maple board that
 * read as neon («쨍함», 2026-10-29): HSL at full saturation is as loud as sRGB
 * goes, and its yellow and green are far lighter than its blue and red, so the
 * middle of the scale glared. Now each hue is the same named colour placed in
 * [oklch] at chroma 0.14, and the lightness follows the hue the way a printed
 * palette does — red and blue deeper (0.66 / 0.70), yellow lightest (0.84) —
 * so all of them carry about the same weight and none outshines the wood. The
 * order (blue → teal → green → yellow → orange → red) is the desktop's, kept.
 *
 * [dim] is the desktop's colour value setting (settings.txt line 43).
 */
internal fun textInkAt(hue: Float, dim: Float = 1f): Color {
    val h = hue.coerceIn(0f, 240f)
    val i = InkAnchors.indexOfLast { it[0] <= h }.coerceIn(0, InkAnchors.size - 2)
    val (a, b) = InkAnchors[i] to InkAnchors[i + 1]
    val t = (h - a[0]) / (b[0] - a[0])
    return oklch((a[2] + (b[2] - a[2]) * t) * dim, InkChroma, a[1] + (b[1] - a[1]) * t)
}
/** HSL hue → (the same named colour's Oklch hue, its lightness). */
private val InkAnchors = listOf(
    floatArrayOf(0f, 29f, 0.66f), //    red — a win
    floatArrayOf(30f, 52f, 0.74f), //   orange
    floatArrayOf(60f, 98f, 0.84f), //   yellow
    floatArrayOf(120f, 142f, 0.80f), // green — even
    floatArrayOf(180f, 200f, 0.80f), // cyan
    floatArrayOf(240f, 262f, 0.70f), // blue — a loss
)
private const val InkChroma = 0.14f
/** HSL to colour, the CSS formula (Zenyth Web `ladder.ts hslHex`). */
internal fun hsl(h: Float, s: Float, l: Float): Color {
    fun f(n: Float): Float {
        val k = (n + h / 30f) % 12f
        val a = s * min(l, 1f - l)
        return l - a * max(-1f, min(min(k - 3f, 9f - k), 1f))
    }
    return Color(f(0f).coerceIn(0f, 1f), f(8f).coerceIn(0f, 1f), f(4f).coerceIn(0f, 1f))
}
/**
 * The five anchors of the value ladder, darkest (lost) first.
 *
 * Placed in **Oklch** — a perceptually uniform space, so "one step" means the
 * same amount of colour everywhere — on an even ladder of relative luminance
 * (0.030 → 0.063 → 0.096 → 0.130 → 0.165), with each anchor then given as much
 * chroma as the sRGB gamut allows at that lightness. Two hard limits set the two
 * ends of that luminance range: white text needs 4.5:1 on the lightest anchor
 * (it gets 4.88) and the darkest must still not be mistaken for a stone. Inside
 * those, the chroma is what buys the discrimination — measured against the
 * palette this replaced, the ladder's total perceptual length is 1.31×, its
 * worst 5 %-wide step 1.59×, and 45 % against 55 % 1.24×.
 *
 * `TagPaletteTest` holds every one of those properties, including for the
 * colours interpolated in between.
 */
private val LadderStops = listOf(
    Color(0xFF04315C), // 0 %   deep petrol blue — darkest, so worst
    Color(0xFF104E5A), // 25 %  dark teal — the desktop's losing cyan, deepened
    Color(0xFF515863), // 50 %  slate — "not known yet" gets the quietest colour
    Color(0xFFAB451C), // 75 %  rust — clearly good
    Color(0xFFDA2533), // 100 % vermilion — the desktop's winning red, and the
    //                    near-complement of the petrol blue at the other end
)
/**
 * Colour for a ladder position in 0..1, blended **in linear sRGB**. Mixing after
 * gamma removal is what keeps the middle of a blend from going muddy, and it has
 * a second property this design leans on: luminance is a linear function of the
 * linear components, so a blend of two anchors always lands between their
 * luminances. Monotone anchors therefore make the whole ladder monotone.
 */
private fun ladderColor(position: Float): Color {
    val t = position.coerceIn(0f, 1f) * (LadderStops.size - 1)
    val i = t.toInt().coerceIn(0, LadderStops.size - 2)
    return mixLinear(LadderStops[i], LadderStops[i + 1], t - i)
}
private fun mixLinear(from: Color, to: Color, fraction: Float): Color =
    mixColors(from, to, fraction)
/** Multiply the light, not the encoded number — the honest way to dim a colour. */
private fun scaleLinear(color: Color, factor: Float): Color = Color(
    linearToSrgb(srgbToLinear(color.red) * factor),
    linearToSrgb(srgbToLinear(color.green) * factor),
    linearToSrgb(srgbToLinear(color.blue) * factor),
)
