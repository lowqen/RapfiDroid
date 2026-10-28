package dev.gomoku.zenyth.feature.board

import android.net.Uri
import dev.gomoku.zenyth.core.common.IncomingLinks
import dev.gomoku.zenyth.core.model.AnalysisSnapshot
import dev.gomoku.zenyth.core.model.AnalyzeParams
import dev.gomoku.zenyth.core.model.AppSettings
import dev.gomoku.zenyth.core.model.BoardMode
import dev.gomoku.zenyth.core.model.CallbackConfig
import dev.gomoku.zenyth.core.model.ComputerSide
import dev.gomoku.zenyth.core.model.ConnectionState
import dev.gomoku.zenyth.core.model.ConsoleLine
import dev.gomoku.zenyth.core.model.DbDeleteScope
import dev.gomoku.zenyth.core.model.DbOpResult
import dev.gomoku.zenyth.core.model.DbState
import dev.gomoku.zenyth.core.model.EngineCapabilities
import dev.gomoku.zenyth.core.model.EngineParams
import dev.gomoku.zenyth.core.model.EngineTarget
import dev.gomoku.zenyth.core.model.FunctionScripts
import dev.gomoku.zenyth.core.model.GameFileContent
import dev.gomoku.zenyth.core.model.GameReport
import dev.gomoku.zenyth.core.model.GameState
import dev.gomoku.zenyth.core.model.GradingPreset
import dev.gomoku.zenyth.core.model.LinkHealth
import dev.gomoku.zenyth.core.model.LngTable
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.Position
import dev.gomoku.zenyth.core.model.ProveOptions
import dev.gomoku.zenyth.core.model.ProveOutcome
import dev.gomoku.zenyth.core.model.ProveOverlay
import dev.gomoku.zenyth.core.model.ProveProgress
import dev.gomoku.zenyth.core.model.QueueEntry
import dev.gomoku.zenyth.core.model.ReviewBudget
import dev.gomoku.zenyth.core.model.ReviewProgress
import dev.gomoku.zenyth.core.model.SettingsFile
import dev.gomoku.zenyth.core.model.Swap2Choice
import dev.gomoku.zenyth.core.model.TapResult
import dev.gomoku.zenyth.core.model.ToolsOutcome
import dev.gomoku.zenyth.core.model.ToolsState
import dev.gomoku.zenyth.data.board.BoardImageIo
import dev.gomoku.zenyth.data.game.GameFileIo
import dev.gomoku.zenyth.data.prefs.UiPrefs
import dev.gomoku.zenyth.domain.engine.EngineCommand
import dev.gomoku.zenyth.domain.engine.EngineResponse
import dev.gomoku.zenyth.domain.engine.SearchAggregator
import dev.gomoku.zenyth.domain.repository.AppearanceRepository
import dev.gomoku.zenyth.domain.repository.DatabaseRepository
import dev.gomoku.zenyth.domain.repository.EngineRepository
import dev.gomoku.zenyth.domain.repository.EngineToolsRepository
import dev.gomoku.zenyth.domain.repository.GameRepository
import dev.gomoku.zenyth.domain.repository.ProveRepository
import dev.gomoku.zenyth.domain.repository.ProveStart
import dev.gomoku.zenyth.domain.repository.ReviewRepository
import dev.gomoku.zenyth.domain.repository.ReviewStart
import dev.gomoku.zenyth.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import org.robolectric.RuntimeEnvironment

/*
 * Fakes for the board's view model, shared by [BoardViewModelTest] and the
 * Phase 0 baselines (docs/리팩토링-성능-위생-계획.md). The engine fake runs the
 * real [SearchAggregator] over the lines it is fed, as
 * `EngineRepositoryImpl.analyze` does, so its snapshots are the app's own.
 */

internal class FakeEngine : EngineRepository {
    val responseFlow = MutableSharedFlow<EngineResponse>(extraBufferCapacity = 256)
    val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Ready)
    val analyses = mutableListOf<Pair<Position, AnalyzeParams>>()
    var stops = 0

    override val state: StateFlow<ConnectionState> get() = stateFlow
    override val responses: SharedFlow<EngineResponse> get() = responseFlow
    override val console: SharedFlow<ConsoleLine> = MutableSharedFlow()
    override val capabilities: StateFlow<EngineCapabilities> = MutableStateFlow(EngineCapabilities())
    override val health: StateFlow<LinkHealth> = MutableStateFlow(LinkHealth())
    override val thermalThreads: StateFlow<Int?> = MutableStateFlow(null)

    override suspend fun connect(target: EngineTarget) = Unit
    override suspend fun send(command: EngineCommand) = Unit
    override fun disconnect() = Unit
    override suspend fun retryNow() = Unit
    override fun park() = Unit
    override suspend fun resumeParked() = Unit
    override suspend fun applyParams(params: EngineParams) = Unit

    override fun analyze(position: Position, params: AnalyzeParams): Flow<AnalysisSnapshot> =
        channelFlow {
            analyses += position to params
            val aggregator = SearchAggregator(position.sideToMove)
            responseFlow.collect { response -> aggregator.consume(response)?.let { trySend(it) } }
        }

    override suspend fun forbidden(position: Position): List<Move> = emptyList()
    override suspend fun balance(position: Position, two: Boolean, bias: Int): List<Move> = emptyList()
    override suspend fun stop() {
        stops++
    }
}

