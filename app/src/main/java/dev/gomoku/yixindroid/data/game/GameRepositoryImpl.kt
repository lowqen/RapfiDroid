package dev.gomoku.yixindroid.data.game

import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.ClockSide
import dev.gomoku.yixindroid.core.model.ComputerSide
import dev.gomoku.yixindroid.core.model.GameClock
import dev.gomoku.yixindroid.core.model.GameEnd
import dev.gomoku.yixindroid.core.model.GamePrompt
import dev.gomoku.yixindroid.core.model.GameResult
import dev.gomoku.yixindroid.core.model.GameState
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.MoveCursor
import dev.gomoku.yixindroid.core.model.OpeningProtocol
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.Referee
import dev.gomoku.yixindroid.core.model.StoneColor
import dev.gomoku.yixindroid.core.model.Swap2Choice
import dev.gomoku.yixindroid.core.model.TapResult
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.GameRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * The game state machine, ported from the desktop.
 *
 * Three pieces of main.c between them define every behaviour here:
 * `on_button_press_windowmain` (2656) decides what a tap means,
 * `iochannelout_watch` (13340) applies what the engine sends back, and the four
 * swap dialogs (2339-2522) carry the opening negotiations. Where a desktop
 * dialog blocks, this raises a [GamePrompt] instead and continues when the
 * answer arrives.
 */
