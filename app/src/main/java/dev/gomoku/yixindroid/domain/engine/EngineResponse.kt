package dev.gomoku.yixindroid.domain.engine

import dev.gomoku.yixindroid.core.model.Move

/**
 * One parsed server line. The type set is ported from the desktop dispatcher
 * (`iochannelout_watch` in main.c), which is the authoritative grammar:
 * coordinates are **row,col ("y,x")**, `INFO …` lines carry the realtime search
 * (assembled by [SearchAggregator]), `MESSAGE REALTIME …` are board overlays.
 */
sealed interface EngineResponse {
    val raw: String

    /** A bare coordinate line = the engine's committed move(s) (1 or 2). */
    data class BestMove(val moves: List<Move>, override val raw: String) : EngineResponse

    // ---- INFO search block (assembled into PvSnapshots by SearchAggregator) ----
    data class InfoPvStart(val index: Int, override val raw: String) : EngineResponse
    data class InfoPvDone(override val raw: String) : EngineResponse
    data class InfoNumPv(val count: Int, override val raw: String) : EngineResponse
    data class InfoDepth(val depth: Int, override val raw: String) : EngineResponse
    data class InfoEval(val mate: Int?, val cp: Int?, override val raw: String) : EngineResponse
    data class InfoWinRate(val winRate: Double, override val raw: String) : EngineResponse
    data class InfoBestline(val line: List<Move>, override val raw: String) : EngineResponse

    /** Numeric search counters if the engine reports them (`INFO NODE/SPEED/TIME`). */
    data class InfoStat(val key: String, val value: Long, override val raw: String) : EngineResponse

    /**
     * Rapfi's human-readable thinking line, observed on a live server:
     * `MESSAGE Depth 2-3 | Eval 814 | Time 1ms | F7 H7`. It appears in the
     * engine's *normal* message mode; the PV here is in **letter labels**, not
     * `y,x`. Parsed as a fallback so depth/eval/PV still show if the detailed
     * mode (`info show_detail 3`) is ever unavailable.
     */
    data class Thinking(
        val depth: Int?,
        val selDepth: Int?,
        val evalCp: Int?,
        val mate: Int?,
        val timeMs: Long?,
        val nodes: Long?,
        val speed: Long?,
        val line: List<Move>,
        override val raw: String,
    ) : EngineResponse

    // ---- MESSAGE REALTIME overlays ----
    data class RealtimeBest(val move: Move, override val raw: String) : EngineResponse
    data class RealtimePos(val move: Move, override val raw: String) : EngineResponse
    data class RealtimeLose(val move: Move, override val raw: String) : EngineResponse
    data class RealtimeDone(val move: Move, override val raw: String) : EngineResponse
    data class RealtimePv(val line: List<Move>, override val raw: String) : EngineResponse
    data class RealtimeVal(val value: Int, override val raw: String) : EngineResponse
    data class RealtimeRefresh(override val raw: String) : EngineResponse

    /** YXSHOWFORBID result: `FORBID` + "yyxx"* + '.' */
    data class Forbid(val cells: List<Move>, override val raw: String) : EngineResponse

    // ---- MESSAGE DATABASE (yixindb), assembled by DatabaseAggregator ----------

    /**
     * One child cell: `MESSAGE DATABASE <y> <x> <tag> <v1> <v2> <v3> <v4> <text>`.
     * [packedTag] holds 1-4 characters packed big-endian (main.c decodes the same
     * int); [fields] keeps the numeric tail verbatim (the desktop skips it) and
     * [text] is the free-form label (`%6s`).
     */
    data class DbCellValue(
        val move: Move,
        val packedTag: Int,
        val fields: List<Int>,
        val text: String,
        override val raw: String,
    ) : EngineResponse

    /** `MESSAGE DATABASE REFRESH` — drop every cell tag before the new set. */
    data class DbRefresh(override val raw: String) : EngineResponse

    /** `MESSAGE DATABASE DONE` — end of one query's cell stream. */
    data class DbDone(override val raw: String) : EngineResponse

    /** `MESSAGE DATABASE ONE <tag> <val> <depth> <bound> [label]`. */
    data class DbOne(
        val tag: Int,
        val value: Int,
        val depth: Int,
        val bound: Int,
        val label: String,
        override val raw: String,
    ) : EngineResponse

    /**
     * `MESSAGE DATABASE TEXT "…"` — the position comment. A comment can span
     * several physical lines, so the closing quote may be missing here; the
     * aggregator keeps consuming raw lines until it appears (as main.c does).
     */
    data class DbTextLine(val body: String, override val raw: String) : EngineResponse

    /** `MESSAGE DATABASE LOAD|SAVE START <file>` / `… DONE`. */
    data class DbFileEvent(
        val saving: Boolean,
        val started: Boolean,
        val file: String,
        override val raw: String,
    ) : EngineResponse

    /** MESSAGE INFO MAX_THREAD_NUM / MAX_HASH_SIZE and similar capabilities. */
    data class Capability(val key: String, val value: String, override val raw: String) : EngineResponse

    data class Ok(override val raw: String) : EngineResponse
    data class Message(val text: String, override val raw: String) : EngineResponse
    data class Debug(val text: String, override val raw: String) : EngineResponse
    data class Error(val text: String, override val raw: String) : EngineResponse
    data class About(val fields: Map<String, String>, override val raw: String) : EngineResponse
    data class Unknown(override val raw: String) : EngineResponse
}
