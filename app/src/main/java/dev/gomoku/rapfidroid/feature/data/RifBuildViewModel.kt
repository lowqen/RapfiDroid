package dev.gomoku.rapfidroid.feature.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.rapfidroid.core.model.PackBuildState
import dev.gomoku.rapfidroid.data.rif.PackBuildManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The screen's half of the on-device pack build. The work itself lives in a
 * singleton with a service behind it, not here — it outlives this screen, the
 * back stack and the app being in the foreground.
 */
@HiltViewModel
class RifBuildViewModel @Inject constructor(
    private val manager: PackBuildManager,
) : ViewModel() {

    val state: StateFlow<PackBuildState> = manager.state

    fun onFilePicked(uri: Uri?) {
        if (uri != null) manager.start(uri)
    }

    fun onCancel() = manager.cancel()

    fun onAcknowledge() = manager.acknowledge()
}
