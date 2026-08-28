package dev.gomoku.yixindroid.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.designsystem.theme.BoardSkin
import dev.gomoku.yixindroid.core.model.CellTag
import dev.gomoku.yixindroid.core.model.DbCellKind
import dev.gomoku.yixindroid.core.model.TagKind
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * The value ladder makes five promises, and this is where they are kept.
 *
 *  1. **Lightness carries the rank** — a higher win rate is always a lighter
 *     chip, so the board can be read in greyscale and, more to the point, at a
 *     glance.
 *  2. **The poles are the desktop's** — winning is warm, losing is cool, the
 *     way `winrate2colorstr` has always painted them.
 *  3. **The colour is spent where the candidates are** — the crowded band
 *     around even gets more of the ladder than the settled end does.
 *  4. **White text stays legible** — every colour a chip can take, anchor or
 *     interpolated, clears WCAG AA (4.5:1) against the white label on it, and
 *     stays clear of the gold board underneath it.
 *  5. **No setting can invert the order** — a `settings.txt` imported from a PC
 *     changes how fast colours separate, never which way they run.
 *
 * A note on the tolerances: `Color` quantises to 8 bits per channel, so two win
 * rates one percent apart can land on the same colour, and rounding can put them
 * a hair out of order. Strict ordering is asserted on steps wide enough to clear
 * that (5 %), and the one-percent test only forbids a drop larger than one
 * 8-bit level.
 */
class TagPaletteTest {

    /** The wood the chips sit on — the background this design is fighting. */
    private val wood = BoardSkin.Light.wood

    @Test
    fun aHigherWinRateIsAlwaysALighterChip() {
        val palette = TagPalette()
        var previous = -1f
        for (percent in 0..100 step 5) {
            val luminance = palette.rateFill(percent).luminance()
            assertThat(luminance).isGreaterThan(previous)
            previous = luminance
        }
    }

    /** The same at full resolution: one percent may repeat a colour, never undo one. */
    @Test
    fun noSinglePercentStepGoesBackwards() {
        val palette = TagPalette()
        for (percent in 0 until 100) {
            val here = palette.rateFill(percent).luminance()
            val next = palette.rateFill(percent + 1).luminance()
            assertThat(next).isAtLeast(here - EIGHT_BIT_STEP)
        }
    }

    /** Ends of the ladder, and a wide gap between them — greyscale still ranks. */
    @Test
    fun theLadderSpansAWideLightnessRange() {
        val palette = TagPalette()
        assertThat(palette.rateFill(100).luminance())
            .isGreaterThan(palette.rateFill(0).luminance() * 1.8f)
    }

    @Test
    fun whiteTextIsLegibleOnEveryChipTheLadderCanProduce() {
        for (palette in palettes()) {
            for (percent in 0..100) {
                val colors = palette.colorsFor(rateTag(percent))
                assertThat(contrast(colors.fill, colors.ink)).isAtLeast(WCAG_AA)
            }
            for (kind in listOf(DbCellKind.WIN, DbCellKind.LOSS, DbCellKind.DRAW, DbCellKind.NOTE)) {
                val colors = palette.colorsForDb(kind, null)
                assertThat(contrast(colors.fill, colors.ink)).isAtLeast(WCAG_AA)
            }
        }
    }

    /** Whatever the settings say, the ladder still runs the same way. */
    @Test
    fun noSettingCanInvertTheOrder() {
        for (palette in palettes()) {
            var previous = -1f
            for (percent in 0..100 step 10) {
                val luminance = palette.rateFill(percent).luminance()
                assertThat(luminance).isAtLeast(previous - EIGHT_BIT_STEP)
                previous = luminance
            }
        }
    }

    /**
     * A proven mate is a fact, and a fact may not read as weaker than a number:
     * the win chip is at least as light as any percentage, the loss chip at
     * least as dark.
     */
    @Test
    fun aMateSitsAtOrBeyondTheEndsOfTheRateRange() {
        for (palette in palettes()) {
            val win = palette.colorsFor(CellTag("W5", TagKind.WIN, depth = 10)).fill.luminance()
            val lose = palette.colorsFor(CellTag("L7", TagKind.LOSE, depth = 10)).fill.luminance()
            for (percent in 0..100) {
                val rate = palette.rateFill(percent).luminance()
                assertThat(win).isAtLeast(rate - EIGHT_BIT_STEP)
                assertThat(lose).isAtMost(rate + EIGHT_BIT_STEP)
            }
        }
    }

    /** `W5` is a fact and `63%` is a number; weight is what separates them. */
    @Test
    fun onlyFactsAreDrawnBold() {
        val palette = TagPalette()
        assertThat(palette.colorsFor(CellTag("W5", TagKind.WIN, depth = 1)).bold).isTrue()
        assertThat(palette.colorsFor(CellTag("L5", TagKind.LOSE, depth = 1)).bold).isTrue()
        assertThat(palette.colorsFor(rateTag(63)).bold).isFalse()
        assertThat(palette.colorsForDb(DbCellKind.DRAW, null).bold).isTrue()
        assertThat(palette.colorsForDb(DbCellKind.RATE, 63).bold).isFalse()
    }

