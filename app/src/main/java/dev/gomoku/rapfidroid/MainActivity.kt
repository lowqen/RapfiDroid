package dev.gomoku.rapfidroid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiDroidTheme
import dev.gomoku.rapfidroid.core.model.HotkeyMap
import dev.gomoku.rapfidroid.data.engine.DebugLogWriter
import dev.gomoku.rapfidroid.domain.repository.AppearanceRepository
import dev.gomoku.rapfidroid.domain.repository.EngineToolsRepository
import dev.gomoku.rapfidroid.domain.repository.SettingsRepository
import kotlinx.coroutines.launch
import dev.gomoku.rapfidroid.navigation.YixinApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Injected directly: the theme must follow settings.txt line 27 (dark mode)
     *  before any screen — and therefore any ViewModel — exists. */
    @Inject
    lateinit var settings: SettingsRepository

    /** The hotkey table and the interpreter its scripts run in. */
    @Inject
    lateinit var appearance: AppearanceRepository

    @Inject
    lateinit var tools: EngineToolsRepository

    /** settings.txt line 36: records engine traffic to a file when switched on. */
    @Inject
    lateinit var debugLog: DebugLogWriter

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        maybeRequestNotifications()
        lifecycleScope.launch { appearance.restore() }
        debugLog.start()
        setContent {
            val current by settings.settings.collectAsStateWithLifecycle()
            // The system bars follow *this app's* dark mode (settings.txt line
            // 27), not the phone's. Without this, a user running the app dark on
            // a light phone got dark status-bar icons on a dark bar — invisible.
            LaunchedEffect(current.darkMode) {
                val style = SystemBarStyle.auto(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                ) { current.darkMode }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            RapfiDroidTheme(darkTheme = current.darkMode) {
                YixinApp()
            }
        }
    }

    /**
     * The desktop's six hotkeys (main.c:10059 `custom_function`). A hardware
     * keyboard is unusual on a phone and ordinary on a tablet or DeX, and the
     * scripts are already there, so the only new part is recognising the press.
     *
     * Handled here rather than in Compose because these keys must work whatever
     * has focus — the desktop's accelerators are window-wide too.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val ctrl = event?.isCtrlPressed == true
        val script = HotkeyMap.scriptFor(appearance.hotkeys.value, keyCode, ctrl)
        if (script != null) {
            lifecycleScope.launch { runCatching { tools.run(script) } }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
