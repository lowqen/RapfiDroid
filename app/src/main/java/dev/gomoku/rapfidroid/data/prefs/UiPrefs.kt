package dev.gomoku.rapfidroid.data.prefs

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
 * belong in [dev.gomoku.rapfidroid.core.model.AppSettings] (pinned to those 67
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
    val autoStartEngine = BoolPref("engine.autostart", true)

    /** Whether a [dev.gomoku.rapfidroid.core.designsystem.component.Disclosure] is open. */
    fun isOpen(id: String, default: Boolean): Boolean = prefs.getBoolean("open.$id", default)

    fun setOpen(id: String, open: Boolean) {
        prefs.edit().putBoolean("open.$id", open).apply()
    }

    fun bool(key: String, default: Boolean) = BoolPref(key, default)

    fun int(key: String, default: Int) = IntPref(key, default)

    fun string(key: String, default: String) = StringPref(key, default)

    inner class BoolPref internal constructor(private val key: String, default: Boolean) {
        private val flow = MutableStateFlow(prefs.getBoolean(key, default))
        val value: StateFlow<Boolean> = flow.asStateFlow()
        fun set(v: Boolean) {
            prefs.edit().putBoolean(key, v).apply()
            flow.value = v
        }
    }

    inner class IntPref internal constructor(private val key: String, default: Int) {
        private val flow = MutableStateFlow(prefs.getInt(key, default))
        val value: StateFlow<Int> = flow.asStateFlow()
        fun set(v: Int) {
            prefs.edit().putInt(key, v).apply()
            flow.value = v
        }
    }

    inner class StringPref internal constructor(private val key: String, default: String) {
        private val flow = MutableStateFlow(prefs.getString(key, default) ?: default)
        val value: StateFlow<String> = flow.asStateFlow()
        fun set(v: String) {
            prefs.edit().putString(key, v).apply()
            flow.value = v
        }
    }
}
