package dev.gomoku.yixindroid.core.model

/**
 * The 26 standard renju openings ("주형"), a **pure-computation** port of the
 * desktop `mo_opening26` classifier (main.c) and the name tables in
 * `tools/freq35.py`. No RenjuNet data is involved, so this table is safe to
 * bundle and show without an imported dataset.
 *
 * Indices 0..12 are the 13 **direct** (直, D1..D13) openings — 2nd stone on
 * H9 — and 13..25 are the 13 **indirect** (間, I1..I13) openings — 2nd stone
 * on I9. Index [NONSTD] (=26) means the first three moves do not follow the
 * basic opening rule.
 *
 * Coordinates use the internal convention of [Move]: `y` runs top-down with
 * board centre at (x=7, y=7). The canonical 2nd stones are therefore
 * H9 = (7,6) for direct and I9 = (8,6) for indirect.
 */
object Opening26 {
    const val COUNT = 26
    const val NONSTD = 26
    private const val BS = Move.DEFAULT_SIZE   // 15
    private const val CTR = 7                  // centre index

    /** "D1".."D13","I1".."I13" — matches freq35 OPABBR / rank5.csv `opening`. */
    val abbr: List<String> =
        (1..13).map { "D$it" } + (1..13).map { "I$it" }

    /** Korean names (한글), faithful to freq35 OPKO. */
    val korean: List<String> = listOf(
        "한성", "계월", "소성", "화월", "잔월", "우월", "금성", "송월", "구월",
        "신월", "서성", "산월", "유성", "장성", "협월", "항성", "수월", "유성",
        "운월", "포월", "람월", "은월", "명성", "사월", "명월", "혜성",
    )

    /** Romaji names, faithful to freq35 OPROMAJI. */
    val romaji: List<String> = listOf(
        "Kansei", "Keigetsu", "Sosei", "Kagetsu", "Zangetsu", "Ugetsu", "Kinsei",
        "Shogetsu", "Kyugetsu", "Shingetsu", "Zuisei", "Sangetsu", "Yusei",
        "Chosei", "Kyogetsu", "Kosei", "Suigetsu", "Ryusei", "Ungetsu", "Hogetsu",
        "Rangetsu", "Gingetsu", "Myojo", "Shagetsu", "Meigetsu", "Suisei",
    )

    // 3rd-move offsets (dy, dx) from centre, internal y down — DTBL/ITBL in freq35.
    private val DTBL = listOf(
        -2 to 0, -2 to -1, -2 to -2, -1 to -1, -1 to -2, 0 to -1, 0 to -2,
        1 to 0, 1 to -1, 1 to -2, 2 to 0, 2 to -1, 2 to -2,
    )
    private val ITBL = listOf(
        -2 to 2, -2 to 1, -2 to 0, -2 to -1, -2 to -2, -1 to 0, -1 to -1,
        -1 to -2, 0 to -1, 0 to -2, 1 to -1, 1 to -2, 2 to -2,
    )

    private val H9 = Move(x = CTR, y = CTR - 1)      // (7,6)
    private val I9 = Move(x = CTR + 1, y = CTR - 1)  // (8,6)
    private val CENTER = Move(CTR, CTR)              // (7,7)

    /** Canonical representative first-3 stones for opening [index], for mini boards. */
    fun representative(index: Int): List<Move> {
        require(index in 0 until COUNT)
        return if (index < 13) {
            val (dy, dx) = DTBL[index]
            listOf(CENTER, H9, Move(CTR + dx, CTR + dy))
        } else {
            val (dy, dx) = ITBL[index - 13]
            listOf(CENTER, I9, Move(CTR + dx, CTR + dy))
        }
    }

    fun isDirect(index: Int): Boolean = index < 13

    /** Human label, e.g. "D1 한성 (Kansei)". */
    fun label(index: Int): String = when (index) {
        NONSTD -> "기타 (규칙외)"
        else -> "${abbr[index]} ${korean[index]} (${romaji[index]})"
    }

    /** "D1".."I13" → 0..25, or [NONSTD] when unknown. */
    fun indexOfAbbr(a: String): Int =
        abbr.indexOf(a).let { if (it >= 0) it else NONSTD }

    // ---- classifier (port of freq35.opening3 / main.c mo_opening26) ----

    /** The 8 D4 transforms in `moveorder.h` order, on internal (y,x). */
    private fun xform(t: Int, y: Int, x: Int): Pair<Int, Int> {
        val b = BS - 1
        return when (t) {
            0 -> y to x
            1 -> x to (b - y)
            2 -> (b - y) to (b - x)
            3 -> (b - x) to y
            4 -> y to (b - x)
            5 -> (b - y) to x
            6 -> x to y
            else -> (b - x) to (b - y)
        }
    }

    // canonical 3rd-move (type, y, x) → opening index
    private val opMap: Map<Triple<Int, Int, Int>, Int> = buildMap {
        DTBL.forEachIndexed { i, (dy, dx) -> put(Triple(0, CTR + dy, CTR + dx), i) }
        ITBL.forEachIndexed { i, (dy, dx) -> put(Triple(1, CTR + dy, CTR + dx), 13 + i) }
    }

    /**
     * Classify the first three moves into 0..25, or [NONSTD]. Returns [NONSTD]
     * unless the 1st stone is the centre and the 2nd maps onto H9 (direct) or
     * I9 (indirect) under some D4 transform — the minimal such candidate wins,
     * exactly as the desktop does.
     */
    fun classify(moves: List<Move>): Int {
        if (moves.size < 3 || moves[0] != CENTER) return NONSTD
        var best: Triple<Int, Int, Int>? = null
        for (t in 0..7) {
            val w = xform(t, moves[1].y, moves[1].x)
            val type = when {
                w.first == H9.y && w.second == H9.x -> 0
                w.first == I9.y && w.second == I9.x -> 1
                else -> continue
            }
            val third = xform(t, moves[2].y, moves[2].x)
            val cand = Triple(type, third.first, third.second)
            if (best == null || less(cand, best!!)) best = cand
        }
        return best?.let { opMap[it] ?: NONSTD } ?: NONSTD
    }

    private fun less(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Boolean {
        if (a.first != b.first) return a.first < b.first
        if (a.second != b.second) return a.second < b.second
        return a.third < b.third
    }
}
