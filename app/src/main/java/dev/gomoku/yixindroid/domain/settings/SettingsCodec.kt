package dev.gomoku.yixindroid.domain.settings

import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.DesktopSettings
import dev.gomoku.yixindroid.core.model.SettingsFile

/**
 * Reads and writes the desktop's own `settings.txt` / `settings_dev.txt`, so the
 * PC and the phone can hand settings to each other verbatim.
 *
 * The desktop parses both files **by position**: `load_setting` calls
 * `read_int_from_file` once per line in a fixed order, so a missing or reordered
 * line silently shifts every setting after it. Therefore:
 *  - [render] always writes all lines of a file, in [DesktopSettings] order,
 *    including reserved slots and one-time markers;
 *  - [parse] matches by line index, not by name, and simply keeps the current
 *    value for any line the input does not have (the desktop's `feof` fallback).
 *
 * Pure and Android-free: unit tests run it against the deployed files.
 */
object SettingsCodec {

    /** The full text of one desktop settings file, newline-terminated. */
    fun render(settings: AppSettings, file: SettingsFile): String =
        DesktopSettings.of(file).joinToString(separator = "\n", postfix = "\n") { it.render(settings) }

    /**
     * Apply one desktop settings file onto [base]. Unknown/short input keeps the
     * corresponding [base] values; out-of-range values are clamped by each spec
     * exactly as `load_setting` clamps them.
     */
    fun parse(text: String, file: SettingsFile, base: AppSettings = AppSettings()): AppSettings {
        val lines = text.lineSequence().toList()
        var out = base
        DesktopSettings.of(file).forEachIndexed { index, spec ->
            val raw = lines.getOrNull(index) ?: return@forEachIndexed
            if (raw.isBlank()) return@forEachIndexed
            out = spec.write(out, valueOf(raw))
        }
        return out
    }

    /** Both files at once, in the order the desktop writes them. */
    fun parseAll(main: String?, dev: String?, base: AppSettings = AppSettings()): AppSettings {
        var out = base
        if (main != null) out = parse(main, SettingsFile.MAIN, out)
        if (dev != null) out = parse(dev, SettingsFile.DEV, out)
        return out
    }

    /**
     * The value part of a line: everything before the first `;`, minus the tab
     * the desktop pads integers with. Mirrors `read_int_from_file` /
     * `read_str_from_file`, which both cut at the first semicolon.
     */
    private fun valueOf(line: String): String =
        line.substringBefore(';').trim { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
}
