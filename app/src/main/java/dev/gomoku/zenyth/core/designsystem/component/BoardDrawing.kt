package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import dev.gomoku.zenyth.core.designsystem.theme.BoardSkin
import dev.gomoku.zenyth.core.model.CandidateState
import dev.gomoku.zenyth.core.model.DbCellKind
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.MoveQuality
import dev.gomoku.zenyth.core.model.ProveMark
import dev.gomoku.zenyth.core.model.ProveOverlay
import dev.gomoku.zenyth.core.model.TagKind
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
 * One board frame. Split out of the composable so the same pixels can be drawn
 * off-screen for the PNG export (see `renderBoardPng`).
 */
fun DrawScope.drawBoard(
    render: BoardRender,
    skin: BoardSkin = BoardSkin.Light,
    provePulse: Boolean = render.provePulse,
) {
    val n = render.size
    val geometry = BoardGeometry(min(size.width, size.height), n)
    val step = geometry.step
    val radius = geometry.radius
    val paints = BoardPaints(stoneText = radius * 0.88f)
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

    val occupiedCells = render.stones.toHashSet()

    // played stones, numbered unless "show number" is off (settings.txt line 14);
    // under a running review, the ones past the position being read fade back
    val reached = render.review?.reached ?: Int.MAX_VALUE
    render.stones.forEachIndexed { i, m ->
        val black = i % 2 == 0
        val number = if (render.showNumbers) "${i + 1}" else ""
        val ahead = i >= reached
        drawStone(cx(m.x), cy(m.y), radius, black, number, if (ahead) AheadOfReview else 1f, skin, hair, paints.stoneNumber)
        // Review grade in the stone's top-right corner (`mq_badge_pixbuf`).
        if (!ahead) render.badges[m]?.takeIf { it != MoveQuality.NONE }?.let { quality ->
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

    // A review at work: the engine's best so far for the position being read,
    // then the stone that position ends on, ringed on the pulse.
    render.review?.let { review ->
        review.best?.let { m ->
            val c = Offset(cx(m.x), cy(m.y))
            if (m !in occupiedCells) {
                drawStone(c.x, c.y, radius, review.reached % 2 == 0, "", 0.45f, skin, hair, paints.stoneNumber)
            }
            drawBestRing(c, radius, hair, skin)
        }
        // A ring standing off the stone, not hugging it like the last-move
        // ring: the first cut drew it there and it read as an ordinary stone.
        review.focus?.let { m ->
            val c = Offset(cx(m.x), cy(m.y))
            drawCircle(skin.stoneShadow, radius = radius * 1.2f, center = c, alpha = 0.3f,
                style = Stroke(width = hair * 4.2f))
            drawCircle(skin.lastMove, radius = radius * 1.2f, center = c, alpha = if (provePulse) 1f else 0.4f,
                style = Stroke(width = hair * 2.8f))
        }
    }

    // PV ghosts: continue numbering/colour from the current position
    val start = render.stones.size
    render.ghosts.forEachIndexed { i, m ->
        val black = (start + i) % 2 == 0
        val number = if (render.showNumbers) "${i + 1}" else ""
        drawStone(cx(m.x), cy(m.y), radius, black, number, 0.4f, skin, hair, paints.stoneNumber)
    }

    render.gradeMarks.forEach { (m, grade) ->
        if (m !in occupiedCells) drawGradeMark(grade, Offset(cx(m.x), cy(m.y)), radius * 0.5f)
    }

    // Values as text, above the ghosts: since the chip went, a translucent
    // ghost stone laid over a number blurred it, and the best move's point
    // always carries the first ghost of the line. The outline keeps it legible.
    val textScale = render.textScale.coerceIn(0.6f, 1.4f)
    render.tags.forEach { (m, tag) ->
        if (tag.label.isNotEmpty() && m !in occupiedCells) {
            drawValueText(
                cx(m.x), cy(m.y), geometry, tag.label, render.palette.textFor(tag), textScale, paints,
                bold = tag.kind != TagKind.RATE,
            )
        }
    }
    // database values and board texts, never over an analysis tag for the same point
    render.dbLabels.forEach { (m, label) ->
        if (label.text.isNotEmpty() && m !in occupiedCells && m !in render.tags) {
            val pct = label.text.dropLast(1).toIntOrNull()
            drawValueText(
                cx(m.x), cy(m.y), geometry, label.text,
                render.palette.textForDb(label.kind, pct), textScale, paints,
                bold = label.kind == DbCellKind.WIN || label.kind == DbCellKind.LOSS,
            )
        }
    }
    render.marks.forEach { (m, mark) ->
        if (m !in occupiedCells && m !in render.tags && m !in render.dbLabels) {
            drawValueText(cx(m.x), cy(m.y), geometry, mark.text, mark.color, textScale * 0.9f, paints, bold = true)
        }
    }

    // best-move highlight: a bright ring over a dark halo, because it lands on
    // bare wood as often as on a dark value chip and has to survive both
    render.bestMark?.let { m -> drawBestRing(Offset(cx(m.x), cy(m.y)), radius, hair, skin) }

    // prove overlay on top of everything (main.c:9061 `prove_cell_pixbuf`)
    render.prove?.let { prove ->
        prove.ghost.forEach { (m, ply) ->
            drawProveGhost(cx(m.x), cy(m.y), radius, hair, ply, prove, provePulse, skin)
        }
        prove.marks.forEach { (m, mark) ->
            if (m in prove.ghost) return@forEach
            drawProveMark(cx(m.x), cy(m.y), radius, hair, mark, prove.budgetLabel(m))
        }
    }
}
/**
 * One frame's text paints. A paint used to be built for every stone number and
 * every value on the board, each setting its font's weight axis, which derives
 * a typeface — a few dozen times a frame. A frame now builds one per weight,
 * and every use sets, before it draws, all that its drawing depends on. One per
 * [drawBoard] call, never shared: the PNG export draws on another thread.
 */
private class BoardPaints(stoneText: Float) {
    val stoneNumber = textPaint(600).apply { textSize = stoneText }
    val valueBold = textPaint(850)
    val valueRegular = textPaint(760)

    private fun textPaint(weight: Int) = android.graphics.Paint().apply {
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        BoardType.apply(this, weight)
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
/** The best-move ring: bright over a dark halo, so it reads on bare wood and on a stone. */
private fun DrawScope.drawBestRing(c: Offset, radius: Float, hair: Float, skin: BoardSkin) {
    drawCircle(skin.bestHalo, radius = radius * 0.87f, center = c, style = Stroke(width = hair * 3.8f))
    drawCircle(skin.best, radius = radius * 0.87f, center = c, style = Stroke(width = hair * 2.1f))
}

/** Stones past the position a review is reading: there, but not yet in play. */
private const val AheadOfReview = 0.4f

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
    drawStone(cx, cy, r, black, "", if (focus && pulse) 0.75f else 0.55f, skin, hair, null)
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
 * A value written straight onto a point, no chip (Zenyth Web, second redesign).
 * Heavy weight, a dark rounded outline under a bright fill, and never wider
 * than 84 % of a cell so neighbouring numbers keep a gap.
 *
 * **One size per length class, not per label** (2026-10-29). The size used to
 * be fitted to each label's own width, so a two-digit `63` shrank to fit while
 * a one-digit `5` did not — the losing moves, being single digits, were drawn
 * the largest, and the eye read size as importance. Now every label of up to
 * two characters is sized as the widest one could be (`W8`, bold), every
 * three-character label as `W88`; only longer, rarer labels fit themselves.
 */
private fun DrawScope.drawValueText(
    cx: Float,
    cy: Float,
    geometry: BoardGeometry,
    text: String,
    ink: Color,
    textScale: Float,
    paints: BoardPaints,
    bold: Boolean = false,
) {
    val r = geometry.radius
    var size = r * (if (text.length >= 4) 0.8f else if (text.length == 3) 0.9f else 1.05f) * textScale
    val reference = when {
        text.length <= 2 -> "W8"
        text.length == 3 -> "W88"
        else -> text
    }
    // Measured at the bold weight, drawn at the label's own.
    val measure = paints.valueBold
    measure.textSize = size
    val room = geometry.step * 0.84f
    val width = measure.measureText(reference) * 1.2f
    if (width > room) size *= room / width
    val paint = if (bold) paints.valueBold else paints.valueRegular
    paint.textSize = size
    val baseline = cy - (paint.descent() + paint.ascent()) / 2
    val canvas = drawContext.canvas.nativeCanvas
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeJoin = android.graphics.Paint.Join.ROUND
    paint.strokeWidth = max(2f, size * 0.2f)
    paint.color = TextOutline.toArgb()
    canvas.drawText(text, cx, baseline, paint)
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = ink.toArgb()
    canvas.drawText(text, cx, baseline, paint)
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
    numberPaint: android.graphics.Paint?,
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
    val paint = numberPaint ?: return
    paint.color = (if (black) skin.whiteHigh else skin.blackLow).copy(alpha = alpha).toArgb()
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
        textSize = step * 0.32f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    BoardType.apply(paint, 500)
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
