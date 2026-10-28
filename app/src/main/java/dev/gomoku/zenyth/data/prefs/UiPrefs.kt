package dev.gomoku.zenyth.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's own UI choices: which board tab was open, which folds were
 * expanded, whether the engine starts with the app.
 *
 * None of this has a line in the desktop's `settings.txt`, so it does not
 * belong in [dev.gomoku.zenyth.core.model.AppSettings] (pinned to those 67
 * lines) — the same reason [LocalEngineStore] is separate.
 *
 * SharedPreferences rather than DataStore: a fold has to know whether it is open
 * in the frame it is first drawn, and a flow that answers a frame later makes
 * every remembered panel blink shut and open again on launch.
 */
@Singleton
class UiPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** Connect to the last engine when the app opens (RapfiWeb's auto boot). */
    val autoStartEngine: BoolPref get() = bool("engine.autostart", true)

    /**
     * Fetch the official joseki curriculum from RapfiWeb by itself. The app's one
     * automatic network request (a static file, nothing sent); off, the refresh
     * button and lesson links still fetch when asked.
     */
    val curriculumAutoUpdate: BoolPref get() = bool("curriculum.autoupdate", true)

    /** Whether a [dev.gomoku.zenyth.core.designsystem.component.Disclosure] is open. */
    fun isOpen(id: String, default: Boolean): Boolean = prefs.getBoolean("open.$id", default)

    fun setOpen(id: String, open: Boolean) {
        prefs.edit().putBoolean("open.$id", open).apply()
    }

    private val cache = HashMap<String, Any>()

    /** One instance per key, so every reader of a key sees the same flow. */
    @Synchronized
    fun bool(key: String, default: Boolean): BoolPref =
        cache.getOrPut(key) { BoolPref(key, default) } as BoolPref

    @Synchronized
    fun int(key: String, default: Int): IntPref =
        cache.getOrPut(key) { IntPref(key, default) } as IntPref

    @Synchronized
    fun string(key: String, default: String): StringPref =
        cache.getOrPut(key) { StringPref(key, default) } as StringPref

    inner class BoolPref internal constructor(private val key: String, default: Boolean) {
        private val flow = MutableStateFlow(prefs.getBoolean(key, default))
        val state: StateFlow<Boolean> = flow.asStateFlow()
        val value: Boolean get() = flow.value
        fun set(v: Boolean) {
            prefs.edit().putBoolean(key, v).apply()
            flow.value = v
        }
    }

    inner class IntPref internal constructor(private val key: String, default: Int) {
        private val flow = MutableStateFlow(prefs.getInt(key, default))
        val state: StateFlow<Int> = flow.asStateFlow()
        val value: Int get() = flow.value
        fun set(v: Int) {
            prefs.edit().putInt(key, v).apply()
            flow.value = v
        }
    }

    inner class StringPref internal constructor(private val key: String, default: String) {
        private val flow = MutableStateFlow(prefs.getString(key, default) ?: default)
        val state: StateFlow<String> = flow.asStateFlow()
        val value: String get() = flow.value
        fun set(v: String) {
            prefs.edit().putString(key, v).apply()
            flow.value = v
        }
    }
}
