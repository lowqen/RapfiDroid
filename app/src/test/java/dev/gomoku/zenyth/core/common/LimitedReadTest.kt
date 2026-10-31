package dev.gomoku.zenyth.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class LimitedReadTest {

    @Test
    fun `reads a stream up to and including the limit`() {
        val bytes = ByteArray(40_000) { it.toByte() }
        assertThat(ByteArrayInputStream(bytes).readAtMost(40_000)).isEqualTo(bytes)
        assertThat(ByteArrayInputStream(ByteArray(0)).readAtMost(10)).isEmpty()
    }

    @Test
    fun `refuses one byte more`() {
        assertThat(ByteArrayInputStream(ByteArray(40_001)).readAtMost(40_000)).isNull()
    }

    /** An endless document (a pipe, a FUSE file): the read stops, it does not fill memory. */
    @Test
    fun `stops reading an endless stream`() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                served += len
                return len
            }
        }
        assertThat(endless.readAtMost(1024 * 1024)).isNull()
        assertThat(served).isLessThan(2L * 1024 * 1024)
    }
}
