package dev.gomoku.yixindroid.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.yixindroid.core.model.DesktopSettings
import dev.gomoku.yixindroid.core.model.SettingCategory
import dev.gomoku.yixindroid.core.model.SettingEditor
import dev.gomoku.yixindroid.core.model.SettingsFile
import dev.gomoku.yixindroid.data.settings.SettingsFileIo
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dev.gomoku.yixindroid.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val engine: EngineRepository,
    private val fileIo: SettingsFileIo,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Which desktop file the pending SAF pick refers to. */
    var pendingFile: SettingsFile = SettingsFile.MAIN
        private set

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s -> _state.update { it.copy(settings = s) } }
        }
        viewModelScope.launch {
            engine.capabilities.collect { c -> _state.update { it.copy(capabilities = c) } }
        }
        viewModelScope.launch {
            engine.state.collect { s -> _state.update { it.copy(connected = s.isLive) } }
        }
    }

    fun onQuery(text: String) = _state.update { it.copy(query = text) }

    fun onCategory(category: SettingCategory?) = _state.update { it.copy(category = category) }

    /**
     * Commit one setting. Numbers are first clamped to the *engine-reported*
     * maximum where there is one (threads, hash), because the spec range only
     * knows the protocol limit — asking for more threads than the server has
     * would silently do nothing useful.
     */
    fun onValue(id: String, raw: String) {
        val spec = DesktopSettings.spec(id)
        val editor = spec?.let { _state.value.editorFor(it) }
        val value = if (editor is SettingEditor.Number) {
            raw.trim().toLongOrNull()?.coerceIn(editor.min, editor.max)?.toString() ?: raw
        } else {
            raw
        }
        viewModelScope.launch { settingsRepository.set(id, value) }
    }

    fun onReset() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            _state.update { it.copy(message = "설정을 PC 기본값으로 되돌렸습니다") }
        }
    }

    fun prepare(file: SettingsFile) {
        pendingFile = file
    }

    fun onExport(uri: Uri?) {
        val target = uri ?: return
        val file = pendingFile
        viewModelScope.launch {
            val result = runCatching { fileIo.write(target, settingsRepository.export(file)) }
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { "${file.fileName} ${file.lineCount}줄을 내보냈습니다" },
                        onFailure = { e -> "내보내기 실패: ${e.message}" },
                    ),
                )
            }
        }
    }

    fun onImport(uri: Uri?) {
        val source = uri ?: return
        val file = pendingFile
        viewModelScope.launch {
            val result = runCatching {
                settingsRepository.import(fileIo.read(source), file)
            }
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { n -> "${file.fileName} ${n}줄을 불러왔습니다" },
                        onFailure = { e -> "불러오기 실패: ${e.message}" },
                    ),
                )
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}
