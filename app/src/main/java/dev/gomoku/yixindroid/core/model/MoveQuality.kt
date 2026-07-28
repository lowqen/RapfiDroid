package dev.gomoku.yixindroid.core.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Move grades, ported from the desktop's stage-4 classifier (main.c:1264-1600).
 * Names, glyphs and colours are the desktop's, so a report reads the same on
 * both machines.
 */
enum class MoveQuality(val label: String, val symbol: String, val colorHex: String) {
    NONE("", "", "#808080"),
    BRILLIANT("Brilliant", "!!", "#26c2a3"),
    GREAT("Great", "!", "#749bbf"),
    BEST("Best", "★", "#81b64c"),
    EXCELLENT("Excellent", "👍", "#a0c15a"),
    GOOD("Good", "✓", "#95b776"),
    INACCURACY("Inaccuracy", "?!", "#f7c631"),
    MISTAKE("Mistake", "?", "#ffa459"),
    BLUNDER("Blunder", "??", "#fa412d"),
    MISSED_WIN("Missed Win", "✕", "#ff7769"),
    FORCED("Forced", "→", "#8c919a"),
    ;

    /**
     * The desktop's Korean grade names (main.c:397, the `devko` table). The CSV
     * export keeps the raw English, exactly as the desktop notes there.
     */
    val korean: String
        get() = when (this) {
            NONE -> ""
            BRILLIANT -> "명수"
            GREAT -> "훌륭한 수"
            BEST -> "최선"
            EXCELLENT -> "우수"
            GOOD -> "좋음"
            INACCURACY -> "부정확"
            MISTAKE -> "실수"
            BLUNDER -> "대실수"
            MISSED_WIN -> "놓친 승리"
            FORCED -> "강제"
        }

    companion object {
        /** The grades a report enumerates, in the desktop's order. */
        val GRADED: List<MoveQuality> = entries.filter { it != NONE }
    }
}

/** `mqpreset` (settings_dev line 3): the threshold scale for the dWR grades. */
enum class GradingPreset(val scale: Double, val label: String) {
    STRICT(0.6, "엄격"),
    DEFAULT(1.0, "기본"),
    LENIENT(1.5, "관대"),
    ;

    companion object {
        fun of(value: Int): GradingPreset = when (value) {
            0 -> STRICT
            2 -> LENIENT
            else -> DEFAULT
        }
    }
}

/**
 * What a review recorded for one position: the desktop's `wrhistory` /
 * `wrmate` / `wrvalid` triple plus `reviewbestmove` and `reviewgap`.
 *
 * [blackWinRate] null is `wrvalid == 0`: the position was never searched, so
 * the graph interpolates across it and the grader treats mate info as absent.
 */
data class PositionRecord(
    val blackWinRate: Double? = null,
    val blackMate: Int = 0,
    val best: Move? = null,
    /** Winrate spread between the engine's best and second-best move. */
    val gap: Double? = null,
) {
    val recorded: Boolean get() = blackWinRate != null
}

/** One graded move, everything the report table and the badges need. */
data class GradedMove(
    val index: Int,
    val move: Move,
    val black: Boolean,
    val quality: MoveQuality,
    /** The mover's winrate loss, 0..1 (`movedelta`). */
    val delta: Double,
    /** Black-perspective winrate after the move, interpolated when not recorded. */
    val blackWinRate: Double,
    val recorded: Boolean,
    val blackMate: Int,
    val best: Move?,
    val gap: Double?,
    val comment: String,
)

/** Per-side counts and accuracy (`mq_tally`). */
data class ReviewTally(
    val counts: Map<MoveQuality, Pair<Int, Int>>,
    /** 0..100, null when that side has no graded move. */
    val blackAccuracy: Double?,
    val whiteAccuracy: Double?,
)

/**
 * A reviewed line: the moves plus one [PositionRecord] per ply (index 0 = the
 * empty board, so `records.size` is `moves.size + 1` once a review has run).
 */