/** A board that does what it is told; the rules are [GameRepositoryTest]'s business. */
internal class FakeGame : GameRepository {
    val positionFlow = MutableStateFlow(Position())
    val stateFlow = MutableStateFlow(GameState())
    val modeFlow = MutableStateFlow(BoardMode.ANALYSIS)
    val futureFlow = MutableStateFlow<List<Move>>(emptyList())
    var tapResult: TapResult = TapResult.Placed
    val taps = mutableListOf<Move>()
    var undos = 0

    override val position: StateFlow<Position> get() = positionFlow
    override val future: StateFlow<List<Move>> get() = futureFlow
    override val forbidden: StateFlow<List<Move>> = MutableStateFlow(emptyList())
    override val state: StateFlow<GameState> get() = stateFlow
    override val mode: StateFlow<BoardMode> get() = modeFlow
    override val gameName: StateFlow<String?> = MutableStateFlow(null)

    override suspend fun setMode(mode: BoardMode) {
        modeFlow.value = mode
    }

    override suspend fun tap(cell: Move): TapResult {
        taps += cell
        if (tapResult == TapResult.Placed) positionFlow.update { it.copy(moves = it.moves + cell) }
        return tapResult
    }

    override suspend fun engineMove(): TapResult = TapResult.Ignored
    override suspend fun stopThinking() = Unit
    override suspend fun newGame(resetClock: Boolean) {
        positionFlow.update { it.copy(moves = emptyList()) }
    }

    override suspend fun setComputerSide(side: ComputerSide) = Unit
    override suspend fun undo() {
        undos++
        positionFlow.update { it.copy(moves = it.moves.dropLast(1)) }
    }

    override suspend fun redo() = Unit
    override suspend fun toStart() = Unit
    override suspend fun toEnd() = Unit
    override suspend fun jumpTo(index: Int) = Unit
    override suspend fun replaceLine(moves: List<Move>) {
        positionFlow.update { it.copy(moves = moves) }
    }

    override suspend fun answerSwap(yes: Boolean) = Unit
    override suspend fun answerSwap2(choice: Swap2Choice) = Unit
    override suspend fun answerFifthCount(count: Int) = Unit
    override suspend fun offerDraw() = Unit
    override suspend fun resign() = Unit
    override fun setAnalyzing(on: Boolean) = Unit
    override fun dismissPrompt() = Unit
}

internal class FakeSettings(initial: AppSettings) : SettingsRepository {
    val flow = MutableStateFlow(initial)
    val writes = mutableListOf<Pair<String, String>>()
    override val settings: StateFlow<AppSettings> get() = flow
    override val loaded: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        flow.value = transform(flow.value)
    }

    override suspend fun set(id: String, raw: String) {
        writes += id to raw
        if (id == "multiPv") flow.update { it.copy(multiPv = raw.toInt()) }
    }

    override suspend fun resetToDefaults() = Unit
    override fun export(file: SettingsFile): String = ""
    override suspend fun import(text: String, file: SettingsFile): Int = 0
}

internal class FakeDatabase : DatabaseRepository {
    val stateFlow = MutableStateFlow(DbState())
    var refreshes = 0
    val positions = mutableListOf<Position>()

    override val state: StateFlow<DbState> get() = stateFlow
    override val position: StateFlow<Position> = MutableStateFlow(Position())
    override suspend fun setPosition(position: Position) {
        positions += position
    }

    override suspend fun refresh() {
        refreshes++
    }

    override suspend fun queryValue() = DbOpResult.Sent
    override suspend fun queryComment() = DbOpResult.Sent
    override suspend fun editComment(comment: String) = DbOpResult.Sent
    override suspend fun editCellLabel(cell: Move, label: String) = DbOpResult.Sent
    override suspend fun editTag(tag: Char?) = DbOpResult.Sent
    override suspend fun editValue(value: Int) = DbOpResult.Sent
    override suspend fun editDepth(depth: Int) = DbOpResult.Sent
    override suspend fun setBestMove() = DbOpResult.Sent
    override suspend fun clearBestMove() = DbOpResult.Sent
    override suspend fun deleteOne() = DbOpResult.Sent
    override suspend fun deleteAll(scope: DbDeleteScope) = DbOpResult.Sent
    override suspend fun save() = DbOpResult.Sent
    override suspend fun saveIfLoaded() = DbOpResult.Sent
    override suspend fun openFile(path: String) = DbOpResult.Sent
    override suspend fun merge(path: String) = DbOpResult.Sent
    override suspend fun split(path: String) = DbOpResult.Sent
    override suspend fun importLib(path: String) = DbOpResult.Sent
    override suspend fun exportLib(path: String) = DbOpResult.Sent
    override suspend fun exportText(path: String, all: Boolean) = DbOpResult.Sent
    override suspend fun importText(path: String) = DbOpResult.Sent
    override suspend fun exportPositions(path: String) = DbOpResult.Sent
    override suspend fun check() = DbOpResult.Sent
    override suspend fun fix() = DbOpResult.Sent
    override suspend fun setEnabled(on: Boolean) = Unit
    override suspend fun setReadOnly(on: Boolean) = Unit
    override suspend fun setDestructiveUnlocked(on: Boolean) = Unit
    override fun clearLog() = Unit
}

