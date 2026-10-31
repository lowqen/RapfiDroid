package dev.gomoku.zenyth

import dev.gomoku.zenyth.core.designsystem.theme.proMode
import dev.gomoku.zenyth.core.designsystem.theme.LocalProMode
import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.gomoku.zenyth.core.common.IncomingLinks
import dev.gomoku.zenyth.core.designsystem.component.LocalHapticsEnabled
import dev.gomoku.zenyth.core.designsystem.component.LocalUiPrefs
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.designsystem.theme.hapticsEnabled
import dev.gomoku.zenyth.core.designsystem.theme.themeAmoled
import dev.gomoku.zenyth.core.designsystem.theme.themeFollowsSystem
import dev.gomoku.zenyth.core.designsystem.theme.themeWallpaperTone
import dev.gomoku.zenyth.core.designsystem.theme.wallpaperHue
import dev.gomoku.zenyth.core.joseki.LessonLink
import dev.gomoku.zenyth.core.model.HotkeyMap
import dev.gomoku.zenyth.data.engine.DebugLogWriter
import dev.gomoku.zenyth.data.prefs.UiPrefs
import dev.gomoku.zenyth.domain.repository.AppearanceRepository
import dev.gomoku.zenyth.domain.repository.EngineToolsRepository
import dev.gomoku.zenyth.domain.repository.SettingsRepository
import dev.gomoku.zenyth.feature.settings.WhatsNewOnUpdate
import dev.gomoku.zenyth.navigation.ZenythRoot
import javax.inject.Inject
import kotlinx.coroutines.launch

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

    /** Remembered folds and board tab — read in the first frame, so not a flow. */
    @Inject
    lateinit var uiPrefs: UiPrefs

    /** A Zenyth Web position link this activity was opened with. */
    @Inject
    lateinit var links: IncomingLinks

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        maybeRequestNotifications()
        lifecycleScope.launch { appearance.restore() }
        debugLog.start()
        if (savedInstanceState == null) takeLink(intent)
        setContent {
            val current by settings.settings.collectAsStateWithLifecycle()
            val followSystem by uiPrefs.themeFollowsSystem.state.collectAsStateWithLifecycle()
            val amoled by uiPrefs.themeAmoled.state.collectAsStateWithLifecycle()
            val wallpaperTone by uiPrefs.themeWallpaperTone.state.collectAsStateWithLifecycle()
            // settings.txt line 27, unless the user chose «기기 설정».
            val dark = if (followSystem) isSystemInDarkTheme() else current.darkMode
            // Re-read when the wallpaper changes: Android recreates the activity.
            val context = LocalContext.current
            val chromeHue = remember(wallpaperTone, context) {
                if (wallpaperTone) wallpaperHue(context) else null
            }
            // The system bars follow *this app's* theme, not the phone's.
            // Without this, a user running the app dark on a light phone got
            // dark status-bar icons on a dark bar — invisible.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                ) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val haptics by uiPrefs.hapticsEnabled.state.collectAsStateWithLifecycle()
            val pro by uiPrefs.proMode.state.collectAsStateWithLifecycle()
            ZenythTheme(darkTheme = dark, amoled = amoled, chromeHue = chromeHue) {
                CompositionLocalProvider(
                    LocalUiPrefs provides uiPrefs,
                    LocalHapticsEnabled provides haptics,
                    LocalProMode provides pro,
                ) {
                    ZenythRoot()
                    // Once after an update: what changed, over the first screen.
                    WhatsNewOnUpdate()
                }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeLink(intent)
    }

    /**
     * Zenyth Web links: a curriculum lesson or course (`…/joseki?lesson=<id>`)
     * goes to the joseki tab, a position (`…/board?p=h8i9j10`) to the board.
     *
     * The activity is exported, so any app can send it any address, not only
     * the ones the intent filter names. A link that cannot be read is dropped;
     * it must never take the app down in onCreate.
     */
    private fun takeLink(intent: Intent?) {
        runCatching {
            if (intent?.action != Intent.ACTION_VIEW) return
            val url = intent.dataString ?: return
            val lesson = LessonLink.parse(url)
            when {
                lesson != null -> links.offerLesson(lesson)
                url.contains("p=") -> links.offer(url)
            }
        }
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