data class ReviewData(
    val moves: List<Move> = emptyList(),
    val size: Int = Move.DEFAULT_SIZE,
    val records: List<PositionRecord> = emptyList(),
) {
    fun record(index: Int): PositionRecord =
        records.getOrElse(index) { PositionRecord() }

    /**
     * `wrgraph_maxindex` (main.c:4592): the cursor, extended to the last ply
     * that carries both a real record and a move.
     */
    fun maxIndex(cursor: Int = moves.size): Int {
        var n = cursor
        for (i in cursor + 1..moves.size) {
            if (record(i).recorded) n = i
        }
        return n.coerceAtLeast(1)
    }

    /** `wrgraph_value_at`: recorded values, linearly interpolated over gaps. */
    fun winRateAt(index: Int, n: Int = maxIndex()): Double {
        record(index).blackWinRate?.let { return it }
        var previous = -1
        for (i in index - 1 downTo 0) if (record(i).recorded) { previous = i; break }
        var next = -1
        for (i in index + 1..n) if (record(i).recorded) { next = i; break }
        val p = if (previous >= 0) record(previous).blackWinRate!! else null
        val q = if (next >= 0) record(next).blackWinRate!! else null
        return when {
            p != null && q != null ->
                p + (q - p) * (index - previous) / (next - previous).toDouble()
            p != null -> p
            q != null -> q
            else -> 0.5
        }
    }

    val hasAnyRecord: Boolean get() = records.any { it.recorded }
}

/**
 * The grader. Kept as a pure object over [ReviewData] so the thresholds can be
 * unit-tested against the desktop without an engine.
 */
object MoveGrader {

    private const val EPS = 1e-9

    /** `SKIP_OPENING_N` — the opening moves that stay ungraded (main.c:1078). */
    const val SKIP_OPENING_N = 5

    /**
     * `classify_moves` (main.c:1398). Returns one entry per move (index 1..N of
     * the desktop), in play order.
     */
    fun grade(
        data: ReviewData,
        preset: GradingPreset = GradingPreset.DEFAULT,
        skipOpening: Boolean = true,
        cursor: Int = data.moves.size,
    ): List<GradedMove> {
        val n = data.maxIndex(cursor)
        val out = ArrayList<GradedMove>(n)
        for (i in 1..n) {
            val move = data.moves.getOrNull(i - 1) ?: break
            val black = (i - 1) % 2 == 0
            val before = data.record(i - 1)
            val after = data.record(i)
            val blackAfter = data.winRateAt(i, n)
            val wb = mover(data.winRateAt(i - 1, n), black)
            val wa = mover(blackAfter, black)
            val mb = if (before.recorded) mateFor(before.blackMate, black) else 0
            val ma = if (after.recorded) mateFor(after.blackMate, black) else 0
            val skipped = skipOpening && i <= SKIP_OPENING_N
            // The desktop leaves both the class and the delta at zero for the
            // opening moves — it `continue`s before recording either.
            val delta = if (skipped) 0.0 else (wb - wa).coerceAtLeast(0.0)
            val quality = if (skipped) {
                MoveQuality.NONE
            } else {
                classify(move, before, after, wb, wa, mb, ma, delta, preset)
            }
            out += GradedMove(
                index = i,
                move = move,
                black = black,
                quality = quality,
                delta = delta,
                blackWinRate = blackAfter,
                recorded = after.recorded,
                blackMate = after.blackMate,
                best = before.best,
                gap = before.gap,
                comment = "",
            )
        }
        return out.map { it.copy(comment = comment(it, data, n)) }
    }

    private fun mover(blackValue: Double, black: Boolean) = if (black) blackValue else 1.0 - blackValue

    private fun mateFor(blackMate: Int, black: Boolean) = if (black) blackMate else -blackMate

