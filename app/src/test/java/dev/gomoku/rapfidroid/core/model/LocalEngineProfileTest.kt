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

    @Test
    fun `the fastest setting can always be picked`() {
        // 8 threads on an 8-core phone measured x1.58 the peak nodes/s of 4
        // (Galaxy A16): the settings screen must offer it, and the profile must
        // carry it through to the engine instead of clamping it away.
        val choices = LocalEngineProfile.THREAD_CHOICES
        assertThat(LocalEngineProfile.MAX_THREADS).isAtLeast(8)
        // Separate asserts: containsAtLeast counts duplicates, and MAX_THREADS is 8.
        assertThat(choices).containsAtLeast(6, 8)
        assertThat(choices).contains(LocalEngineProfile.MAX_THREADS)
        assertThat(LocalEngineProfile(threadNum = 8).clamp(desktop).threadNum).isEqualTo(8)
    }

    @Test
    fun `the default is one of the offered choices`() {
        // Otherwise a fresh install shows no chip selected.
        assertThat(LocalEngineProfile.THREAD_CHOICES).contains(LocalEngineProfile.DEFAULT_THREADS)
        assertThat(LocalEngineProfile.THREAD_CHOICES).isInStrictOrder()
    }

    // --- R3: the defaults are the device's ---

    @Test
    fun `a phone gets every core by default, up to the cap`() {
        // The Galaxy A16: 2 big + 6 little, 5.5 GB reported. 8 threads was the
        // fastest setting measured there.
        val a16 = LocalEngineProfile.forDevice(DeviceSpec(cores = 8, totalRamMb = 5_500))
        assertThat(a16.threads).isEqualTo(8)
        assertThat(a16.hashMb).isEqualTo(256)

        assertThat(LocalEngineProfile.forDevice(DeviceSpec(4, 3_600)).threads).isEqualTo(4)
        assertThat(LocalEngineProfile.forDevice(DeviceSpec(10, 11_000)).threads)
            .isEqualTo(LocalEngineProfile.MAX_THREADS)
    }

    @Test
    fun `the table grows with the RAM tier and never past the limit`() {
        assertThat(LocalEngineProfile.hashForRam(1_800)).isEqualTo(64)
        assertThat(LocalEngineProfile.hashForRam(3_600)).isEqualTo(128)
        assertThat(LocalEngineProfile.hashForRam(5_500)).isEqualTo(256)
        assertThat(LocalEngineProfile.hashForRam(7_400)).isEqualTo(LocalEngineProfile.MAX_HASH_MB)
        assertThat(LocalEngineProfile.hashForRam(15_000)).isEqualTo(LocalEngineProfile.MAX_HASH_MB)
    }

    @Test
    fun `an unreadable device falls back to the constants`() {
        val fallback = LocalEngineProfile.forDevice(null)
        assertThat(fallback.threads).isEqualTo(LocalEngineProfile.DEFAULT_THREADS)
        assertThat(fallback.hashMb).isEqualTo(LocalEngineProfile.DEFAULT_HASH_MB)
        assertThat(LocalEngineProfile.hashForRam(0)).isEqualTo(LocalEngineProfile.DEFAULT_HASH_MB)
    }

    @Test
    fun `cpu lists are counted the way the kernel writes them`() {
        assertThat(DeviceSpec.countCpuList("0-7\n")).isEqualTo(8)
        assertThat(DeviceSpec.countCpuList("0-3,6-7")).isEqualTo(6)
        assertThat(DeviceSpec.countCpuList("0")).isEqualTo(1)
        assertThat(DeviceSpec.countCpuList("")).isNull()
        assertThat(DeviceSpec.countCpuList("garbage")).isNull()
        assertThat(DeviceSpec.countCpuList("7-0")).isNull()
    }

    @Test
    fun `a five or seven core phone still sees its own default offered`() {
        assertThat(LocalEngineProfile.threadChoices(5)).contains(5)
        assertThat(LocalEngineProfile.threadChoices(7)).isInStrictOrder()
        assertThat(LocalEngineProfile.threadChoices(8)).isEqualTo(LocalEngineProfile.THREAD_CHOICES)
    }

    private val a16Defaults = LocalEngineProfile.forDevice(DeviceSpec(8, 5_500))

    @Test
    fun `nothing stored means the device defaults`() {
        val p = LocalEngineProfile.fromStored(null, null, null, legacy = false, defaults = a16Defaults)
        assertThat(p).isEqualTo(a16Defaults)
    }

    @Test
    fun `the old fixed defaults stored before R3 do not pin a phone to them`() {
        // Before R3 every save wrote all three numbers, so 3 / 128 rode along
        // with whatever the user actually changed.
        val p = LocalEngineProfile.fromStored(3, 128, false, legacy = true, defaults = a16Defaults)
        assertThat(p.threads).isEqualTo(8)
        assertThat(p.hashMb).isEqualTo(256)
        assertThat(p.useDatabase).isFalse()
    }

    @Test
    fun `real choices survive, before and after R3`() {
        val old = LocalEngineProfile.fromStored(8, 64, true, legacy = true, defaults = a16Defaults)
        assertThat(old.threads).isEqualTo(8)
        assertThat(old.hashMb).isEqualTo(64)

        // Once stamped, a stored 3 is a choice like any other.
        val now = LocalEngineProfile.fromStored(3, 128, true, legacy = false, defaults = a16Defaults)
        assertThat(now.threads).isEqualTo(3)
        assertThat(now.hashMb).isEqualTo(128)
    }
}
