package dev.gomoku.zenyth.domain.engine

import kotlin.random.Random

/**
 * Engine output for the Phase 0 safety net and baselines
 * (docs/리팩토링-성능-위생-계획.md): every line shape the parser knows, a search
 * the way Rapfi streams one, and seeded noise around both.
 */
internal object EngineStreams {

    /** One of every shape in `ResponseParser`'s grammar, and its near misses. */
    val corpus: List<String> = listOf(
        "", "   ", "\t", "OK", "ok", "garbage", "7,7", "7,7 8,8", "  7 , 8 ", "7,7 8", "07,08", "-1,3",
        "name=\"Rapfi\", version=\"2024.10\", author=\"Rapfi developers\", country=\"China\"",
        "name=Rapfi, version=1.0", "NAME=\"x\" VERSION=\"y\"",
        "MESSAGE INFO MAX_THREAD_NUM 4", "MESSAGE INFO HASH_SIZE", "MESSAGE INFO",
        "MESSAGE Depth 2-3 | Eval 814 | Time 1ms | F7 H7",
        "MESSAGE Depth 12-20 | Eval +M5 | Time 312ms | Nodes 123456 | Speed 384k | H8 I9 G7",
        "MESSAGE Depth 9 | Evaluation -M3 | N 9k | H8",
        "MESSAGE depth 3-4 | eval -40 | time 5ms | z9 H8", "MESSAGE Depth 3 no pipe", "MESSAGE hello there",
        "MESSAGE REALTIME POS 7,7", "MESSAGE REALTIME DONE 7,7", "MESSAGE REALTIME BEST 7,8",
        "MESSAGE REALTIME LOSE 6,6", "MESSAGE REALTIME REFRESH", "MESSAGE REALTIME PV 7,7 6,8 8,6",
        "MESSAGE REALTIME VAL 123", "MESSAGE REALTIME VAL x", "MESSAGE REALTIME POS bad",
        "MESSAGE REALTIME POS 7,7 extra", "MESSAGE REALTIME", "MESSAGE REALTIME WHAT",
        "message realtime pos 7,7", "Message Realtime Best 3,4",
        "INFO PV 0", "INFO PV 3", "INFO PV DONE", "INFO PV done", "INFO PV", "INFO NUMPV 2", "INFO NUMPV x",
        "INFO DEPTH 12", "INFO DEPTH 12-20", "INFO DEPTH", "INFO EVAL 40", "INFO EVAL -15", "INFO EVAL +M5",
        "INFO EVAL -M7", "INFO EVAL +m3", "INFO EVAL 12 extra", "INFO EVAL", "INFO WINRATE 0.6213",
        "INFO WINRATE 1", "INFO WINRATE x", "INFO BESTLINE 7,7 6,8 8,6", "INFO BESTLINE", "INFO BESTLINE 7,7  x 8,8",
        "INFO NODE 1234", "INFO NODES 123456", "INFO SPEED 384k", "INFO TIME 3200", "INFO TIME none",
        "INFO TOTALNODES 9876543", "INFO TOTALTIME 4100", "INFO TOTALNODES x", "INFO FOO bar", "INFO",
        "info pv 0", "info depth 7", "Info Bestline 1,2",
        "MESSAGE DATABASE REFRESH", "MESSAGE DATABASE DONE", "MESSAGE DATABASE ONE 87 120 18 3 label",
        "MESSAGE DATABASE ONE 87 120 18 3", "MESSAGE DATABASE ONE 87 x 18 3", "MESSAGE DATABASE ONE 1 2",
        "MESSAGE DATABASE TEXT hello world", "MESSAGE DATABASE LOAD START games.db", "MESSAGE DATABASE LOAD DONE",
        "MESSAGE DATABASE SAVE START out.db", "MESSAGE DATABASE SAVE DONE",
        "MESSAGE DATABASE 7 8 87 0 0 0 0 abcdefgh", "MESSAGE DATABASE 7 8 87 1 2", "MESSAGE DATABASE 7 8",
        "MESSAGE DATABASE x y z", "MESSAGE DATABASE 7 8 87 -3 4 text more",
        "MESSAGE SWAP2 MOVE1 7 7", "MESSAGE SWAP2 MOVE2 7 8", "MESSAGE SWAP2 SWAP1 YES", "MESSAGE SWAP2 SWAP2 NO",
        "MESSAGE SWAP2 OTHER", "MESSAGE SWAP2",
        "MESSAGE SOOSORV MOVE5 C 7 8", "MESSAGE SOOSORV MOVE5 C 7", "MESSAGE SOOSORV MOVE5 R",
        "MESSAGE SOOSORV MOVE5 D", "MESSAGE SOOSORV MOVE5 7 9", "MESSAGE SOOSORV MOVE5 x",
        "MESSAGE SOOSORV MOVE4 7 7 3", "MESSAGE SOOSORV SWAP1 Y", "MESSAGE SOOSORV SWAP2 N", "MESSAGE SOOSORV",
        "DEBUG search started", "ERROR unknown command", "UNKNOWN command", "FORBID 07070808.",
        "FORBID 0707080", "FORBID .", "FORBID", "FORBIDxx", "  INFO PV 0  ", "MESSAGEREALTIME POS 7,7",
        // Numbers past Int and Long, signs where only digits belong.
        "INFO NODES 99999999999999999999", "INFO NODES 9223372036854775807", "INFO NODES 9223372036854775808",
        "INFO PV 2147483648", "INFO DEPTH -2147483648", "INFO EVAL +M2147483647", "INFO EVAL -M-2147483648",
        "MESSAGE REALTIME POS +7,-8", "MESSAGE REALTIME VAL +12", "MESSAGE REALTIME POS 2147483648,1",
    )

