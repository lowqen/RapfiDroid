package dev.gomoku.rapfidroid.domain.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class EngineVariantSelectorTest {

    /** Galaxy A16 (MT6789: 6 × A55 + 2 × A76), as `adb shell cat /proc/cpuinfo` printed it — trimmed to two cores. */
    private val a16 = """
        processor	: 0
        BogoMIPS	: 26.00
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp
        CPU implementer	: 0x41
        CPU part	: 0xd05

        processor	: 7
        BogoMIPS	: 26.00
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp
        CPU implementer	: 0x41
        CPU part	: 0xd0b
    """.trimIndent()

    /** An ARMv8.0 Cortex-A53 phone: no LSE, no RDM, no dot product. */
    private val a53 = """
        processor	: 0
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 cpuid
        CPU part	: 0xd03
    """.trimIndent()

    @Test
    fun `a16 gets the dotprod build`() {
        assertThat(EngineVariantSelector.choose(a16, dotprodShipped = true, dotprodFailed = false))
            .isEqualTo(EngineVariant.DOTPROD)
    }

    @Test
    fun `armv8_0 stays on neon`() {
        assertThat(EngineVariantSelector.choose(a53, dotprodShipped = true, dotprodFailed = false))
            .isEqualTo(EngineVariant.NEON)
    }

    @Test
    fun `a recorded sigill wins over what cpuinfo claims`() {
        assertThat(EngineVariantSelector.choose(a16, dotprodShipped = true, dotprodFailed = true))
            .isEqualTo(EngineVariant.NEON)
    }

    @Test
    fun `no dotprod binary, unreadable or unknown cpuinfo all mean neon`() {
        assertThat(EngineVariantSelector.choose(a16, dotprodShipped = false, dotprodFailed = false))
            .isEqualTo(EngineVariant.NEON)
        assertThat(EngineVariantSelector.choose(null, dotprodShipped = true, dotprodFailed = false))
            .isEqualTo(EngineVariant.NEON)
        assertThat(EngineVariantSelector.choose("Hardware : something", dotprodShipped = true, dotprodFailed = false))
            .isEqualTo(EngineVariant.NEON)
    }

    @Test
    fun `asimddp alone is not enough - the 8_2 baseline brings atomics and rdm`() {
        val ddpOnly = "Features\t: fp asimd asimddp"
        assertThat(EngineVariantSelector.choose(ddpOnly, dotprodShipped = true, dotprodFailed = false))
            .isEqualTo(EngineVariant.NEON)
    }

    @Test
    fun `a feature only one cluster has does not count`() {
        val mixed = """
            processor	: 0
            Features	: fp asimd atomics asimdrdm
            processor	: 4
            Features	: fp asimd atomics asimdrdm asimddp
        """.trimIndent()
        assertThat(EngineVariantSelector.commonFeatures(mixed)).doesNotContain("asimddp")
        assertThat(EngineVariantSelector.choose(mixed, dotprodShipped = true, dotprodFailed = false))
            .isEqualTo(EngineVariant.NEON)
    }

    @Test
    fun `only a dotprod death by SIGILL counts as illegal instruction`() {
        assertThat(EngineVariantSelector.isIllegalInstruction(EngineVariant.DOTPROD, 132)).isTrue()
        assertThat(EngineVariantSelector.isIllegalInstruction(EngineVariant.DOTPROD, 4)).isTrue()
        // SIGKILL (low-memory killer, our own reaper) and a clean exit are not the CPU's fault.
        assertThat(EngineVariantSelector.isIllegalInstruction(EngineVariant.DOTPROD, 137)).isFalse()
        assertThat(EngineVariantSelector.isIllegalInstruction(EngineVariant.DOTPROD, 0)).isFalse()
        // NEON has nowhere to fall back to.
        assertThat(EngineVariantSelector.isIllegalInstruction(EngineVariant.NEON, 132)).isFalse()
    }

    /**
     * Every variant the selector can pick must actually be in the APK — a
     * missing dotprod binary would only degrade to NEON, but a missing NEON one
     * would leave no engine at all. Runs against the shipped jniLibs, like
     * `MobileEngineConfigTest` runs against the shipped config.
     */
    @Test
    fun `every variant ships`() {
        for (v in EngineVariant.entries) {
            val so = File("src/main/jniLibs/arm64-v8a/${v.binaryName}")
            assertThat(so.exists()).isTrue()
            assertThat(so.length()).isGreaterThan(1_000_000L)
        }
    }
}
