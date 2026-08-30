package dev.gomoku.rapfidroid.domain.rif

import java.io.Reader

/**
 * A pull scanner for exactly the XML the RIF is: elements, double-quoted
 * attributes, text, comments and the processing instruction. No namespaces, no
 * CDATA, no DTD — the RIF header even names the six entities it uses.
 *
 * Deliberately not a general parser. It is here because the platform's
 * `XmlPullParser` cannot run in a JVM unit test and StAX cannot run on Android,
 * and a 43 MB file has to be read the same way in both places for the tests to
 * mean anything.
 */
internal class XmlLite(private val reader: Reader) {

    private var pushed = NONE
    private var lastWasSelfClosing = false

    /** The next start or end tag, or null at end of input. */
    fun nextTag(): Tag? {
        while (true) {
            var c = read()
            while (c != -1 && c != '<'.code) c = read()
            if (c == -1) return null
            c = read()
            when {
                c == -1 -> return null
                // <!-- comment -->, <!DOCTYPE ...>
                c == '!'.code -> skipBang()
                // <?xml ... ?>
                c == '?'.code -> skipTo('>')
                c == '/'.code -> {
                    val name = readName()
                    skipTo('>')
                    lastWasSelfClosing = false
                    return Tag(name, closing = true, selfClosing = false, attrs = emptyMap())
                }
                else -> {
                    unread(c)
                    return readStartTag()
                }
            }
        }
    }

    /**
     * The text of the element just returned by [nextTag], consuming its end tag.
     * Empty for a self-closing element, which never had a body to read.
     */
    fun textUntilClose(): String {
        if (lastWasSelfClosing) return ""
        val sb = StringBuilder()
        while (true) {
            val c = read()
            if (c == -1) return decode(sb.toString())
            if (c == '<'.code) break
            sb.append(c.toChar())
        }
        skipTo('>')   // the matching </name>
        return decode(sb.toString())
    }

    private fun readStartTag(): Tag {
        val name = readName()
        val attrs = HashMap<String, String>(8)
        var selfClosing = false
        while (true) {
            var c = read()
            while (c == ' '.code || c == '\t'.code || c == '\n'.code || c == '\r'.code) c = read()
            when (c) {
                -1, '>'.code -> break
                '/'.code -> {
                    selfClosing = true
                    skipTo('>')
                    break
                }
                else -> {
                    unread(c)
                    val key = readName()
                    if (key.isEmpty()) {
                        // Not an attribute name: step over whatever it is rather
                        // than spin on it forever.
                        if (read() == -1) break else continue
                    }
                    var d = read()
                    while (d == ' '.code) d = read()
                    if (d != '='.code) {
                        attrs[key] = ""
                        if (d == '>'.code || d == -1) break
                        continue
                    }
                    d = read()
                    while (d == ' '.code) d = read()
                    val quote = if (d == '"'.code || d == '\''.code) d else -1
                    val value = StringBuilder()
                    if (quote == -1) {
                        if (d != -1) value.append(d.toChar())
                        var e = read()
                        while (e != -1 && e != ' '.code && e != '>'.code) {
                            value.append(e.toChar())
                            e = read()
                        }
                        if (e == '>'.code) {
                            attrs[key] = decode(value.toString())
                            break
                        }
                    } else {
                        var e = read()
                        while (e != -1 && e != quote) {
                            value.append(e.toChar())
                            e = read()
                        }
                    }
                    attrs[key] = decode(value.toString())
                }
            }
        }
        lastWasSelfClosing = selfClosing
        return Tag(name, closing = false, selfClosing = selfClosing, attrs = attrs)
    }

    private fun readName(): String {
        val sb = StringBuilder(16)
        while (true) {
            val c = read()
            if (c == -1) break
            val ch = c.toChar()
            if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == ':' || ch == '.') {
                sb.append(ch)
            } else {
                unread(c)
                break
            }
        }
        return sb.toString()
    }

    /** `<!-- ... -->` needs the three-character terminator; anything else ends at `>`. */
    private fun skipBang() {
        val a = read()
        if (a != '-'.code) {
            if (a != -1) unread(a)
            skipTo('>')
            return
        }
        val b = read()
        if (b != '-'.code) {
            if (b != -1) unread(b)
            skipTo('>')
            return
        }
        var dashes = 0
        while (true) {
            val c = read()
            if (c == -1) return
            when {
                c == '-'.code -> dashes++
                c == '>'.code && dashes >= 2 -> return
                else -> dashes = 0
            }
        }
    }

    private fun skipTo(target: Char) {
        while (true) {
            val c = read()
            if (c == -1 || c == target.code) return
        }
    }

    private fun read(): Int {
        if (pushed != NONE) {
            val c = pushed
            pushed = NONE
            return c
        }
        return reader.read()
    }

    private fun unread(c: Int) {
        pushed = c
    }

    /** The six entities the RIF header names, plus numeric character references. */
    private fun decode(s: String): String {
        if ('&' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') {
                sb.append(c)
                i++
                continue
            }
            val end = s.indexOf(';', i + 1)
            if (end < 0 || end - i > 12) {
                sb.append(c)
                i++
                continue
            }
            when (val entity = s.substring(i + 1, end)) {
                "amp" -> sb.append('&')
                "lt" -> sb.append('<')
                "gt" -> sb.append('>')
                "apos" -> sb.append('\'')
                "quot" -> sb.append('"')
                "copy" -> sb.append('©')
                else -> {
                    val code = when {
                        entity.startsWith("#x") || entity.startsWith("#X") ->
                            entity.substring(2).toIntOrNull(16)
                        entity.startsWith("#") -> entity.substring(1).toIntOrNull()
                        else -> null
                    }
                    if (code != null && code in 1..0x10FFFF) {
                        sb.appendCodePoint(code)
                    } else {
                        sb.append(s, i, end + 1)
                    }
                }
            }
            i = end + 1
        }
        return sb.toString()
    }

    class Tag(
        val name: String,
        val closing: Boolean,
        val selfClosing: Boolean,
        private val attrs: Map<String, String>,
    ) {
        fun str(key: String): String = attrs[key] ?: ""

        fun int(key: String, default: Int = 0): Int =
            attrs[key]?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: default
    }

    private companion object {
        const val NONE = -2
    }
}
