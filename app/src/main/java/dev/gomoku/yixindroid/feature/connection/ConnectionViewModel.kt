package dev.gomoku.yixindroid.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dev.gomoku.yixindroid.data.prefs.EndpointStore
import dev.gomoku.yixindroid.domain.engine.EngineCommand
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val repository: EngineRepository,
    private val endpointStore: EndpointStore,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val host = MutableStateFlow(EngineEndpoint.DEFAULT_HOST)
    private val port = MutableStateFlow(EngineEndpoint.DEFAULT_PORT.toString())
    private val draft = MutableStateFlow("")
    private val consoleLines = MutableStateFlow(ConsoleBuffer())

    // combine takes five flows at most, so host+port travel as one pair and the
    // link state travels with it.
    private val link = combine(host, port, repository.health) { h, p, health ->
        Triple(h, p, health)
    }

    val uiState: StateFlow<ConnectionUiState> =
        combine(
            link,
            repository.state,
            consoleLines,
            draft,
            settingsRepository.settings,
        ) { (h, p, health), st, buffer, d, config ->
            ConnectionUiState(
                host = h,
                port = p,
                state = st,
                health = health,
                console = buffer.lines,
                commandDraft = d,
                showLog = config.showLog,
                logScalePercent = config.logScale,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConnectionUiState(),
        )

    init {
        viewModelScope.launch {
            val saved = endpointStore.endpoint.first()
            host.value = saved.host
            port.value = saved.port.toString()
        }
        viewModelScope.launch {
            repository.console.collect { line ->
                consoleLines.update { it.plus(line) }
            }
        }
    }

    fun onHostChange(value: String) { host.value = value.trim() }

    fun onPortChange(value: String) { port.value = value.filter(Char::isDigit).take(5) }

    fun onDraftChange(value: String) { draft.value = value }

    fun onConnect() {
        val endpoint = EngineEndpoint(
            host = host.value.trim(),
            port = port.value.toIntOrNull() ?: EngineEndpoint.DEFAULT_PORT,
        )
        viewModelScope.launch {
            endpointStore.save(endpoint)
            runCatching { repository.connect(endpoint) }
        }
    }

    fun onDisconnect() {
        repository.disconnect()
    }

    fun onSend() {
        val line = draft.value.trim()
        if (line.isEmpty()) return
        draft.value = ""
        viewModelScope.launch {
            runCatching { repository.send(EngineCommand.Raw(line)) }
        }
    }

    /** Stop waiting out the backoff and try the endpoint again now. */
    fun onRetryNow() {
        viewModelScope.launch { runCatching { repository.retryNow() } }
    }

    fun onClearConsole() {
        consoleLines.value = ConsoleBuffer()
    }
}
