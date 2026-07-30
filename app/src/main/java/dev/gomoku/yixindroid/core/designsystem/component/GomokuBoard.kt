package dev.gomoku.yixindroid.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.gomoku.yixindroid.core.model.CandidateState
import dev.gomoku.yixindroid.core.model.CellTag
import dev.gomoku.yixindroid.core.model.DbCellKind
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.MoveQuality
import dev.gomoku.yixindroid.core.model.ProveMark
import dev.gomoku.yixindroid.core.model.ProveOverlay
import dev.gomoku.yixindroid.core.model.TagKind
import kotlin.math.min
import kotlin.math.roundToInt

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
)

/**
 * A database label on one point: the stored value (`W5`, `39%`, `D`) or a
 * free-form board text. Drawn only while the engine is idle, like the desktop
 * (main.c:1913 — analysis tags win during a search).
 */
data class DbLabel(val text: String, val kind: DbCellKind)

/**
 * Colour rules for the analysis tags, mirroring the desktop's saturation
 * settings (settings.txt lines 39–43: losing/winning move saturation, min/max
 * win-rate saturation, value).
 */
data class TagPalette(
    val losingSaturation: Int = 0,
    val winningSaturation: Int = 83,
    val minRateSaturation: Int = 20,
    val maxRateSaturation: Int = 80,
    val value: Int = 100,
) {
    /** Tag colour: mate wins/losses use the fixed hues, rates interpolate. */
    fun colorFor(tag: CellTag): Color = when (tag.kind) {
        TagKind.WIN -> hsv(WIN_HUE, winningSaturation / 100f, value / 100f)
        TagKind.LOSE -> hsv(LOSE_HUE, (100 - losingSaturation) / 100f, value / 100f)
        TagKind.RATE -> rateColor(tag.winRatePct ?: 50)
    }

    /**
     * Database labels use the same scale (main.c colours them with
     * `winrate2colorstr`: W = 100 %, L = 0 %, D = 50 %, `NN%` = the rate).
     * Free-form notes get no value colour.
     */
    fun colorForDb(kind: DbCellKind, ratePct: Int?): Color = when (kind) {
        DbCellKind.WIN -> hsv(WIN_HUE, winningSaturation / 100f, value / 100f)
        DbCellKind.LOSS -> hsv(LOSE_HUE, (100 - losingSaturation) / 100f, value / 100f)
        DbCellKind.DRAW -> rateColor(50)
        DbCellKind.RATE -> rateColor(ratePct ?: 50)
        DbCellKind.NOTE -> NoteColor
    }

    private fun rateColor(percent: Int): Color {
        val pct = percent.coerceIn(0, 100) / 100f
        val sat = (minRateSaturation + (maxRateSaturation - minRateSaturation) *
            kotlin.math.abs(pct - 0.5f) * 2f) / 100f
        return hsv(if (pct >= 0.5f) WIN_HUE else LOSE_HUE, sat, value / 100f)
    }

    private fun hsv(hue: Float, saturation: Float, v: Float): Color =
        Color.hsv(hue, saturation.coerceIn(0f, 1f), v.coerceIn(0.2f, 1f))

    private companion object {
        const val WIN_HUE = 210f   // blue = good for the side to move
        const val LOSE_HUE = 8f    // red
        val NoteColor = Color(0xFF5B4636)
    }
}

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

private val Gold = Color(0xFFDCB35C)
private val Line = Color(0xFF7A5A2B)
private val Black = Color(0xFF1C1A17)
private val White = Color(0xFFF5F2EA)
private val Accent = Color(0xFF81B64C)
private val Forbid = Color(0xFFE2705F)

/**
 * @param onLongPress the desktop opens its "board text" dialog on Ctrl+click or
 *   a middle click (main.c:2677); on a phone a long press is the natural stand-in.
 */
@androidx.compose.runtime.Composable
fun GomokuBoard(
    render: BoardRender,
    modifier: Modifier = Modifier,
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
        drawBoard(render)
    }
}

/**
 * One board frame. Split out of the composable so the same pixels can be drawn
 * off-screen for the PNG export (see `renderBoardPng`).
 */