    /** An even position is "not known yet", and that answer has its own colour. */
    @Test
    fun evenIsSlateAndADrawIsNot() {
        val palette = TagPalette()
        val even = palette.colorsFor(rateTag(50)).fill
        val draw = palette.colorsForDb(DbCellKind.DRAW, null).fill
        assertThat(draw).isNotEqualTo(even)
        // Same rank, different answer: a draw is not half a win.
        assertThat(draw.luminance()).isWithin(0.02f).of(even.luminance())
        assertThat(draw.green).isGreaterThan(draw.blue)
        assertThat(even.blue).isGreaterThan(even.green)
    }

    /** Board text is not a value, so it stays off the ladder entirely. */
    @Test
    fun boardTextKeepsItsParchment() {
        val note = TagPalette().colorsForDb(DbCellKind.NOTE, null)
        assertThat(note.fill.luminance()).isGreaterThan(wood.luminance())
        assertThat(note.ink).isNotEqualTo(Color.White)
        assertThat(contrast(note.fill, note.ink)).isAtLeast(WCAG_AA)
    }

    /**
     * The dark board cannot separate the chips by itself, and says so.
     *
     * The ladder spans luminance 0.03..0.165. Gold wood sits well above that, so
     * every chip stands off it; a *dark* wood sits inside it, and the arithmetic
     * below is the proof that no dark wood could ever pass the test underneath
     * this one — which is why [BoardSkin.chipRim] exists and why the dark skin is
     * the only one that sets it.
     */
    @Test
    fun theDarkBoardSeparatesChipsWithARingInstead() {
        val darkWood = BoardSkin.Dark.wood
        val rim = requireNotNull(BoardSkin.Dark.chipRim) { "the dark board must ring its chips" }
        assertThat(BoardSkin.Light.chipRim).isNull()

        // The wood really does land inside the ladder — the reason for the ring.
        val darkest = TagPalette().rateFill(0).luminance()
        val lightest = TagPalette().rateFill(100).luminance()
        assertThat(darkWood.luminance()).isGreaterThan(darkest)
        assertThat(darkWood.luminance()).isLessThan(lightest)

        // And the ring does the job the fill cannot: visible against the wood,
        // and against every chip it can be drawn around.
        assertThat(contrast(rim, darkWood)).isAtLeast(MIN_BOARD_SEPARATION)
        for (palette in palettes()) {
            for (percent in 0..100) {
                assertThat(contrast(rim, palette.rateFill(percent))).isAtLeast(MIN_BOARD_SEPARATION)
            }
            for (kind in listOf(DbCellKind.WIN, DbCellKind.LOSS, DbCellKind.DRAW)) {
                assertThat(contrast(rim, palette.colorsForDb(kind, null).fill))
                    .isAtLeast(MIN_BOARD_SEPARATION)
            }
        }
    }

    /**
     * The chips have to stay off the wood. This is the number the pastel version
     * failed: a pale fill on a gold board is the same lightness as the board.
     * Board text is the one deliberate exception (it is a note, not a value).
     */
    @Test
    fun everyValueChipStandsOffTheBoard() {
        for (palette in palettes()) {
            for (percent in 0..100) {
                assertThat(contrast(palette.rateFill(percent), wood)).isAtLeast(MIN_BOARD_SEPARATION)
            }
            for (kind in listOf(DbCellKind.WIN, DbCellKind.LOSS, DbCellKind.DRAW)) {
                val fill = palette.colorsForDb(kind, null).fill
                assertThat(contrast(fill, wood)).isAtLeast(MIN_BOARD_SEPARATION)
            }
        }
    }

    /**
     * `colorValue` darkens the ladder and nothing else — the order is untouched.
     * A dimmed ladder is compressed as well as darker, so its steps are checked
     * strictly only at the quarters; in between, 8-bit rounding is again the
     * limit rather than the design.
     */
    @Test
    fun dimmingOnlyDarkens() {
        val bright = TagPalette()
        val dim = TagPalette(value = 50)
        var previous = -1f
        for (percent in 0..100 step 5) {
            val dimmed = dim.rateFill(percent).luminance()
            assertThat(dimmed).isLessThan(bright.rateFill(percent).luminance())
            assertThat(dimmed).isAtLeast(previous - EIGHT_BIT_STEP)
            previous = dimmed
        }
        var quarter = -1f
        for (percent in 0..100 step 25) {
            val dimmed = dim.rateFill(percent).luminance()
            assertThat(dimmed).isGreaterThan(quarter)
            quarter = dimmed
        }
    }

    /**
     * The rate settings are a *speed*: a wide min..max span leaves the neutral
     * middle sooner than a narrow one, which is exactly the complaint this
     * redesign started from — everything worth comparing sits near 50 %.
     */
    @Test
    fun theRateSettingsDecideHowFastEvenSplitsApart() {
        val slow = TagPalette(minRateSaturation = 0, maxRateSaturation = 20)
        val fast = TagPalette(minRateSaturation = 40, maxRateSaturation = 90)
        fun spread(p: TagPalette) = p.rateFill(55).luminance() - p.rateFill(45).luminance()
        assertThat(spread(fast)).isGreaterThan(spread(slow))
        // Even under the slowest setting the two sides are still told apart.
        assertThat(spread(slow)).isGreaterThan(0f)
    }

