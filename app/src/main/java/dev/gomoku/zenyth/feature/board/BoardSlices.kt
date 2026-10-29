package dev.gomoku.zenyth.feature.board

import androidx.compose.runtime.Immutable
import dev.gomoku.zenyth.core.model.AnalysisSnapshot
import dev.gomoku.zenyth.core.model.ConnectionState
import dev.gomoku.zenyth.core.model.DbPositionValue
import dev.gomoku.zenyth.core.model.DbState
import dev.gomoku.zenyth.core.model.FontSpec
import dev.gomoku.zenyth.core.model.FunctionScripts
import dev.gomoku.zenyth.core.model.GameClock
import dev.gomoku.zenyth.core.model.GameState
import dev.gomoku.zenyth.core.model.LngTable
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.MoveQuality
import dev.gomoku.zenyth.core.model.OpeningEval
import dev.gomoku.zenyth.core.model.StoneColor

/*
 * What each card of the board screen reads, and nothing else (Phase 4 of
 * docs/리팩토링-성능-위생-계획.md). `uiState` changes with every engine line and
 * every clock tick; a card handed the whole [BoardUiState] was composed again
 * each time, though for nearly every card nothing it shows had changed. Handed
 * its slice, a card is skipped until one of its own values changes: a slice is
 * an immutable value, compared by equality.
 *
 * A slice keeps [BoardUiState]'s names — `ui.game.result`, `ui.render.size`,
 * `ui.busy` — so the card's code reads the same, and a value a card reads but
 * its slice lacks does not compile. Derived values come from [BoardUiState]'s
 * own getters, so each rule has one home.
 */

/** The game without its clock, which ticks four times a second; only [ClockRow] reads the clock. */
private val NoClock = GameClock()

private val BoardUiState.gameWithoutClock: GameState get() = game.copy(clock = NoClock)

/** The two facts of the board a card prints: its size and its last move. */
@Immutable
internal data class RenderSpot(val lastMove: Move?, val size: Int)

private val BoardUiState.renderSpot: RenderSpot get() = RenderSpot(render.lastMove, render.size)

@Immutable
internal data class PositionCardUi(
    val rule: Int,
    val game: GameState,
    val sideToMove: StoneColor,
    val futureCount: Int,
    val moveCount: Int,
    val render: RenderSpot,
    val openingNames: List<String>,
    val openingGrade: OpeningEval.Grade?,
    val moveGrade: MoveQuality?,
    val analysisTab: Boolean,
    val db: DbState,
    val dbActive: Boolean,
    val dbValue: DbPositionValue?,
    val dbCommentFont: FontSpec,
    val gameName: String?,
    val connection: ConnectionState,
)

internal fun BoardUiState.positionCardUi() = PositionCardUi(
    rule = rule,
    game = gameWithoutClock,
    sideToMove = sideToMove,
    futureCount = futureCount,
    moveCount = moveCount,
    render = renderSpot,
    openingNames = openingNames,
    openingGrade = openingGrade,
    moveGrade = moveGrade,
    analysisTab = analysisTab,
    db = db,
    dbActive = dbActive,
    dbValue = dbValue,
    dbCommentFont = dbCommentFont,
    gameName = gameName,
    connection = connection,
)

@Immutable
internal data class PositionStripUi(
    val sideToMove: StoneColor,
    val game: GameState,
    val moveCount: Int,
    val render: RenderSpot,
    val openingNames: List<String>,
    val openingGrade: OpeningEval.Grade?,
)

internal fun BoardUiState.positionStripUi() = PositionStripUi(
    sideToMove = sideToMove,
    game = gameWithoutClock,
    moveCount = moveCount,
    render = renderSpot,
    openingNames = openingNames,
    openingGrade = openingGrade,
)

@Immutable
internal data class AnalysisOptionsUi(val analysisSec: Int, val multiPv: Int, val playBestOnStop: Boolean)

internal fun BoardUiState.analysisOptionsUi() = AnalysisOptionsUi(analysisSec, multiPv, playBestOnStop)

@Immutable
internal data class ToolsCardUi(
    val canAnalyze: Boolean,
    val busy: Boolean,
    val isRenju: Boolean,
    val showForbidden: Boolean,
    val canTransform: Boolean,
    val toolbar: List<FunctionScripts.ToolbarItem>,
    val language: LngTable,
    val toolbarStyle: Int,
    val connection: ConnectionState,
)

internal fun BoardUiState.toolsCardUi() = ToolsCardUi(
    canAnalyze = canAnalyze,
    busy = busy,
    isRenju = isRenju,
    showForbidden = showForbidden,
    canTransform = canTransform,
    toolbar = toolbar,
    language = language,
    toolbarStyle = toolbarStyle,
    connection = connection,
)

/** The game card without its clock; [ClockRow] reads that on its own. */
@Immutable
internal data class GameCardUi(
    val connection: ConnectionState,
    val game: GameState,
    val strength: Int,
    val unlimitedTurn: Boolean,
    val engineOnMove: Boolean,
    val moveTimeMs: Long,
    val rule: Int,
    val openingNeedsOddSize: Boolean,
    val showClock: Boolean,
)

internal fun BoardUiState.gameCardUi() = GameCardUi(
    connection = connection,
    game = gameWithoutClock,
    strength = strength,
    unlimitedTurn = unlimitedTurn,
    engineOnMove = engineOnMove,
    moveTimeMs = moveTimeMs,
    rule = rule,
    openingNeedsOddSize = openingNeedsOddSize,
    showClock = showClock,
)

@Immutable
internal data class MoveToolbarUi(
    val game: GameState,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val analysisTab: Boolean,
    val analyzing: Boolean,
    val balancing: Boolean,
    val canAnalyze: Boolean,
)

internal fun BoardUiState.moveToolbarUi() = MoveToolbarUi(
    game = gameWithoutClock,
    canUndo = canUndo,
    canRedo = canRedo,
    analysisTab = analysisTab,
    analyzing = analyzing,
    balancing = balancing,
    canAnalyze = canAnalyze,
)

@Immutable
internal data class SummaryUi(
    val analysisTab: Boolean,
    val connection: ConnectionState,
    val game: GameState,
    val sideToMove: StoneColor,
    val moveCount: Int,
    val shown: AnalysisSnapshot?,
    val analyzing: Boolean,
    val balancing: Boolean,
    val render: RenderSpot,
)

internal fun BoardUiState.summaryUi() = SummaryUi(
    analysisTab = analysisTab,
    connection = connection,
    game = gameWithoutClock,
    sideToMove = sideToMove,
    moveCount = moveCount,
    shown = shown,
    analyzing = analyzing,
    balancing = balancing,
    render = renderSpot,
)
