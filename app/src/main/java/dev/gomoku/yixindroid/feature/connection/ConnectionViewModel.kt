package dev.gomoku.yixindroid.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import dev.gomoku.yixindroid.core.model.EngineTarget
import dev.gomoku.yixindroid.core.model.FontSpec
import dev.gomoku.yixindroid.core.model.LinkHealth
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
    private val localMode = MutableStateFlow(true)
    private val draft = MutableStateFlow("")
    private val consoleLines = MutableStateFlow(ConsoleBuffer())

    /** Everything about *where* the engine is, as one value. */
    private data class Link(
        val host: String,
        val port: String,
        val local: Boolean,
        val serverEnabled: Boolean,
        val health: LinkHealth,
    )

    // combine takes five flows at most, so the endpoint fields, the chosen
    // engine and the link state travel together as one.
    private val link = combine(
        host,
        port,
        localMode,
        endpointStore.serverEnabled,
        repository.health,
    ) { h, p, local, serverEnabled, health ->
        Link(h, p, local, serverEnabled, health)
    }

    val uiState: StateFlow<ConnectionUiState> =
        combine(
            link,
            repository.state,
            consoleLines,
            draft,
            settingsRepository.settings,
        ) { l, st, buffer, d, config ->
            ConnectionUiState(
                host = l.host,
                port = l.port,
                localMode = l.local,
                serverEnabled = l.serverEnabled,
                state = st,
                health = l.health,
                console = buffer.lines,
                commandDraft = d,
                showLog = config.showLog,
                logScalePercent = config.logScale,
                logFont = FontSpec.parse(config.textLogFont),
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
            localMode.value = endpointStore.localMode.first()
        }
        viewModelScope.launch {
            repository.console.collect { line ->
                consoleLines.update { it.plus(line) }
            }
        }
    }

    fun onHostChange(value: String) { host.value = value.trim() }

    fun onPortChange(value: String) { port.value = value.filter(Char::isDigit).take(5) }

    fun onLocalModeChange(local: Boolean) { localMode.value = local }

    fun onDraftChange(value: String) { draft.value = value }

    fun onConnect() {
        val endpoint = EngineEndpoint(
            host = host.value.trim(),
            port = port.value.toIntOrNull() ?: EngineEndpoint.DEFAULT_PORT,
        )
        // `localMode` already reflects the gate (the store forces it while the
        // server engine is off), but reading the state rather than the field
        // keeps the two from disagreeing if the switch flips mid-tap.
        val local = localMode.value || !uiState.value.serverEnabled
        val target = if (local) EngineTarget.Local else EngineTarget.Remote(endpoint)
        viewModelScope.launch {
            // The address is saved either way: choosing the on-device engine is
            // not a decision to forget the server.
            endpointStore.save(target, endpoint)
            runCatching { repository.connect(target) }
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

    /** Stop waiting out the backoff and try the engine again now. */
    fun onRetryNow() {
        viewModelScope.launch { runCatching { repository.retryNow() } }
    }

    fun onClearConsole() {
        consoleLines.value = ConsoleBuffer()
    }
}
