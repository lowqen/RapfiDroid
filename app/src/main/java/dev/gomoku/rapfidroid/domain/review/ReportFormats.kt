package dev.gomoku.rapfidroid.domain.review

import dev.gomoku.rapfidroid.core.model.GameReport
import dev.gomoku.rapfidroid.core.model.GradedMove
import dev.gomoku.rapfidroid.core.model.MoveGrader
import dev.gomoku.rapfidroid.core.model.MoveQuality
import dev.gomoku.rapfidroid.core.model.QueueEntry
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The report files the desktop writes, byte-compatible where it matters:
 *
 *  - CSV  = `review_export_to` (main.c:6990) — deliberately English.
 *  - MD   = `queue_write_report` (main.c:7291) — Korean, like the desktop under
 *           its Korean UI (`lngdev`), which is the only mode this app has.
 *  - HTML = `report_export_html` (main.c:8420) — the same single-file page, fed
 *           the same `GAME` object; the static shell lives in `assets/`.
 */
object ReportFormats {

    // ---- CSV ---------------------------------------------------------------

    fun csv(report: GameReport): String = buildString {
        append("position,black_winrate,mate,bestmove,played,played_class,gap\n")
        val data = report.data
        val total = report.moves.size
        for (i in 0..total) {
            val record = data.record(i)
            val best = MoveGrader.coord(record.best, report.size)
            val played = MoveGrader.coord(data.moves.getOrNull(i), report.size)
            val quality = report.moves.getOrNull(i - 1)?.quality ?: MoveQuality.NONE
            val gap = record.gap?.let { fixed(it, 4) } ?: ""
            append(i).append(',')
            if (record.recorded) {
                append(fixed(record.blackWinRate!!, 4)).append(',')
                append(record.blackMate).append(',')
            } else {
                append(",,")
            }
            append(best).append(',').append(played).append(',')
            append(quality.label).append(',').append(gap).append('\n')
        }
    }

    // ---- Markdown ----------------------------------------------------------

    fun markdown(report: GameReport): String = buildString {
        append("# 게임 리뷰: ").append(report.title).append("\n\n")
        append("- 수: ").append(report.moveCount)
            .append(", 수당 ").append(report.budget.label).append('\n')
        append("- 정확도: 흑 **").append(fixed(report.tally.blackAccuracy ?: 0.0, 1))
            .append("%** / 백 **").append(fixed(report.tally.whiteAccuracy ?: 0.0, 1))
            .append("%**\n\n")
        append("| 등급 | 흑 | 백 |\n|---|---|---|\n")
        MoveQuality.GRADED.forEach { quality ->
            val (black, white) = report.tally.counts[quality] ?: (0 to 0)
            append("| ").append(quality.symbol).append(' ').append(quality.korean)
                .append(" | ").append(black).append(" | ").append(white).append(" |\n")
        }
        append("\n## 최악의 수\n\n")
        report.worst.forEach { move ->
            append("- #").append(move.index).append(' ')
                .append(MoveGrader.coord(move.move, report.size))
                .append(" (").append(if (move.black) "흑" else "백").append(") — ")
                .append(move.quality.korean).append(", -")
                .append(fixed(move.delta * 100, 1)).append("%p, 최선은 ")
                .append(MoveGrader.coord(move.best, report.size)).append('\n')
        }
        append("\n## 수순\n\n| # | 수 | 등급 | dWR | 흑 승률 | 최선 | 격차 | 코멘트 |\n")
        append("|---|---|---|---|---|---|---|---|\n")
        report.moves.forEach { move ->
            append("| ").append(move.index)
                .append(" | ").append(MoveGrader.coord(move.move, report.size))
                .append(" | ").append(gradeCell(move))
                .append(" | ").append(deltaCell(move))
                .append(" | ").append(MoveGrader.winRateCell(move))
                .append(" | ").append(MoveGrader.coord(move.best, report.size))
                .append(" | ").append(gapCell(move))
                .append(" | ").append(move.comment)
                .append(" |\n")
        }
    }