fun DrawScope.drawBoard(render: BoardRender) {
    val n = render.size
    val geometry = BoardGeometry(min(size.width, size.height), n)
    val step = geometry.step
    val radius = geometry.radius
    // Stroke widths follow the grid: the PNG export draws the same frame at a
    // higher resolution, where fixed pixel widths would come out as hairlines.
    val hair = (step / 45f).coerceAtLeast(1f)
    fun cx(x: Int) = geometry.cx(x)
    fun cy(y: Int) = geometry.cy(y)

    drawRect(Gold, size = size)

    // grid
    for (i in 0 until n) {
        drawLine(Line, Offset(cx(0), cy(i)), Offset(cx(n - 1), cy(i)), strokeWidth = hair)
        drawLine(Line, Offset(cx(i), cy(0)), Offset(cx(i), cy(n - 1)), strokeWidth = hair)
    }
    // star points (15x15): center + 4 (3,3)-style
    if (n == 15) {
        listOf(3 to 3, 3 to 11, 11 to 3, 11 to 11, 7 to 7).forEach { (x, y) ->
            drawCircle(Line, radius = step * 0.09f, center = Offset(cx(x), cy(y)))
        }
    }

    drawLabels(geometry)

    // forbidden markers
    render.forbidden.forEach { m ->
        val c = Offset(cx(m.x), cy(m.y))
        val r = radius * 0.7f
        drawLine(Forbid, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), strokeWidth = hair * 2f)
        drawLine(Forbid, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), strokeWidth = hair * 2f)
    }

    // realtime candidate cells (POS = live, DONE = settled) — drawn under stones
    render.candidates.forEach { (m, state) ->
        val c = Offset(cx(m.x), cy(m.y))
        val live = state == CandidateState.LIVE
        drawCircle(
            if (live) Accent else Line,
            radius = radius * if (live) 0.30f else 0.20f,
            center = c,
            alpha = if (live) 0.75f else 0.45f,
        )
    }

    // realtime losing cells
    render.loseCells.forEach { m ->
        val c = Offset(cx(m.x), cy(m.y))
        val r = radius * 0.5f
        drawLine(Forbid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = hair * 1.7f)
    }

    // per-cell analysis tags (winrate % / W n / L n) on empty points
    val occupiedCells = render.stones.toHashSet()
    if (render.tags.isNotEmpty()) {
        render.tags.forEach { (m, tag) ->
            if (tag.label.isNotEmpty() && m !in occupiedCells) {
                drawTag(cx(m.x), cy(m.y), radius, tag.label, render.palette.colorFor(tag))
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
                render.palette.colorForDb(label.kind, pct),
            )
        }
    }

    // played stones, numbered unless "show number" is off (settings.txt line 14)
    render.stones.forEachIndexed { i, m ->
        val black = i % 2 == 0
        val number = if (render.showNumbers) "${i + 1}" else ""
        drawStone(cx(m.x), cy(m.y), radius, black, number, alpha = 1f)
        // Review grade in the stone's top-right corner (`mq_badge_pixbuf`).
        render.badges[m]?.takeIf { it != MoveQuality.NONE }?.let { quality ->
            drawBadge(cx(m.x), cy(m.y), radius, quality, hair)
        }
    }

    // last-move ring
    render.lastMove?.let { m ->
        drawCircle(Accent, radius = radius * 0.55f, center = Offset(cx(m.x), cy(m.y)),
            style = Stroke(width = hair * 2f))
    }

    // PV ghosts: continue numbering/colour from the current position
    val start = render.stones.size
    render.ghosts.forEachIndexed { i, m ->
        val black = (start + i) % 2 == 0
        val number = if (render.showNumbers) "${i + 1}" else ""
        drawStone(cx(m.x), cy(m.y), radius, black, number, alpha = 0.4f)
    }

    // best-move highlight
    render.bestMark?.let { m ->
        drawCircle(Gold, radius = radius * 0.85f, center = Offset(cx(m.x), cy(m.y)),
            style = Stroke(width = hair * 2.3f))
    }

    // prove overlay on top of everything (main.c:9061 `prove_cell_pixbuf`)
    render.prove?.let { prove ->
        prove.ghost.forEach { (m, ply) ->
            drawProveGhost(cx(m.x), cy(m.y), radius, hair, ply, prove)
        }
        prove.marks.forEach { (m, mark) ->
            if (m in prove.ghost) return@forEach
            drawProveMark(cx(m.x), cy(m.y), radius, hair, mark, prove.budgetLabel(m))
        }
    }
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
 * focus and gets a heavier ring (the desktop pulses it; a static ring reads the
 * same on a phone and avoids a 500 ms redraw of the whole board).
 */
private fun DrawScope.drawProveGhost(
    cx: Float,
    cy: Float,
    r: Float,
    hair: Float,
    ply: Int,
    prove: ProveOverlay,
) {
    val black = prove.isBlack(ply)
    val focus = ply == prove.ghostLen
    drawStone(cx, cy, r, black, "", alpha = 0.55f)
    drawCircle(
        if (prove.isAttack(ply)) ProveAttack else ProveDefend,
        radius = r * 0.90f,
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

/** A filled rounded chip with the tag text, sized to sit on one intersection. */
private fun DrawScope.drawTag(cx: Float, cy: Float, r: Float, label: String, color: Color) {
    drawCircle(color, radius = r * 0.82f, center = Offset(cx, cy), alpha = 0.92f)
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.WHITE
        textSize = r * (if (label.length >= 4) 0.62f else 0.78f)
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        label, cx, cy - (paint.descent() + paint.ascent()) / 2, paint,
    )
}

private fun DrawScope.drawStone(cx: Float, cy: Float, r: Float, black: Boolean, number: String, alpha: Float) {
    drawCircle(if (black) Black else White, radius = r, center = Offset(cx, cy), alpha = alpha)
    drawCircle(Color(0x55000000), radius = r, center = Offset(cx, cy), alpha = alpha, style = Stroke(width = 1f))
    val paint = android.graphics.Paint().apply {
        color = (if (black) White else Black).copy(alpha = alpha).toArgb()
        textSize = r * 0.9f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        number, cx, cy - (paint.descent() + paint.ascent()) / 2, paint,
    )
}

private fun DrawScope.drawLabels(geometry: BoardGeometry) {
    val step = geometry.step
    val paint = android.graphics.Paint().apply {
        color = Line.toArgb()
        textSize = step * 0.32f
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
