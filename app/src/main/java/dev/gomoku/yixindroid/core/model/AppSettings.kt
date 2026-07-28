package dev.gomoku.yixindroid.core.model

import kotlinx.serialization.Serializable

/**
 * Every setting the desktop Yixin-Board persists — **all 67 of them**, in the
 * exact order of the two files it writes in `save_setting()` (main.c:11598):
 * `settings.txt` (47 lines) and `settings_dev.txt` (20 lines).
 *
 * Field order here mirrors the file order so the mapping can be read off by eye;
 * the authoritative machine-readable mapping lives in [DesktopSettings], whose
 * list index *is* the line number, and [dev.gomoku.yixindroid.domain.settings.SettingsCodec]
 * renders/parses the desktop text format from it.
 *
 * Units follow the file, not the wire, and the file is not internally consistent:
 *  - lines 7/8 (`time limit`) are **seconds** (main.c divides by 1000 on save),
 *  - line 29 (`time increment`) is **milliseconds** (written raw),
 *  - line 19 (`hash size`) is **megabytes** (`set_hashsize` shifts it to KB).
 * [toEngineParams] does the conversions in one place.
 */
@Serializable
data class AppSettings(
    // ---------------- settings.txt (47 lines) ----------------
    /** 1. board size 10..22 (`rboardsize`). */
    val boardSize: Int = 15,
    /** 2. UI language index: 0 English, 3 Korean, … (`language/<n>.lng`). */
    val language: Int = 0,
    /** 3. rule as stored: 0 freestyle, 1 standard, 2 free renju, 3 swap-1,
     *     4 Yamaguchi/RIF, 5 Soosorv-8, 6 swap-2. See [engineRule]. */
    val rule: Int = 2,
    /** 4. computer plays black. */
    val computerBlack: Boolean = true,
    /** 5. computer plays white. */
    val computerWhite: Boolean = false,
    /** 6. 0 unlimited time, 1 custom, 2..12 predefined. */
    val level: Int = 0,
    /** 7. turn time limit, **seconds**. */
    val timeoutTurnSec: Int = 2000,
    /** 8. match time limit, **seconds**. */
    val timeoutMatchSec: Int = 100000,
    /** 9. max search depth (plies). */
    val maxDepth: Int = 100,
    /** 10. max nodes. */
    val maxNode: Long = 1_000_000_000,
    /** 11. style: rash 0 .. cautious 5 (`caution_factor`). */
    val style: Int = 3,
    /** 12. toolbar style — desktop only. */
    val toolbarStyle: Int = 2,
    /** 13. show the log pane (the app's raw console). */
    val showLog: Boolean = true,
    /** 14. draw move numbers on stones. */
    val showNumber: Boolean = true,
    /** 15. draw the analysis overlay. */
    val showAnalysis: Boolean = true,
    /** 16. draw win rates in the analysis overlay. */
    val showAnalysisWinrate: Boolean = true,
    /** 17. warn before destructive board actions. */
    val showWarning: Boolean = true,
    /** 18. engine threads. */
    val threadNum: Int = 4,
    /** 19. hash size, **MB**. */
    val hashSizeMb: Int = 8192,
    /** 20. default multi-PV count (`yxnbest`). */
    val multiPv: Int = 3,
    /** 21. auto-reset blocked cells — desktop block feature (P10). */
    val blockAutoReset: Boolean = true,
    /** 22. auto-reset blocked paths — desktop block feature (P10). */
    val blockPathAutoReset: Boolean = false,
    /** 23. pondering. */
    val pondering: Boolean = false,
    /** 24. extra threat check in global search: 0 none, 1 VCT, 2 VC2. */
    val vcThread: Int = 0,
    /** 25. clear the hash before each search (`yxhashclear`). */
    val hashAutoClear: Boolean = false,
    /** 26. toolbar position — desktop only. */
    val toolbarPos: Int = 0,
    /** 27. dark mode. */
    val darkMode: Boolean = true,
    /** 28. show the game clock. */
    val showClock: Boolean = false,
    /** 29. time increment per move, **milliseconds**. */
    val incrementMs: Int = 0,
    /** 30. show renju forbidden points. */
    val showForbidden: Boolean = true,
    /** 31. warn when the player runs out of time (`show_dialog_timeout`). */
    val checkTimeout: Boolean = false,
    /** 32. use the engine's yixindb database. */
    val useDatabase: Boolean = true,
    /** 33. database read-only. Always pushed to the engine — a server-side
     *      `readonly = true` otherwise silently discards results (main.c:14467). */
    val databaseReadonly: Boolean = false,
    /** 34. draw database texts on the board (P7). */
    val showBoardText: Boolean = true,
    /** 35. confirm "delete all branches" (P7). */
    val showDbDelConfirm: Boolean = true,
    /** 36. record a debug log. */
    val recordDebugLog: Boolean = false,
    /** 37. log area scale, percent (desktop hdpi scale ×100). */
    val logScale: Int = 140,
    /** 38. symmetric nbest for 5th moves (`info nbestsym`). */
    val nbestSym: Boolean = false,
    /** 39..43 colour saturations / value for the analysis tags. */
    val lossSaturation: Int = 0,
    val winSaturation: Int = 83,
    val minSaturation: Int = 20,
    val maxSaturation: Int = 80,
    val colorValue: Int = 100,
    /** 44..47 fonts — desktop only (kept verbatim so the files round-trip). */
    val boardTextFont: String = "SimHei, sans-serif 12",
    val textLogFont: String = "Sarasa Mono SC, Cascadia Mono, SimHei, monospace 9",
    val dbCommentFont: String = "SimHei, sans-serif 12",
    val dbCommentFont2: String = "Courier New, monospace 10",

    // ---------------- settings_dev.txt (20 lines) ----------------
    /** 1. show the evaluation bar. */
    val showEvalBar: Boolean = true,
    /** 2. show the win-rate graph. */
    val showWrGraph: Boolean = true,
    /** 3. move-quality preset: 0 strict, 1 default, 2 lenient (P8). */
    val mqPreset: Int = 1,
    /** 4. move-quality badges on stones (P8). */
    val showMoveBadge: Boolean = true,
    /** 5. prove mode initial budget per node, seconds (P8). */
    val proveBudget0Sec: Int = 10,
    /** 6. prove mode budget cap, seconds (P8). */
    val proveBudgetMaxSec: Int = 320,
    /** 7. prove mode attacker candidates (P8). */
    val proveNbest: Int = 1,
    /** 8. board zoom, percent 60..300. */
    val boardZoomPercent: Int = 100,
    /** 9. **reserved slot** (was "fit board to window"). The desktop parses these
     *     files by position, so this line is read and written but never reused. */
    val reservedFitBoard: Int = 0,
    /** 10. periodic `YXSAVEDATABASE` (P7). */
    val dbAutoSave: Boolean = true,
    /** 11. auto-save interval, minutes (P7). */
    val dbAutoSaveMinutes: Int = 5,
    /** 12. prove: best attack move first (P8). */
    val proveBestFirst: Boolean = true,
    /** 13. prove: early probe of the strongest defense (P8). */
    val proveProbe: Boolean = true,
    /** 14. review budget unit: 0 seconds, 1 depth (P8). */
    val reviewByDepth: Boolean = false,
    /** 15. review fixed depth per move (P8). */
    val reviewDepth: Int = 14,
    /** 16. prove budget unit: 0 seconds, 1 depth (P8). */
    val proveByDepth: Boolean = false,
    /** 17. prove initial depth per node (P8). */
    val proveDepth0: Int = 15,
    /** 18. prove depth cap per node (P8). */
    val proveDepthMax: Int = 30,
    /** 19. skip grading/searching opening moves 1-5 (P8). */
    val skipOpening: Boolean = true,
    /** 20. one-time dark+Korean defaults marker — written, never edited. */
    val devDefaults: Boolean = true,
) {
    /**
     * Rule as the engine wants it (`INFO rule`): only 0/1/2 exist there. The
     * opening rules are a GUI layer on top of a base rule, exactly as
     * `load_setting` decodes them (main.c:14067).
     */
    val engineRule: Int
        get() = when (rule) {
            3 -> 0      // swap after first move — freestyle base
            4, 5 -> 2   // Yamaguchi / Soosorv-8 — free renju base
            6 -> 1      // swap-2 — standard base
            else -> rule.coerceIn(0, 2)
        }

    /** True when forbidden points exist at all (renju bases). */
    val isRenju: Boolean get() = engineRule == 2

    /**
     * The opening negotiation on top of the base rule — the desktop's
     * `specialrule`, decoded exactly as `load_setting` does (main.c:14070).
     */
    val opening: OpeningProtocol
        get() = when (rule) {
            3 -> OpeningProtocol.SWAP_FIRST
            4 -> OpeningProtocol.RIF
            5 -> OpeningProtocol.SOOSORV
            6 -> OpeningProtocol.SWAP2
            else -> OpeningProtocol.NONE
        }

    /** An overline wins under every rule except standard gomoku (main.c:2236). */
    val allowsOverlineWin: Boolean get() = engineRule != 1

    /**
     * The opening protocols measure from the exact centre (`boardsize / 2`), so
     * an even board has no centre point to open on.
     */
    val openingNeedsOddSize: Boolean
        get() = opening != OpeningProtocol.NONE && boardSize % 2 == 0

    /** Which colours the engine plays (settings.txt lines 4-5). */
    val computerSide: ComputerSide get() = ComputerSide.of(computerBlack, computerWhite)

    /** The engine-facing subset, with the file's units converted to the wire's. */
    fun toEngineParams(): EngineParams = EngineParams(
        rule = engineRule,
        boardSize = boardSize,
        level = level,
        timeoutTurnMs = timeoutTurnSec.toLong() * 1000,
        timeoutMatchMs = timeoutMatchSec.toLong() * 1000,
        maxDepth = maxDepth,
        maxNode = maxNode,
        incrementMs = incrementMs,
        cautionFactor = style,
        threadNum = threadNum,
        hashSizeMb = hashSizeMb,
        pondering = if (pondering) 1 else 0,
        vcThread = vcThread,
        multiPv = multiPv,
        useDatabase = useDatabase,
        databaseReadonly = databaseReadonly,
        nbestSym = nbestSym,
    )
}
