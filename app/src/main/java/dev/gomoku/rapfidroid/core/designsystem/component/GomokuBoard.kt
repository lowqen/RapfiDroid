package dev.gomoku.rapfidroid.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.designsystem.theme.BoardSkin
import dev.gomoku.rapfidroid.core.designsystem.theme.linearToSrgb
import dev.gomoku.rapfidroid.core.designsystem.theme.mixColors
import dev.gomoku.rapfidroid.core.designsystem.theme.srgbToLinear
import dev.gomoku.rapfidroid.core.designsystem.theme.YixinTheme
import dev.gomoku.rapfidroid.core.model.CandidateState
import dev.gomoku.rapfidroid.core.model.CellTag
import dev.gomoku.rapfidroid.core.model.DbCellKind
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.MoveQuality
import dev.gomoku.rapfidroid.core.model.ProveMark
import dev.gomoku.rapfidroid.core.model.ProveOverlay
import dev.gomoku.rapfidroid.core.model.TagKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/** Everything the board needs to draw one frame (pure data → easy to preview). */
data class BoardRender(
    val size: Int = Move.DEFAULT_SIZE,
    val stones: List<Move> = emptyList(),        // played order, black first
    val lastMove: Move? = null,
    val forbidden: List<Move> = emptyList(),
    val ghosts: List<Move> = emptyList(),         // PV preview from the current position
    val bestMark: Move? = null,                   // realtime / PV-head highlight
    val tags: Map<Move, CellTag> = emptyMap(),    // per-cell winrate / mate labels
    val candidates: Map<Move, CandidateState> = emptyMap(), // realtime POS/DONE
    val loseCells: Set<Move> = emptySet(),        // realtime LOSE
    val dbLabels: Map<Move, DbLabel> = emptyMap(), // yixindb values / board texts
    val showNumbers: Boolean = true,              // settings.txt line 14
    val palette: TagPalette = TagPalette(),       // saturation/value settings
    /** Review grades on played stones (settings_dev line 4, `mq_badge_pixbuf`). */
    val badges: Map<Move, MoveQuality> = emptyMap(),
    /** Prove overlay while a proof runs; null when none does. */
    val prove: ProveOverlay? = null,
    /** Half of the desktop's 500 ms prove heartbeat (`provepulse`, main.c:9212). */
    val provePulse: Boolean = false,
    /**
     * Size of the text drawn on points — analysis tags and database values —
     * from the desktop's "Board Text Font" (settings.txt line 44). The families
     * in that setting are PC fonts nobody has here; the size is the part that
     * transfers, and it is the part being adjusted.
     */
    val textScale: Float = 1f,
    /** Points the engine has been told to ignore (`block`, main.c:1899). */
    val blocked: Set<Move> = emptySet(),
)

/**
 * A database label on one point: the stored value (`W5`, `39%`, `D`) or a
 * free-form board text. Drawn only while the engine is idle, like the desktop
 * (main.c:1913 — analysis tags win during a search).
 */
data class DbLabel(val text: String, val kind: DbCellKind)

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
    }
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

/**
 * Where the intersections sit inside a square board.
 *
 * The margin around the grid carries the coordinate labels. The desktop can
 * afford a full cell there; on a phone the board is only as wide as the screen,
 * so this leaves three quarters of one — all the labels need — which makes every
 * stone a few percent larger.
 *
 * Drawing and hit testing both go through this, so they cannot drift apart.
 */
class BoardGeometry(val side: Float, val n: Int) {
    val step: Float = side / (n - 1 + 2 * MARGIN)
    val origin: Float = step * MARGIN
    val radius: Float get() = step * 0.45f

    fun cx(x: Int): Float = origin + x * step
    fun cy(y: Int): Float = origin + y * step

    /** Nearest intersection to a touch, clamped to the board. */
    fun cellAt(px: Float, py: Float): Move = Move(
        x = ((px - origin) / step).roundToInt().coerceIn(0, n - 1),
        y = ((py - origin) / step).roundToInt().coerceIn(0, n - 1),
    )

    companion object {
        /** Margin in cells on each side of the grid. */
        const val MARGIN = 0.75f
    }
}

