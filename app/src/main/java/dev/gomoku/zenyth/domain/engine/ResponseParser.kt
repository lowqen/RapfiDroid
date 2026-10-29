package dev.gomoku.zenyth.domain.engine

import dev.gomoku.zenyth.core.model.Move

/**
 * Pure, per-line parser ported from the desktop `iochannelout_watch` grammar.
 * No I/O, no cross-line state — the realtime `INFO …` block is stitched into
 * PVs by [SearchAggregator]. Fully JVM-unit-testable.
 *
 * Coordinates on the wire are **row,col ("y,x")**; [CoordMapper] converts.
 *
 * The lines a search streams — `INFO …` and `MESSAGE REALTIME …`, dozens a
 * second — are read in place, by index, so a line costs its response and
 * nothing else: no upper-cased copy of the line, no substrings, no split
 * lists. The rarer lines (database, openings, about) keep the plain string
 * code. Either way the result is the one the string code gave, character for
 * character; `EngineIoEquivalenceTest` holds it to that.
 */
object ResponseParser {

    // one or two committed moves: "y,x" or "y,x y2,x2"
    private val bestMove = Regex("""^(\d+)\s*,\s*(\d+)(?:\s+(\d+)\s*,\s*(\d+))?$""")

    // The rest of the grammar's patterns, held here for the same reason
    // `bestMove` is: `Regex(…)` compiles on construction, and these are built
    // once per engine line otherwise.
    private val whitespace = Regex("""\s+""")
    private val signedDigits = Regex("""-?\d+""")
    private val aboutField = Regex("""(\w+)\s*=\s*"([^"]*)"|(\w+)\s*=\s*([^,]+)""")

    /** `boardtext` is a `char[8]` filled with `%6s` on the desktop. */
    private const val DB_TEXT_MAX = 6

    fun parse(rawLine: String, coord: CoordMapper): EngineResponse {
        val raw = rawLine
        val line = rawLine.trim()
        if (line.isEmpty()) return EngineResponse.Unknown(raw)
        val end = line.length

        when {
            line.upperStarts(0, end, "MESSAGE REALTIME") -> return parseRealtime(line, line.offset(16), coord, raw)
            line.upperStarts(0, end, "MESSAGE DATABASE") -> return parseDatabase(line.substring(16).trim(), coord, raw)
            line.upperStarts(0, end, "MESSAGE SWAP2") ->
                return parseOpening(line.substring(13).trim(), swap2 = true, coord = coord, raw = raw)
            line.upperStarts(0, end, "MESSAGE SOOSORV") ->
                return parseOpening(line.substring(15).trim(), swap2 = false, coord = coord, raw = raw)
            line.upperStarts(0, end, "MESSAGE INFO") -> return parseCapability(line.substring(12).trim(), raw)
            line.upperStarts(0, end, "MESSAGE") -> {
                val body = line.drop(7).trim()
                return parseThinking(body, raw) ?: EngineResponse.Message(body, raw)
            }
            line.upperStarts(0, end, "INFO") -> return parseInfo(line, line.offset(4), coord, raw)
            line.upperStarts(0, end, "DEBUG") -> return EngineResponse.Debug(line.drop(5).trim(), raw)
            line.upperStarts(0, end, "ERROR") -> return EngineResponse.Error(line.drop(5).trim(), raw)
            line.upperStarts(0, end, "UNKNOWN") -> return EngineResponse.Unknown(raw)
            line.upperStarts(0, end, "FORBID") -> return parseForbid(line, coord, raw)
            line.upperEquals(0, end, "OK") -> return EngineResponse.Ok(raw)
            looksLikeAbout(line) -> return EngineResponse.About(parseAboutFields(line), raw)
        }

        bestMove.matchEntire(line)?.let { m ->
            val moves = buildList {
                add(coord.fromWire(m.groupValues[1].toInt(), m.groupValues[2].toInt()))
                if (m.groupValues[3].isNotEmpty()) {
                    add(coord.fromWire(m.groupValues[3].toInt(), m.groupValues[4].toInt()))
                }
            }
            return EngineResponse.BestMove(moves, raw)
        }

        return EngineResponse.Unknown(raw)
    }

