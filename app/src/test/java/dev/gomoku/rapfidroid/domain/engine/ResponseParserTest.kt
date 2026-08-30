package dev.gomoku.rapfidroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.Move
import org.junit.Test

class ResponseParserTest {

    private val coord = CoordMapper()

    @Test
    fun bestMoveIsRowCol() {
        // "row,col" 7,8 -> Move(x=8, y=7)
        val r = ResponseParser.parse("7,8", coord) as EngineResponse.BestMove
        assertThat(r.moves).containsExactly(Move(x = 8, y = 7))
    }

    @Test
    fun bestMoveDoublePair() {
        val r = ResponseParser.parse("7,7 8,8", coord) as EngineResponse.BestMove
        assertThat(r.moves).containsExactly(Move(x = 7, y = 7), Move(x = 8, y = 8)).inOrder()
    }

    @Test
    fun infoBlockTypes() {
        assertThat(ResponseParser.parse("INFO PV 0", coord))
            .isEqualTo(EngineResponse.InfoPvStart(0, "INFO PV 0"))
        assertThat(ResponseParser.parse("INFO PV DONE", coord))
            .isInstanceOf(EngineResponse.InfoPvDone::class.java)
        assertThat((ResponseParser.parse("INFO DEPTH 12-30", coord) as EngineResponse.InfoDepth).depth)
            .isEqualTo(12)
        assertThat((ResponseParser.parse("INFO WINRATE 0.62", coord) as EngineResponse.InfoWinRate).winRate)
            .isWithin(1e-9).of(0.62)
    }

    @Test
    fun evalMateAndCp() {
        assertThat((ResponseParser.parse("INFO EVAL +M5", coord) as EngineResponse.InfoEval).mate).isEqualTo(5)
        assertThat((ResponseParser.parse("INFO EVAL -M3", coord) as EngineResponse.InfoEval).mate).isEqualTo(-3)
        val cp = ResponseParser.parse("INFO EVAL 45", coord) as EngineResponse.InfoEval
        assertThat(cp.mate).isNull()
        assertThat(cp.cp).isEqualTo(45)
    }

    @Test
    fun bestlineIsMoveList() {
        val r = ResponseParser.parse("INFO BESTLINE 7,7 6,8", coord) as EngineResponse.InfoBestline
        assertThat(r.line).containsExactly(Move(x = 7, y = 7), Move(x = 8, y = 6)).inOrder()
    }

    @Test
    fun realtimeAndForbid() {
        val best = ResponseParser.parse("MESSAGE REALTIME BEST 7,7", coord) as EngineResponse.RealtimeBest
        assertThat(best.move).isEqualTo(Move(x = 7, y = 7))

        // FORBID + "yyxx"* + '.': 0707 -> (7,7), 0810 -> row8,col10 -> Move(10,8)
        val forbid = ResponseParser.parse("FORBID07070810.", coord) as EngineResponse.Forbid
        assertThat(forbid.cells).containsExactly(Move(x = 7, y = 7), Move(x = 10, y = 8)).inOrder()
    }

    @Test
    fun okErrorMessageAbout() {
        assertThat(ResponseParser.parse("OK", coord)).isInstanceOf(EngineResponse.Ok::class.java)
        assertThat((ResponseParser.parse("ERROR bad cmd", coord) as EngineResponse.Error).text).isEqualTo("bad cmd")
        assertThat((ResponseParser.parse("MESSAGE hi there", coord) as EngineResponse.Message).text).isEqualTo("hi there")
        val about = ResponseParser.parse("""name="Rapfi", version="0.43"""", coord) as EngineResponse.About
        assertThat(about.fields["name"]).isEqualTo("Rapfi")
    }
}