/**
 * @param skin how the board is painted in the current theme; see [BoardSkin].
 *   A parameter rather than a field of [BoardRender] because a render comes from
 *   a view model, which has no theme.
 * @param onLongPress the desktop opens its "board text" dialog on Ctrl+click or
 *   a middle click (main.c:2677); on a phone a long press is the natural stand-in.
 */
@androidx.compose.runtime.Composable
fun GomokuBoard(
    render: BoardRender,
    modifier: Modifier = Modifier,
    skin: BoardSkin = YixinTheme.board,
    onTap: ((Move) -> Unit)? = null,
    onLongPress: ((Move) -> Unit)? = null,
) {
    val n = render.size
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(
                if (onTap != null || onLongPress != null) {
                    Modifier.pointerInput(n, onTap, onLongPress) {
                        fun cellAt(offset: Offset): Move =
                            BoardGeometry(this.size.width.toFloat(), n).cellAt(offset.x, offset.y)
                        detectTapGestures(
                            onTap = if (onTap != null) { offset -> onTap(cellAt(offset)) } else null,
                            onLongPress = if (onLongPress != null) {
                                { offset -> onLongPress(cellAt(offset)) }
                            } else {
                                null
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        drawBoard(render, skin)
    }
}

/**
 * One board frame. Split out of the composable so the same pixels can be drawn
 * off-screen for the PNG export (see `renderBoardPng`).
 */
fun DrawScope.drawBoard(render: BoardRender, skin: BoardSkin = BoardSkin.Light) {
    val n = render.size
    val geometry = BoardGeometry(min(size.width, size.height), n)
    val step = geometry.step
    val radius = geometry.radius
    // Stroke widths follow the grid: the PNG export draws the same frame at a
    // higher resolution, where fixed pixel widths would come out as hairlines.
    val hair = (step / 45f).coerceAtLeast(1f)
    fun cx(x: Int) = geometry.cx(x)
    fun cy(y: Int) = geometry.cy(y)

    drawWood(geometry, skin)

    // grid — the four outer lines heavier, the way a real board is cut: it is
    // what tells the eye where the playing area ends without drawing a frame.
    for (i in 0 until n) {
        val edge = i == 0 || i == n - 1
        val w = if (edge) hair * 1.8f else hair
        drawLine(skin.line, Offset(cx(0), cy(i)), Offset(cx(n - 1), cy(i)), strokeWidth = w)
        drawLine(skin.line, Offset(cx(i), cy(0)), Offset(cx(i), cy(n - 1)), strokeWidth = w)
    }
    // star points (15x15): center + 4 (3,3)-style
    if (n == 15) {
        listOf(3 to 3, 3 to 11, 11 to 3, 11 to 11, 7 to 7).forEach { (x, y) ->
            drawCircle(skin.line, radius = step * 0.11f, center = Offset(cx(x), cy(y)))
        }
    }

    drawLabels(geometry, skin)

    // blocked points: the engine will not consider these at all, so they are
    // drawn heavier than a forbidden marker and under everything else
    render.blocked.forEach { m ->
        val c = Offset(cx(m.x), cy(m.y))
        val r = radius * 0.85f
        drawCircle(skin.blocked, radius = r, center = c, alpha = 0.16f)
        drawLine(skin.blocked, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), strokeWidth = hair * 2.4f)
        drawLine(skin.blocked, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), strokeWidth = hair * 2.4f)
    }

    // forbidden markers
    render.forbidden.forEach { m ->
        val c = Offset(cx(m.x), cy(m.y))
        val r = radius * 0.7f
        drawLine(skin.forbid, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), strokeWidth = hair * 2f)
        drawLine(skin.forbid, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), strokeWidth = hair * 2f)
    }

    // realtime candidate cells (POS = live, DONE = settled) — drawn under stones
    render.candidates.forEach { (m, state) ->
        val c = Offset(cx(m.x), cy(m.y))
        val live = state == CandidateState.LIVE
        drawCircle(
            if (live) skin.candidate else skin.line,
            radius = radius * if (live) 0.30f else 0.20f,
            center = c,
            alpha = if (live) 0.75f else 0.45f,
        )
    }

    // realtime losing cells
    render.loseCells.forEach { m ->
        val c = Offset(cx(m.x), cy(m.y))
        val r = radius * 0.5f
        drawLine(skin.forbid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = hair * 1.7f)
    }

    // per-cell analysis tags (winrate % / W n / L n) on empty points
    val occupiedCells = render.stones.toHashSet()
    if (render.tags.isNotEmpty()) {
        render.tags.forEach { (m, tag) ->
            if (tag.label.isNotEmpty() && m !in occupiedCells) {
                drawTag(
                    cx(m.x), cy(m.y), radius, tag.label,
                    render.palette.colorsFor(tag), render.textScale, skin.chipRim, hair,
                )
            }
        }
    }

    // database values / board texts — the desktop draws these while the
    // engine is idle, and never over an analysis tag for the same point.
    render.dbLabels.forEach { (m, label) ->
        if (label.text.isNotEmpty() && m !in occupiedCells && m !in render.tags) {
            val pct = label.text.dropLast(1).toIntOrNull()
            drawTag(
                cx(m.x), cy(m.y), radius, label.text,
                render.palette.colorsForDb(label.kind, pct), render.textScale, skin.chipRim, hair,
            )
        }
    }

    // played stones, numbered unless "show number" is off (settings.txt line 14)
    render.stones.forEachIndexed { i, m ->
        val black = i % 2 == 0
        val number = if (render.showNumbers) "${i + 1}" else ""
        drawStone(cx(m.x), cy(m.y), radius, black, number, 1f, skin, hair)
        // Review grade in the stone's top-right corner (`mq_badge_pixbuf`).
        render.badges[m]?.takeIf { it != MoveQuality.NONE }?.let { quality ->
            drawBadge(cx(m.x), cy(m.y), radius, quality, hair)
        }
    }

    // The stone just played: a thin ring hugging its edge, not the wide inner
    // ring this used to be. That one sat at the same radius and weight as a
    // value chip, so on a busy board the newest stone and a 63 % looked alike.
    render.lastMove?.let { m ->
        val c = Offset(cx(m.x), cy(m.y))
        drawCircle(skin.stoneShadow, radius = radius * 0.99f, center = c, alpha = 0.35f,
            style = Stroke(width = hair * 3f))
        drawCircle(skin.lastMove, radius = radius * 0.99f, center = c,
            style = Stroke(width = hair * 1.8f))
    }

    // PV ghosts: continue numbering/colour from the current position
    val start = render.stones.size
    render.ghosts.forEachIndexed { i, m ->
        val black = (start + i) % 2 == 0
        val number = if (render.showNumbers) "${i + 1}" else ""
        drawStone(cx(m.x), cy(m.y), radius, black, number, 0.4f, skin, hair)
    }

    // best-move highlight: a bright ring over a dark halo, because it lands on
    // bare wood as often as on a dark value chip and has to survive both
    render.bestMark?.let { m ->
        val c = Offset(cx(m.x), cy(m.y))
        drawCircle(skin.bestHalo, radius = radius * 0.87f, center = c,
            style = Stroke(width = hair * 3.8f))
        drawCircle(skin.best, radius = radius * 0.87f, center = c,
            style = Stroke(width = hair * 2.1f))
    }

    // prove overlay on top of everything (main.c:9061 `prove_cell_pixbuf`)
    render.prove?.let { prove ->
        prove.ghost.forEach { (m, ply) ->
            drawProveGhost(cx(m.x), cy(m.y), radius, hair, ply, prove, render.provePulse, skin)
        }
        prove.marks.forEach { (m, mark) ->
            if (m in prove.ghost) return@forEach
            drawProveMark(cx(m.x), cy(m.y), radius, hair, mark, prove.budgetLabel(m))
        }
    }
}

