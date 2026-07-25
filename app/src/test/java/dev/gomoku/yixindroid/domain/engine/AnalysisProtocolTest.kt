package dev.gomoku.yixindroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.CandidateState
import dev.gomoku.yixindroid.core.model.EngineParams
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.StoneColor
import dev.gomoku.yixindroid.core.model.TagKind
import org.junit.Test

/**
 * P6 protocol regressions. The bug this locks down: the app only sent `START`,
 * so Rapfi stayed in its plain message mode and never emitted the `INFO PV …`
 * blocks the board is built from. The desktop sends `info show_detail 3` +
 * `yxshowinfo` in `init_engine()` (main.c:14465).
 */
class AnalysisProtocolTest {

    private val coord = CoordMapper()
    private fun line(text: String) = ResponseParser.parse(text, coord)

    // ---- the commands that switch the engine into detailed output ----

    @Test
    fun showDetailAndShowInfoSerializeLikeTheDesktop() {
        assertThat(EngineCommand.ShowDetail(3).serialize(coord)).isEqualTo("info show_detail 3")
        assertThat(EngineCommand.YxShowInfo.serialize(coord)).isEqualTo("yxshowinfo")
        assertThat(EngineCommand.DatabaseReadonly(false).serialize(coord))
            .isEqualTo("info database_readonly 0")
        assertThat(EngineCommand.DatabaseReadonly(true).serialize(coord))
            .isEqualTo("info database_readonly 1")
    }

    // ---- engine parameters: must match the desktop settings.txt defaults ----

    @Test
    fun defaultParamsMatchTheDesktopSettings() {
        val pairs = EngineParams().infoPairs()
        val map = pairs.toMap()
        // settings.txt line 3 = 2 (free renju). Getting this wrong made the
        // engine load the freestyle weights and disagree with the PC entirely.
        assertThat(map["rule"]).isEqualTo("2")
        assertThat(pairs.first().first).isEqualTo("rule") // sent before START
        assertThat(map["thread_num"]).isEqualTo("4")      // line 18
        assertThat(map["caution_factor"]).isEqualTo("3")  // line 11 (style)
        // set_hashsize sends megabytes shifted into kilobytes: 8192 << 10
        assertThat(map["hash_size"]).isEqualTo((8192L shl 10).toString())
        assertThat(map["pondering"]).isEqualTo("0")
        assertThat(map["vcthread"]).isEqualTo("0")
        // level 0 -> the predefined branch: unlimited nodes, board-sized depth
        assertThat(map["max_node"]).isEqualTo("-1")
        assertThat(map["max_depth"]).isEqualTo("225")
        assertThat(map["timeout_turn"]).isEqualTo("2000000")
        assertThat(map["timeout_match"]).isEqualTo("100000000")
    }

    @Test
    fun customLevelUsesTheCustomTimesAndDepth() {
        val map = EngineParams(level = 1, timeoutTurnMs = 2_000, maxDepth = 100, maxNode = 12345)
            .infoPairs().toMap()
        assertThat(map["timeout_turn"]).isEqualTo("2000")
        assertThat(map["max_depth"]).isEqualTo("100")
        assertThat(map["max_node"]).isEqualTo("12345")
    }

    @Test
    fun predefinedLevelPicksTheNodeTable() {
        // main.c max_node_values[5] = 5M
        assertThat(EngineParams(level = 5).infoPairs().toMap()["max_node"]).isEqualTo("5000000")
    }

    // ---- observed plain-text thinking line (fallback path) ----

    @Test
    fun plainThinkingLineIsParsed() {
        val r = line("MESSAGE Depth 2-3 | Eval 814 | Time 1ms | F7 H7")
        assertThat(r).isInstanceOf(EngineResponse.Thinking::class.java)
        val t = r as EngineResponse.Thinking
        assertThat(t.depth).isEqualTo(2)
        assertThat(t.selDepth).isEqualTo(3)
        assertThat(t.evalCp).isEqualTo(814)
        assertThat(t.timeMs).isEqualTo(1L)
        assertThat(t.line).containsExactly(Move.fromLabel("F7"), Move.fromLabel("H7")).inOrder()
    }

    @Test
    fun plainThinkingMateIsParsed() {
        val t = line("MESSAGE Depth 8-9 | Eval +M3 | Time 20ms | H8") as EngineResponse.Thinking
        assertThat(t.mate).isEqualTo(3)
        assertThat(t.evalCp).isNull()
    }

    @Test
    fun ordinaryMessageStaysAMessage() {
        assertThat(line("MESSAGE Evaluator set to mix9svq."))
            .isInstanceOf(EngineResponse.Message::class.java)
        assertThat(line("MESSAGE DATABASE LOAD DONE"))
            .isInstanceOf(EngineResponse.Message::class.java)
    }

    @Test
    fun thinkingFallbackFeedsTheAggregator() {
        val agg = SearchAggregator(StoneColor.BLACK)
        val snap = agg.consume(line("MESSAGE Depth 4-4 | Eval 689 | Time 1ms | F9 G10"))
        assertThat(snap).isNotNull()
        assertThat(snap!!.depth).isEqualTo(4)
        assertThat(snap.stats.evalCp).isEqualTo(689)
        assertThat(snap.best?.line).containsExactly(Move.fromLabel("F9"), Move.fromLabel("G10"))
            .inOrder()
    }

