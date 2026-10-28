package dev.gomoku.rapfidroid.core.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicLightColorScheme
import dev.gomoku.rapfidroid.data.prefs.UiPrefs

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
 * Off by default, so nobody's theme changes under them with this release. It is
 * switched off again when a desktop settings file is imported, because the
 * point of importing one is that the app looks like the PC (decision D4).
 */
val UiPrefs.themeFollowsSystem: UiPrefs.BoolPref get() = bool("theme.followSystem", false)

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