    /**
     * A search as Rapfi streams it under `yxnbest`: per depth, per line, the
     * realtime root-move overlays, the `INFO` block, the running counters and
     * the pipe-separated summary; then the move it settles on.
     */
    fun search(depths: Int, multiPv: Int, rootMoves: Int = 12, seed: Int = 1): List<String> {
        val rnd = Random(seed)
        fun cell() = "${rnd.nextInt(3, 12)},${rnd.nextInt(3, 12)}"
        fun line(n: Int) = List(n) { cell() }.joinToString(" ")
        val out = ArrayList<String>()
        var nodes = 0L
        var time = 0L
        for (depth in 1..depths) {
            out += "MESSAGE REALTIME REFRESH"
            for (pv in 0 until multiPv) {
                repeat(rootMoves) { i ->
                    val c = cell()
                    out += "MESSAGE REALTIME POS $c"
                    if (i % 3 == 0) out += "MESSAGE REALTIME LOSE ${cell()}"
                    out += "MESSAGE REALTIME DONE $c"
                }
                nodes += rnd.nextLong(20_000, 200_000)
                time += rnd.nextLong(5, 80)
                val rate = rnd.nextInt(20, 80)
                val pvLine = line(minOf(depth, 10))
                out += "MESSAGE REALTIME BEST ${pvLine.substringBefore(' ')}"
                out += "MESSAGE REALTIME PV $pvLine"
                out += "INFO PV $pv"
                out += "INFO NUMPV $multiPv"
                out += "INFO DEPTH $depth-${depth + rnd.nextInt(0, 9)}"
                out += if (rnd.nextInt(40) == 0) "INFO EVAL +M${rnd.nextInt(3, 20)}" else "INFO EVAL ${rnd.nextInt(-400, 400)}"
                out += "INFO WINRATE 0.${rate}${rnd.nextInt(10, 99)}"
                out += "INFO NODES $nodes"
                out += "INFO SPEED ${nodes * 1000 / maxOf(time, 1)}"
                out += "INFO TIME $time"
                out += "INFO BESTLINE $pvLine"
                out += "INFO PV DONE"
                out += "INFO TOTALNODES $nodes"
                out += "INFO TOTALTIME $time"
                out += "MESSAGE Depth $depth-${depth + 4} | Eval ${rnd.nextInt(-400, 400)} | Time ${time}ms | Nodes $nodes | H8 I9 G7"
            }
        }
        out += pvMove(rnd)
        return out
    }

    private fun pvMove(rnd: Random) = "${rnd.nextInt(3, 12)},${rnd.nextInt(3, 12)}"