    /** `queue_finish`'s summary file (main.c:7518). */
    fun queueSummary(entries: List<QueueEntry>, reviewed: Int, failed: Int): String = buildString {
        append("# 분석 큐 요약\n\n총 ").append(entries.size)
            .append("개: 리뷰 ").append(reviewed)
            .append(", 실패 ").append(failed)
            .append(", 미실행 ").append(entries.size - reviewed - failed).append("\n\n")
        entries.forEachIndexed { i, entry ->
            append("- [").append("%02d".format(i + 1)).append("] ").append(entry.name)
                .append(" — ").append(entry.result.ifEmpty { "미실행" }).append('\n')
        }
    }

    private fun gradeCell(move: GradedMove) =
        if (move.quality == MoveQuality.NONE) " " else "${move.quality.symbol} ${move.quality.korean}"

    private fun deltaCell(move: GradedMove) =
        if (move.quality == MoveQuality.NONE || move.quality == MoveQuality.FORCED) "-"
        else "-${fixed(move.delta * 100, 1)}%p"

    private fun gapCell(move: GradedMove) =
        move.gap?.let { "${(it * 100).roundToInt()}%p" } ?: "-"

    // ---- HTML --------------------------------------------------------------

    /**
     * The desktop's report page: [shell] is the static HTML split at the two
     * points where `report_export_html` injects data (title, then the `GAME`
     * object). Everything between is generated exactly as the desktop does, so
     * the same JavaScript drives both pages.
     */
    fun html(report: GameReport, shell: ReportShell, dateStamp: String, log: String = ""): String =
        buildString {
            append(shell.head)
            append(escapeHtml(report.title)).append(" - Yixin 게임 리포트")
            append(shell.body)
            append(gameJson(report, dateStamp, log))
            append(shell.tail)
        }

    /** The `GAME = {...}` object, field for field as main.c:8447 writes it. */
    fun gameJson(report: GameReport, dateStamp: String, log: String): String = buildString {
        val data = report.data
        val n = report.moves.size
        append("{\"title\":").append(json(report.title))
        append(",\"date\":").append(json(dateStamp))
        append(",\"rule\":").append(json(report.ruleName))
        append(",\"ver\":\"android\",\"commit\":\"\",\"size\":").append(report.size)
        append(",\"spm\":").append(if (report.budget.byDepth) 0 else report.budget.seconds)
        append(",\"dpm\":").append(if (report.budget.byDepth) report.budget.depth else 0)
        val first = data.record(0)
        append(",\"wr0\":").append(fixed(data.winRateAt(0, n.coerceAtLeast(1)), 4))
        append(",\"mate0\":").append(if (first.recorded) first.blackMate else 0)
        append(",\"valid0\":").append(if (first.recorded) 1 else 0)
        append(",\"acc\":[").append(fixed(report.tally.blackAccuracy ?: -1.0, 1))
            .append(',').append(fixed(report.tally.whiteAccuracy ?: -1.0, 1)).append(']')
        append(",\"kk\":{\"brilliant\":").append(MoveQuality.BRILLIANT.ordinal)
            .append(",\"great\":").append(MoveQuality.GREAT.ordinal)
            .append(",\"best\":").append(MoveQuality.BEST.ordinal)
            .append(",\"inacc\":").append(MoveQuality.INACCURACY.ordinal)
            .append(",\"blunder\":").append(MoveQuality.BLUNDER.ordinal)
            .append(",\"missed\":").append(MoveQuality.MISSED_WIN.ordinal)
            .append(",\"forced\":").append(MoveQuality.FORCED.ordinal).append('}')
        append(",\"L\":").append(KOREAN_LABELS)
        append(",\"mq\":[")
        MoveQuality.entries.forEachIndexed { i, quality ->
            if (i > 0) append(',')
            append("{\"n\":").append(json(quality.korean))
                .append(",\"s\":").append(json(quality.symbol))
                .append(",\"c\":").append(json(quality.colorHex)).append('}')
        }
        append("],\"cnt\":[[")
        MoveQuality.entries.forEachIndexed { i, quality ->
            if (i > 0) append(',')
            append(report.tally.counts[quality]?.first ?: 0)
        }
        append("],[")
        MoveQuality.entries.forEachIndexed { i, quality ->
            if (i > 0) append(',')
            append(report.tally.counts[quality]?.second ?: 0)
        }
        append("]],\"moves\":[")
        report.moves.forEachIndexed { i, move ->
            if (i > 0) append(',')
            append("{\"c\":").append(json(MoveGrader.coord(move.move, report.size)))
                .append(",\"x\":").append(move.move.x)
                .append(",\"y\":").append(move.move.y)
                .append(",\"cls\":").append(move.quality.ordinal)
                .append(",\"d\":").append(fixed(move.delta, 4))
                .append(",\"iwr\":").append(fixed(move.blackWinRate, 4))
                .append(",\"mate\":").append(if (move.recorded) move.blackMate else 0)
                .append(",\"v\":").append(if (move.recorded) 1 else 0)
                .append(",\"best\":").append(json(MoveGrader.coord(move.best, report.size)))
                .append(",\"bx\":").append(move.best?.x ?: -1)
                .append(",\"by\":").append(move.best?.y ?: -1)
                .append(",\"gap\":").append(fixed(move.gap ?: -1.0, 3))
                .append(",\"cmt\":").append(json(move.comment)).append('}')
        }
        append("],\"movestr\":")
        append(json(report.data.moves.joinToString("") { MoveGrader.coord(it, report.size) }))
        append(",\"log\":").append(json(log))
        append('}')
    }