/**
 * The board itself: a plate with rounded corners and a slight fall of light
 * across it, rather than a flat rectangle of colour that reaches every edge of
 * the screen. Both are one draw call; only one of them looks like an object.
 */
private fun DrawScope.drawWood(geometry: BoardGeometry, skin: BoardSkin) {
    val side = min(size.width, size.height)
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(skin.wood, skin.woodEdge),
            start = Offset.Zero,
            end = Offset(side, side),
        ),
        size = Size(side, side),
        cornerRadius = CornerRadius(geometry.step * 0.5f),
    )
}

/** Attack ring colour of the prove overlay (main.c:9089): orange, defense blue. */
private val ProveAttack = Color(0xFFE67D21)
private val ProveDefend = Color(0xFF3F8FED)
private val ProveWin = Color(0xFF21A86B)
private val ProveLoss = Color(0xFFD44747)
private val ProveExhausted = Color(0xFF5C5C66)
private val ProveOpen = Color(0xFF858A94)
private val ProveLatent = Color(0xFF8C8F9E)

/**
 * One stone of the line under search: a translucent stone with its ply number and
 * a ring saying whether that ply is an attack or a defense. The last ply is the
 * focus stone, which pulses on the desktop's 500 ms heartbeat (`prove_pulse_tick`,
 * main.c:9206) — that blink is how the board says the search is still alive, so
 * it is worth the two redraws a second.
 */
