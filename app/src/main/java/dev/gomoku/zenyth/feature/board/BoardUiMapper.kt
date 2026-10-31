package dev.gomoku.zenyth.feature.board

import dev.gomoku.zenyth.core.designsystem.component.BoardRender
import dev.gomoku.zenyth.core.designsystem.component.DbLabel
import dev.gomoku.zenyth.core.designsystem.component.ReviewFocus
import dev.gomoku.zenyth.core.designsystem.component.TagPalette
import dev.gomoku.zenyth.core.i18n.AppLanguage
import dev.gomoku.zenyth.core.i18n.appLanguage
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.AnalysisSnapshot
import dev.gomoku.zenyth.core.model.AppSettings
import dev.gomoku.zenyth.core.model.BoardMode
import dev.gomoku.zenyth.core.model.BoardTransform
import dev.gomoku.zenyth.core.model.DbCell
import dev.gomoku.zenyth.core.model.FontSpec
import dev.gomoku.zenyth.core.model.FunctionScripts
import dev.gomoku.zenyth.core.model.GameReport
import dev.gomoku.zenyth.core.model.GradingPreset
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.MoveGrader
import dev.gomoku.zenyth.core.model.MoveQuality
import dev.gomoku.zenyth.core.model.OpeningEval
import dev.gomoku.zenyth.core.model.OpeningName
import dev.gomoku.zenyth.core.model.OpeningTables
import dev.gomoku.zenyth.core.model.Position
import dev.gomoku.zenyth.core.model.PositionRecord
import dev.gomoku.zenyth.core.model.ProvePhase
import dev.gomoku.zenyth.core.model.ReviewProgress
import dev.gomoku.zenyth.core.model.StoneColor
import dev.gomoku.zenyth.feature.board.BoardViewModel.Panel
import dev.gomoku.zenyth.feature.board.BoardViewModel.Read
import kotlinx.coroutines.flow.map

/**
 * The board screen's state, built from what [BoardViewModel] holds (Phase 7-5 of
 * docs/리팩토링-성능-위생-계획.md). The view model drives the engine, the game and
 * the database; this turns the board, the reading, the panel and the settings
 * into [BoardUiState]. Moved out of the view model as it was.
 *
 * [liveRecords] is the view model's evaluation history as it stands when a badge
 * is graded — read then, as it always was, not taken from the emission.
 */
internal class BoardUiMapper(private val liveRecords: () -> List<PositionRecord>) {

    fun map(
        pos: Position,
        read: Read?,
        p: Panel,
        config: AppSettings,
        history: List<PositionRecord>,
    ): BoardUiState {
        val current = read?.takeIf { it.moves == pos.moves }?.snap
        val hasData = read != null && (read.snap.pvs.isNotEmpty() || read.snap.stats.depth > 0)
        val stale = current == null && hasData
        val analysisTab = p.mode == BoardMode.ANALYSIS
        val quality = if (analysisTab) badgeFor(pos, p.report, config) else null
        return BoardUiState(
            render = buildRender(pos, current, p, config, quality),
            moveCount = pos.moves.size,
            connection = p.connection,
            mode = p.mode,
            analyzing = p.analyzing,
            defending = p.defending,
            shown = current ?: read?.snap?.takeIf { stale },
            stale = stale,
            staleAt = if (stale) read.moves.size else 0,
            eval = if (analysisTab) evalFor(pos, read, p) else null,
            multiPv = config.multiPv,
            previewPv = p.preview,
            winRateHistory = if (config.showWrGraph) winRates(history) else emptyList(),
            analysisSec = p.analysisSec,
            playBestOnStop = p.playBest,
            showEvalBar = config.showEvalBar,
            showWrGraph = config.showWrGraph,
            showWarning = config.showWarning,
            boardZoomPercent = config.boardZoomPercent,
            openingNames = openingNames(NamesKey(pos.moves, pos.size, appLanguage(), OpeningTables.names)),
            openingGrade = openingGrade(GradeKey(pos.moves, pos.size, OpeningTables.evals)),
            moveGrade = quality?.takeIf { it != MoveQuality.NONE },
            gameName = p.gameName,
            linkBackup = p.linkBackup,
            db = p.db,
            dbValue = p.db.value,
            notice = p.notice,
            futureCount = p.future.size,
            balancing = p.balancing,
            positionString = positionString(BoardKey(pos.moves, pos.size)),
            game = p.game,
            moveTimeMs = GameLevels.moveTimeOf(config.level, config.timeoutTurnSec),
            strength = GameLevels.strengthOf(config.level, config.maxNode),
            sideToMove = pos.sideToMove,
            showForbidden = config.showForbidden,
            isRenju = config.isRenju,
            rule = config.rule,
            showClock = config.showClock,
            openingNeedsOddSize = config.openingNeedsOddSize,
            research = researchRun(pos, p),
            toolbar = toolbar(p.toolbar),
            language = p.language,
            toolbarStyle = config.toolbarStyle,
            dbCommentFont = fromSettings(config).dbCommentFont,
        )
    }

