package dev.gomoku.zenyth.core.common

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Upper bounds for documents the user picks. A file manager hands over anything,
 * and `readBytes()` on a few hundred megabytes is an OutOfMemoryError that takes
 * the app down, not an error message. The `.rif` import streams and is not read
 * whole, so it has no bound here.
 */
object ReadLimits {
    /** Game files (.psq/.sav/.pos) are a few KB; a joseki export stays far below this. */
    const val DOCUMENT = 16 * 1024 * 1024

    /** settings.txt, toolbar and hotkey scripts, .lng tables: the desktop's are a few KB. */
    const val TEXT = 1024 * 1024
}

/**
 * The whole stream, or null when it is longer than [limit] bytes. Stops reading
 * one buffer past the limit, so a huge file costs at most that much memory.
 */
fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (true) {
        val n = read(buf)
        if (n < 0) return out.toByteArray()
        out.write(buf, 0, n)
        if (out.size() > limit) return null
    }
}