    private fun classify(
        move: Move,
        before: PositionRecord,
        after: PositionRecord,
        wb: Double,
        wa: Double,
        mb: Int,
        ma: Int,
        delta: Double,
        preset: GradingPreset,
    ): MoveQuality {
        val scale = preset.scale
        val isBest = before.best != null && before.best == move
        val gap = before.gap
        // Absolute winrate of the second-best move, mover's view.
        val second = if (gap != null) wb - gap else -1.0

        // Brilliant: the review saw the win hinge on this one move, or the move
        // establishes a forced win that was not already known.
        if (isBest && gap != null && gap >= 0.25 - EPS &&
            ((ma > 0 && mb <= 0) || (after.recorded && wb <= 0.70 && wa >= 0.90))
        ) {
            return MoveQuality.BRILLIANT
        }
        if (ma > 0 && before.recorded && mb <= 0 && wb <= 0.95) return MoveQuality.BRILLIANT

        // Already lost: grade the resistance when both mate distances are exact.
        if (wb <= 0.05 && mb <= 0) {
            if (mb < 0 && ma < 0 && before.recorded && after.recorded) {
                val kept = -ma - (-mb - 1)
                return when {
                    kept >= 0 -> MoveQuality.BEST
                    kept >= -2 -> MoveQuality.GOOD
                    kept >= -5 -> MoveQuality.INACCURACY
                    kept >= -9 -> MoveQuality.MISTAKE
                    else -> MoveQuality.BLUNDER
                }
            }
            return MoveQuality.FORCED
        }

        if (mb > 0 && ma <= 0 && after.recorded && wa <= 0.75) return MoveQuality.MISSED_WIN

        // Great: the only move that keeps the position playable.
        if (isBest && wb < 0.95 &&
            ((gap != null && gap >= 0.15 - EPS && wb > 0.05 &&
                second > 0.05 + EPS && second <= 0.40 + EPS) ||
                (gap == null && before.recorded && after.recorded &&
                    wb < 0.65 && wa - wb >= 0.25 - EPS))
        ) {
            return MoveQuality.GREAT
        }

        if (isBest) return MoveQuality.BEST

        val graded = when {
            delta <= 0.01 * scale + EPS -> MoveQuality.BEST
            delta <= 0.03 * scale + EPS -> MoveQuality.EXCELLENT
            delta <= 0.08 * scale + EPS -> MoveQuality.GOOD
            delta <= 0.15 * scale + EPS -> MoveQuality.INACCURACY
            delta <= 0.30 * scale + EPS -> MoveQuality.MISTAKE
            else -> MoveQuality.BLUNDER
        }
        // A win turned into a loss is always a blunder, whatever the delta says.
        return if (wb >= 0.65 && wa <= 0.35) MoveQuality.BLUNDER else graded
    }

    /** `mq_comment` (main.c:1507): one coaching line from the same numbers. */
    private fun comment(graded: GradedMove, data: ReviewData, n: Int): String {
        val i = graded.index
        val black = graded.black
        val before = data.record(i - 1)
        val after = data.record(i)
        val wb = mover(data.winRateAt(i - 1, n), black)
        val wa = mover(data.winRateAt(i, n), black)
        val mb = if (before.recorded) mateFor(before.blackMate, black) else 0
        val ma = if (after.recorded) mateFor(after.blackMate, black) else 0
        val d = (graded.delta * 100).roundToInt()
        val resist = wb <= 0.05 && mb < 0 && ma < 0 && before.recorded && after.recorded
        val shed = if (resist) (-mb - 1) - (-ma) else 0
        val best = coord(before.best, data.size)
        // Wording taken verbatim from the desktop's Korean table (main.c:407) so
        // the same review reads the same on both machines.
        return when (graded.quality) {
            MoveQuality.BRILLIANT -> "찾기 어려운 결정타 — 승부를 결정지었습니다."
            MoveQuality.GREAT -> "국면을 지키는 유일한 수 — 다른 수는 모두 무너집니다."
            MoveQuality.MISSED_WIN -> "필승(M$mb)이 있었지만 이 수로 사라졌습니다."
            MoveQuality.BEST -> when {
                resist -> "가장 끈질긴 방어 — 메이트를 최대한 늦춥니다."
                before.best != null && before.best == graded.move -> "엔진의 최선수입니다."
                else -> "엔진 최선수에 못지않은 수입니다."
            }
            MoveQuality.EXCELLENT -> "거의 최선 (-${d}%p) — ${best}이(가) 조금 더 정확했습니다."
            MoveQuality.GOOD ->
                if (resist) "버틸 만한 방어지만 메이트를 필요 이상 ${shed}수 앞당깁니다."
                else "무난하지만 ${d}%p 손해 — ${best}이(가) 더 유리했습니다."
            MoveQuality.INACCURACY ->
                if (resist) "방어가 느슨합니다 — 메이트가 필요 이상 ${shed}수 빨라집니다."
                else "${d}%p 손실 — ${best}이(가) 더 정확했습니다."
            MoveQuality.MISTAKE ->
                if (resist) "약한 저항 — 패배까지의 수순을 ${shed}수 단축시킵니다."
                else "${d}%p 손실 — ${best}이(가) 명백히 더 좋았습니다."
            MoveQuality.BLUNDER -> when {
                wb >= 0.65 && wa <= 0.35 ->
                    "승세를 날렸습니다 — 이기던 국면(${(wb * 100).roundToInt()}%)이 " +
                        "지는 국면(${(wa * 100).roundToInt()}%)이 되었습니다."
                mb >= 0 && ma < 0 -> "필패(M${-ma})에 걸려들었습니다 — ${best}을(를) 고려해야 했습니다."
                resist -> "방어 붕괴 — 메이트가 ${shed}수 가까워졌습니다."
                else -> "${d}%p 손실 — 여기서는 ${best}이(가) 필요했습니다."
            }
            MoveQuality.FORCED -> "이미 패배가 확정된 국면 — 어떤 방어도 결과를 바꿀 수 없습니다."
            MoveQuality.NONE -> ""
        }
    }