    // ---- per-cell tags, ported from main.c INFO PV DONE ----

    @Test
    fun pvDoneTagsTheHeadOfTheBestline() {
        val agg = SearchAggregator(StoneColor.BLACK)
        agg.consume(line("INFO NUMPV 1"))
        agg.consume(line("INFO PV 0"))
        agg.consume(line("INFO DEPTH 10"))
        agg.consume(line("INFO WINRATE 0.62"))
        agg.consume(line("INFO BESTLINE 7,7 6,8"))
        val snap = agg.consume(line("INFO PV DONE"))!!

        val head = coord.fromWire(7, 7)
        assertThat(snap.tags).containsKey(head)
        assertThat(snap.tags[head]!!.label).isEqualTo("62%")
        assertThat(snap.tags[head]!!.kind).isEqualTo(TagKind.RATE)
        assertThat(snap.tags[head]!!.depth).isEqualTo(10)
    }

    @Test
    fun mateShowsAsWinOrLossTag() {
        val agg = SearchAggregator(StoneColor.BLACK)
        agg.consume(line("INFO PV 0"))
        agg.consume(line("INFO DEPTH 6"))
        agg.consume(line("INFO EVAL +M5"))
        agg.consume(line("INFO BESTLINE 7,7"))
        var snap = agg.consume(line("INFO PV DONE"))!!
        assertThat(snap.tags[coord.fromWire(7, 7)]!!.label).isEqualTo("W5")
        assertThat(snap.tags[coord.fromWire(7, 7)]!!.kind).isEqualTo(TagKind.WIN)

        agg.consume(line("INFO PV 0"))
        agg.consume(line("INFO DEPTH 6"))
        agg.consume(line("INFO EVAL -M4"))
        agg.consume(line("INFO BESTLINE 8,8"))
        snap = agg.consume(line("INFO PV DONE"))!!
        assertThat(snap.tags[coord.fromWire(8, 8)]!!.label).isEqualTo("L4")
        assertThat(snap.tags[coord.fromWire(8, 8)]!!.kind).isEqualTo(TagKind.LOSE)
    }

    @Test
    fun shallowTagsAreClearedOnTheLastPvOfARound() {
        val agg = SearchAggregator(StoneColor.BLACK)
        agg.consume(line("INFO NUMPV 1"))
        // shallow iteration tags A
        agg.consume(line("INFO PV 0"))
        agg.consume(line("INFO DEPTH 4"))
        agg.consume(line("INFO WINRATE 0.50"))
        agg.consume(line("INFO BESTLINE 3,3"))
        agg.consume(line("INFO PV DONE"))
        // deeper iteration tags B -> A must be dropped
        agg.consume(line("INFO PV 0"))
        agg.consume(line("INFO DEPTH 9"))
        agg.consume(line("INFO WINRATE 0.55"))
        agg.consume(line("INFO BESTLINE 4,4"))
        val snap = agg.consume(line("INFO PV DONE"))!!

        assertThat(snap.tags).containsKey(coord.fromWire(4, 4))
        assertThat(snap.tags).doesNotContainKey(coord.fromWire(3, 3))
    }

    // ---- realtime overlays ----

    @Test
    fun realtimeOverlaysTrackCandidatesAndLosses() {
        val agg = SearchAggregator(StoneColor.BLACK)
        agg.consume(line("MESSAGE REALTIME POS 7,7"))
        agg.consume(line("MESSAGE REALTIME POS 6,8"))
        agg.consume(line("MESSAGE REALTIME LOSE 1,1"))
        agg.consume(line("MESSAGE REALTIME VAL 640"))
        var snap = agg.consume(line("MESSAGE REALTIME BEST 7,7"))!!

        assertThat(snap.candidates[coord.fromWire(7, 7)]).isEqualTo(CandidateState.LIVE)
        assertThat(snap.loseCells).contains(coord.fromWire(1, 1))
        assertThat(snap.stats.realtimeVal).isEqualTo(640)
        assertThat(snap.realtimeBest).isEqualTo(coord.fromWire(7, 7))

        snap = agg.consume(line("MESSAGE REALTIME DONE 7,7"))!!
        assertThat(snap.candidates[coord.fromWire(7, 7)]).isEqualTo(CandidateState.DONE)

        snap = agg.consume(line("MESSAGE REALTIME REFRESH"))!!
        assertThat(snap.candidates).isEmpty()
    }

    @Test
    fun partialSnapshotsAreEmittedBeforePvDone() {
        val agg = SearchAggregator(StoneColor.BLACK)
        agg.consume(line("INFO PV 0"))
        // depth alone must already produce a snapshot so the UI reacts promptly
        val snap = agg.consume(line("INFO DEPTH 7"))
        assertThat(snap).isNotNull()
        assertThat(snap!!.stats.depth).isEqualTo(7)
    }
}
