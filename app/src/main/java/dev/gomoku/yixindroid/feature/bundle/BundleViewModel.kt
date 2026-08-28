package dev.gomoku.yixindroid.feature.bundle

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.data.bundle.BundleImporter
import dev.gomoku.yixindroid.domain.repository.AppearanceRepository
import dev.gomoku.yixindroid.domain.repository.ExplorerRepository
import dev.gomoku.yixindroid.domain.repository.RankingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the one card that gets data into the app.
 *
 * @param packs games in the loaded explorer packs, 0 when there are none.
 * @param freqGames games in the loaded freq dataset, 0 when there is none.
 * @param appearance where the toolbar/labels came from, null on defaults.
 */
data class BundleUiState(
    val packGames: Int = 0,
    val packPositions: Int = 0,
    val freqGames: Int = 0,
    val appearance: String? = null,
    val running: Boolean = false,
    val report: BundleImporter.Report? = null,
    val message: String? = null,
) {
    val packsLoaded: Boolean get() = packGames > 0
    val freqLoaded: Boolean get() = freqGames > 0

    /** Of the three importable things, how many are in place. */
    val ready: Int
        get() = listOf(packsLoaded, freqLoaded, appearance != null).count { it }
}

@HiltViewModel
class BundleViewModel @Inject constructor(
    private val importer: BundleImporter,
    explorer: ExplorerRepository,
    rankings: RankingsRepository,
    appearance: AppearanceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BundleUiState())
    val uiState: StateFlow<BundleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            explorer.packs.collect { info ->
                _state.update {
                    it.copy(
                        packGames = info?.totalGames ?: 0,
                        packPositions = info?.positions ?: 0,
                    )
                }
            }
        }
        viewModelScope.launch {
            rankings.freq.collect { b -> _state.update { it.copy(freqGames = b?.gameCount ?: 0) } }
        }
        viewModelScope.launch {
            appearance.source.collect { src -> _state.update { it.copy(appearance = src) } }
        }
        viewModelScope.launch {
            importer.running.collect { on -> _state.update { it.copy(running = on) } }
        }
        viewModelScope.launch {
            importer.report.collect { r -> _state.update { it.copy(report = r) } }
        }
    }

    fun onPickFolder(tree: Uri?) {
        val uri = tree ?: return
        viewModelScope.launch {
            val result = importer.importAll(uri)
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { report -> report.summary() },
                        onFailure = { e ->
                            tr("폴더를 읽지 못했습니다: ${e.message}", "Could not read the folder: ${e.message}")
                        },
                    ),
                )
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}