    /**
     * The eval bar: this position's search, or one of the positions before it —
     * after a move the bar keeps the last value until a new one arrives, but a
     * value from *after* an undone move is never shown. Without a search, the
     * database's value for this position.
     */
    private fun evalFor(pos: Position, read: Read?, p: Panel): EvalView? {
        val snap = read?.takeIf { it.moves.size <= pos.moves.size && pos.moves.subList(0, it.moves.size) == it.moves }?.snap
        val rate = snap?.blackWinRate()
        val live = p.analyzing && rate != null
        val db = p.db.value
        if (!live && db != null && p.db.enabled) {
            return EvalView(db.blackWinRate * 100, mate = db.blackMate, fromDb = true)
        }
        if (snap == null || rate == null) return null
        if (pos.moves.isEmpty() && !p.analyzing) return null
        val mate = snap.blackMate()?.takeIf { it != 0 }
        val black = snap.sideToMove == StoneColor.BLACK
        val cp = snap.stats.evalCp?.let { if (black) it else -it }
        return EvalView(rate * 100, evalCp = cp, mate = mate)
    }

    /** The bar over the tabs while a review or a proof runs; see [ResearchRun]. */
    private fun researchRun(pos: Position, panel: Panel): ResearchRun? = when {
        panel.proveProgress.running -> proveRun(pos, panel)
        panel.reviewProgress.running -> reviewRun(panel.reviewProgress)
        else -> null
    }

    private fun reviewRun(r: ReviewProgress): ResearchRun {
        val positions = r.total + 1
        val queue = r.queue?.let { tr("큐 ${it.index}/${it.total} · ", "Queue ${it.index}/${it.total} · ", "队列 ${it.index}/${it.total} · ") }.orEmpty()
        val reading = r.best?.let { best ->
            listOfNotNull(
                tr("최선 ${best.label(r.size)}", "Best ${best.label(r.size)}", "最佳 ${best.label(r.size)}"),
                mateOrRate(r.mate, r.winRatePct),
                r.depth.takeIf { it > 0 }?.let { tr("깊이 $it", "depth $it", "深度 $it") },
            ).joinToString(" · ")
        } ?: tr("읽는 중", "Reading", "读取中")
        return ResearchRun(
            prove = false,
            title = queue + tr("게임 리뷰 · 국면 ${r.index}/$positions", "Game review · position ${r.index}/$positions", "对局复盘 · 局面 ${r.index}/$positions"),
            detail = reading,
            stones = r.line.take(r.index),
            fraction = r.fraction,
            // The review's place as a wave, as on its own card: with a time
            // budget the head runs through each position at the budget's pace
            // and waits at the next one's start; by depth it eases along.
            perSecond = if (r.budget.byDepth) 0f else 1f / (r.budget.seconds * positions),
            ceiling = ((r.index + 1f) / positions).coerceAtMost(1f),
        )
    }

    private fun proveRun(pos: Position, panel: Panel): ResearchRun {
        val p = panel.proveProgress
        val searching = p.phase == ProvePhase.SEARCH
        val detail = if (searching) {
            listOfNotNull(
                if (p.attack) tr("공격", "Attack", "进攻") else tr("방어", "Defence", "防守"),
                p.candTotal.takeIf { it > 0 }?.let { tr("후보 ${p.candIndex}/$it", "candidate ${p.candIndex}/$it", "候选 ${p.candIndex}/$it") },
                p.depth.takeIf { it > 0 }?.let { tr("깊이 $it", "depth $it", "深度 $it") },
                // Bare: this line carries five readings and the bar has one.
                mateOrRate(p.mate, p.winRatePct, compact = true),
                if (p.byDepth) {
                    tr("${p.elapsedSec}초", "${p.elapsedSec}s", "${p.elapsedSec} 秒")
                } else {
                    tr("${p.elapsedSec}/${p.budget / 1000}초", "${p.elapsedSec}/${p.budget / 1000}s", "${p.elapsedSec}/${p.budget / 1000} 秒")
                },
            ).joinToString(" · ")
        } else {
            tr("탐색 ${p.searches}회 · 트리 ${p.nodes}개", "${p.searches} searches · ${p.nodes} nodes", "搜索 ${p.searches} 次 · 树 ${p.nodes} 个节点")
        }
        val line = panel.prove.ghost.entries.sortedBy { it.value }.map { it.key }
        // The wave is the search under way: its depth against the budget's,
        // or its time running on towards the budget's seconds.
        val budget = p.budget.coerceAtLeast(1)
        return ResearchRun(
            prove = true,
            title = tr("국면 증명 · 해결 ${p.resolved} · 미결 ${p.open}", "Proof · ${p.resolved} settled · ${p.open} open", "局面证明 · 已定 ${p.resolved} · 未定 ${p.open}"),
            detail = detail,
            stones = pos.moves.take(panel.prove.rootLen.coerceAtMost(pos.moves.size)) + line,
            fraction = when {
                !searching -> 0f
                p.byDepth -> p.depth.toFloat() / budget
                else -> p.elapsedSec * 1000f / budget
            }.coerceIn(0f, 1f),
            perSecond = if (searching && !p.byDepth) 1000f / budget else 0f,
            wave = p.searches,
        )
    }

