package dev.gomoku.yixindroid.data.tools

import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.BlockTargets
import dev.gomoku.yixindroid.core.model.BoardTransform
import dev.gomoku.yixindroid.core.model.CallbackConfig
import dev.gomoku.yixindroid.core.model.ConsoleCommand
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.ToolsOutcome
import dev.gomoku.yixindroid.core.model.ToolsState
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.engine.EngineResponse
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.EngineToolsRepository
import dev.gomoku.yixindroid.domain.repository.GameRepository
import dev.gomoku.yixindroid.domain.repository.ProveRepository
import dev.gomoku.yixindroid.domain.repository.ReviewRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The engine operations menu, and with it the desktop's console command
 * language (main.c `execute_command` / `custom_function`).
 *
 * Everything that touches the engine or the board goes through [run], which
 * parses a script line by line with [ConsoleCommand] and dispatches. Buttons in
 * the UI build the same text a user could type, so there is one code path — and
 * it is the one the desktop's toolbar, hotkeys and callbacks share too.
 *
 * `sleep` suspends the rest of the script exactly as `custom_function` does:
 * the desktop re-arms the remainder on a `g_timeout_add`, which here is simply
 * a `delay` inside the running coroutine.
 */
@Singleton
class EngineToolsRepositoryImpl @Inject constructor(
    private val engine: EngineRepository,
    private val game: GameRepository,
    private val settings: SettingsRepository,
    private val store: ToolsStore,
    private val review: ReviewRepository,
    // Lazy for the same reason the explorer resolves them lazily: these two only
    // matter as a guard, and wiring them eagerly would close a Hilt cycle.
    private val prove: Provider<ProveRepository>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : EngineToolsRepository {

    private val scope = CoroutineScope(SupervisorJob() + io)
    private val lock = Mutex()

    private val _state = MutableStateFlow(ToolsState())
    override val state: StateFlow<ToolsState> = _state.asStateFlow()

    private val _output = MutableSharedFlow<ToolsOutcome>(extraBufferCapacity = 64)
    override val output: SharedFlow<ToolsOutcome> = _output.asSharedFlow()

    /** Consecutive 50 % evaluations, for the draw callback (`curdrawingcount`). */
    private var drawingCount = 0
    private var wasThinking = false
    private var lastPlies = 0

    /** Latest values of the PV block being received (`curmatestep`/`curwinrate`). */
    private var pvMate: Int? = null
    private var pvWinRate: Int? = null

    init {
        // The desktop fires the verdict callbacks from its `INFO PV … DONE`
        // handler, off the very same two values (main.c:13780-13830).
        engine.responses.onEach { r ->
            when (r) {
                is EngineResponse.InfoPvStart -> { pvMate = null; pvWinRate = null }
                is EngineResponse.InfoEval -> pvMate = r.mate
                is EngineResponse.InfoWinRate ->
                    pvWinRate = Math.round(r.winRate * 100).toInt()
                is EngineResponse.InfoPvDone -> onSearchVerdict(pvMate, pvWinRate)
                else -> Unit
            }
        }.launchIn(scope)

        // Auto-reset fires when the engine's move lands, not when a search is
        // merely stopped (main.c:13940 sits in the real-move branch).
        game.state.onEach { st ->
            val plies = game.position.value.moves.size
            if (wasThinking && !st.thinking && plies > lastPlies) onEngineMove(plies)
            wasThinking = st.thinking
            lastPlies = plies
        }.launchIn(scope)
    }

    fun shutdown() {
        scope.cancel()
    }

    override suspend fun restore() {
        _state.value = _state.value.copy(callbacks = store.load())
    }

    override suspend fun setCallbacks(config: CallbackConfig) {
        _state.update { it.copy(callbacks = config) }
        store.save(config)
    }

    override suspend fun run(script: String) = lock.withLock { execute(script) }

    private suspend fun execute(script: String) {
        val lines = ConsoleCommand.script(script)
        var i = 0
        while (i < lines.size) {
            val size = game.position.value.size
            when (val cmd = ConsoleCommand.parse(lines[i], size, _state.value.commandMode)) {
                is ConsoleCommand.Sleep -> {
                    // The desktop defers the remainder and returns; the same
                    // effect, minus the callback plumbing.
                    if (cmd.ms > 1) delay(cmd.ms.toLong())
                }
                else -> dispatch(cmd, size)
            }
            i++
        }
    }

    private suspend fun dispatch(cmd: ConsoleCommand, size: Int) {
        when (cmd) {
            // ---- console plumbing ------------------------------------------
            is ConsoleCommand.CommandMode -> {
                _state.update { it.copy(commandMode = cmd.on) }
                log("명령 전달 모드 ${if (cmd.on) "켜짐 — 입력이 엔진으로 그대로 갑니다" else "꺼짐"}")
            }
            is ConsoleCommand.RawLine -> engine.send(EngineCommand.Raw(cmd.line))
            is ConsoleCommand.Echo -> log(cmd.text)
            ConsoleCommand.Help -> log(HELP)
            ConsoleCommand.ClearLog -> Unit   // the console owns its buffer
            is ConsoleCommand.Sleep -> Unit   // handled by the runner

            // ---- hash / TT --------------------------------------------------
            ConsoleCommand.HashClear -> engine.send(EngineCommand.YxHashClear)
            ConsoleCommand.HashUsage -> engine.send(EngineCommand.YxShowHashUsage)
            is ConsoleCommand.HashAutoClear -> {
                settings.set("hashAutoClear", if (cmd.on) "1" else "0")
                log("해시 자동 지우기 ${onOff(cmd.on)}")
            }
            is ConsoleCommand.HashDump ->
                if (cmd.path.isBlank()) err("hash dump <서버 경로> 가 필요합니다")
                else engine.send(EngineCommand.YxHashDump(cmd.path))
            is ConsoleCommand.HashLoad ->
                if (cmd.path.isBlank()) err("hash load <서버 경로> 가 필요합니다")
                else engine.send(EngineCommand.YxHashLoad(cmd.path))

            // ---- position stack ---------------------------------------------
            is ConsoleCommand.PushPos -> pushPos(cmd.slot, size)
            is ConsoleCommand.PopPos -> popPos(cmd.slot, size)
            ConsoleCommand.GetPos ->
                log(BoardTransform.toPositionString(game.position.value.moves, size))
            is ConsoleCommand.PutPos -> putPos(cmd.text, size)

            // ---- blocked points ---------------------------------------------
            is ConsoleCommand.Block -> block(cmd.cells)
            is ConsoleCommand.BlockUndo -> {
                engine.send(EngineCommand.YxBlockUndo(cmd.cell))
                _state.update { it.copy(blocked = it.blocked - cmd.cell) }
            }
            ConsoleCommand.BlockReset -> resetBlocks()
            is ConsoleCommand.BlockCompare -> blockCompare(cmd.cells, size)
            is ConsoleCommand.BlockAutoReset -> {
                settings.set("blockAutoReset", if (cmd.on) "1" else "0")
                log("차단 자동 해제 ${onOff(cmd.on)}")
            }

            is ConsoleCommand.BlockPath -> blockPath(cmd.cells, undo = false)
            is ConsoleCommand.BlockPathUndo -> blockPath(cmd.cells, undo = true)
            ConsoleCommand.BlockPathReset -> engine.send(EngineCommand.YxBlockPathReset)
            is ConsoleCommand.BlockPathExcept -> blockPathExcept(cmd.cells, size)
            is ConsoleCommand.BlockPathAutoReset -> {
                settings.set("blockPathAutoReset", if (cmd.on) "1" else "0")
                log("차단 경로 자동 해제 ${onOff(cmd.on)}")
            }

            // ---- forced forbidden points -------------------------------------
            is ConsoleCommand.ForbidAdd ->
                engine.send(EngineCommand.YxForbid(true, cmd.cell, cmd.side))
            is ConsoleCommand.ForbidDel ->
                engine.send(EngineCommand.YxForbid(false, cmd.cell, cmd.side))

            // ---- search tools -------------------------------------------------
            ConsoleCommand.SearchDefend -> {
                busy()?.let { return err(it) }
                engine.send(EngineCommand.YxBoard(game.position.value.placements()))
                engine.send(EngineCommand.YxSearchDefend)
            }
            is ConsoleCommand.Nbest -> {
                busy()?.let { return err(it) }
                val s = settings.settings.value
                engine.send(EngineCommand.InfoNbestSym(s.nbestSym))
                engine.send(EngineCommand.YxBoard(game.position.value.placements()))
                engine.send(EngineCommand.YxNbest(cmd.count ?: s.multiPv))
            }
            is ConsoleCommand.SearchFrom ->
                engine.send(EngineCommand.InfoStartDepth(cmd.depth))
            is ConsoleCommand.Balance -> {
                busy()?.let { return err(it) }
                engine.send(EngineCommand.YxBoard(game.position.value.placements()))
                engine.send(EngineCommand.YxBalance(cmd.two, cmd.bias))
            }
            ConsoleCommand.BestLine -> log("BESTLINE 은 분석 패널 상태줄에 있습니다")

            // ---- engine maintenance -------------------------------------------
            ConsoleCommand.PrintFeatures -> engine.send(EngineCommand.YxPrintFeature)
            ConsoleCommand.SendBoard ->
                engine.send(EngineCommand.YxBoard(game.position.value.placements()))
            ConsoleCommand.DbRefresh -> {
                val s = settings.settings.value
                engine.send(EngineCommand.Info("usedatabase", if (s.useDatabase) "1" else "0"))
                engine.send(EngineCommand.DatabaseReadonly(s.databaseReadonly))
            }

            // ---- game actions --------------------------------------------------
            is ConsoleCommand.ThinkingCmd -> thinking(cmd.action)
            is ConsoleCommand.Undo -> guarded { if (cmd.all) game.toStart() else game.undo() }
            is ConsoleCommand.Redo -> guarded { if (cmd.all) game.toEnd() else game.redo() }
            ConsoleCommand.Draw -> guarded { game.offerDraw() }
            ConsoleCommand.Resign -> guarded { game.resign() }
            is ConsoleCommand.Symmetry -> guarded {
                game.replaceLine(
                    BoardTransform.symmetry(game.position.value.moves, size, cmd.symmetry),
                )
            }
            is ConsoleCommand.Shift -> guarded {
                val moved = BoardTransform.shift(game.position.value.moves, size, cmd.direction)
                if (moved == null) err("판 밖으로 나가서 옮길 수 없습니다")
                else game.replaceLine(moved)
            }

            // ---- callbacks -------------------------------------------------------
            is ConsoleCommand.CallbackEnabled -> {
                _state.update { it.copy(callbacksSuspended = !cmd.on) }
                log("콜백 ${onOff(cmd.on)}")
            }

            is ConsoleCommand.Unknown ->
                if (cmd.line.isNotEmpty()) err("알 수 없는 명령입니다: ${cmd.line}")
        }
    }

    // ---- individual actions -------------------------------------------------

    private suspend fun pushPos(slot: Int, size: Int) {
        if (slot !in 0 until ToolsState.STACK_SLOTS) {
            return err("슬롯 번호는 0~9 여야 합니다")
        }
        val text = BoardTransform.toPositionString(game.position.value.moves, size)
        _state.update { st ->
            st.copy(stack = st.stack.toMutableList().also { it[slot] = text })
        }
        log("현재 국면을 슬롯 $slot 에 저장했습니다")
    }

    private suspend fun popPos(slot: Int, size: Int) {
        if (slot !in 0 until ToolsState.STACK_SLOTS) {
            return err("슬롯 번호는 0~9 여야 합니다")
        }
        val text = _state.value.stack[slot]
        if (text.isNullOrEmpty()) return err("슬롯 $slot 이 비어 있습니다")
        putPos(text, size)
    }

    private suspend fun putPos(text: String, size: Int) {
        busy()?.let { return err(it) }
        val moves = BoardTransform.fromPositionString(text, size)
        if (moves.isEmpty()) return err("국면 문자열을 읽을 수 없습니다: $text")
        game.replaceLine(moves)
    }

    private suspend fun block(cells: List<Move>) {
        if (cells.isEmpty()) return err("차단할 점이 없습니다 (예: block h8i8)")
        for (c in cells) engine.send(EngineCommand.YxBlock(c))
        _state.update { it.copy(blocked = it.blocked + cells) }
    }

    /** `block compare <cells>`: block **everything except** these points. */
    private suspend fun blockCompare(cells: List<Move>, size: Int) {
        val keep = cells.toSet()
        val target = BlockTargets.complement(keep, game.position.value.moves, size)
        engine.send(EngineCommand.YxBlockReset)
        for (c in target) engine.send(EngineCommand.YxBlock(c))
        _state.update { it.copy(blocked = target.toSet()) }
        log("${keep.size}개 점만 남기고 ${target.size}개를 차단했습니다")
    }

    private suspend fun blockPath(cells: List<Move>, undo: Boolean) {
        if (cells.isEmpty()) return err("차단할 경로가 없습니다 (예: blockpath h8h7)")
        engine.send(
            EngineCommand.YxBlockPath(game.position.value.moves, cells, undo),
        )
    }

    /**
     * `blockpath except <cells>`: block every continuation but these. The
     * desktop walks the whole board and sends one `yxblockpath` per point
     * (main.c:10490-10510).
     */
    private suspend fun blockPathExcept(cells: List<Move>, size: Int) {
        val line = game.position.value.moves
        val target = BlockTargets.complement(cells, line, size)
        for (m in target) engine.send(EngineCommand.YxBlockPath(line, listOf(m)))
        log("${cells.size}개 후속수만 남기고 ${target.size}개 경로를 차단했습니다")
    }

    private suspend fun resetBlocks() {
        engine.send(EngineCommand.YxBlockReset)
        _state.update { it.copy(blocked = emptySet()) }
    }

    private suspend fun thinking(action: ConsoleCommand.Thinking) {
        busy()?.let { return err(it) }
        when (action) {
            ConsoleCommand.Thinking.START -> game.engineMove()
            ConsoleCommand.Thinking.STOP -> game.stopThinking()
            ConsoleCommand.Thinking.TOGGLE ->
                if (game.state.value.thinking) game.stopThinking() else game.engineMove()
        }
    }

    private suspend inline fun guarded(action: () -> Unit) {
        val reason = busy()
        if (reason != null) err(reason) else action()
    }

    // ---- callbacks ----------------------------------------------------------

    /** The engine's move landed: auto-reset the blocks, then fire the move
     *  callback (main.c:13940 and 13976). */
    private fun onEngineMove(plies: Int) {
        scope.launch {
            val s = settings.settings.value
            if (s.blockAutoReset) lock.withLock { resetBlocks() }
            if (s.blockPathAutoReset) engine.send(EngineCommand.YxBlockPathReset)
            fire(_state.value.callbacks.moveScript(plies))
        }
    }

    /**
     * A finished search reported its verdict. Mirrors main.c:13795-13830 —
     * mate / mated fire immediately, a draw only after [CallbackConfig.drawCount]
     * consecutive 50 % evaluations.
     */
    private fun onSearchVerdict(mate: Int?, winRatePercent: Int?) {
        val cb = _state.value.callbacks
        when {
            mate != null && mate > 0 -> {
                drawingCount = 0
                fire(cb.onMate)
            }
            mate != null && mate < 0 -> {
                drawingCount = 0
                fire(cb.onMated)
            }
            winRatePercent == 50 -> {
                drawingCount++
                if (drawingCount >= cb.drawCount) {
                    drawingCount = 0
                    fire(cb.onDraw)
                }
            }
            else -> drawingCount = 0
        }
    }

    private fun fire(script: String) {
        if (script.isBlank() || !_state.value.callbacksActive) return
        scope.launch { lock.withLock { execute(script) } }
    }

    // ---- helpers ------------------------------------------------------------

    /** Board writes are refused while a research run owns the conversation. */
    private fun busy(): String? = when {
        review.progress.value.running -> "게임 리뷰가 진행 중입니다 — 먼저 중지하세요"
        prove.get().progress.value.running -> "국면 증명이 진행 중입니다 — 먼저 중지하세요"
        else -> null
    }

    private fun onOff(on: Boolean) = if (on) "켜짐" else "꺼짐"

    private fun log(text: String) {
        _output.tryEmit(ToolsOutcome(text))
    }

    private fun err(text: String) {
        _output.tryEmit(ToolsOutcome(text, isError = true))
    }

    private companion object {
        val HELP = """
            사용 가능한 명령
             hash clear / hash usage / hash autoclear [on,off]
             hash dump [서버경로] / hash load [서버경로]
             block [좌표…] / block undo [좌표] / block reset / block compare [좌표…]
             block autoreset [on,off]
             blockpath [좌표…] / blockpath undo [좌표…] / blockpath reset
             blockpath except [좌표…] / blockpath autoreset [on,off]
             forbid [0,1] [좌표] / forbid undo [0,1] [좌표]
             searchdefend / nbest [k] / search from [깊이] / balance1 [n] / balance2 [n]
             pushpos [0-9] / poppos [0-9] / getpos / putpos [문자열]
             send board / dbrefresh / print features
             thinking [start,stop,toggle] / undo [one,all] / redo [one,all]
             draw / resign / rotate [90,180,270] / flip [-,|,\,/] / move [^,v,<,>]
             callback [on,off] / command [on,off] / echo [문장] / sleep [ms] / clear
        """.trimIndent()
    }
}