    /** `review_coordstr` (main.c:6982): lowercase file + bottom-up rank, `-` for none. */
    fun coord(move: Move?, size: Int): String =
        move?.let { "${'a' + it.x}${size - it.y}" } ?: "-"

    /** `mq_tally` (main.c:1585): per-class counts per side and per-side accuracy. */
    fun tally(graded: List<GradedMove>): ReviewTally {
        val counts = LinkedHashMap<MoveQuality, Pair<Int, Int>>()
        MoveQuality.GRADED.forEach { counts[it] = 0 to 0 }
        val sum = doubleArrayOf(0.0, 0.0)
        val n = intArrayOf(0, 0)
        graded.forEach { move ->
            if (move.quality == MoveQuality.NONE) return@forEach
            val side = if (move.black) 0 else 1
            val current = counts.getValue(move.quality)
            counts[move.quality] =
                if (side == 0) current.first + 1 to current.second
                else current.first to current.second + 1
            if (move.quality != MoveQuality.FORCED) {
                sum[side] += 100.0 * exp(-move.delta * 100.0 / 40.0)
                n[side]++
            }
        }
        return ReviewTally(
            counts = counts,
            blackAccuracy = if (n[0] > 0) sum[0] / n[0] else null,
            whiteAccuracy = if (n[1] > 0) sum[1] / n[1] else null,
        )
    }

    /**
     * The three worst moves, ranked by dWR with missed wins boosted to the top
     * group (main.c:7301 / 7706 use the same key).
     */
    fun worst(graded: List<GradedMove>, count: Int = 3): List<GradedMove> {
        val pool = graded.filter {
            it.quality != MoveQuality.FORCED &&
                it.quality.ordinal >= MoveQuality.INACCURACY.ordinal &&
                key(it) > 0.0   // `key > worstkey` starting at 0 — a free move is not "worst"
        }
        return pool.sortedByDescending { key(it) }.take(count)
    }

    private fun key(move: GradedMove) =
        move.delta + if (move.quality == MoveQuality.MISSED_WIN) 0.5 else 0.0

    /** `wr` column: `B M3` / `W M2` for a mate, else the percentage. */
    fun winRateCell(move: GradedMove): String = when {
        move.recorded && move.blackMate != 0 ->
            "${if (move.blackMate > 0) "B" else "W"} M${abs(move.blackMate)}"
        move.recorded -> "${(move.blackWinRate * 100).roundToInt()}%"
        else -> "(${(move.blackWinRate * 100).roundToInt()}%)"
    }
}
