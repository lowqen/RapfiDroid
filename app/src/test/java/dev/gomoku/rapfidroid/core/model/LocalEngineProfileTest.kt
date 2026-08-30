package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Server-sized numbers, sent unchanged to an engine running on the phone, are
 * not a preference problem — `INFO hash_size` becomes the searcher's memory
 * limit directly, and the table then halves until an allocation "succeeds",
 * after which the app is killed while filling it.
 *
 * 1024 MB is the app's own default and still far too much here; importing the
 * deployed `settings.txt` brings back 8192.
 */
class LocalEngineProfileTest {

    /** The app's out-of-box engine parameters: 4 threads, 1024 MB, database attached. */
    private val desktop = EngineParams()

    @Test
    fun `the defaults are still more than a phone can hold`() {
        assertThat(desktop.threadNum).isEqualTo(4)
        assertThat(desktop.hashSizeMb).isEqualTo(1024)
        assertThat(desktop.useDatabase).isTrue()
    }

    @Test
    fun `clamping brings the two dangerous numbers down`() {
        val local = LocalEngineProfile().clamp(desktop)

        assertThat(local.threadNum).isEqualTo(LocalEngineProfile.DEFAULT_THREADS)
        assertThat(local.hashSizeMb).isEqualTo(LocalEngineProfile.DEFAULT_HASH_MB)
        // The device's own database is on: it starts empty and holds only what
        // this phone analysed. It is the server's that will not fit.
        assertThat(local.useDatabase).isTrue()
    }

    @Test
    fun `the database switch is the profile's, not the desktop's`() {
        val off = LocalEngineProfile(useDatabase = false).clamp(desktop)
        assertThat(off.useDatabase).isFalse()

        val on = LocalEngineProfile(useDatabase = true).clamp(desktop.copy(useDatabase = false))
        assertThat(on.useDatabase).isTrue()
    }

    @Test
    fun `everything that makes the two ends agree is left alone`() {
        val settings = desktop.copy(
            rule = 2,
            level = 1,
            cautionFactor = 5,
            multiPv = 3,
            vcThread = 1,
            nbestSym = true,
        )
        val local = LocalEngineProfile().clamp(settings)

        assertThat(local.rule).isEqualTo(settings.rule)
        assertThat(local.level).isEqualTo(settings.level)
        assertThat(local.cautionFactor).isEqualTo(settings.cautionFactor)
        assertThat(local.multiPv).isEqualTo(settings.multiPv)
        assertThat(local.vcThread).isEqualTo(settings.vcThread)
        assertThat(local.nbestSym).isEqualTo(settings.nbestSym)
        assertThat(local.boardSize).isEqualTo(settings.boardSize)
    }

    @Test
    fun `the clamped hash is what actually goes on the wire`() {
        val pairs = LocalEngineProfile().clamp(desktop).infoPairs().toMap()

        // main.c sends megabytes shifted into kilobytes; 128 MB = 131072 KB.
        assertThat(pairs["hash_size"]).isEqualTo("131072")
        assertThat(pairs["thread_num"]).isEqualTo("3")
        // `INFO usedatabase 1` is what makes the engine create rapfi.db in its
        // working directory, which on device is the app's own storage.
        assertThat(pairs["usedatabase"]).isEqualTo("1")
    }

    @Test
    fun `profile values out of range are clamped in both directions`() {
        val tooBig = LocalEngineProfile(threadNum = 999, hashSizeMb = 99_999)
        assertThat(tooBig.threads).isEqualTo(LocalEngineProfile.MAX_THREADS)
        assertThat(tooBig.hashMb).isEqualTo(LocalEngineProfile.MAX_HASH_MB)

        val tooSmall = LocalEngineProfile(threadNum = 0, hashSizeMb = 0)
        assertThat(tooSmall.threads).isEqualTo(1)
        assertThat(tooSmall.hashMb).isEqualTo(LocalEngineProfile.MIN_HASH_MB)
    }

    @Test
    fun `tt size is the hash in KiB`() {
        assertThat(LocalEngineProfile(hashSizeMb = 128).ttSizeKb).isEqualTo(131_072L)
        assertThat(LocalEngineProfile(hashSizeMb = 64).ttSizeKb).isEqualTo(65_536L)
    }
}
