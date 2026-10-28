package dev.gomoku.zenyth.data.tools

import dev.gomoku.zenyth.core.common.IoDispatcher
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.BlockTargets
import dev.gomoku.zenyth.core.model.BoardTransform
import dev.gomoku.zenyth.core.model.CallbackConfig
import dev.gomoku.zenyth.core.model.ConsoleCommand
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.ToolsOutcome
import dev.gomoku.zenyth.core.model.ToolsState
import dev.gomoku.zenyth.domain.engine.EngineCommand
import dev.gomoku.zenyth.domain.engine.EngineResponse
import dev.gomoku.zenyth.domain.repository.EngineRepository
import dev.gomoku.zenyth.domain.repository.EngineToolsRepository
import dev.gomoku.zenyth.domain.repository.GameRepository
import dev.gomoku.zenyth.domain.repository.ProveRepository
import dev.gomoku.zenyth.domain.repository.ReviewRepository
import dev.gomoku.zenyth.domain.repository.SettingsRepository
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
                log(tr("명령 전달 모드 ${if (cmd.on) "켜짐 — 입력이 엔진으로 그대로 갑니다" else "꺼짐"}", "Pass-through mode ${if (cmd.on) "on — input goes straight to the engine" else "off"}", "透传模式${if (cmd.on) "开启，输入将直接发送给引擎" else "关闭"}"))
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
                log(tr("해시 자동 지우기 ${onOff(cmd.on)}", "Auto-clear hash ${onOff(cmd.on)}", "自动清空置换表 ${onOff(cmd.on)}"))
            }
            is ConsoleCommand.HashDump ->
                if (cmd.path.isBlank()) err(tr("hash dump <서버 경로> 가 필요합니다", "hash dump needs <server path>", "hash dump 需要 <服务器路径>"))
                else engine.send(EngineCommand.YxHashDump(cmd.path))
            is ConsoleCommand.HashLoad ->
                if (cmd.path.isBlank()) err(tr("hash load <서버 경로> 가 필요합니다", "hash load needs <server path>", "hash load 需要 <服务器路径>"))
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
                log(tr("차단 자동 해제 ${onOff(cmd.on)}", "Auto-reset blocks ${onOff(cmd.on)}", "自动重置排除 ${onOff(cmd.on)}"))
            }

            is ConsoleCommand.BlockPath -> blockPath(cmd.cells, undo = false)
            is ConsoleCommand.BlockPathUndo -> blockPath(cmd.cells, undo = true)
            ConsoleCommand.BlockPathReset -> engine.send(EngineCommand.YxBlockPathReset)
            is ConsoleCommand.BlockPathExcept -> blockPathExcept(cmd.cells, size)
            is ConsoleCommand.BlockPathAutoReset -> {
                settings.set("blockPathAutoReset", if (cmd.on) "1" else "0")
                log(tr("차단 경로 자동 해제 ${onOff(cmd.on)}", "Auto-reset blocked paths ${onOff(cmd.on)}", "自动重置排除路径 ${onOff(cmd.on)}"))
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
            ConsoleCommand.BestLine -> log(tr("BESTLINE 은 분석 패널 상태줄에 있습니다", "BESTLINE is in the status line of the analysis panel", "BESTLINE 在分析面板的状态栏中"))

            // ---- engine maintenance -------------------------------------------
            ConsoleCommand.PrintFeatures -> engine.send(EngineCommand.YxPrintFeature)
            ConsoleCommand.SendBoard ->
                engine.send(EngineCommand.YxBoard(game.position.value.placements()))
            ConsoleCommand.DbRefresh -> {
                // No `usedatabase` re-push: a repeated `1` costs a full second
                // copy of the database in the engine. main.c:11414 dropped it
                // for the same reason; the repository would swallow it anyway.
                engine.send(EngineCommand.DatabaseReadonly(settings.settings.value.databaseReadonly))
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
                if (moved == null) err(tr("판 밖으로 나가서 옮길 수 없습니다", "A stone would leave the board, so nothing was moved", "有棋子会移出棋盘，无法平移"))
                else game.replaceLine(moved)
            }

            // ---- callbacks -------------------------------------------------------
            is ConsoleCommand.CallbackEnabled -> {
                _state.update { it.copy(callbacksSuspended = !cmd.on) }
                log(tr("콜백 ${onOff(cmd.on)}", "Callbacks ${onOff(cmd.on)}", "回调 ${onOff(cmd.on)}"))
            }

            is ConsoleCommand.Unknown ->
                if (cmd.line.isNotEmpty()) err(tr("알 수 없는 명령입니다: ${cmd.line}", "Unknown command: ${cmd.line}", "未知命令: ${cmd.line}"))
        }
    }

    // ---- individual actions -------------------------------------------------

    private suspend fun pushPos(slot: Int, size: Int) {
        if (slot !in 0 until ToolsState.STACK_SLOTS) {
            return err(tr("슬롯 번호는 0~9 여야 합니다", "The slot number must be 0-9", "存储位编号必须为 0~9"))
        }
        val text = BoardTransform.toPositionString(game.position.value.moves, size)
        _state.update { st ->
            st.copy(stack = st.stack.toMutableList().also { it[slot] = text })
        }
        log(tr("현재 국면을 슬롯 $slot 에 저장했습니다", "Stored the current position in slot $slot", "已将当前局面存入存储位 $slot"))
    }

    private suspend fun popPos(slot: Int, size: Int) {
        if (slot !in 0 until ToolsState.STACK_SLOTS) {
            return err(tr("슬롯 번호는 0~9 여야 합니다", "The slot number must be 0-9", "存储位编号必须为 0~9"))
        }
        val text = _state.value.stack[slot]
        if (text.isNullOrEmpty()) return err(tr("슬롯 $slot 이 비어 있습니다", "Slot $slot is empty", "存储位 $slot 为空"))
        putPos(text, size)
    }

    private suspend fun putPos(text: String, size: Int) {
        busy()?.let { return err(it) }
        val moves = BoardTransform.fromPositionString(text, size)
        if (moves.isEmpty()) return err(tr("국면 문자열을 읽을 수 없습니다: $text", "Cannot read the position string: $text", "无法读取局面代码: $text"))
        game.replaceLine(moves)
    }

    private suspend fun block(cells: List<Move>) {
        if (cells.isEmpty()) return err(tr("차단할 점이 없습니다 (예: block h8i8)", "No points to block (e.g. block h8i8)", "没有要排除的点 (例: block h8i8)"))
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
        log(tr("${keep.size}개 점만 남기고 ${target.size}개를 차단했습니다", "Blocked ${target.size}, keeping only ${keep.size} points", "只保留 ${keep.size} 个点，排除了 ${target.size} 个"))
    }

    private suspend fun blockPath(cells: List<Move>, undo: Boolean) {
        if (cells.isEmpty()) return err(tr("차단할 경로가 없습니다 (예: blockpath h8h7)", "No paths to block (e.g. blockpath h8h7)", "没有要排除的路径 (例: blockpath h8h7)"))
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
        log(tr("${cells.size}개 후속수만 남기고 ${target.size}개 경로를 차단했습니다", "Blocked ${target.size} paths, keeping only ${cells.size} continuations", "只保留 ${cells.size} 个后续着法，排除了 ${target.size} 条路径"))
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
        review.progress.value.running -> tr("게임 리뷰가 진행 중입니다 — 먼저 중지하세요", "A game review is running — stop it first", "正在进行对局复盘，请先停止")
        prove.get().progress.value.running -> tr("국면 증명이 진행 중입니다 — 먼저 중지하세요", "A proof is running — stop it first", "正在进行局面证明，请先停止")
        else -> null
    }

    private fun onOff(on: Boolean) = if (on) tr("켜짐", "on", "开启") else tr("꺼짐", "off", "关闭")

    private fun log(text: String) {
        _output.tryEmit(ToolsOutcome(text))
    }

    private fun err(text: String) {
        _output.tryEmit(ToolsOutcome(text, isError = true))
    }

    private companion object {
        /** Read each time, so it follows a language change like the rest of the app. */
        val HELP: String get() = tr(HELP_KO, HELP_EN, HELP_ZH)

        val HELP_KO = """
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

        val HELP_EN = """
            Commands
             hash clear / hash usage / hash autoclear [on,off]
             hash dump [server path] / hash load [server path]
             block [points…] / block undo [point] / block reset / block compare [points…]
             block autoreset [on,off]
             blockpath [points…] / blockpath undo [points…] / blockpath reset
             blockpath except [points…] / blockpath autoreset [on,off]
             forbid [0,1] [point] / forbid undo [0,1] [point]
             searchdefend / nbest [k] / search from [depth] / balance1 [n] / balance2 [n]
             pushpos [0-9] / poppos [0-9] / getpos / putpos [string]
             send board / dbrefresh / print features
             thinking [start,stop,toggle] / undo [one,all] / redo [one,all]
             draw / resign / rotate [90,180,270] / flip [-,|,\,/] / move [^,v,<,>]
             callback [on,off] / command [on,off] / echo [text] / sleep [ms] / clear
        """.trimIndent()

        val HELP_ZH = """
            可用命令
             hash clear / hash usage / hash autoclear [on,off]
             hash dump [服务器路径] / hash load [服务器路径]
             block [坐标…] / block undo [坐标] / block reset / block compare [坐标…]
             block autoreset [on,off]
             blockpath [坐标…] / blockpath undo [坐标…] / blockpath reset
             blockpath except [坐标…] / blockpath autoreset [on,off]
             forbid [0,1] [坐标] / forbid undo [0,1] [坐标]
             searchdefend / nbest [k] / search from [深度] / balance1 [n] / balance2 [n]
             pushpos [0-9] / poppos [0-9] / getpos / putpos [代码]
             send board / dbrefresh / print features
             thinking [start,stop,toggle] / undo [one,all] / redo [one,all]
             draw / resign / rotate [90,180,270] / flip [-,|,\,/] / move [^,v,<,>]
             callback [on,off] / command [on,off] / echo [文字] / sleep [ms] / clear
        """.trimIndent()
    }
}
