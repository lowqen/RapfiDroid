package dev.gomoku.rapfidroid.core.i18n

import java.util.Locale

/**
 * Three languages, translated where the text is written — the desktop's own idiom.
 *
 * main.c wraps every user-visible string in `DT("English text")` and looks the
 * translation up at the point of use; the English stays in the source as the
 * fallback, which is why a missing `.lng` entry degrades to readable English
 * instead of to a key. [tr] is the same thing with every language spelled out:
 *
 * ```
 * Text(tr("분석 시작", "Analyze", "开始分析"))
 * ```
 *
 * Why not `strings.xml` for these. Three reasons, in order of weight:
 *
 *  1. This text is not only in composables. Settings labels live in a plain
 *     data table (`DesktopSettings`), grades and rules carry their own names,
 *     and view models produce the notices — none of those have a `Context`, so
 *     resources would need a provider injected through all of them.
 *  2. Translations that sit on one line cannot drift. Split across several XML
 *     files, the usual failure is a string that was never updated with the one
 *     next to it, and nothing points that out. Here the compiler does: every
 *     argument is required, so a string without its Chinese does not build.
 *  3. The desktop is the oracle for this port and it works this way. Terms that
 *     the programs share are taken from its `language` folder so the vocabulary
 *     matches the PC — `0.lng` for English, `1.lng` (简体中文) for Chinese.
 *
 * `strings.xml` still holds what the *platform* reads: the launcher label and
 * the notification channel, which Android asks for outside any of our code.
 *
 * Locale comes from [Locale.getDefault], which Android sets from the system
 * language and from the per-app language of Android 13+ (see
 * `res/xml/locales_config.xml`). Changing it restarts the activity, so there is
 * nothing to observe — the next composition reads the new value. Chinese is
 * Simplified; a Traditional-Chinese phone gets it too, which reads far better
 * there than English would.
 */
fun tr(ko: String, en: String, zh: String): String = when (appLanguage()) {
    AppLanguage.KOREAN -> ko
    AppLanguage.CHINESE -> zh
    AppLanguage.ENGLISH -> en
}

enum class AppLanguage { KOREAN, ENGLISH, CHINESE }

/** The language the app is showing: Korean, Chinese, or English for everything else. */
fun appLanguage(): AppLanguage = when (Locale.getDefault().language) {
    "ko" -> AppLanguage.KOREAN
    "zh" -> AppLanguage.CHINESE
    else -> AppLanguage.ENGLISH
}

/** True when the app is showing Korean. */
fun isKorean(): Boolean = appLanguage() == AppLanguage.KOREAN

/** True when the app is showing Chinese. */
fun isChinese(): Boolean = appLanguage() == AppLanguage.CHINESE