    /** `INFO <key> <value>` — the realtime search stream. */
    private fun parseInfo(line: String, from: Int, coord: CoordMapper, raw: String): EngineResponse {
        val rs = line.trimStartAt(from, line.length)
        val re = line.trimEndAt(rs, line.length)
        val space = line.indexOf(' ', rs).let { if (it < 0 || it >= re) -1 else it }
        val ke = if (space < 0) re else space
        val vs = if (space < 0) re else line.trimStartAt(space + 1, re)
        val ve = if (space < 0) re else line.trimEndAt(vs, re)
        fun key(word: String) = line.upperEquals(rs, ke, word)
        fun message() = EngineResponse.Message("INFO " + line.substring(rs, re), raw)
        return when {
            key("PV") ->
                if (line.upperStarts(vs, ve, "DONE")) EngineResponse.InfoPvDone(raw)
                else EngineResponse.InfoPvStart(line.intOrNull(vs, ve) ?: 0, raw)
            key("NUMPV") -> EngineResponse.InfoNumPv(line.intOrNull(vs, ve) ?: 1, raw)
            key("DEPTH") -> {
                val dash = line.indexOf('-', vs).let { if (it < 0 || it >= ve) ve else it }
                val ds = line.trimStartAt(vs, dash)
                EngineResponse.InfoDepth(line.intOrNull(ds, line.trimEndAt(ds, dash)) ?: 0, raw)
            }
            key("BESTLINE") -> EngineResponse.InfoBestline(parseMoveList(line, vs, ve, coord), raw)
            key("WINRATE") -> EngineResponse.InfoWinRate(line.substring(vs, ve).toDoubleOrNull() ?: 0.0, raw)
            key("EVAL") -> parseEval(line, vs, ve, raw)
            key("NODE") -> stat("NODE", line, vs, ve, raw) ?: message()
            key("NODES") -> stat("NODES", line, vs, ve, raw) ?: message()
            key("SPEED") -> stat("SPEED", line, vs, ve, raw) ?: message()
            key("TIME") -> stat("TIME", line, vs, ve, raw) ?: message()
            // The engine's running counters (R4 / Zenyth Web P4-1), every 0.5 s of a search.
            // Without them a long multi-threaded iteration shows no node count for seconds.
            key("TOTALNODES") -> stat("NODES", line, vs, ve, raw) ?: message()
            key("TOTALTIME") -> stat("TIME", line, vs, ve, raw) ?: message()
            else -> message()
        }
    }

    private fun stat(key: String, line: String, from: Int, to: Int, raw: String): EngineResponse? =
        line.leadingDigits(from, to)?.let { EngineResponse.InfoStat(key, it, raw) }

    /** EVAL is "+M<n>" / "-M<n>" (mate) or a numeric centipawn value. */
    private fun parseEval(line: String, from: Int, to: Int, raw: String): EngineResponse {
        val ts = line.trimStartAt(from, to)
        val te = line.indexOf(' ', ts).let { if (it < 0 || it >= to) line.trimEndAt(ts, to) else it }
        val mateSign = when {
            te - ts >= 2 && line.regionMatches(ts, "+M", 0, 2, ignoreCase = true) -> 1
            te - ts >= 2 && line.regionMatches(ts, "-M", 0, 2, ignoreCase = true) -> -1
            else -> 0
        }
        return when (mateSign) {
            1 -> EngineResponse.InfoEval(mate = line.intOrNull(ts + 2, te), cp = null, raw = raw)
            -1 -> EngineResponse.InfoEval(mate = line.intOrNull(ts + 2, te)?.let { -it }, cp = null, raw = raw)
            else -> EngineResponse.InfoEval(mate = null, cp = line.intOrNull(ts, te), raw = raw)
        }
    }