    private val heads = listOf(
        "MESSAGE", "MESSAGE REALTIME", "MESSAGE DATABASE", "MESSAGE SWAP2", "MESSAGE SOOSORV", "MESSAGE INFO",
        "INFO", "DEBUG", "ERROR", "UNKNOWN", "FORBID", "OK", "", "name=", "version=",
    )
    private val words = listOf(
        "PV", "DONE", "NUMPV", "DEPTH", "BESTLINE", "WINRATE", "EVAL", "NODE", "NODES", "SPEED", "TIME",
        "TOTALNODES", "TOTALTIME", "POS", "BEST", "LOSE", "REFRESH", "VAL", "ONE", "TEXT", "LOAD", "SAVE",
        "START", "MOVE1", "MOVE4", "MOVE5", "SWAP1", "SWAP2", "YES", "NO", "Y", "N", "C", "R", "D", "Depth",
        "Eval", "Evaluation", "Time", "|", "-", "+M3", "-M7", "ms", "k", "H8", "I9", "z9", "a1", "ß", "ı", "é",
        "\"q\"", "name=\"Rapfi\",", "version=\"1\"", ",", ".", "0707", "0808.",
    )

    /**
     * Characters where case-blind matching and `uppercase()` part ways (`ß` grows
     * to `SS`, the Kelvin sign equals `K` only ignoring case, ligatures, dotless
     * i), whitespace Kotlin trims but `\s` does not split on, and digits that are
     * not ASCII.
     */
    private val tricky = listOf(
        'ß', 'ı', 'İ', 'ſ', '\u212A', 'ﬆ', 'ﬁ', '\u00A0', '\u2003', '\u000B', '\u001F', '７', '٣', '+', '-', ',', ' ', '\t',
    )

    /** [corpus] and a search, each line bent at a few seeded places by [tricky] characters and case flips. */
    fun mutated(count: Int, seed: Int = 11): List<String> {
        val rnd = Random(seed)
        val base = corpus + search(depths = 3, multiPv = 2, seed = seed)
        return List(count) {
            val chars = base[rnd.nextInt(base.size)].toMutableList()
            repeat(rnd.nextInt(1, 4)) {
                val at = if (chars.isEmpty()) 0 else rnd.nextInt(chars.size + 1)
                when (rnd.nextInt(4)) {
                    0 -> chars.add(at, tricky[rnd.nextInt(tricky.size)])
                    1 -> if (at < chars.size) chars[at] = tricky[rnd.nextInt(tricky.size)]
                    2 -> if (at < chars.size) chars[at] = chars[at].let { if (it.isUpperCase()) it.lowercaseChar() else it.uppercaseChar() }
                    else -> if (at < chars.size) chars.removeAt(at)
                }
            }
            chars.joinToString("")
        }
    }

    /** Seeded lines built from the grammar's own words, cases and spacing mixed. */
    fun fuzz(count: Int, seed: Int = 7): List<String> {
        val rnd = Random(seed)
        fun mixCase(s: String) = buildString {
            s.forEach { c -> append(if (rnd.nextInt(4) == 0) (if (c.isUpperCase()) c.lowercaseChar() else c.uppercaseChar()) else c) }
        }
        fun token(): String = when (rnd.nextInt(6)) {
            0 -> rnd.nextInt(-3, 25).toString()
            1 -> "${rnd.nextInt(-1, 22)},${rnd.nextInt(-1, 22)}"
            2 -> "0.${rnd.nextInt(0, 9999)}"
            3 -> rnd.nextLong(0, 9_999_999).toString()
            else -> words[rnd.nextInt(words.size)]
        }
        fun space() = when (rnd.nextInt(8)) {
            0 -> "  "
            1 -> "\t"
            2 -> ""
            else -> " "
        }
        return List(count) {
            buildString {
                if (rnd.nextInt(10) == 0) append(space())
                append(mixCase(heads[rnd.nextInt(heads.size)]))
                repeat(rnd.nextInt(0, 9)) {
                    append(space())
                    append(mixCase(token()))
                }
                if (rnd.nextInt(10) == 0) append(space())
            }
        }
    }
}
