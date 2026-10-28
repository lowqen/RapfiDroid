package dev.gomoku.zenyth.data.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.zenyth.core.model.ThermalState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone's [ThermalState], kept current for the life of the process.
 *
 * Thermal status comes from `PowerManager`'s listener (API 29; older phones report
 * none and are never capped), battery saver from its broadcast. Both are cheap
 * system callbacks, registered once — this is an app-wide singleton, like the
 * engine connection it feeds.
 */
@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val power = context.getSystemService(PowerManager::class.java)

    private val _state = MutableStateFlow(
        ThermalState(
            status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power?.currentThermalStatus ?: 0 else 0,
            powerSave = power?.isPowerSaveMode == true,
        ),
    )
    val state: StateFlow<ThermalState> = _state.asStateFlow()

    init {
        if (power != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                power.addThermalStatusListener(ContextCompat.getMainExecutor(context)) { status ->
                    _state.update { it.copy(status = status) }
                }
            }
            ContextCompat.registerReceiver(
                context,
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        _state.update { it.copy(powerSave = power.isPowerSaveMode) }
                    }
                },
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }
}
