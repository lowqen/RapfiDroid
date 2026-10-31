package dev.gomoku.zenyth.core.model

/**
 * Where a server-engine address points, read from the text alone — no DNS
 * lookup, so it is safe to ask on every keystroke.
 *
 * The server link is plain TCP: piskvork has no encryption and none is added
 * (the engine's side is not changed for this). Privacy on that link is
 * therefore the network's: Tailscale encrypts it end to end (WireGuard), a home
 * network at least keeps it off the internet, and anything else sends every
 * position and command in the clear. The connection tab warns on [PUBLIC]
 * (docs/보안-강화-계획.md, Phase 5).
 */
enum class EndpointReach {
    /** 100.64.0.0/10, fd7a:115c:a1e0::/48 or a MagicDNS `*.ts.net` name. */
    TAILSCALE,

    /** Loopback, RFC 1918, link-local, IPv6 ULA, `.local`/`.lan`-style names, or a bare host name. */
    PRIVATE,

    /** Anything else: a public address, or a DNS name that may resolve to one. */
    PUBLIC;

    companion object {
        /** Null for a blank address, which points nowhere yet. */
        fun of(host: String): EndpointReach? {
            val h = host.trim().removePrefix("[").removeSuffix("]").lowercase()
            if (h.isEmpty()) return null
            ipv4(h)?.let { return ofIpv4(it) }
            if (':' in h) return ipv6(h)?.let(::ofIpv6) ?: PUBLIC
            return when {
                h == "localhost" || h.endsWith(".localhost") -> PRIVATE
                h.endsWith(".ts.net") -> TAILSCALE
                PRIVATE_SUFFIXES.any { h.endsWith(it) } -> PRIVATE
                // `rapfi-server`: a MagicDNS short name or a LAN host. A name the
                // public internet could resolve has a dot in it.
                '.' !in h -> PRIVATE
                else -> PUBLIC
            }
        }

        private val PRIVATE_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain")

        private fun ipv4(h: String): IntArray? {
            val parts = h.split('.')
            if (parts.size != 4) return null
            val out = IntArray(4)
            for ((i, p) in parts.withIndex()) {
                if (p.isEmpty() || p.length > 3 || !p.all(Char::isDigit)) return null
                out[i] = p.toInt().takeIf { it <= 255 } ?: return null
            }
            return out
        }

        private fun ofIpv4(o: IntArray): EndpointReach = when {
            o[0] == 100 && o[1] in 64..127 -> TAILSCALE
            o[0] == 10 || o[0] == 127 -> PRIVATE
            o[0] == 172 && o[1] in 16..31 -> PRIVATE
            o[0] == 192 && o[1] == 168 -> PRIVATE
            o[0] == 169 && o[1] == 254 -> PRIVATE
            else -> PUBLIC
        }

        /** The eight 16-bit groups of an IPv6 literal, or null when it is not one. Zone ids (`%wlan0`) are dropped. */
        private fun ipv6(text: String): IntArray? {
            val h = text.substringBefore('%')
            if (h.count { it == ':' } < 2 || h.split("::").size > 2) return null
            fun groups(s: String): List<Int>? {
                if (s.isEmpty()) return emptyList()
                val out = ArrayList<Int>()
                val parts = s.split(':')
                for ((i, p) in parts.withIndex()) {
                    // An embedded IPv4 tail (::ffff:1.2.3.4) is two groups.
                    if (i == parts.lastIndex && '.' in p) {
                        val v4 = ipv4(p) ?: return null
                        out += (v4[0] shl 8) or v4[1]
                        out += (v4[2] shl 8) or v4[3]
                    } else {
                        // Hex digits only: toIntOrNull(16) would also take a sign.
                        if (p.isEmpty() || p.length > 4 || !p.all { it.isDigit() || it in 'a'..'f' }) return null
                        out += p.toInt(16)
                    }
                }
                return out
            }
            val halves = h.split("::")
            val head = groups(halves[0]) ?: return null
            val tail = if (halves.size == 2) groups(halves[1]) ?: return null else emptyList()
            val missing = 8 - head.size - tail.size
            if (missing < 0 || (halves.size == 1 && missing != 0) || (halves.size == 2 && missing < 1)) return null
            return (head + List(missing) { 0 } + tail).toIntArray()
        }

        private fun ofIpv6(g: IntArray): EndpointReach = when {
            g[0] == 0xfd7a && g[1] == 0x115c && g[2] == 0xa1e0 -> TAILSCALE
            g.take(7).all { it == 0 } && g[7] == 1 -> PRIVATE        // ::1
            (g[0] and 0xffc0) == 0xfe80 -> PRIVATE                     // fe80::/10
            (g[0] and 0xfe00) == 0xfc00 -> PRIVATE                     // fc00::/7
            // ::ffff:a.b.c.d is that IPv4 address.
            g.take(5).all { it == 0 } && g[5] == 0xffff ->
                ofIpv4(intArrayOf(g[6] shr 8, g[6] and 0xff, g[7] shr 8, g[7] and 0xff))
            else -> PUBLIC
        }
    }
}
