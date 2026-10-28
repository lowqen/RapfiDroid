package dev.gomoku.zenyth.data.engine

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import dev.gomoku.zenyth.core.model.ConnectionState
import dev.gomoku.zenyth.core.model.EngineIdlePolicy
import dev.gomoku.zenyth.domain.repository.DatabaseRepository
import dev.gomoku.zenyth.domain.repository.EngineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hangs up an engine session left idle in the background, and brings it back when
 * the app returns ([EngineIdlePolicy]). Once the session is down the foreground
 * service stops by itself, so the notification goes with it.
 *
 * Started once from the Application; it watches the whole process (any activity
 * visible counts as foreground), not one screen.
 */
@Singleton
class EngineIdleWatcher @Inject constructor(
    private val engine: EngineRepository,
    private val database: DatabaseRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val foreground = MutableStateFlow(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> foreground.value = true
                    Lifecycle.Event.ON_STOP -> foreground.value = false
                    else -> Unit
                }
            },
        )
        scope.launch {
            combine(foreground, engine.state, engine.health) { fg, state, health ->
                EngineIdlePolicy.isIdle(fg, state, health)
            }
                .distinctUntilChanged()
                // Any change — the app coming back, a search starting — cancels the wait.
                .collectLatest { idle ->
                    if (idle) {
                        delay(EngineIdlePolicy.PARK_AFTER_MS)
                        engine.park()
                    }
                }
        }
        scope.launch {
            foreground.collect { fg ->
                when {
                    fg -> engine.resumeParked()
                    // Leaving the app is when a low-memory kill becomes likely; the
                    // database is held only in the engine's memory until saved (T3).
                    // Only while idle — a search drops every command but stop.
                    engine.state.value == ConnectionState.Ready -> runCatching { database.saveIfLoaded() }
                }
            }
        }
    }
}
