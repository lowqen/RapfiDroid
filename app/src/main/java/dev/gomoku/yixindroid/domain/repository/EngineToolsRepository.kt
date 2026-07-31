package dev.gomoku.yixindroid.domain.repository

import dev.gomoku.yixindroid.core.model.CallbackConfig
import dev.gomoku.yixindroid.core.model.ToolsOutcome
import dev.gomoku.yixindroid.core.model.ToolsState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The engine operations menu — hash tools, blocked points, forced forbidden
 * points, the position stack, and the console command language that drives them
 * (main.c `execute_command`).
 *
 * Everything goes through [run]: buttons build the same script text a user could
 * type, so there is exactly one code path and it is the one the desktop's
 * toolbar, hotkeys and callbacks all share.
 */
interface EngineToolsRepository {

    val state: StateFlow<ToolsState>

    /** Client-side console output (the desktop's `printf_log` lines). */
    val output: SharedFlow<ToolsOutcome>

    /** Execute a console script — one or more lines, `sleep` included. */
    suspend fun run(script: String)

    suspend fun setCallbacks(config: CallbackConfig)

    /** Restore persisted tool settings (call once at startup). */
    suspend fun restore()
}