    /**
     * The desktop's `winrate2colorstr` is `hue = (100 - winrate) * 1.8`: red at
     * 100 %, cyan at 0 %. Someone coming from the PC reads the poles before they
     * read anything else, so the poles may not be swapped — this is the test
     * that would have caught the first version of this ladder, which ran the
     * other way.
     */
    @Test
    fun winningIsWarmAndLosingIsCoolLikeTheDesktop() {
        val palette = TagPalette()
        val won = palette.rateFill(100)
        val lost = palette.rateFill(0)
        assertThat(won.red).isGreaterThan(won.blue)
        assertThat(lost.blue).isGreaterThan(lost.red)
        // Mates sit past the ends of the percentage range and follow the same rule.
        val mateWin = palette.colorsFor(CellTag("W5", TagKind.WIN, depth = 1)).fill
        val mateLoss = palette.colorsFor(CellTag("L5", TagKind.LOSE, depth = 1)).fill
        assertThat(mateWin.red).isGreaterThan(mateWin.blue)
        assertThat(mateLoss.blue).isGreaterThan(mateLoss.red)
    }

    /**
     * Where the colour is spent. Candidates cluster around even, so the ramp is
     * bent to give the crowded middle more of the ladder than the end where the
     * answer is already known.
     */
    @Test
    fun theCrowdedMiddleGetsMoreColourThanTheSettledEnd() {
        val palette = TagPalette()
        fun gap(a: Int, b: Int) = distance(palette.rateFill(a), palette.rateFill(b))
        assertThat(gap(45, 55)).isGreaterThan(gap(90, 100))
        assertThat(gap(40, 60)).isGreaterThan(gap(80, 100))
    }

    /**
     * The old ramp added a floor the instant a position stopped being exactly
     * even, so 50 % and 51 % sat a fifth of the ladder apart — a seam the eye
     * reads as a category boundary that is not there. The bend that replaced it
     * is steepest at the middle by design, but no single point of win rate may
     * cost more than a fifth of the whole ladder.
     */
    @Test
    fun evenIsASlopeAndNotAStep() {
        val palette = TagPalette()
        val ladder = distance(palette.rateFill(0), palette.rateFill(100))
        for (percent in 0 until 100) {
            assertThat(distance(palette.rateFill(percent), palette.rateFill(percent + 1)))
                .isLessThan(ladder / 5f)
        }
    }

    /** Every chip keeps white text legible *and* stays off the wood, at once. */
    @Test
    fun theLadderClearsBothContrastBarsEverywhere() {
        val palette = TagPalette()
        for (percent in 0..100) {
            val fill = palette.rateFill(percent)
            assertThat(contrast(fill, Color.White)).isAtLeast(WCAG_AA)
            assertThat(contrast(fill, wood)).isAtLeast(MIN_BOARD_SEPARATION)
        }
    }

    private fun TagPalette.rateFill(percent: Int): Color = colorsFor(rateTag(percent)).fill

    /** Straight-line distance in sRGB — a stand-in for "how different they look". */
    private fun distance(a: Color, b: Color): Float {
        val dr = a.red - b.red
        val dg = a.green - b.green
        val db = a.blue - b.blue
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }

    private fun rateTag(percent: Int) =
        CellTag("$percent%", TagKind.RATE, depth = 10, winRatePct = percent)

    /** Defaults plus the corners a `settings.txt` import can put us in. */
    private fun palettes() = listOf(
        TagPalette(),
        TagPalette(losingSaturation = 100, winningSaturation = 100),
        TagPalette(losingSaturation = 0, winningSaturation = 0),
        TagPalette(minRateSaturation = 0, maxRateSaturation = 0),
        TagPalette(minRateSaturation = 100, maxRateSaturation = 100),
        TagPalette(minRateSaturation = 90, maxRateSaturation = 10), // swapped on purpose
        TagPalette(value = 40),
        TagPalette(value = 0),   // clamped by the palette, not by the caller
        TagPalette(value = 200), // ditto, from a hand-edited settings file
        TagPalette(losingSaturation = -5, winningSaturation = 130, minRateSaturation = -20),
    )

    private fun contrast(a: Color, b: Color): Float {
        val hi = max(a.luminance(), b.luminance())
        val lo = min(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private companion object {
        const val WCAG_AA = 4.5f

        /**
         * A chip is never this close to the wood. Lower than the 3:1 asked of
         * graphical objects, because the teal end also carries a near-complementary
         * hue against gold; the numbers below this were where chips disappeared.
         */
        const val MIN_BOARD_SEPARATION = 2.4f

        /** One 8-bit level of luminance around the dark end of the ladder. */
        const val EIGHT_BIT_STEP = 0.0017f
    }
}
