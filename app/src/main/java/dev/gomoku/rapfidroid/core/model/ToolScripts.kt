package dev.gomoku.rapfidroid.core.model

import dev.gomoku.rapfidroid.core.i18n.tr

/**
 * The engine-maintenance scripts, copied verbatim from the desktop's own
 * toolbar definitions (`test-yixin/function/toolbar33-36.txt`).
 *
 * They are scripts rather than single commands because `bench`, `traceboard`,
 * `tracesearch` and `reloadconfig` are **engine** commands, not GUI ones: the
 * desktop reaches them by turning on raw passthrough, sending the line, and
 * turning it off again. Reproducing the wrapper keeps the app and the PC on the
 * same wire bytes — see the note in [ConsoleCommand].
 */
object ToolScripts {

    /** toolbar33 "Bench" — the engine's built-in benchmark. */
    const val BENCH = "command on\nbench\ncommand off"

    /** toolbar34 "Trace" — dump the engine's board and search state. */
    const val TRACE = "send board\ncommand on\ntraceboard\ntracesearch\ncommand off"

    /** toolbar35 "NNUE(default)" — the deployed `config.toml`. */
    const val CONFIG_NNUE = "config.toml"

    /** toolbar36 "Classic(checkmate)" — the deployed `config_classical.toml`. */
    const val CONFIG_CLASSIC = "config_classical.toml"

    /**
     * Reload the engine's configuration, which is how the desktop switches
     * evaluation mode. `dbrefresh` afterwards re-pushes the database flags,
     * because a reload resets them.
     *
     * The path is resolved **by the engine**, i.e. relative to the server's
     * working directory — not to anything on the phone.
     */
    fun reload(file: String): String =
        "command on\nreloadconfig $file\ncommand off\ndbrefresh"

    /** The evaluation modes the deployment actually ships a config for. */
    val evaluationModes: List<Pair<String, String>> = listOf(
        tr("NNUE (기본)", "NNUE (default)") to CONFIG_NNUE,
        tr("Classic (사활)", "Classic (life and death)") to CONFIG_CLASSIC,
    )
}