    /** A mate as «M5» («-M5» when the mover is the one mated), else the win rate. */
    private fun mateOrRate(mate: Int, winRatePct: Int?, compact: Boolean = false): String? = when {
        mate > 0 -> "M$mate"
        mate < 0 -> "-M${-mate}"
        compact -> winRatePct?.let { "$it%" }
        else -> winRatePct?.let { tr("승률 $it%", "win $it%", "胜率 $it%") }
    }

    /**
     * Where a running review is reading, when the board shows its line — the
     * line itself or the start of it. A queued game, or a board moved off the
     * line, gets nothing drawn: the bar still says where the review is.
     */
    private fun reviewFocus(pos: Position, r: ReviewProgress): ReviewFocus? {
        if (!r.running || r.size != pos.size) return null
        val shown = pos.moves.size
        if (shown > r.line.size || r.line.subList(0, shown) != pos.moves) return null
        // Rewound past the position being read: the board does not reach it.
        if (r.index > shown) return null
        return ReviewFocus(reached = r.index, focus = pos.moves.getOrNull(r.index - 1), best = r.best)
    }

    /**
     * The board as drawn. The analysis tab shows the engine's overlay for this
     * position (and keeps it after a stop that left the board alone); the game
     * tab shows none of it — no values, no best-move ring, no line — because
     * there it would be coaching. Database values come before engine values on
     * the same point: a proven or hand-written record outranks an estimate.
     */
    private fun buildRender(
        pos: Position,
        snap: AnalysisSnapshot?,
        panel: Panel,
        config: AppSettings,
        quality: MoveQuality?,
    ): BoardRender {
        val analysisTab = panel.mode == BoardMode.ANALYSIS
        // A review reading this line takes the board over: its place in the
        // game and the engine's best there — and none of the analysis overlay,
        // which belongs to whatever was read before.
        val review = reviewFocus(pos, panel.reviewProgress)
        val shown = if (analysisTab && review == null) snap else null
        val pv = when {
            panel.preview != null -> shown?.pvs?.firstOrNull { it.index == panel.preview }?.line
            else -> shown?.best?.line?.ifEmpty { shown.realtimeLine }
        }.orEmpty()
        val ghosts = pv.filterNot { pos.moves.contains(it) }.take(GHOST_LIMIT)
        val bestMark = shown?.realtimeBest ?: shown?.best?.head
        val overlay = config.showAnalysis && shown != null
        val dbLabels = if (analysisTab && review == null && config.useDatabase && !panel.game.thinking) {
            dbLabelsOf(DbLabelsKey(panel.db.snapshot.cells, config.showBoardText))
        } else {
            emptyMap()
        }
        val tags = if (overlay && config.showAnalysisWinrate && panel.preview == null) {
            shown.tags.filterKeys { it !in dbLabels }
        } else {
            emptyMap()
        }
        return BoardRender(
            size = pos.size,
            stones = pos.moves,
            lastMove = if (review == null) pos.moves.lastOrNull() else null,
            forbidden = panel.forbidden,
            ghosts = ghosts,
            bestMark = if (bestMark != null && !pos.moves.contains(bestMark)) bestMark else null,
            tags = tags,
            candidates = if (overlay && panel.analyzing) shown.candidates else emptyMap(),
            loseCells = if (overlay && panel.analyzing) shown.loseCells else emptySet(),
            dbLabels = dbLabels,
            badges = if (config.showMoveBadge && quality != null && quality != MoveQuality.NONE && pos.moves.isNotEmpty()) {
                mapOf(pos.moves.last() to quality)
            } else {
                emptyMap()
            },
            prove = panel.prove.takeIf { it.active },
            review = review,
            blocked = panel.blocked,
            showNumbers = config.showNumber,
            textScale = fromSettings(config).textScale,
            palette = fromSettings(config).palette,
        )
    }