internal class FakeReview : ReviewRepository {
    val progressFlow = MutableStateFlow(ReviewProgress())
    override val progress: StateFlow<ReviewProgress> get() = progressFlow
    override val report: StateFlow<GameReport?> = MutableStateFlow(null)
    override val queueReports: StateFlow<List<GameReport>> = MutableStateFlow(emptyList())
    override val queue: StateFlow<List<QueueEntry>> = MutableStateFlow(emptyList())
    override val log: StateFlow<List<String>> = MutableStateFlow(emptyList())
    override suspend fun start(budget: ReviewBudget) = ReviewStart.Started
    override suspend fun startQueue(budget: ReviewBudget) = ReviewStart.Started
    override suspend fun cancel() = Unit
    override fun enqueue(entries: List<QueueEntry>) = Unit
    override fun removeQueued(uri: String) = Unit
    override fun clearQueue() = Unit
    override suspend fun loadGame(content: GameFileContent) = Unit
    override fun setPreset(preset: GradingPreset) = Unit
    override fun clearReport() = Unit
}

internal class FakeProve : ProveRepository {
    val progressFlow = MutableStateFlow(ProveProgress())
    override val progress: StateFlow<ProveProgress> get() = progressFlow
    override val overlay: StateFlow<ProveOverlay> = MutableStateFlow(ProveOverlay())
    override val outcome: StateFlow<ProveOutcome?> = MutableStateFlow(null)
    override val log: StateFlow<List<String>> = MutableStateFlow(emptyList())
    override suspend fun start(options: ProveOptions): ProveStart = ProveStart.Started
    override suspend fun cancel() = Unit
    override fun clearOutcome() = Unit
}

internal class FakeTools : EngineToolsRepository {
    override val state: StateFlow<ToolsState> = MutableStateFlow(ToolsState())
    override val output: SharedFlow<ToolsOutcome> = MutableSharedFlow()
    override suspend fun run(script: String) = Unit
    override suspend fun setCallbacks(config: CallbackConfig) = Unit
    override suspend fun restore() = Unit
}

internal class FakeAppearance : AppearanceRepository {
    override val toolbar: StateFlow<List<FunctionScripts.ToolbarItem>> = MutableStateFlow(emptyList())
    override val hotkeys: StateFlow<List<FunctionScripts.HotkeyItem>> = MutableStateFlow(emptyList())
    override val language: StateFlow<LngTable> = MutableStateFlow(LngTable.EMPTY)
    override val source: StateFlow<String?> = MutableStateFlow(null)
    override suspend fun importFrom(tree: Uri, languageIndex: Int): Result<String> = Result.success("")
    override suspend fun reset() = Unit
    override suspend fun restore() = Unit
}

/** A [BoardViewModel] on fakes, and the fakes to drive it with. */
internal class BoardRig(
    val vm: BoardViewModel,
    val engine: FakeEngine,
    val game: FakeGame,
    val settings: FakeSettings,
    val database: FakeDatabase,
    val review: FakeReview,
    val prove: FakeProve,
    val links: IncomingLinks,
)

/** [io] runs the file helpers, which no test here reaches. */
internal fun boardRig(settings: AppSettings, io: CoroutineDispatcher): BoardRig {
    val context = RuntimeEnvironment.getApplication()
    val engine = FakeEngine()
    val game = FakeGame()
    val fakeSettings = FakeSettings(settings)
    val database = FakeDatabase()
    val review = FakeReview()
    val prove = FakeProve()
    val links = IncomingLinks()
    val vm = BoardViewModel(
        repository = engine,
        settingsRepository = fakeSettings,
        database = database,
        game = game,
        review = review,
        prove = prove,
        tools = FakeTools(),
        appearance = FakeAppearance(),
        imageIo = BoardImageIo(context, io),
        fileIo = GameFileIo(context, io),
        links = links,
        uiPrefs = UiPrefs(context),
    )
    return BoardRig(vm, engine, game, fakeSettings, database, review, prove, links)
}
