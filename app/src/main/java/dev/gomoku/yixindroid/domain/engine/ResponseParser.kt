package dev.gomoku.yixindroid.domain.engine

import dev.gomoku.yixindroid.core.model.Move

/**
 * Pure, per-line parser ported from the desktop `iochannelout_watch` grammar.
 * No I/O, no cross-line state — the realtime `INFO …` block is stitched into
 * PVs by [SearchAggregator]. Fully JVM-unit-testable.
 *
 * Coordinates on the wire are **row,col ("y,x")**; [CoordMapper] converts.
 */
object ResponseParser {

    // one or two committed moves: "y,x" or "y,x y2,x2"
    private val bestMove = Regex("""^(\d+)\s*,\s*(\d+)(?:\s+(\d+)\s*,\s*(\d+))?$""")

    /** `boardtext` is a `char[8]` filled with `%6s` on the desktop. */
    private const val DB_TEXT_MAX = 6

    fun parse(rawLine: String, coord: CoordMapper): EngineResponse {
        val raw = rawLine
        val line = rawLine.trim()
        if (line.isEmpty()) return EngineResponse.Unknown(raw)

        val upper = line.uppercase()

        when {
            upper.startsWith("MESSAGE REALTIME") -> return parseRealtime(line.substring(16).trim(), coord, raw)
            upper.startsWith("MESSAGE DATABASE") -> return parseDatabase(line.substring(16).trim(), coord, raw)
            upper.startsWith("MESSAGE INFO") -> return parseCapability(line.substring(12).trim(), raw)
            upper.startsWith("MESSAGE") -> {
                val body = line.drop(7).trim()
                return parseThinking(body, raw) ?: EngineResponse.Message(body, raw)
            }
            upper.startsWith("INFO") -> return parseInfo(line.substring(4).trim(), coord, raw)
            upper.startsWith("DEBUG") -> return EngineResponse.Debug(line.drop(5).trim(), raw)
            upper.startsWith("ERROR") -> return EngineResponse.Error(line.drop(5).trim(), raw)
            upper.startsWith("UNKNOWN") -> return EngineResponse.Unknown(raw)
            upper.startsWith("FORBID") -> return parseForbid(line, coord, raw)
            upper == "OK" -> return EngineResponse.Ok(raw)
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
    private fun parseInfo(rest: String, coord: CoordMapper, raw: String): EngineResponse {
        val space = rest.indexOf(' ')
        val key = if (space < 0) rest else rest.substring(0, space)
        val value = if (space < 0) "" else rest.substring(space + 1).trim()
        return when (key.uppercase()) {
            "PV" ->
                if (value.uppercase().startsWith("DONE")) EngineResponse.InfoPvDone(raw)
                else EngineResponse.InfoPvStart(value.toIntOrNull() ?: 0, raw)
            "NUMPV" -> EngineResponse.InfoNumPv(value.toIntOrNull() ?: 1, raw)
            "DEPTH" -> EngineResponse.InfoDepth(value.substringBefore('-').trim().toIntOrNull() ?: 0, raw)
            "BESTLINE" -> EngineResponse.InfoBestline(parseMoveList(value, coord), raw)
            "WINRATE" -> EngineResponse.InfoWinRate(value.toDoubleOrNull() ?: 0.0, raw)
            "EVAL" -> parseEval(value, raw)
            "NODE", "NODES", "SPEED", "TIME" ->
                digits(value)?.let { EngineResponse.InfoStat(key.uppercase(), it, raw) }
                    ?: EngineResponse.Message("INFO $rest", raw)
            else -> EngineResponse.Message("INFO $rest", raw)
        }
    }

    /** EVAL is "+M<n>" / "-M<n>" (mate) or a numeric centipawn value. */
    private fun parseEval(value: String, raw: String): EngineResponse {
        val token = value.trim().substringBefore(' ')
        return when {
            token.startsWith("+M", ignoreCase = true) ->
                EngineResponse.InfoEval(mate = token.drop(2).toIntOrNull(), cp = null, raw = raw)
            token.startsWith("-M", ignoreCase = true) ->
                EngineResponse.InfoEval(mate = token.drop(2).toIntOrNull()?.let { -it }, cp = null, raw = raw)
            else ->
                EngineResponse.InfoEval(mate = null, cp = token.toIntOrNull(), raw = raw)
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
                "NODE", "NODES", "N" -> nodes = digits(value)
                "SPEED" -> speed = digits(value)
                else -> {
                    val moves = seg.split(Regex("\\s+")).mapNotNull { Move.fromLabel(it) }
                    if (moves.isNotEmpty() && moves.size == seg.split(Regex("\\s+")).size) line = moves
                }
            }
        }
        return EngineResponse.Thinking(
            depth, selDepth, evalCp, mate, timeMs, nodes, speed, line, raw,
        )
    }

    /** Leading digits of e.g. "12345k" / "1.2M" -> plain count where possible. */
    private fun digits(value: String): Long? =
        Regex("""\d+""").find(value)?.value?.toLongOrNull()

    /** `MESSAGE REALTIME <sub>` overlays. Sub-tokens matched like the desktop. */
    private fun parseRealtime(rest: String, coord: CoordMapper, raw: String): EngineResponse {
        val upper = rest.uppercase()
        return when {
            upper.startsWith("BEST") -> pairOrUnknown(rest.drop(4), coord, raw) { EngineResponse.RealtimeBest(it, raw) }
            upper.startsWith("LOSE") -> pairOrUnknown(rest.drop(4), coord, raw) { EngineResponse.RealtimeLose(it, raw) }
            upper.startsWith("POS") -> pairOrUnknown(rest.drop(3), coord, raw) { EngineResponse.RealtimePos(it, raw) }
            upper.startsWith("DONE") -> pairOrUnknown(rest.drop(4), coord, raw) { EngineResponse.RealtimeDone(it, raw) }
            upper.startsWith("REFRESH") -> EngineResponse.RealtimeRefresh(raw)
            upper.startsWith("PV") -> EngineResponse.RealtimePv(parseMoveList(rest.drop(2).trim(), coord), raw)
            upper.startsWith("VAL") -> EngineResponse.RealtimeVal(rest.drop(3).trim().toIntOrNull() ?: 0, raw)
            else -> EngineResponse.Unknown(raw)
        }
    }

    private inline fun pairOrUnknown(
        rest: String,
        coord: CoordMapper,
        raw: String,
        make: (Move) -> EngineResponse,
    ): EngineResponse {
        val move = coord.parsePair(rest.trim().substringBefore(' '))
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
        val parts = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
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
        val parts = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
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

    /** Space-separated "y,x" tokens (BESTLINE / REALTIME PV). */
    private fun parseMoveList(value: String, coord: CoordMapper): List<Move> =
        value.trim().split(Regex("\\s+"))
            .mapNotNull { if (it.isBlank()) null else coord.parsePair(it) }

    private fun looksLikeAbout(line: String): Boolean =
        line.contains("name=", ignoreCase = true) && line.contains("version=", ignoreCase = true)

    private fun parseAboutFields(line: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        Regex("""(\w+)\s*=\s*"([^"]*)"|(\w+)\s*=\s*([^,]+)""").findAll(line).forEach { m ->
            val key = (m.groupValues[1].ifEmpty { m.groupValues[3] }).trim()
            val v = (m.groupValues[2].ifEmpty { m.groupValues[4] }).trim()
            if (key.isNotEmpty()) out[key] = v
        }
        return out
    }
}
