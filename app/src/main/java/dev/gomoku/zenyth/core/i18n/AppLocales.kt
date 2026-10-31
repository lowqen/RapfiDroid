package dev.gomoku.zenyth.core.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * The app's own language, apart from the phone's (Android 13+). Settings ▸ 언어
 * and the first-run setup both pick it here, so the two cannot disagree on
 * what «기기 설정» means or which Chinese is asked for.
 *
 * Setting it recreates the activity; [tr] then reads the new value.
 */
object AppLocales {
    /** One row of the picker: [tag] is "" for the phone's language. */
    data class Choice(val tag: String, val label: String)

    /** The rows, in the order both pickers show them. */
    fun choices(): List<Choice> = listOf(
        Choice("", tr("기기 설정", "System", "跟随系统")),
        Choice("ko", "한국어"),
        Choice("en", "English"),
        Choice("zh", "中文"),
    )

    /** Null below Android 13, where the language follows the phone's settings only. */
    private fun manager(context: Context): LocaleManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
        } else {
            null
        }

    /** Whether this phone lets the app pick its own language. */
    fun supported(context: Context): Boolean = manager(context) != null

    /** The chosen [Choice.tag]: "" when the app follows the phone. */
    fun current(context: Context): String {
        val manager = manager(context) ?: return ""
        return manager.applicationLocales.toLanguageTags().substringBefore('-').substringBefore(',')
    }

    fun set(context: Context, tag: String) {
        val manager = manager(context) ?: return
        // Chinese is Simplified, so ask for the mainland locale.
        manager.applicationLocales = LocaleList.forLanguageTags(if (tag == "zh") "zh-CN" else tag)
    }
}
