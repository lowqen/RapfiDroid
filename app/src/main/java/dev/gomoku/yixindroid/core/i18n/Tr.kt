package dev.gomoku.yixindroid.core.i18n

import java.util.Locale

/**
 * Two languages, translated where the text is written — the desktop's own idiom.
 *
 * main.c wraps every user-visible string in `DT("English text")` and looks the
 * translation up at the point of use; the English stays in the source as the
 * fallback, which is why a missing `.lng` entry degrades to readable English
 * instead of to a key. [tr] is the same thing with the pair spelled out:
 *
 * ```
 * Text(tr("분석 시작", "Analyze"))
 * ```
 *
 * Why not `strings.xml` for these. Three reasons, in order of weight:
 *
 *  1. This text is not only in composables. Settings labels live in a plain
 *     data table (`DesktopSettings`), grades and rules carry their own names,
 *     and view models produce the notices — none of those have a `Context`, so
 *     resources would need a provider injected through all of them.
 *  2. A pair that sits on one line cannot drift. Split across two XML files,
 *     the usual failure is an English string that was never updated with the
 *     Korean one next to it, and nothing points that out.
 *  3. The desktop is the oracle for this port and it works this way. Terms that
 *     both programs share are taken from `language/0.lng` so the vocabulary
 *     matches the PC — "BESTLINE", "Prove Position", "Balance" and the rest.
 *
 * `strings.xml` still holds what the *platform* reads: the launcher label and
 * the notification channel, which Android asks for outside any of our code.
 *
 * Locale comes from [Locale.getDefault], which Android sets from the system
 * language and from the per-app language of Android 13+ (see
 * `res/xml/locales_config.xml`). Changing it restarts the activity, so there is
 * nothing to observe — the next composition reads the new value.
 */
fun tr(ko: String, en: String): String = if (isKorean()) ko else en

/** True when the app is showing Korean. Anything else gets English. */
fun isKorean(): Boolean = Locale.getDefault().language == "ko"
