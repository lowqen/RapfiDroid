package dev.gomoku.zenyth.core.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicLightColorScheme
import dev.gomoku.zenyth.data.prefs.UiPrefs

/**
 * The theme choices that have no line in `settings.txt`.
 *
 * Light or dark *is* a desktop setting (line 27) and stays one: picking «라이트»
 * or «다크» writes it, so an exported settings file still carries the choice.
 * What the desktop cannot express — follow the phone, black for OLED, the
 * wallpaper's tone — is the app's own and lives with the other UI choices.
 */

/**
 * «기기 설정»: light or dark follows the phone instead of settings.txt line 27.
 *
 * Its default is settled once by [settleThemeDefault]; until then it reads off.
 * It is switched off again when a desktop settings file is imported, because
 * the point of importing one is that the app looks like the PC (decision D4).
 */
val UiPrefs.themeFollowsSystem: UiPrefs.BoolPref get() = bool(THEME_FOLLOW_KEY, false)

private const val THEME_FOLLOW_KEY = "theme.followSystem"

/**
 * Settles «기기 설정»'s default on the first launch that reads it. A fresh
 * install follows the phone (user request, 2026-10-31: it opened dark, the
 * desktop's own default in line 27). An update keeps what it had — off, the
 * old default — so nobody's theme changes under them. Written either way, so
 * it is decided once: the install times [freshInstall] reads move with the
 * next update. Call before the first frame.
 */
fun UiPrefs.settleThemeDefault(freshInstall: () -> Boolean) {
    if (!contains(THEME_FOLLOW_KEY)) themeFollowsSystem.set(freshInstall())
}

/** Black page and containers in the dark theme, for OLED screens. */
val UiPrefs.themeAmoled: UiPrefs.BoolPref get() = bool("theme.amoled", false)

/** The wallpaper's hue on the chrome ramps (Android 12+). Off by default (D1). */
val UiPrefs.themeWallpaperTone: UiPrefs.BoolPref get() = bool("theme.wallpaperTone", false)

/**
 * «진동» — the board's and the toolbar's haptics (P3) on or off. On by default:
 * they only answer the user's own hand, at a threshold. Read by the activity
 * into `LocalHapticsEnabled`.
 */
val UiPrefs.hapticsEnabled: UiPrefs.BoolPref get() = bool("haptics.enabled", true)

/**
 * «고급 사용자 모드» — off by default (user request, 2026-10-29). Off, the app
 * shows what playing and studying need; on, it adds the expert surfaces: the DB
 * tab, the engine console and its command line, the engine screen's Tools, the
 * board's Tools card and the PC settings list. Nothing is removed — only folded
 * away until asked for. Read by the activity into [LocalProMode].
 */
val UiPrefs.proMode: UiPrefs.BoolPref get() = bool("pro.enabled", false)

/** See [proMode]. True outside the activity (previews, tests): show everything. */
val LocalProMode = androidx.compose.runtime.staticCompositionLocalOf { true }

/** Whether this phone has wallpaper colours to offer at all. */
val wallpaperToneAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The hue to lay on the chrome, or null for the app's own paper.
 *
 * Read off the primary of the phone's wallpaper scheme. A grey wallpaper has a
 * primary with almost no chroma, whose hue is noise; that gives null, not a
 * random tint.
 */
fun wallpaperHue(context: Context): Float? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val (_, chroma, hue) = oklchOf(dynamicLightColorScheme(context).primary)
    return if (chroma < 0.02f) null else hue
}
