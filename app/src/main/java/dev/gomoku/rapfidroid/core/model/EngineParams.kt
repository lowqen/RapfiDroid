package dev.gomoku.rapfidroid.core.model

/**
 * The engine-side parameters the desktop pushes on connect. Defaults mirror the
 * deployed `test-yixin/settings.txt` so the app analyses **the same way the PC
 * does** — without these Rapfi falls back to its own config (freestyle rule,
 * default threads/hash) and every score, best move and depth differs.
 *
 * Field order and command spelling follow main.c (`set_rule`, `set_level`,
 * `set_cautionfactor`, `set_threadnum`, `set_hashsize`, `set_pondering`,
 * `set_vcthread`, plus `info usedatabase` / `info nbestsym`). Build one from the
 * user's settings with [AppSettings.toEngineParams]; the standalone defaults
 * exist so the engine is never left unconfigured before the settings load.
 */
data class EngineParams(
    /** Engine rule: 0 freestyle, 1 standard, 2 free renju (settings.txt line 3). */
    val rule: Int = 2,
    val boardSize: Int = Move.DEFAULT_SIZE,
    /** 0 = unlimited time, 1 = custom, 2..12 = predefined (settings.txt line 6). */
    val level: Int = 0,
    /** Used only when [level] == 1. Milliseconds (settings.txt stores seconds). */
    val timeoutTurnMs: Long = 2_000_000,
    val timeoutMatchMs: Long = 100_000_000,
    val maxDepth: Int = 100,
    val maxNode: Long = 1_000_000_000,
    /** Milliseconds; settings.txt line 29 already stores ms. */
    val incrementMs: Int = 0,
    /** Style, rash 0 .. cautious 5 (settings.txt line 11). */
    val cautionFactor: Int = 3,
    val threadNum: Int = 4,
    /** Matches [AppSettings.hashSizeMb]: 1024, not the deployed file's 8192. */
    val hashSizeMb: Int = 1024,
    val pondering: Int = 0,
    /** Additional threat check in global search: 0 none, 1 VCT, 2 VC2. */
    val vcThread: Int = 0,
    /** Default multi-PV count (settings.txt line 20). Not an `INFO` key: the
     *  desktop passes it as `yxnbest <n>` per search. */
    val multiPv: Int = 3,
    /** `info usedatabase` (settings.txt line 32). */
    val useDatabase: Boolean = true,
    /** `info database_readonly` (settings.txt line 33) — always pushed. */
    val databaseReadonly: Boolean = false,
    /** `info nbestsym` (settings.txt line 38). */
    val nbestSym: Boolean = false,
) {
    /**
     * `INFO key value` pairs in the desktop's order. `INFO rule` comes first so the
     * following `START` initialises with the right rule (the desktop flags a
     * restart when the rule changes).
     */
    fun infoPairs(): List<Pair<String, String>> = buildList {
        add("rule" to rule.coerceIn(0, 2).toString())
        addAll(levelPairs())
        add("caution_factor" to cautionFactor.coerceIn(0, MAX_CAUTION).toString())
        add("thread_num" to threadNum.coerceAtLeast(1).toString())
        // main.c set_hashsize sends megabytes shifted into kilobytes.
        add("hash_size" to (hashSizeMb.coerceAtLeast(1).toLong() shl 10).toString())
        add("pondering" to pondering.coerceIn(0, 1).toString())
        add("vcthread" to vcThread.coerceIn(0, 2).toString())
        add("usedatabase" to bit(useDatabase))
        // Never inherited from the engine's config: a server-side readonly = true
        // silently discards every search result and DB edit (main.c:14467).
        add("database_readonly" to bit(databaseReadonly))
        add("nbestsym" to bit(nbestSym))
    }

    /**
     * `set_level` (main.c:2922): level 1 uses the custom values, everything else
     * the predefined node table with unlimited time. Public because a game
     * review overrides these for the run and has to put them back afterwards,
     * exactly as the desktop calls `set_level(levelchoice)` when it finishes.
     */
    fun levelPairs(): List<Pair<String, String>> =
        if (level == 1) {
            listOf(
                "timeout_turn" to timeoutTurnMs.toString(),
                "timeout_match" to timeoutMatchMs.toString(),
                "time_left" to timeoutMatchMs.toString(),
                "max_node" to maxNode.toString(),
                "max_depth" to maxDepth.toString(),
                "time_increment" to incrementMs.toString(),
            )
        } else {
            val nodes = MAX_NODE_BY_LEVEL.getOrElse(level) { -1L }
            listOf(
                "max_node" to nodes.toString(),
                "timeout_match" to PRESET_MATCH_MS.toString(),
                "time_left" to PRESET_MATCH_MS.toString(),
                "timeout_turn" to PRESET_TURN_MS.toString(),
                "max_depth" to (boardSize * boardSize).toString(),
                "time_increment" to "0",
            )
        }

    private fun bit(on: Boolean) = if (on) "1" else "0"

    /**
     * Rule and board size are baked in by `START`, so changing either needs a
     * fresh handshake rather than a plain `INFO` push (the desktop raises
     * `isneedrestart` for exactly these).
     */
    fun needsRestart(other: EngineParams): Boolean =
        rule != other.rule || boardSize != other.boardSize

    companion object {
        private const val MAX_CAUTION = 5
        private const val PRESET_MATCH_MS = 100_000_000L
        private const val PRESET_TURN_MS = 2_000_000L

        /** `max_node_values` from main.c set_level (index = level). */
        private val MAX_NODE_BY_LEVEL = longArrayOf(
            -1, -1, 100_000, 500_000, 1_000_000, 5_000_000, 10_000_000, 20_000_000,
            50_000_000, 100_000_000, 200_000_000, 500_000_000, 1_000_000_000,
        )
    }
}
