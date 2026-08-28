package dev.gomoku.yixindroid.core.model

/**
 * The two user-supplied opening tables — a port of the desktop's `openstore.h`.
 *
 *   `opening_names.txt`  4수 이름       key = [OpeningName]'s FRAME key
 *   `opening_evals.txt`  흑 5수 유불리   key = [PosKey] (STONE SET)
 *
 * Both are optional and **nothing may depend on a row existing**: a position
 * with no row simply shows nothing. Most fourth moves will never be named and
 * most fifth moves will never be graded — the normal case, not a failure.
 *
 * **Two keys, on purpose.** A name depends on the move *order*: which white
 * stone was move 2 is what makes a line 한성 rather than something else, so
 * `h8 h9 h10 g9` (한성's 4th move) and `h8 g9 h10 h9` (an indirect opening's
 * 4th) are two names for the *same four stones in the same colours*. An
 * evaluation depends on the *position*: same stones, same side to move, same
 * game, so those two lines must share one grade. Rotations and mirrors fold in
 * both.
 *
 * Measured on the shipped data: 2,719 source lines collapse to 1,889 shapes
 * (830 were one position written in a different order), and those shapes are
 * reached by 4,013 legal move orders — keying on the shape covers 1,294 lines
 * the source never wrote down.
 *
 * No new key is implemented here: names go through [OpeningName.keyOf] and
 * grades through [PosKey.of], the two that `rifdb/rif_crosscheck.py` already
 * holds against the desktop and Python.
 */
object OpeningEval {

    /** How a grade is drawn. Deliberately **not** the engine's winrate colour
     *  ramp (`GomokuBoard`'s value ladder): that is a number the engine
     *  computed and this is a number a person wrote down, and painting them
     *  alike would make the two indistinguishable. Shape carries the meaning;
     *  colour only reinforces it. Mirrors `os_grades` in `openstore.h`. */
    enum class Mark { CIRCLE, PENTAGON, SQUARE, TRIANGLE, CROSS }

    data class Grade(
        /** Black's view: +5 흑승 … 0 동등 … −5 백승. */
        val code: Int,
        val ko: String,
        val en: String,
        val mark: Mark,
        /** ARGB. */
        val fill: Long,
        /** Inner dot, or null. */
        val dot: Long? = null,
    ) {
        val label: String get() = if (code >= 0) "+$code" else "$code"
    }

    /** Best for black first. Wording follows the user's evaluation table, so
     *  this screen and the table they read say the same words. */
    val ladder: List<Grade> = listOf(
        Grade(5, "흑승", "Black wins", Mark.CIRCLE, 0xFF16181C),
        Grade(4, "흑 거의 승", "Black nearly wins", Mark.PENTAGON, 0xFF3B4FC0),
        Grade(3, "흑 매우 유리", "Black clearly better", Mark.PENTAGON, 0xFF8478DF),
        Grade(2, "흑 조금 유리", "Black slightly better", Mark.PENTAGON, 0xFFA6D5EF),
        Grade(1, "흑 미세 유리", "Black marginally better", Mark.SQUARE, 0xFF1F9D3A, 0xFF0D0F12),
        Grade(0, "동등", "Balanced", Mark.SQUARE, 0xFF5CE062),
        Grade(-1, "백 미세 유리", "White marginally better", Mark.SQUARE, 0xFFBDF0BB, 0xFFFFFFFF),
        Grade(-2, "백 조금 유리", "White slightly better", Mark.TRIANGLE, 0xFFFFE14D),
        Grade(-3, "백 매우 유리", "White clearly better", Mark.TRIANGLE, 0xFFFFA726),
        Grade(-4, "백 거의 승", "White nearly wins", Mark.TRIANGLE, 0xFFEF5A25),
        Grade(-5, "백승", "White wins", Mark.CROSS, 0xFFE5322D),
    )

    const val MIN_CODE = -5
    const val MAX_CODE = 5

    /** Grades hang on the position after 4수 or 5수; the table's format allows
     *  both, and the shipped data is all 5수. */
    val PLIES = 4..5

    fun of(code: Int?): Grade? = code?.let { c -> ladder.firstOrNull { it.code == c } }
}

