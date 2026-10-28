package dev.gomoku.zenyth.data.engine

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.zenyth.core.model.capHash
import dev.gomoku.zenyth.data.prefs.LocalEngineStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lowers the on-device engine's table after the system killed it for memory (T3).
 *
 * The engine holds its table plus ~95 MB from the moment it starts (D5), so a
 * table that is too big for the phone shows up as a kill, not as slowness. Two
 * ways the kill is seen:
 *
 *  - **The whole app was killed** — the usual low-memory kill takes the app's
 *    processes together. Android 11+ keeps the reason ([ApplicationExitInfo]); if
 *    it was low memory while an on-device session was open, the next start lowers
 *    the table one step.
 *  - **Only the engine was killed** (SIGKILL while the app lives on). That is
 *    counted as memory only if the system says memory is low at that moment —
 *    otherwise it may be the phantom-process killer (T4), which a smaller table
 *    would not appease.
 *
 * The ceiling stays until the user picks a table size again.
 */
@Singleton
class MemoryGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LocalEngineStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activity = context.getSystemService(ActivityManager::class.java)

    /** Called once at app start, before the engine is likely to be up. */
    fun checkLastExit() {
        scope.launch {
            val wasOpen = store.engineWasOpen()
            store.setEngineOpen(false)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || activity == null) return@launch
            val seen = store.exitSeenMs()
            val exits = runCatching {
                activity.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
            }.getOrDefault(emptyList()).filter { it.timestamp > seen }
            if (exits.isEmpty()) return@launch
            store.setExitSeenMs(exits.maxOf { it.timestamp })
            if (wasOpen && exits.any { it.reason == ApplicationExitInfo.REASON_LOW_MEMORY }) lower("app killed for memory")
        }
    }

    /** The engine process died of SIGKILL without being asked to close. */
    fun onEngineKilled() {
        val info = ActivityManager.MemoryInfo()
        activity?.getMemoryInfo(info)
        if (info.lowMemory) {
            scope.launch { lower("engine killed while memory was low") }
        } else {
            Log.w(TAG, "engine killed while memory was not low — not lowering the table (T4?)")
        }
    }

    fun sessionOpened() {
        scope.launch { store.setEngineOpen(true) }
    }

    fun sessionClosed() {
        scope.launch { store.setEngineOpen(false) }
    }

    private suspend fun lower(why: String) {
        val current = store.profile.first().capHash(store.memoryCapMb.first()).hashMb
        val next = store.lowerMemoryCap(current)
        Log.w(TAG, "$why — table $current MB -> ${next ?: "unchanged (already the smallest)"}")
    }

    private companion object {
        const val TAG = "MemoryGuard"
        const val MAX_EXITS = 5
    }
}