    /**
     * `Depth 2-3 | Eval 814 | Time 1ms | F7 H7` — pipe-separated fields, the
     * unkeyed segment being the PV in letter labels. Returns null when the body
     * is an ordinary message so the caller can fall back to [EngineResponse.Message].
     */
    private fun parseThinking(body: String, raw: String): EngineResponse.Thinking? {
        if (!body.startsWith("Depth", ignoreCase = true) || '|' !in body) return null
        var depth: Int? = null
        var selDepth: Int? = null
        var evalCp: Int? = null
        var mate: Int? = null
        var timeMs: Long? = null
        var nodes: Long? = null
        var speed: Long? = null
        var line: List<Move> = emptyList()

        for (segment in body.split('|')) {
            val seg = segment.trim()
            if (seg.isEmpty()) continue
            val key = seg.substringBefore(' ').uppercase()
            val value = seg.substringAfter(' ', "").trim()
            when (key) {
                "DEPTH" -> {
                    depth = value.substringBefore('-').trim().toIntOrNull()
                    selDepth = value.substringAfter('-', "").trim().toIntOrNull()
                }
                "EVAL", "EVALUATION" -> when {
                    value.startsWith("+M", true) -> mate = value.drop(2).trim().toIntOrNull()
                    value.startsWith("-M", true) -> mate = value.drop(2).trim().toIntOrNull()?.let { -it }
                    else -> evalCp = value.toIntOrNull()
                }
                "TIME" -> timeMs = value.removeSuffix("ms").trim().toLongOrNull()
                "NODE", "NODES", "N" -> nodes = value.leadingDigits(0, value.length)
                "SPEED" -> speed = value.leadingDigits(0, value.length)
                else -> {
                    // Split once, not twice: the token count is the same list's
                    // size, and this runs on every MESSAGE line of a search.
                    val tokens = seg.split(whitespace)
                    val moves = tokens.mapNotNull { Move.fromLabel(it) }
                    if (moves.isNotEmpty() && moves.size == tokens.size) line = moves
                }
            }
        }
        return EngineResponse.Thinking(
            depth, selDepth, evalCp, mate, timeMs, nodes, speed, line, raw,
        )
    }

    /** `MESSAGE REALTIME <sub>` overlays. Sub-tokens matched like the desktop. */
    private fun parseRealtime(line: String, from: Int, coord: CoordMapper, raw: String): EngineResponse {
        val rs = line.trimStartAt(from, line.length)
        val re = line.trimEndAt(rs, line.length)
        // `rest.drop(n)`: past the sub-token, never past the end.
        fun after(n: Int) = minOf(rs + n, re)
        return when {
            line.upperStarts(rs, re, "BEST") -> pairOrUnknown(line, after(4), re, coord, raw) { EngineResponse.RealtimeBest(it, raw) }
            line.upperStarts(rs, re, "LOSE") -> pairOrUnknown(line, after(4), re, coord, raw) { EngineResponse.RealtimeLose(it, raw) }
            line.upperStarts(rs, re, "POS") -> pairOrUnknown(line, after(3), re, coord, raw) { EngineResponse.RealtimePos(it, raw) }
            line.upperStarts(rs, re, "DONE") -> pairOrUnknown(line, after(4), re, coord, raw) { EngineResponse.RealtimeDone(it, raw) }
            line.upperStarts(rs, re, "REFRESH") -> EngineResponse.RealtimeRefresh(raw)
            line.upperStarts(rs, re, "PV") -> EngineResponse.RealtimePv(parseMoveList(line, after(2), re, coord), raw)
            line.upperStarts(rs, re, "VAL") -> {
                val vs = line.trimStartAt(after(3), re)
                EngineResponse.RealtimeVal(line.intOrNull(vs, line.trimEndAt(vs, re)) ?: 0, raw)
            }
            else -> EngineResponse.Unknown(raw)
        }
    }

    /** The first space-delimited token of `[from, to)` as a "y,x" pair. */
    private inline fun pairOrUnknown(
        line: String,
        from: Int,
        to: Int,
        coord: CoordMapper,
        raw: String,
        make: (Move) -> EngineResponse,
    ): EngineResponse {
        val ts = line.trimStartAt(from, to)
        val te = line.trimEndAt(ts, to)
        val space = line.indexOf(' ', ts).let { if (it < 0 || it >= te) te else it }
        val move = parsePair(line, ts, space, coord)
        return if (move != null) make(move) else EngineResponse.Unknown(raw)
    }

    /**
     * `MESSAGE DATABASE …` — the yixindb stream. main.c dispatches on the first
     * character of the remainder (REFRESH / DONE / ONE / TEXT / LOAD / SAVE) and
     * treats everything else as a cell record:
     *
     * ```
     * MESSAGE DATABASE <y> <x> <tag> <v1> <v2> <v3> <v4> <text>
     * ```
     *
     * The desktop consumes exactly seven numbers before the text
     * (`"%d %d %d %*d %*d %*d %*d %n"`) and keeps only y/x/tag; we keep the tail
     * too. Fewer numbers than that still parse — y/x/tag are all the UI needs.
     */
    private fun parseDatabase(rest: String, coord: CoordMapper, raw: String): EngineResponse {
        val upper = rest.uppercase()
        return when {
            upper.startsWith("REFRESH") -> EngineResponse.DbRefresh(raw)
            upper.startsWith("DONE") -> EngineResponse.DbDone(raw)
            upper.startsWith("ONE") -> parseDbOne(rest.drop(3).trim(), raw)
            upper.startsWith("TEXT") -> EngineResponse.DbTextLine(rest.drop(4).trim(), raw)
            upper.startsWith("LOAD") -> parseDbFile(rest.drop(4).trim(), saving = false, raw = raw)
            upper.startsWith("SAVE") -> parseDbFile(rest.drop(4).trim(), saving = true, raw = raw)
            else -> parseDbCell(rest, coord, raw)
        }
    }