private fun DrawScope.drawProveGhost(
    cx: Float,
    cy: Float,
    r: Float,
    hair: Float,
    ply: Int,
    prove: ProveOverlay,
    pulse: Boolean,
    skin: BoardSkin,
) {
    val black = prove.isBlack(ply)
    val focus = ply == prove.ghostLen
    drawStone(cx, cy, r, black, "", if (focus && pulse) 0.75f else 0.55f, skin, hair)
    drawCircle(
        if (prove.isAttack(ply)) ProveAttack else ProveDefend,
        radius = r * if (focus && pulse) 0.99f else 0.90f,
        center = Offset(cx, cy),
        alpha = if (focus) 1f else 0.72f,
        style = Stroke(width = hair * if (focus) 3.4f else 2f),
    )
    val paint = android.graphics.Paint().apply {
        color = if (black) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        textSize = r * if (ply < 10) 0.76f else 0.62f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        "$ply", cx, cy - (paint.descent() + paint.ascent()) / 2, paint,
    )
}

/** Status marker on a root candidate (`PM_*`, main.c:9099-9134). */
private fun DrawScope.drawProveMark(
    cx: Float,
    cy: Float,
    r: Float,
    hair: Float,
    mark: ProveMark,
    budget: String,
) {
    if (mark == ProveMark.NONE) return
    val center = Offset(cx, cy)
    if (mark == ProveMark.LATENT) {
        // A latent alternative is an outline only — it has no budget yet.
        drawCircle(ProveLatent, radius = r * 0.58f, center = center, alpha = 0.85f,
            style = Stroke(width = hair * 2f))
        return
    }
    val (color, glyph) = when (mark) {
        ProveMark.WIN -> ProveWin to "✓"
        ProveMark.LOSS -> ProveLoss to "✗"
        ProveMark.EXH -> ProveExhausted to "!"
        else -> ProveOpen to budget
    }
    drawCircle(color, radius = r * 0.67f, center = center, alpha = 0.92f)
    drawCircle(Color.White, radius = r * 0.67f, center = center, alpha = 0.9f,
        style = Stroke(width = hair))
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.WHITE
        textSize = r * if (glyph.length > 2) 0.5f else 0.7f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        glyph, cx, cy - (paint.descent() + paint.ascent()) / 2, paint,
    )
}

/**
 * The desktop's quality badge (`mq_badge_pixbuf`, main.c:1349): a filled circle
 * of the grade's colour with a white outline, at 21 % of the stone's width,
 * sitting on the stone's top-right corner.
 */
private fun DrawScope.drawBadge(cx: Float, cy: Float, r: Float, quality: MoveQuality, hair: Float) {
    val radius = r * 0.42f
    val center = Offset(cx + r - radius * 0.5f, cy - r + radius * 0.5f)
    val color = Color(0xFF000000L or (quality.colorHex.removePrefix("#").toLong(16)))
    drawCircle(color, radius = radius, center = center)
    drawCircle(
        Color.White, radius = radius, center = center, alpha = 0.9f,
        style = Stroke(width = hair * 1.2f),
    )
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.WHITE
        textSize = radius * (if (quality.symbol.length > 1) 1.05f else 1.35f)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        quality.symbol, center.x, center.y - (paint.descent() + paint.ascent()) / 2, paint,
    )
}

