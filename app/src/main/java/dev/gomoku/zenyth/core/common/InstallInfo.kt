package dev.gomoku.zenyth.core.common

import android.content.Context

/**
 * Whether this is the version the phone first installed, with no update since.
 * Android stamps both times on install and moves only the second on an update.
 *
 * Two readers ask it once each: «새로워진 점» (a fresh install has nothing to
 * compare with) and the first-run setup (an update keeps the user's setup).
 * A failed lookup answers false — the quiet side for both.
 */
fun isFreshInstall(context: Context): Boolean = runCatching {
    @Suppress("DEPRECATION")
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.firstInstallTime == info.lastUpdateTime
}.getOrDefault(false)