    /** `ONE <tag> <val> <depth> <bound> [label]` (main.c:13562). */
    private fun parseDbOne(rest: String, raw: String): EngineResponse {
        val parts = rest.split(whitespace).filter { it.isNotBlank() }
        if (parts.size < 4) return EngineResponse.Message("DATABASE ONE $rest", raw)
        val nums = parts.take(4).map { it.toIntOrNull() ?: return EngineResponse.Message("DATABASE ONE $rest", raw) }
        return EngineResponse.DbOne(
            tag = nums[0],
            value = nums[1],
            depth = nums[2],
            bound = nums[3],
            label = parts.drop(4).joinToString(" "),
            raw = raw,
        )
    }

    /** `LOAD|SAVE START <file>` / `LOAD|SAVE DONE` (main.c:13624-13678). */
    private fun parseDbFile(rest: String, saving: Boolean, raw: String): EngineResponse {
        val started = rest.uppercase().startsWith("START")
        val file = if (started) rest.drop(5).trim() else ""
        return EngineResponse.DbFileEvent(saving = saving, started = started, file = file, raw = raw)
    }

    private fun parseDbCell(rest: String, coord: CoordMapper, raw: String): EngineResponse {
        val parts = rest.split(whitespace).filter { it.isNotBlank() }
        if (parts.size < 3) return EngineResponse.Message("DATABASE $rest", raw)
        val y = parts[0].toIntOrNull() ?: return EngineResponse.Message("DATABASE $rest", raw)
        val x = parts[1].toIntOrNull() ?: return EngineResponse.Message("DATABASE $rest", raw)
        val tag = parts[2].toIntOrNull() ?: return EngineResponse.Message("DATABASE $rest", raw)
        val tail = parts.drop(3)
        val fields = tail.takeWhile { it.toIntOrNull() != null }.map { it.toInt() }
        // `%6s`: the desktop stores at most six characters of the free-form text.
        val text = tail.drop(fields.size).firstOrNull().orEmpty().take(DB_TEXT_MAX)
        return EngineResponse.DbCellValue(
            move = coord.fromWire(y, x),
            packedTag = tag,
            fields = fields,
            text = text,
            raw = raw,
        )
    }

    /**
     * `MESSAGE SWAP2 …` / `MESSAGE SOOSORV …` — the opening negotiation
     * (main.c:13347-13537). Coordinates are space separated here.
     */
    private fun parseOpening(
        rest: String,
        swap2: Boolean,
        coord: CoordMapper,
        raw: String,
    ): EngineResponse {
        val upper = rest.uppercase()
        val body = rest.substringAfter(' ', "").trim()
        val nums = signedDigits.findAll(body).map { it.value.toInt() }.toList()

        // MOVE5 under Soosorv is the fifth-move stage, not a plain stone.
        if (!swap2 && upper.startsWith("MOVE5")) {
            val tail = rest.drop(5).trim()
            val tailUpper = tail.uppercase()
            return when {
                tailUpper.startsWith("C") -> {
                    val c = signedDigits.findAll(tail).map { it.value.toInt() }.toList()
                    if (c.size >= 2) {
                        EngineResponse.SoosorvFifth(
                            EngineResponse.SoosorvFifth.Kind.CHOOSE,
                            coord.fromWire(c[0], c[1]), raw,
                        )
                    } else {
                        EngineResponse.Message("SOOSORV $rest", raw)
                    }
                }
                tailUpper.startsWith("R") ->
                    EngineResponse.SoosorvFifth(EngineResponse.SoosorvFifth.Kind.REFRESH, null, raw)
                tailUpper.startsWith("D") ->
                    EngineResponse.SoosorvFifth(EngineResponse.SoosorvFifth.Kind.DONE, null, raw)
                else -> {
                    val c = signedDigits.findAll(tail).map { it.value.toInt() }.toList()
                    if (c.size >= 2) {
                        EngineResponse.SoosorvFifth(
                            EngineResponse.SoosorvFifth.Kind.OFFER,
                            coord.fromWire(c[0], c[1]), raw,
                        )
                    } else {
                        EngineResponse.Message("SOOSORV $rest", raw)
                    }
                }
            }
        }

        if (upper.startsWith("MOVE") && nums.size >= 2) {
            val index = upper.getOrNull(4)?.digitToIntOrNull() ?: 0
            return EngineResponse.OpeningMove(
                swap2 = swap2,
                index = index,
                move = coord.fromWire(nums[0], nums[1]),
                // Soosorv MOVE4 appends N (main.c:13472 "%d %d %d").
                fifthCount = nums.getOrNull(2),
                raw = raw,
            )
        }

        if (upper.startsWith("SWAP")) {
            val which = upper.getOrNull(4)?.digitToIntOrNull() ?: 1
            // Swap2 spells it YES/NO, Soosorv just Y/N.
            val yes = body.uppercase().startsWith("Y")
            return EngineResponse.OpeningSwap(swap2 = swap2, which = which, yes = yes, raw = raw)
        }

        return EngineResponse.Message(if (swap2) "SWAP2 $rest" else "SOOSORV $rest", raw)
    }

