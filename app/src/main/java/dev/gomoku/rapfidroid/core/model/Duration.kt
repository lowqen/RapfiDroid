package dev.gomoku.rapfidroid.core.model

import dev.gomoku.rapfidroid.core.i18n.tr
import kotlin.math.roundToLong

/**
 * Typing a time by hand — the move time, the analysis time and the review and
 * prove budgets all share this reader (RapfiWeb `core/settings/duration.ts`).
 *
 * Accepted, case and spaces ignored, `.` or `,` as the decimal point:
 *
 * ```
 *  2.5                       → the field's own unit, usually seconds
 *  2.5s · 2.5초 · 2.5sec
 *  1500ms · 1500밀리초
 *  1m · 1분 · 1min · 1h · 1시간
 *  0 · 무제한 · ∞ · unlimited  → only where the field takes "unlimited" (0)
 * ```
 *
 * What can be read is fixed up: a value out of range is clamped to the end it
 * passed. What cannot — a negative, a word, an empty field — goes back to the
 * field's default, and the reason comes back with it for the screen to show.
 */
object Duration {

    enum class Unit { MS, S }

    data class Spec(
        val minMs: Long,
        val maxMs: Long,
        /** Where an unreadable entry goes. 0 means unlimited. */
        val defaultMs: Long,
        val unit: Unit,
        val unlimited: Boolean = false,
        /** Rounded to this many ms — 1000 for values stored in whole seconds. */
        val stepMs: Long = 1,
    )

    sealed interface Result {
        val ms: Long

        data class Ok(override val ms: Long) : Result
        data class Clamped(override val ms: Long, val note: String) : Result
        data class Fallback(override val ms: Long, val note: String) : Result
    }

    private val UNIT_MS = mapOf(
        "ms" to 1L, "밀리초" to 1L, "毫秒" to 1L,
        "s" to 1_000L, "sec" to 1_000L, "초" to 1_000L, "秒" to 1_000L,
        "m" to 60_000L, "min" to 60_000L, "분" to 60_000L, "分" to 60_000L, "分钟" to 60_000L,
        "h" to 3_600_000L, "시간" to 3_600_000L, "时" to 3_600_000L, "小时" to 3_600_000L,
    )

    private val UNLIMITED = setOf("무제한", "∞", "unlimited", "inf", "infinite", "不限时", "不限", "无限")

    private val NUMBER = Regex("^([+-]?\\d+(?:[.,]\\d*)?|[+-]?[.,]\\d+)([a-z가-힣\u4e00-\u9fff]*)$")

    /** «무제한» · «800ms» · «2.5초» · «90초» · «10분». */
    fun format(ms: Long): String {
        if (ms <= 0) return tr("무제한", "Unlimited", "不限时")
        if (ms < 1000) return "${ms}ms"
        if (ms >= 600_000 && ms % 60_000 == 0L) {
            val m = ms / 60_000
            return tr("${m}분", "${m} min", "${m} 分钟")
        }
        val s = trim(ms / 1000.0, 2)
        return tr("${s}초", "${s}s", "${s} 秒")
    }

    /** Text to put back in the field, in its own unit and without the unit word. */
    fun text(ms: Long, unit: Unit): String {
        if (ms <= 0) return ""
        return if (unit == Unit.S) trim(ms / 1000.0, 3) else ms.toString()
    }

    fun parse(text: String, spec: Spec): Result {
        fun fallback(why: String) = Result.Fallback(
            spec.defaultMs,
            tr("$why. 기본값(${format(spec.defaultMs)})으로 돌렸습니다", "$why. Reset to the default (${format(spec.defaultMs)})", "$why。已恢复为默认值(${format(spec.defaultMs)})"),
        )

        val raw = text.trim().lowercase().replace(Regex("\\s+"), "")
        if (raw.isEmpty()) return fallback(tr("시간이 비었습니다", "No time given", "未填写时间"))
        if (raw in UNLIMITED) {
            return if (spec.unlimited) Result.Ok(0)
            else fallback(tr("이 칸은 무제한을 받지 않습니다", "This field cannot be unlimited", "此项不能设为不限时"))
        }
        val m = NUMBER.matchEntire(raw)
            ?: return fallback(tr("«${text.trim()}» 는 시간이 아닙니다", "«${text.trim()}» is not a time", "«${text.trim()}» 不是时间"))
        val num = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            ?: return fallback(tr("수가 아닙니다", "Not a number", "不是数字"))
        val word = m.groupValues[2]
        val factor = if (word.isEmpty()) {
            if (spec.unit == Unit.S) 1_000L else 1L
        } else {
            UNIT_MS[word] ?: return fallback(tr("«$word» 는 모르는 단위입니다", "«$word» is not a unit", "«$word» 不是可识别的单位"))
        }
        if (num.isNaN() || num.isInfinite()) return fallback(tr("수가 아닙니다", "Not a number", "不是数字"))
        if (num < 0) return fallback(tr("음수는 시간이 될 수 없습니다", "A time cannot be negative", "时间不能为负数"))

        var ms = (num * factor).roundToLong()
        if (ms == 0L) {
            return if (spec.unlimited) Result.Ok(0)
            else fallback(tr("0 은 이 칸에서 쓸 수 없습니다", "0 is not allowed here", "此项不能为 0"))
        }
        val step = spec.stepMs.coerceAtLeast(1)
        ms = (ms.toDouble() / step).roundToLong() * step
        if (ms < spec.minMs) {
            return Result.Clamped(spec.minMs, tr("가장 짧은 ${format(spec.minMs)}로 맞췄습니다", "Set to the shortest, ${format(spec.minMs)}", "已设为最短的 ${format(spec.minMs)}"))
        }
        if (ms > spec.maxMs) {
            return Result.Clamped(spec.maxMs, tr("가장 긴 ${format(spec.maxMs)}로 맞췄습니다", "Set to the longest, ${format(spec.maxMs)}", "已设为最长的 ${format(spec.maxMs)}"))
        }
        return Result.Ok(ms)
    }

    private fun trim(v: Double, places: Int): String {
        val s = "%.${places}f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
        return s.ifEmpty { "0" }
    }

    /** The engine's move time in a game (0 = until «지금 두기»). */
    val MOVE_TIME = Spec(minMs = 500, maxMs = 3_600_000, defaultMs = 0, unit = Unit.S, unlimited = true, stepMs = 100)

    /** Analysis time, stored in whole seconds (0 = until stopped). */
    val ANALYSIS_TIME = Spec(minMs = 1_000, maxMs = 3_600_000, defaultMs = 0, unit = Unit.S, unlimited = true, stepMs = 1_000)
}