    /** UI strings the page falls back on per key — the desktop's Korean block. */
    private const val KOREAN_LABELS =
        "{\"black\":\"흑\",\"white\":\"백\"," +
            "\"h\":[\"#\",\"수\",\"등급\",\"ΔWR\",\"승률\",\"최선\",\"격차\",\"코멘트\"]," +
            "\"worst\":\"최악의 수\",\"clean\":\"없음 — 깔끔한 대국.\"," +
            "\"movesw\":\"수\",\"acclab\":\" 정확도\",\"gen\":\"생성:\"," +
            "\"startpos\":\"시작 국면입니다.\",\"best\":\"최선\",\"gap\":\"격차\"," +
            "\"empty\":\"(비어 있음)\",\"t_wr\":\"승률\",\"t_gr\":\"등급\"," +
            "\"t_mv\":\"수순\",\"t_log\":\"엔진 로그\",\"t_raw\":\"원본 데이터\"," +
            "\"hint\":\"← → 키 · 돌/점/행 클릭으로 이동\"," +
            "\"spm\":\"초/수\",\"dpm\":\"깊이\",\"permove\":\"/수\"}"

    // ---- helpers -----------------------------------------------------------

    /** `%.<digits>f` without locale surprises (Kotlin's `%f` follows the locale). */
    fun fixed(value: Double, digits: Int): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val negative = value < 0
        val scale = generateSequence(1L) { it * 10 }.take(digits + 1).last()
        val scaled = (abs(value) * scale).roundToLong()
        val whole = scaled / scale
        val fraction = scaled % scale
        val sign = if (negative) "-" else ""
        return if (digits == 0) "$sign$whole"
        else "$sign$whole.${fraction.toString().padStart(digits, '0')}"
    }

    fun json(text: String): String = buildString {
        append('"')
        text.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    fun escapeHtml(text: String): String = buildString {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }
}

/** The static parts of the desktop's report page, loaded from `assets/`. */
data class ReportShell(val head: String, val body: String, val tail: String)