/**
 * A value chip on one intersection: one opaque fill from the ladder and the text
 * on top of it. No ring — the fill is far enough from the wood on its own, and a
 * ring drawn a hundred times is the noise it was meant to hide.
 *
 * A mate is drawn **bold**, which is the whole difference between `W5` and `63%`
 * on this scale: one is a fact, the other a number.
 */
private fun DrawScope.drawTag(
    cx: Float,
    cy: Float,
    r: Float,
    label: String,
    colors: TagColors,
    textScale: Float = 1f,
    rim: Color? = null,
    hair: Float = 1f,
) {
    drawCircle(colors.fill, radius = r * 0.82f, center = Offset(cx, cy))
    // Only the dark board asks for this — see [BoardSkin.chipRim].
    rim?.let {
        drawCircle(it, radius = r * 0.82f, center = Offset(cx, cy), style = Stroke(width = hair * 1.3f))
    }
    val paint = android.graphics.Paint().apply {
        color = colors.ink.toArgb()
        // Never wider than the point it sits on, however large the setting.
        textSize = r * (if (label.length >= 4) 0.62f else 0.78f) * textScale.coerceIn(0.6f, 1.4f)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = colors.bold
    }
    drawContext.canvas.nativeCanvas.drawText(
        label, cx, cy - (paint.descent() + paint.ascent()) / 2, paint,
    )
}

/**
 * One stone.
 *
 * Three things separate a stone from a filled circle, and none of them costs
 * more than a draw call: it **casts a shadow**, so it sits on the wood instead
 * of being painted into it; its face is a **radial gradient** lit from the top
 * left, the way a glass or slate stone actually is; and its rim is the stone's
 * own colour darkened rather than a black outline, which at 225 stones was a
 * grid of soot. The highlight is deliberately weak — this shape is repeated a
 * couple of hundred times, and anything glossier turns the board into jewellery.
 *
 * The rim is [hair], not 1px: the PNG export draws this same stone four times
 * larger, where a one-pixel rim disappears.
 */
private fun DrawScope.drawStone(
    cx: Float,
    cy: Float,
    r: Float,
    black: Boolean,
    number: String,
    alpha: Float,
    skin: BoardSkin,
    hair: Float,
) {
    val center = Offset(cx, cy)
    drawCircle(
        skin.stoneShadow,
        radius = r * 1.03f,
        center = Offset(cx + r * 0.05f, cy + r * 0.09f),
        alpha = alpha * 0.55f,
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to (if (black) skin.blackHigh else skin.whiteHigh),
            1f to (if (black) skin.blackLow else skin.whiteLow),
            center = Offset(cx - r * 0.34f, cy - r * 0.38f),
            radius = r * 1.7f,
        ),
        radius = r,
        center = center,
        alpha = alpha,
    )
    drawCircle(
        if (black) skin.blackRim else skin.whiteRim,
        radius = r,
        center = center,
        alpha = alpha * 0.8f,
        style = Stroke(width = hair),
    )
    if (number.isEmpty()) return
    val paint = android.graphics.Paint().apply {
        color = (if (black) skin.whiteHigh else skin.blackLow).copy(alpha = alpha).toArgb()
        textSize = r * 0.88f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        number, cx, cy - (paint.descent() + paint.ascent()) / 2, paint,
    )
}

/**
 * The coordinates in the margin.
 *
 * They used to be the grid's own colour, which made them look like two more
 * lines of it; they are not part of the grid at all, they are the label of the
 * thing the grid is. Their own tone, a size up, and they read at a glance
 * without competing with a single stone.
 */
private fun DrawScope.drawLabels(geometry: BoardGeometry, skin: BoardSkin) {
    val step = geometry.step
    val paint = android.graphics.Paint().apply {
        color = skin.label.toArgb()
        textSize = step * 0.36f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    for (i in 0 until geometry.n) {
        // Columns above the top line, rows left of the first one — both inside
        // the margin, so a label can never sit on the grid.
        drawContext.canvas.nativeCanvas.drawText(
            ('A' + i).toString(), geometry.cx(i), geometry.origin - step * 0.22f, paint,
        )
        drawContext.canvas.nativeCanvas.drawText(
            (geometry.n - i).toString(),
            geometry.origin * 0.42f,
            geometry.cy(i) + step * 0.12f,
            paint,
        )
    }
}
