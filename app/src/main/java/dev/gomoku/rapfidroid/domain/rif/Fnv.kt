package dev.gomoku.rapfidroid.domain.rif

/**
 * FNV-1a, 64-bit — the hash the stats pack indexes positions by.
 *
 * The reader (`RjStatsPack`) carries its own copy so that reading a pack does
 * not depend on the code that builds one; this is the builder's. They must
 * agree, and [PackWriterTest] holds them to it by reading back what it wrote.
 */
internal object Fnv {

    private const val OFFSET = -0x340d631b7bdddcdbL   // 0xcbf29ce484222325
    private const val PRIME = 0x100000001b3L

    fun hash64(bytes: ByteArray): Long {
        var h = OFFSET
        for (b in bytes) {
            h = h xor (b.toLong() and 0xff)
            h *= PRIME
        }
        return h
    }

    fun hash64(s: String): Long = hash64(s.toByteArray(Charsets.US_ASCII))
}