@Singleton
class GameRepositoryImpl @Inject constructor(
    private val engine: EngineRepository,
    private val settingsRepository: SettingsRepository,
    @IoDispatcher io: CoroutineDispatcher,
) : GameRepository {

    private val scope = CoroutineScope(SupervisorJob() + io)

    private val _position = MutableStateFlow(Position(size = Move.DEFAULT_SIZE))
    override val position: StateFlow<Position> = _position.asStateFlow()

    private val _future = MutableStateFlow<List<Move>>(emptyList())
    override val future: StateFlow<List<Move>> = _future.asStateFlow()

    private val _forbidden = MutableStateFlow<List<Move>>(emptyList())
    override val forbidden: StateFlow<List<Move>> = _forbidden.asStateFlow()

    private val _state = MutableStateFlow(GameState())
    override val state: StateFlow<GameState> = _state.asStateFlow()

    private val settings: AppSettings get() = settingsRepository.settings.value

    /** Last computer-side value taken from the settings, to tell user edits from swaps. */
    private var seededSide: ComputerSide? = null

    @Volatile
    private var analyzing = false

    init {
        scope.launch {
            settingsRepository.settings.collect { s -> onSettings(s) }
        }
        scope.launch {
            engine.responses.collect { onResponse(it) }
        }
        // The desktop runs its clock off a GTK timeout; 250 ms is close enough to
        // keep the seconds honest, and it only runs while a game is on the clock.
        scope.launch {
            while (true) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    /** Stops the ticker and collectors (tests; the app keeps the singleton alive). */
    fun shutdown() {
        scope.cancel()
    }

    // ---- settings mirror ----------------------------------------------------

    private suspend fun onSettings(s: AppSettings) {
        val side = s.computerSide
        val sideChanged = side != seededSide
        if (sideChanged) seededSide = side
        val sizeChanged = s.boardSize != _position.value.size
        _state.update {
            it.copy(
                computerSide = if (sideChanged) side else it.computerSide,
                opening = s.opening,
                clock = it.clock.withLimits(GameClock.fromSettings(s)),
            )
        }
        // Board size and rule are baked into the engine by START, and the desktop
        // starts a fresh game for either (`isneedrestart`, plus a new board).
        if (sizeChanged) {
            _position.value = Position(size = s.boardSize)
            _future.value = emptyList()
            newGame(resetClock = true)
        }
    }

    // ---- taps ---------------------------------------------------------------

    override suspend fun tap(cell: Move): TapResult {
        val s = _state.value
        val pos = _position.value
        if (!cell.isInside(pos.size)) return TapResult.Ignored
        // The desktop swallows clicks while the engine thinks and after the game
        // is decided (main.c:2663 / 2676).
        if (s.thinking) return TapResult.Rejected("엔진이 생각 중입니다")
        if (s.over) return TapResult.Rejected("대국이 끝났습니다 — 무르거나 새 대국을 시작하세요")

        val occupied = pos.moves.contains(cell)

        // Swap2: the opening three (or five) stones are placed by hand.
        if (s.opening == OpeningProtocol.SWAP2 &&
            (pos.moves.size < 3 || (pos.moves.size < 5 && !s.swapDone))
        ) {
            return if (occupied) TapResult.Ignored else swap2Tap(cell)
        }

        // A tap while the engine is on move means "go ahead" (main.c:2725).
        if (!s.offeringFifth && engineOnMove(s, pos) && openingLetsEngineMove(s, pos)) {
            if (s.opening == OpeningProtocol.SWAP_FIRST && pos.moves.isEmpty() &&
                s.computerSide != ComputerSide.BOTH
            ) {
                // The desktop opens this rule with a canned move and then asks
                // the human whether to take over (main.c:2731).
                _state.update { it.copy(needsRestart = true) }
                place(SWAP_FIRST_OPENING)
                prompt(GamePrompt.Swap())
                return TapResult.Placed
            }
            engineTurn(pushWholeBoard = true)
            return TapResult.Ignored
        }

        if (occupied) {
            // Soosorv: choosing one of the fifth moves on offer (main.c:2752).
            if (s.opening == OpeningProtocol.SOOSORV && s.offeringFifth &&
                s.computerSide != ComputerSide.WHITE && moveNumberOf(pos, cell) >= 4
            ) {
                _state.update { it.copy(needsRestart = true, offeringFifth = false) }
                rewindTo(4)
                place(cell)
                log("5수 선택: ${cell.label(pos.size)}")
                return TapResult.Placed
            }
            return TapResult.Ignored
        }

        // Renju forbidden point: refused, exactly like the desktop's dialog.
        if (!s.offeringFifth && pos.sideToMove == StoneColor.BLACK && cell in _forbidden.value) {
            prompt(GamePrompt.Forbidden(cell))
            return TapResult.Rejected("금수입니다 (${cell.label(pos.size)})")
        }

        place(cell)
        val handled = openingAfterHumanMove(cell)
        val next = _state.value
        if (!next.over && !handled && openingLetsEngineMove(next, _position.value) &&
            engineOnMove(next, _position.value)
        ) {
            addIncrement(ClockSide.HUMAN)
            if (next.needsRestart) engineTurn(pushWholeBoard = true) else engineTurn(false, cell)
        }
        return TapResult.Placed
    }

    /** The Swap2 opening: two stones, judge, two more, judge (main.c:2683). */
    private suspend fun swap2Tap(cell: Move): TapResult {
        val s = _state.value
        val n = _position.value.moves.size
        val enginePlaysBlack = s.computerSide.plays(StoneColor.BLACK)
        val enginePlaysWhite = s.computerSide.plays(StoneColor.WHITE)
        return when {
            n < 2 && !enginePlaysBlack -> {
                place(cell)
                TapResult.Placed
            }
            n == 2 && !enginePlaysBlack -> {
                place(cell)
                askEngine(EngineCommand.YxSwap2Step(2))
                TapResult.Placed
            }
            n == 3 && !enginePlaysWhite && !s.swapDone -> {
                place(cell)
                TapResult.Placed
            }
            n == 4 && !enginePlaysWhite && !s.swapDone -> {
                place(cell)
                askEngine(EngineCommand.YxSwap2Step(3))
                TapResult.Placed
            }
            else -> TapResult.Ignored
        }
    }

    /**
     * Opening bookkeeping after a human stone, returning true when it already
     * handed the position to the engine (the desktop's `flag`).
     */
    private suspend fun openingAfterHumanMove(cell: Move): Boolean {
        val s = _state.value
        val pos = _position.value
        val n = pos.moves.size
        var handled = false

        if (s.opening == OpeningProtocol.SWAP_FIRST && n == 1 &&
            s.computerSide != ComputerSide.NONE
        ) {
            // The desktop flips a coin on the two borderline openings (main.c:2777).
            if (Referee.swapAfterFirstMove(cell, pos.size, Random.nextBoolean())) {
                applySwapSides()
                prompt(GamePrompt.SwapInfo)
            }
        }

        if (s.opening == OpeningProtocol.SOOSORV && n == 3 && s.computerSide != ComputerSide.BLACK) {
            if (!Referee.openingAreaOk(pos.moves, pos.size)) {
                newGame(resetClock = false)
                prompt(GamePrompt.IllegalOpening)
                return true
            }
        }
        if (s.opening == OpeningProtocol.SOOSORV && n == 3 &&
            s.computerSide != ComputerSide.NONE && s.computerSide != ComputerSide.BLACK
        ) {
            addIncrement(ClockSide.HUMAN)
            engine.send(EngineCommand.YxSoosorvStep(2, moves = pos.moves))
            handled = true
        }
        if (s.opening == OpeningProtocol.SOOSORV && n == 4 &&
            s.computerSide != ComputerSide.NONE && s.computerSide != ComputerSide.WHITE
        ) {
            // The desktop asks for N here and sends step 4 with the answer.
            prompt(GamePrompt.FifthCount)
            handled = true
        }
        if (s.opening == OpeningProtocol.SOOSORV && n in 5..(4 + s.fifthCount) &&
            s.computerSide != ComputerSide.NONE && s.computerSide != ComputerSide.BLACK
        ) {
            if (!s.fifthStageDone) _state.update { it.copy(offeringFifth = true) }
            if (_state.value.offeringFifth) handled = true
        }
        if (s.opening == OpeningProtocol.SOOSORV && n == 4 + s.fifthCount &&
            s.computerSide != ComputerSide.NONE && s.computerSide != ComputerSide.BLACK &&
            !s.fifthStageDone
        ) {
            _state.update {
                it.copy(offeringFifth = false, fifthStageDone = true, needsRestart = true)
            }
            addIncrement(ClockSide.HUMAN)
            engine.send(EngineCommand.YxSoosorvStep(6, moves = _position.value.moves))
            handled = true
        }
        return handled
    }

    // ---- engine dialogue ----------------------------------------------------

    /** Is the colour to move one the engine plays? */
    private fun engineOnMove(s: GameState, pos: Position): Boolean = s.engineOwns(pos.sideToMove)

    /**
     * RIF and Soosorv have the first three moves entered by hand, so the engine
     * is not asked to move inside them (main.c:2726 / 2865).
     */
    private fun openingLetsEngineMove(s: GameState, pos: Position): Boolean =
        !s.opening.handEnteredOpening || pos.moves.size >= 3 || pos.moves.isEmpty()

    override suspend fun engineMove(): TapResult {
        val s = _state.value
        if (s.thinking) return TapResult.Rejected("이미 생각 중입니다")
        if (s.over) return TapResult.Rejected("대국이 끝났습니다")
        if (!engine.state.value.isLive) return TapResult.Rejected("엔진에 연결되지 않았습니다")
        if (!engineOnMove(s, _position.value)) {
            return TapResult.Rejected("지금은 컴퓨터 차례가 아닙니다")
        }
        engineTurn(pushWholeBoard = true)
        return TapResult.Placed
    }

    override suspend fun stopThinking() {
        if (!_state.value.thinking) return
        engine.send(EngineCommand.YxStop)
    }

    /**
     * Hand the move to the engine. The desktop always precedes it with the
     * remaining time and an optional hash clear, then either the whole board
     * (after anything that disturbed the line) or a single `TURN`.
     */
    private suspend fun engineTurn(pushWholeBoard: Boolean, lastMove: Move? = null) {
        val s = _state.value
        engine.send(EngineCommand.InfoTimeLeft(s.clock.engineTimeLeftMs()))
        if (settings.hashAutoClear) engine.send(EngineCommand.YxHashClear)
        _state.update {
            it.copy(
                thinking = true,
                clock = it.clock.start(ClockSide.COMPUTER),
                needsRestart = if (pushWholeBoard) false else it.needsRestart,
            )
        }
        if (pushWholeBoard || lastMove == null) {
            pushBoard(think = true)
        } else {
            engine.send(EngineCommand.Turn(lastMove))
        }
    }

    /**
     * Ask the engine to judge the position the human just built (Swap2 steps 2
     * and 3): the board goes over as an analysis board first, then the step.
     */
    private suspend fun askEngine(step: EngineCommand) {
        addIncrement(ClockSide.HUMAN)
        engine.send(EngineCommand.InfoTimeLeft(_state.value.clock.engineTimeLeftMs()))
        if (settings.hashAutoClear) engine.send(EngineCommand.YxHashClear)
        _state.update {
            it.copy(thinking = true, clock = it.clock.start(ClockSide.COMPUTER), needsRestart = false)
        }
        pushBoard(think = false)
        engine.send(step)
    }

    /** `send_board`: the desktop re-sends `START` before every board push. */
    private suspend fun pushBoard(think: Boolean) {
        val pos = _position.value
        engine.send(EngineCommand.Start(pos.size))
        engine.send(
            if (think) EngineCommand.Board(pos.placements())
            else EngineCommand.YxBoard(pos.placements()),
        )
    }

    private suspend fun onResponse(response: EngineResponse) {
        when (response) {
            is EngineResponse.BestMove -> if (_state.value.thinking) onEngineMoves(response.moves)
            is EngineResponse.OpeningMove -> onOpeningMove(response)
            is EngineResponse.OpeningSwap -> onOpeningSwap(response)
            is EngineResponse.SoosorvFifth -> onSoosorvFifth(response)
            else -> Unit
        }
    }

    /** The engine's move(s) — one, or two for a balance search (main.c:13934). */
    private suspend fun onEngineMoves(moves: List<Move>) {
        val pos = _position.value
        addIncrement(ClockSide.COMPUTER)
        _state.update { it.copy(thinking = false, clock = it.clock.start(ClockSide.HUMAN)) }
        val legal = moves.filter { it.isInside(pos.size) && !pos.moves.contains(it) }
        if (legal.isEmpty()) {
            // main.c treats an illegal answer as the end of the game (13955).
            _state.update {
                it.copy(
                    result = GameResult(GameEnd.RESIGNED, pos.sideToMove.other()),
                    log = it.log + "엔진이 둘 수 없는 자리를 보냈습니다",
                )
            }
            return
        }
        legal.forEach { place(it) }
        val next = _state.value
        if (!next.over && engineOnMove(next, _position.value)) {
            engineTurn(pushWholeBoard = true)
        }
    }

    private suspend fun onOpeningMove(response: EngineResponse.OpeningMove) {
        place(response.move)
        when {
            response.swap2 && response.index == 3 -> {
                addIncrement(ClockSide.COMPUTER)
                _state.update { it.copy(thinking = false, clock = it.clock.start(ClockSide.HUMAN)) }
                prompt(GamePrompt.Swap2)
            }
            response.swap2 && response.index == 5 -> {
                addIncrement(ClockSide.COMPUTER)
                _state.update {
                    it.copy(
                        swapDone = true,
                        thinking = false,
                        clock = it.clock.start(ClockSide.HUMAN),
                    )
                }
                prompt(GamePrompt.Swap())
            }
            !response.swap2 && response.index == 3 -> {
                addIncrement(ClockSide.COMPUTER)
                _state.update { it.copy(thinking = false, clock = it.clock.start(ClockSide.HUMAN)) }
                prompt(GamePrompt.Swap())
            }
            !response.swap2 && response.index == 4 -> {
                // MOVE4 also carries N, and the candidates start being shown.
                addIncrement(ClockSide.COMPUTER)
                _state.update {
                    it.copy(
                        thinking = false,
                        clock = it.clock.start(ClockSide.HUMAN),
                        fifthCount = response.fifthCount ?: it.fifthCount,
                        offeringFifth = if (it.fifthStageDone) it.offeringFifth else true,
                    )
                }
                prompt(GamePrompt.Swap(fifthCount = _state.value.fifthCount))
            }
            // MOVE1/2 (and Swap2 MOVE4) are just stones on the board.
            else -> Unit
        }
    }

    private suspend fun onOpeningSwap(response: EngineResponse.OpeningSwap) {
        val pos = _position.value
        when {
            // Swap2, first question: the engine keeps white and plays on, or takes
            // black and hands the move back (main.c:13367).
            response.swap2 && response.which == 1 && !response.yes -> {
                _state.update { it.copy(swapDone = true) }
                engineTurn(pushWholeBoard = true)
            }
            response.swap2 && response.which == 1 && response.yes -> {
                addIncrement(ClockSide.COMPUTER)
                _state.update {
                    it.copy(
                        swapDone = true,
                        thinking = false,
                        clock = it.clock.start(ClockSide.HUMAN),
                    )
                }
                applySwapSides()
                prompt(GamePrompt.SwapInfo)
            }
            // Swap2, second question (main.c:13405).
            response.swap2 && response.yes -> {
                addIncrement(ClockSide.COMPUTER)
                _state.update { it.copy(thinking = false, clock = it.clock.start(ClockSide.HUMAN)) }
                log("컴퓨터가 흑을 선택했습니다")
            }
            response.swap2 -> {
                log("컴퓨터가 백을 선택했습니다")
                applySwapSides()
                engineTurn(pushWholeBoard = true)
            }
            // Soosorv: yes = the engine swaps, no = continue with the next step.
            response.yes -> {
                addIncrement(ClockSide.COMPUTER)
                _state.update { it.copy(thinking = false, clock = it.clock.start(ClockSide.HUMAN)) }
                applySwapSides()
                prompt(GamePrompt.SwapInfo)
            }
            response.which == 1 -> engine.send(
                EngineCommand.YxSoosorvStep(3, moves = pos.moves),
            )
            else -> engine.send(
                EngineCommand.YxSoosorvStep(5, fifthCount = _state.value.fifthCount, moves = pos.moves),
            )
        }
    }

    private suspend fun onSoosorvFifth(response: EngineResponse.SoosorvFifth) {
        when (response.kind) {
            EngineResponse.SoosorvFifth.Kind.CHOOSE -> {
                // The engine picked one of the offered fifth moves: the board goes
                // back to four stones and its choice becomes move five (13502).
                rewindTo(4)
                response.move?.let { place(it) }
                engineTurn(pushWholeBoard = true)
            }
            EngineResponse.SoosorvFifth.Kind.REFRESH ->
                _state.update { it.copy(offeringFifth = true) }
            EngineResponse.SoosorvFifth.Kind.DONE -> {
                addIncrement(ClockSide.COMPUTER)
                _state.update {
                    it.copy(
                        fifthStageDone = true,
                        offeringFifth = if (it.fifthCount == 1) false else it.offeringFifth,
                        thinking = false,
                        clock = it.clock.start(ClockSide.HUMAN),
                    )
                }
            }
            EngineResponse.SoosorvFifth.Kind.OFFER -> response.move?.let { place(it) }
        }
    }

    // ---- prompts ------------------------------------------------------------

    override suspend fun answerSwap(yes: Boolean) {
        val s = _state.value
        val pos = _position.value
        dismissPrompt()
        addIncrement(ClockSide.HUMAN)
        if (!yes) {
            // Swap2 hands the colours over even on "no" (main.c:2485).
            if (s.opening == OpeningProtocol.SWAP2) applySwapSides()
            return
        }
        when (s.opening) {
            OpeningProtocol.SWAP2 -> engineTurn(pushWholeBoard = true)
            OpeningProtocol.SOOSORV -> {
                _state.update { it.copy(needsRestart = true) }
                applySwapSides()
                if (pos.moves.size == 3) {
                    engine.send(EngineCommand.YxSoosorvStep(3, moves = pos.moves))
                } else {
                    engine.send(
                        EngineCommand.YxSoosorvStep(5, fifthCount = s.fifthCount, moves = pos.moves),
                    )
                }
            }
            OpeningProtocol.SWAP_FIRST -> {
                _state.update { it.copy(needsRestart = true) }
                place(SWAP_FIRST_REPLY)
                applySwapSides()
            }
            else -> Unit
        }
        log("교환")
    }

    override suspend fun answerSwap2(choice: Swap2Choice) {
        dismissPrompt()
        when (choice) {
            Swap2Choice.STAY_WHITE -> _state.update { it.copy(swapDone = true) }
            Swap2Choice.SWAP -> {
                addIncrement(ClockSide.HUMAN)
                applySwapSides()
                log("교환")
                _state.update { it.copy(swapDone = true) }
                engineTurn(pushWholeBoard = true)
            }
            // "Add two more": the human simply places the fourth and fifth stones.
            Swap2Choice.ADD_TWO -> Unit
        }
    }

    override suspend fun answerFifthCount(count: Int) {
        dismissPrompt()
        val n = count.coerceIn(1, MAX_FIFTH)
        _state.update { it.copy(fifthCount = n) }
        addIncrement(ClockSide.HUMAN)
        engine.send(
            EngineCommand.YxSoosorvStep(4, fifthCount = n, moves = _position.value.moves),
        )
    }

    override suspend fun offerDraw() {
        engine.send(EngineCommand.YxDraw)
        _state.update {
            it.copy(
                result = GameResult(GameEnd.DRAW_AGREED, null),
                thinking = false,
                clock = it.clock.stop(),
                log = it.log + "무승부를 제안했습니다 (yxdraw)",
            )
        }
    }

    override suspend fun resign() {
        val loser = _position.value.sideToMove
        engine.send(EngineCommand.YxResign)
        _state.update {
            it.copy(
                result = GameResult(GameEnd.RESIGNED, loser.other()),
                thinking = false,
                clock = it.clock.stop(),
                log = it.log + "기권했습니다 (yxresign)",
            )
        }
    }

    override fun dismissPrompt() {
        _state.update { it.copy(prompt = null) }
    }

    private fun prompt(prompt: GamePrompt) {
        _state.update { it.copy(prompt = prompt) }
    }

    // ---- game / navigation --------------------------------------------------

    override suspend fun newGame(resetClock: Boolean) {
        val s = settings
        _position.value = Position(size = s.boardSize)
        _future.value = emptyList()
        _forbidden.value = emptyList()
        _state.update {
            val clock = if (resetClock) {
                GameClock.fromSettings(s).copy(running = ClockSide.HUMAN)
            } else {
                it.clock
            }
            it.copy(
                thinking = false,
                result = null,
                prompt = null,
                clock = clock,
                needsRestart = true,
                swapDone = false,
                offeringFifth = false,
                fifthStageDone = false,
                fifthCount = 1,
                log = if (resetClock) emptyList() else it.log,
            )
        }
        if (resetClock) startOpening()
        refreshForbidden()
    }

    /**
     * The opening protocols where the engine moves first have to be kicked off
     * (`new_game_resetclock`, main.c:4263).
     */
    private suspend fun startOpening() {
        val s = _state.value
        if (s.computerSide != ComputerSide.BLACK) return
        when (s.opening) {
            OpeningProtocol.SWAP2 -> {
                _state.update { it.copy(thinking = true, clock = it.clock.start(ClockSide.COMPUTER)) }
                engine.send(EngineCommand.YxSwap2Step(1))
            }
            OpeningProtocol.SOOSORV -> {
                _state.update { it.copy(thinking = true, clock = it.clock.start(ClockSide.COMPUTER)) }
                engine.send(EngineCommand.YxSoosorvStep(1))
            }
            else -> Unit
        }
    }

    override suspend fun setComputerSide(side: ComputerSide) {
        seededSide = side
        _state.update { it.copy(computerSide = side, needsRestart = true) }
        settingsRepository.set("computerBlack", if (side.plays(StoneColor.BLACK)) "1" else "0")
        settingsRepository.set("computerWhite", if (side.plays(StoneColor.WHITE)) "1" else "0")
        refreshForbidden()
    }

    /** The desktop's paired `change_side_menu` calls: the engine takes the other colour. */
    private suspend fun applySwapSides() {
        setComputerSide(_state.value.computerSide.swapped())
    }

    override suspend fun undo() = jumpTo(_position.value.moves.size - 1)
    override suspend fun redo() = jumpTo(_position.value.moves.size + 1)
    override suspend fun toStart() = jumpTo(0)
    override suspend fun toEnd() = jumpTo(Int.MAX_VALUE)

    override suspend fun jumpTo(target: Int) {
        if (_state.value.thinking) stopThinking()
        val pos = _position.value
        val (played, tail) = MoveCursor.splitAt(pos.moves + _future.value, target)
        if (played.size == pos.moves.size) return
        _position.value = pos.copy(moves = played)
        _future.value = tail
        afterPositionChange()
    }

    override suspend fun replaceLine(moves: List<Move>) {
        if (_state.value.thinking) stopThinking()
        _position.value = _position.value.copy(moves = moves)
        _future.value = emptyList()
        afterPositionChange()
    }

    /** Drop back to [count] stones, as the desktop's repeated `change_piece` does. */
    private fun rewindTo(count: Int) {
        val pos = _position.value
        if (pos.moves.size <= count) return
        _position.value = pos.copy(moves = pos.moves.take(count))
    }

    private fun place(cell: Move) {
        val pos = _position.value
        if (pos.moves.contains(cell) || !cell.isInside(pos.size)) return
        _future.value = MoveCursor.tailAfter(_future.value, cell)
        _position.value = pos.play(cell)
        judge()
        refreshForbidden()
    }

    private fun afterPositionChange() {
        judge()
        refreshForbidden()
    }

    /** Win/draw check after every board change (the desktop does it in make_move). */
    private fun judge() {
        val pos = _position.value
        val result = Referee.result(pos.moves, pos.size, settings.allowsOverlineWin)
        _state.update {
            when {
                result != null && it.result != result -> it.copy(
                    result = result,
                    thinking = false,
                    clock = it.clock.stop(),
                    log = it.log + result.describe(),
                )
                result == null && it.result != null -> it.copy(result = null)
                else -> it
            }
        }
    }

    override fun setAnalyzing(on: Boolean) {
        analyzing = on
        refreshForbidden()
    }

    /**
     * `show_forbid`: only when the human plays black and it is Black's move, on a
     * renju base rule, with the display setting on — and never during a search,
     * where the extra `YXBOARD` would disturb the engine.
     */
    private fun refreshForbidden() {
        val s = settings
        val pos = _position.value
        val st = _state.value
        val wanted = s.showForbidden && s.isRenju && !st.thinking && !analyzing &&
            pos.sideToMove == StoneColor.BLACK && !st.computerSide.plays(StoneColor.BLACK) &&
            engine.state.value.isLive
        if (!wanted) {
            _forbidden.value = emptyList()
            return
        }
        scope.launch {
            _forbidden.value = runCatching { engine.forbidden(pos) }.getOrDefault(emptyList())
        }
    }

    // ---- clock --------------------------------------------------------------

    private fun tick() {
        val s = _state.value
        if (!s.active || s.over || s.clock.running == null) return
        val ticked = s.clock.tick(TICK_MS)
        val warn = settings.checkTimeout && !ticked.timedOutNotified && ticked.playerTimedOut()
        _state.value = s.copy(
            clock = if (warn) ticked.copy(timedOutNotified = true) else ticked,
            prompt = if (warn) GamePrompt.Timeout else s.prompt,
        )
    }

    private fun addIncrement(side: ClockSide) {
        _state.update { it.copy(clock = it.clock.addIncrement(side)) }
    }

    private fun log(text: String) {
        _state.update { it.copy(log = (it.log + text).takeLast(LOG_LIMIT)) }
    }

    private fun moveNumberOf(pos: Position, cell: Move): Int = pos.moves.indexOf(cell) + 1

    private companion object {
        const val TICK_MS = 250L
        const val LOG_LIMIT = 30
        /** The desktop's N dialog accepts 1..8 and re-asks otherwise (main.c:3857). */
        const val MAX_FIFTH = 8

        /**
         * The canned first and second moves the desktop plays under "swap after
         * 1st move" (main.c:2733 `make_move(1, 7)` and 2468 `make_move(4, 5)`) —
         * y,x in the desktop's order.
         */
        val SWAP_FIRST_OPENING = Move(x = 7, y = 1)
        val SWAP_FIRST_REPLY = Move(x = 5, y = 4)
    }
}