    private fun parseCapability(rest: String, raw: String): EngineResponse {
        val space = rest.indexOf(' ')
        val key = if (space < 0) rest else rest.substring(0, space)
        val value = if (space < 0) "" else rest.substring(space + 1).trim()
        return EngineResponse.Capability(key, value, raw)
    }

    /** `FORBID` + groups of "yyxx" (2-digit row, 2-digit col) until '.'. */
    private fun parseForbid(line: String, coord: CoordMapper, raw: String): EngineResponse {
        val body = line.drop(6)
        val cells = ArrayList<Move>()
        var i = 0
        while (i + 4 <= body.length && body[i] != '.') {
            val row = body.substring(i, i + 2).toIntOrNull() ?: break
            val col = body.substring(i + 2, i + 4).toIntOrNull() ?: break
            cells.add(coord.fromWire(row, col))
            i += 4
        }
        return EngineResponse.Forbid(cells, raw)
    }

    /**
     * Space-separated "y,x" tokens (BESTLINE / REALTIME PV), split where `\s+`
     * splits: the ASCII whitespace, not every character [Char.isWhitespace] knows.
     */
    private fun parseMoveList(line: String, from: Int, to: Int, coord: CoordMapper): List<Move> {
        val out = ArrayList<Move>()
        val end = line.trimEndAt(from, to)
        var i = line.trimStartAt(from, end)
        while (i < end) {
            var j = i
            while (j < end && !line[j].isRegexSpace()) j++
            if (j > i) parsePair(line, i, j, coord)?.let(out::add)
            i = j
            while (i < end && line[i].isRegexSpace()) i++
        }
        return out
    }

    /** `CoordMapper.parsePair` on `[from, to)`: a "row,col" token, or null. */
    private fun parsePair(line: String, from: Int, to: Int, coord: CoordMapper): Move? {
        val ts = line.trimStartAt(from, to)
        val te = line.trimEndAt(ts, to)
        val comma = line.indexOf(',', ts)
        if (comma < 0 || comma >= te || line.indexOf(',', comma + 1).let { it in 0 until te }) return null
        val rs = line.trimStartAt(ts, comma)
        val row = line.intOrNull(rs, line.trimEndAt(rs, comma)) ?: return null
        val cs = line.trimStartAt(comma + 1, te)
        val col = line.intOrNull(cs, line.trimEndAt(cs, te)) ?: return null
        return coord.fromWire(row, col)
    }

    private fun looksLikeAbout(line: String): Boolean =
        line.contains("name=", ignoreCase = true) && line.contains("version=", ignoreCase = true)

    private fun parseAboutFields(line: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        aboutField.findAll(line).forEach { m ->
            val key = (m.groupValues[1].ifEmpty { m.groupValues[3] }).trim()
            val v = (m.groupValues[2].ifEmpty { m.groupValues[4] }).trim()
            if (key.isNotEmpty()) out[key] = v
        }
        return out
    }

