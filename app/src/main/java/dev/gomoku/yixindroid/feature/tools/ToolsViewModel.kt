package dev.gomoku.yixindroid.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.CallbackConfig
import dev.gomoku.yixindroid.core.model.ToolScripts
import dev.gomoku.yixindroid.core.model.ToolsState
import dev.gomoku.yixindroid.domain.repository.EngineToolsRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One line of the tools console. */
data class ToolsLine(val text: String, val isError: Boolean)

data class ToolsUiState(
    val tools: ToolsState = ToolsState(),
    val settings: AppSettings = AppSettings(),
    val draft: String = "",
    val hashPath: String = "rapfi.hash",
    val startDepth: String = "1",
    val log: List<ToolsLine> = emptyList(),
    val editingCallbacks: Boolean = false,
) {
    val blockedCount: Int get() = tools.blocked.size
    val filledSlots: List<Int>
        get() = tools.stack.indices.filter { !tools.stack[it].isNullOrEmpty() }
}

/**
 * The engine tools tab: hash / blocked points / position stack / maintenance
 * scripts, plus a console for the raw command language.
 *
 * Every button below builds a **command line** and hands it to the repository,
 * so a button press and a typed command take the identical path — the same
 * arrangement the desktop has, where its toolbar buttons are literally stored
 * command scripts.
 */
@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val repository: EngineToolsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { repository.restore() }
        combine(repository.state, settingsRepository.settings) { t, s -> t to s }
            .onEach { (t, s) -> _ui.update { it.copy(tools = t, settings = s) } }
            .launchIn(viewModelScope)
        repository.output.onEach { out ->
            _ui.update {
                it.copy(log = (it.log + ToolsLine(out.text, out.isError)).takeLast(MAX_LOG))
            }
        }.launchIn(viewModelScope)
    }

    /** Run a console script — the single entry point for buttons and typing. */
    fun run(script: String) {
        if (script.isBlank()) return
        _ui.update { it.copy(log = it.log + ToolsLine("> ${script.replace('\n', ';')}", false)) }
        viewModelScope.launch { repository.run(script) }
    }

    fun onDraftChange(text: String) = _ui.update { it.copy(draft = text) }

    fun onSubmitDraft() {
        val text = _ui.value.draft
        _ui.update { it.copy(draft = "") }
        run(text)
    }

    fun onHashPathChange(text: String) = _ui.update { it.copy(hashPath = text) }
    fun onStartDepthChange(text: String) = _ui.update { it.copy(startDepth = text) }

    fun onClearLog() = _ui.update { it.copy(log = emptyList()) }

    // ---- shortcuts ----------------------------------------------------------

    fun onHashDump() = run("hash dump ${_ui.value.hashPath}")
    fun onHashLoad() = run("hash load ${_ui.value.hashPath}")
    fun onSearchFrom() = run("search from ${_ui.value.startDepth.toIntOrNull() ?: 1}")
    fun onBench() = run(ToolScripts.BENCH)
    fun onTrace() = run(ToolScripts.TRACE)
    fun onReload(file: String) = run(ToolScripts.reload(file))
    fun onPush(slot: Int) = run("pushpos $slot")
    fun onPop(slot: Int) = run("poppos $slot")

    fun onToggleHashAutoClear() =
        run("hash autoclear ${if (_ui.value.settings.hashAutoClear) "off" else "on"}")

    fun onToggleBlockAutoReset() =
        run("block autoreset ${if (_ui.value.settings.blockAutoReset) "off" else "on"}")

    fun onToggleBlockPathAutoReset() =
        run("blockpath autoreset ${if (_ui.value.settings.blockPathAutoReset) "off" else "on"}")

    // ---- callbacks ----------------------------------------------------------

    fun onEditCallbacks(editing: Boolean) = _ui.update { it.copy(editingCallbacks = editing) }

    fun onCallbacksChange(config: CallbackConfig) {
        viewModelScope.launch { repository.setCallbacks(config) }
    }

    private companion object {
        const val MAX_LOG = 300
    }
}