/**
 * The loaded tables. Held as plain maps because both files are small (8 KB of
 * names, 42 KB of grades) and every lookup is on the UI path.
 *
 * Set once by the store that reads the files; tests assign directly. This
 * mirrors the desktop, where `openname_of` reaches a file-scope table for the
 * same reason: the name of a position is a property of the position, not
 * something every caller should have to carry.
 */
object OpeningTables {

    data class Name(val ko: String, val en: String?)

    @Volatile
    var names: Map<String, Name> = emptyMap()

    @Volatile
    var evals: Map<String, Int> = emptyMap()

    fun clear() {
        names = emptyMap()
        evals = emptyMap()
    }

    /** Rows the loader could not use, for the "N lines ignored" notice. */
    data class Load<T>(val rows: Map<String, T>, val bad: Int)

    /** `"h8 h9 g9"` → moves, or null when any token is unreadable. Strict on
     *  purpose: these files are hand-editable and a half-parsed line is worse
     *  than a skipped one. */
    fun parseMoves(text: String, size: Int = Move.DEFAULT_SIZE): List<Move>? {
        val out = ArrayList<Move>(5)
        for (tok in text.trim().split(' ', '\t').filter { it.isNotEmpty() }) {
            if (tok.length !in 2..3) return null
            val x = tok[0] - 'a'
            val row = tok.drop(1).toIntOrNull() ?: return null
            if (x !in 0 until size || row !in 1..size) return null
            out.add(Move(x, size - row))
        }
        return out
    }

    /**
     * `<ply>\t<frame key>\t<한글>\t<English>`. The key is recomputed from the
     * coordinates, so a hand-rotated line still lands on the right position
     * instead of quietly never matching.
     */
    fun parseNames(text: String, size: Int = Move.DEFAULT_SIZE): Load<Name> {
        val out = LinkedHashMap<String, Name>()
        var bad = 0
        forEachRow(text) { f ->
            val moves = f?.let { parseMoves(it[1], size) }
            val key = moves?.takeIf {
                it.isNotEmpty() && it.size <= OpeningName.MAX_PLY &&
                    f[0].trim().toIntOrNull() == it.size && f[2].isNotEmpty()
            }?.let { OpeningName.keyOf(it, size) }
            if (key == null) {
                bad++
                return@forEachRow
            }
            // first line wins, so what shows never depends on file order
            out.getOrPut(key) { Name(f!![2], f[3].ifEmpty { null }) }
        }
        return Load(out, bad)
    }

    /**
     * `<ply>\t<representative line>\t<grade>\t<note>`.
     *
     * The second column is **not the key** — it is a representative move order,
     * and the key is that line's [PosKey]. That one step is the whole of the
     * transposition merge: every other order of the same stones looks the row
     * up just as well.
     */
    fun parseEvals(text: String, size: Int = Move.DEFAULT_SIZE): Load<Int> {
        val out = LinkedHashMap<String, Int>()
        var bad = 0
        forEachRow(text) { f ->
            val moves = f?.let { parseMoves(it[1], size) }
            val grade = f?.get(2)?.removePrefix("+")?.toIntOrNull()
            if (moves == null || grade == null || moves.size !in OpeningEval.PLIES ||
                f!![0].trim().toIntOrNull() != moves.size ||
                grade < OpeningEval.MIN_CODE || grade > OpeningEval.MAX_CODE
            ) {
                bad++
                return@forEachRow
            }
            out.getOrPut(PosKey.of(moves, size).key) { grade }
        }
        return Load(out, bad)
    }

    /** Splits the shared shape of both files: UTF-8 BOM, CRLF, `#` comments,
     *  blank lines, and 3 or 4 tab-separated fields. A line that is none of
     *  those and still has too few fields is handed over as `null`, so each
     *  parser counts it as rejected rather than losing it silently. */
    private inline fun forEachRow(text: String, body: (List<String>?) -> Unit) {
        for (raw in text.removePrefix("﻿").split('\n')) {
            val line = raw.removeSuffix("\r")
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val f = line.split('\t')
            if (f.size < 3) {
                body(null)
                continue
            }
            body(listOf(f[0], f[1].trim(), f[2].trim(), f.getOrElse(3) { "" }.trim()))
        }
    }
}