    // ---- reading in place --------------------------------------------------
    //
    // Each of these is a String function of the old code, on `[from, to)`
    // instead of on a copy. Where Unicode makes `uppercase()` and a case-blind
    // compare disagree (`ß` → `SS`, the Kelvin sign), the ASCII-only fast path
    // steps aside and the old copy-and-uppercase does the deciding.

    /**
     * [n], where the old code took `substring(n)` — which throws past the end.
     * Only a line `uppercase()` stretched (`ß` → `SS`) gets there, and it fails
     * as it always did.
     */
    private fun String.offset(n: Int): Int = if (n <= length) n else substring(n).length

    /** `substring(from, to).uppercase().startsWith(prefix)`, for an upper-case ASCII [prefix]. */
    private fun String.upperStarts(from: Int, to: Int, prefix: String): Boolean {
        val n = prefix.length
        if (to - from >= n && isAscii(from, from + n)) {
            for (k in 0 until n) if (this[from + k].asciiUpper() != prefix[k]) return false
            return true
        }
        if (to - from < n && isAscii(from, to)) return false
        return substring(from, to).uppercase().startsWith(prefix)
    }

    /** `substring(from, to).uppercase() == word`, for an upper-case ASCII [word]. */
    private fun String.upperEquals(from: Int, to: Int, word: String): Boolean {
        if (isAscii(from, to)) {
            if (to - from != word.length) return false
            for (k in word.indices) if (this[from + k].asciiUpper() != word[k]) return false
            return true
        }
        return substring(from, to).uppercase() == word
    }

    private fun String.isAscii(from: Int, to: Int): Boolean {
        for (k in from until to) if (this[k].code >= 0x80) return false
        return true
    }

    private fun Char.asciiUpper(): Char = if (this in 'a'..'z') this - 32 else this

    /** Where `substring(from, to).trimStart()` would begin. */
    private fun String.trimStartAt(from: Int, to: Int): Int {
        var i = from
        while (i < to && this[i].isWhitespace()) i++
        return i
    }

    /** Where `substring(from, to).trimEnd()` would end. */
    private fun String.trimEndAt(from: Int, to: Int): Int {
        var i = to
        while (i > from && this[i - 1].isWhitespace()) i--
        return i
    }

    /** What `\s` matches: space, tab, LF, VT, FF, CR. */
    private fun Char.isRegexSpace(): Boolean =
        this == ' ' || this == '\t' || this == '\n' || this == '\u000B' || this == '\u000C' || this == '\r'

    /**
     * `substring(from, to).toIntOrNull()` — the standard library's algorithm,
     * signs and all, any Unicode digit, null on overflow.
     */
    private fun String.intOrNull(from: Int, to: Int): Int? {
        if (to <= from) return null
        val first = this[from]
        val start: Int
        val negative: Boolean
        val limit: Int
        if (first < '0') {
            if (to - from == 1) return null
            start = from + 1
            when (first) {
                '-' -> {
                    negative = true
                    limit = Int.MIN_VALUE
                }
                '+' -> {
                    negative = false
                    limit = -Int.MAX_VALUE
                }
                else -> return null
            }
        } else {
            start = from
            negative = false
            limit = -Int.MAX_VALUE
        }
        val limitForMaxRadix = (-Int.MAX_VALUE) / 36
        var limitBeforeMul = limitForMaxRadix
        var result = 0
        for (i in start until to) {
            val digit = Character.digit(this[i].code, 10)
            if (digit < 0) return null
            if (result < limitBeforeMul) {
                if (limitBeforeMul == limitForMaxRadix) {
                    limitBeforeMul = limit / 10
                    if (result < limitBeforeMul) return null
                } else {
                    return null
                }
            }
            result *= 10
            if (result < limit + digit) return null
            result -= digit
        }
        return if (negative) result else -result
    }

    /**
     * The first run of ASCII digits in `[from, to)` as a count — what
     * `Regex("\\d+").find(…)?.value?.toLongOrNull()` gave: "12345k" → 12345,
     * null when there is no digit or the run passes [Long.MAX_VALUE].
     */
    private fun String.leadingDigits(from: Int, to: Int): Long? {
        var i = from
        while (i < to && this[i] !in '0'..'9') i++
        if (i == to) return null
        var result = 0L
        while (i < to && this[i] in '0'..'9') {
            val d = this[i] - '0'
            if (result > (Long.MAX_VALUE - d) / 10) {
                // Past Long: the old `toLongOrNull` gave null for the whole run.
                return null
            }
            result = result * 10 + d
            i++
        }
        return result
    }
}
