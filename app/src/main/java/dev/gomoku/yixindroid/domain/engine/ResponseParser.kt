package dev.gomoku.yixindroid.domain.engine

/**
 * Pure function: one server line -> one [EngineResponse]. No I/O, no state, so
 * it is fully unit-testable on the JVM (see ResponseParserTest). Keeping this
 * side-effect free is the whole point of the P1 raw-console methodology —
 * capture real lines, then grow this parser against them.
 */
object ResponseParser {

    private val coordinate = Regex("""^(\d+)\s*,\s*(\d+)$""")

    fun parse(rawLine: String, coord: CoordMapper): EngineResponse {
        val raw = rawLine
        val line = rawLine.trim()

        coordinate.matchEntire(line)?.let { m ->
            val x = m.groupValues[1].toInt()
            val y = m.groupValues[2].toInt()
            return EngineResponse.BestMove(coord.fromWire(x, y), raw)
        }

        val upper = line.uppercase()
        return when {
            line.isEmpty() -> EngineResponse.Unknown(raw)
            upper == "OK" -> EngineResponse.Ok(raw)
            upper.startsWith("ERROR") ->
                EngineResponse.Error(line.drop(5).trim(), raw)
            upper.startsWith("MESSAGE") ->
                EngineResponse.Message(line.drop(7).trim(), raw)
            upper.startsWith("DEBUG") ->
                EngineResponse.Debug(line.drop(5).trim(), raw)
            looksLikeAbout(line) ->
                EngineResponse.About(parseAboutFields(line), raw)
            else -> EngineResponse.Unknown(raw)
        }
    }

    private fun looksLikeAbout(line: String): Boolean =
        line.contains("name=", ignoreCase = true) &&
            line.contains("version=", ignoreCase = true)

    /** Parse `key="value", key2="value2"` into a map (quotes optional). */
    private fun parseAboutFields(line: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        Regex("""(\w+)\s*=\s*"([^"]*)"|(\w+)\s*=\s*([^,]+)""")
            .findAll(line)
            .forEach { m ->
                val key = (m.groupValues[1].ifEmpty { m.groupValues[3] }).trim()
                val value = (m.groupValues[2].ifEmpty { m.groupValues[4] }).trim()
                if (key.isNotEmpty()) out[key] = value
            }
        return out
    }
}
