package dev.gomoku.yixindroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.Move
import org.junit.Test

class ResponseParserTest {

    private val coord = CoordMapper()

    @Test
    fun bestMove_coordinate() {
        val r = ResponseParser.parse("7,7", coord)
        assertThat(r).isInstanceOf(EngineResponse.BestMove::class.java)
        assertThat((r as EngineResponse.BestMove).move).isEqualTo(Move(7, 7))
    }

    @Test
    fun bestMove_respectsFlip() {
        val flipped = CoordMapper(flipY = true)
        val r = ResponseParser.parse("0,14", flipped) as EngineResponse.BestMove
        assertThat(r.move).isEqualTo(Move(0, 0))
    }

    @Test
    fun ok_error_message_debug() {
        assertThat(ResponseParser.parse("OK", coord)).isInstanceOf(EngineResponse.Ok::class.java)

        val err = ResponseParser.parse("ERROR unknown command", coord)
        assertThat((err as EngineResponse.Error).text).isEqualTo("unknown command")

        val msg = ResponseParser.parse("MESSAGE realtime depth 12", coord)
        assertThat((msg as EngineResponse.Message).text).isEqualTo("realtime depth 12")

        val dbg = ResponseParser.parse("DEBUG loaded config", coord)
        assertThat((dbg as EngineResponse.Debug).text).isEqualTo("loaded config")
    }

    @Test
    fun about_fields_parsed() {
        val line = """name="Rapfi", version="0.43.02", author="Rapfi Team", country="CN""""
        val about = ResponseParser.parse(line, coord) as EngineResponse.About
        assertThat(about.fields["name"]).isEqualTo("Rapfi")
        assertThat(about.fields["version"]).isEqualTo("0.43.02")
        assertThat(about.fields["author"]).isEqualTo("Rapfi Team")
    }

    @Test
    fun junk_is_unknown_not_a_move() {
        assertThat(ResponseParser.parse("SUGGEST something", coord))
            .isInstanceOf(EngineResponse.Unknown::class.java)
        assertThat(ResponseParser.parse("", coord))
            .isInstanceOf(EngineResponse.Unknown::class.java)
    }
}