    /**
     * The grade of the **current move only** — main.c:2114 grades every move but
     * paints just `bn == piecenum`. Live values (engine or database) and a review
     * report whose line still matches the board feed one record list.
     */
    private fun badgeFor(pos: Position, report: GameReport?, config: AppSettings): MoveQuality? {
        if (pos.moves.isEmpty()) return null
        return badge(BadgeKey(pos.moves, pos.size, report, liveRecords(), config.mqPreset, config.skipOpening))
    }

    // ---- derived values, remembered -----------------------------------------
    //
    // `uiState` is rebuilt for every engine line that moves the reading - dozens
    // a second while a search streams - yet most of what goes into it follows
    // the board, the settings or the review, not the reading. Each value below
    // is recomputed only when one of its inputs changed. A key holds every
    // input, the language `tr()` reads and the opening tables a user can replace
    // at run time included, so a remembered value is exactly what computing it
    // again would give. Only the `uiState` upstream uses them, one emission at
    // a time.

    private class Memo<K, V>(private val compute: (K) -> V) {
        private var key: Any? = Unset
        private var value: Any? = null

        @Suppress("UNCHECKED_CAST")
        operator fun invoke(key: K): V {
            if (this.key != key) {
                value = compute(key)
                this.key = key
            }
            return value as V
        }

        private object Unset
    }

    private data class BoardKey(val moves: List<Move>, val size: Int)

    private data class NamesKey(
        val moves: List<Move>,
        val size: Int,
        val language: AppLanguage,
        val names: Map<String, OpeningTables.Name>,
    )

    private data class GradeKey(val moves: List<Move>, val size: Int, val evals: Map<String, Int>)

    private data class BadgeKey(
        val moves: List<Move>,
        val size: Int,
        val report: GameReport?,
        val live: List<PositionRecord>,
        val preset: Int,
        val skipOpening: Boolean,
    )

    private data class DbLabelsKey(val cells: Map<Move, DbCell>, val showBoardText: Boolean)

    /** What the settings alone decide about the board's drawing. */
    private class SettingsParts(val palette: TagPalette, val textScale: Float, val dbCommentFont: FontSpec)

    private val openingNames = Memo<NamesKey, List<String>> { k ->
        (1..minOf(OpeningName.MAX_PLY, k.moves.size)).mapNotNull { OpeningName.nameAt(it, k.moves, k.size) }
    }

    private val openingGrade = Memo<GradeKey, OpeningEval.Grade?> { k -> OpeningName.gradeAt(k.moves, k.size) }

    private val positionString = Memo<BoardKey, String> { k -> BoardTransform.toPositionString(k.moves, k.size) }

    private val winRates = Memo<List<PositionRecord>, List<Double?>> { history -> history.map { it.blackWinRate } }

    private val toolbar = Memo<List<FunctionScripts.ToolbarItem>, List<FunctionScripts.ToolbarItem>> { items ->
        items.filterNot { FunctionScripts.isBoardRowDuplicate(it.script) }
    }

    private val dbLabelsOf = Memo<DbLabelsKey, Map<Move, DbLabel>> { k ->
        k.cells.mapNotNull { (move, cell) ->
            val text = cell.display(k.showBoardText)
            if (text.isEmpty()) null else move to DbLabel(text, cell.kindOf())
        }.toMap()
    }

    private val fromSettings = Memo<AppSettings, SettingsParts> { config ->
        SettingsParts(
            palette = TagPalette(
                losingSaturation = config.lossSaturation,
                winningSaturation = config.winSaturation,
                minRateSaturation = config.minSaturation,
                maxRateSaturation = config.maxSaturation,
                value = config.colorValue,
            ),
            textScale = FontSpec.parse(config.boardTextFont).scale,
            dbCommentFont = FontSpec.parse(config.dbCommentFont),
        )
    }

    private val badge = Memo<BadgeKey, MoveQuality?> { k ->
        val ply = k.moves.size
        val fromReport = k.report?.data?.takeIf { it.matchesPrefix(k.moves) }?.records.orEmpty()
        val merged = (0..ply).map { i ->
            fromReport.getOrNull(i)?.takeIf { it.recorded } ?: k.live.getOrNull(i) ?: PositionRecord()
        }
        MoveGrader.currentBadge(
            moves = k.moves,
            size = k.size,
            records = merged,
            preset = GradingPreset.of(k.preset),
            skipOpening = k.skipOpening,
        )
    }

    private companion object {
        const val GHOST_LIMIT = 8
    }
}
