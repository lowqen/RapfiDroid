package dev.gomoku.yixindroid.domain.engine

import dev.gomoku.yixindroid.core.model.LocalEngineProfile

/**
 * Turns the shipped `config.toml` — a **verbatim copy of the server's** — into
 * the one the on-device engine reads.
 *
 * Shipping the server's file and patching two lines (rather than writing a fresh
 * config) is deliberate: every search parameter, database rule and candidate
 * range stays identical, so a position analysed on the phone is analysed the way
 * the PC would. Only the two numbers that would kill a phone are rewritten.
 *
 * Why they would kill it: `default_tt_size_kb` is applied **when the config is
 * committed**, i.e. before the app has said a word — the shipped value is
 * 8388608 KiB (8 GiB). And `default_thread_num = 0` means "every core", which on
 * a phone means the little cores too.
 *
 * Pure on purpose: this is the one piece of the local-engine path that can be
 * tested without a device, and it is the piece that has to be right.
 */
object MobileEngineConfig {

    /** `default_tt_size_kb` — the transposition table, sized at config load. */
    private const val TT_KEY = "default_tt_size_kb"

    /** `default_thread_num` — 0 would mean every core including the little ones. */
    private const val THREADS_KEY = "default_thread_num"

    fun render(template: String, profile: LocalEngineProfile): String {
        var out = replaceScalar(template, TT_KEY, profile.ttSizeKb.toString())
        out = replaceScalar(out, THREADS_KEY, profile.threads.toString())
        requireCoordModeNone(out)
        return out
    }

    /**
     * Replace `key = <anything>` on its own line, keeping the rest of the file —
     * comments included — byte for byte.
     *
     * Fails loudly when the key is absent. A silently unpatched template is the
     * bad case: the engine would start, allocate 8 GiB and be killed, and the
     * only symptom the user sees is "the local engine won't connect".
     */
    private fun replaceScalar(text: String, key: String, value: String): String {
        val line = Regex("^[ \\t]*$key[ \\t]*=.*$", RegexOption.MULTILINE)
        require(line.containsMatchIn(text)) {
            "config.toml 템플릿에 `$key` 항목이 없습니다 — 서버 config 를 새로 복사했다면 이 코드를 함께 고칠 것"
        }
        return line.replace(text) { "$key = $value" }
    }

    /**
     * The app reads engine coordinates as `y,x` with no flip, which is only true
     * while the engine converts nothing. If a future copy of the server config
     * ever carries `flipY_X`, every move and every PV would land transposed on
     * the board — so refuse to write that config at all.
     */
    private fun requireCoordModeNone(text: String) {
        val ok = Regex("^[ \\t]*coord_conversion_mode[ \\t]*=[ \\t]*\"none\"", RegexOption.MULTILINE)
        require(ok.containsMatchIn(text)) {
            "config.toml 의 coord_conversion_mode 가 \"none\" 이 아닙니다 — 앱의 y,x 좌표 해석이 깨집니다"
        }
    }
}
