package dev.gomoku.zenyth.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.gomoku.zenyth.core.model.EndpointReach.PRIVATE
import dev.gomoku.zenyth.core.model.EndpointReach.PUBLIC
import dev.gomoku.zenyth.core.model.EndpointReach.TAILSCALE
import org.junit.Test

class EndpointReachTest {

    private fun reach(host: String) = EndpointReach.of(host)

    @Test
    fun `tailscale addresses and names`() {
        for (h in listOf("100.64.0.1", "100.100.1.2", "100.127.255.255", " 100.100.100.100 ",
            "fd7a:115c:a1e0::1", "[fd7a:115c:a1e0:ab12::53]", "rapfi-server.tail1234.ts.net", "BOX.TS.NET")) {
            assertWithMessage(h).that(reach(h)).isEqualTo(TAILSCALE)
        }
        // Just outside 100.64.0.0/10.
        assertThat(reach("100.63.255.255")).isEqualTo(PUBLIC)
        assertThat(reach("100.128.0.1")).isEqualTo(PUBLIC)
    }

    @Test
    fun `private networks`() {
        for (h in listOf("10.0.0.5", "172.16.0.1", "172.31.255.1", "192.168.1.20", "127.0.0.1", "169.254.3.4",
            "localhost", "::1", "fe80::1%wlan0", "fd12:3456::7", "nas.local", "box.lan", "pc.home.arpa",
            "rapfi-server", "::ffff:192.168.0.2")) {
            assertWithMessage(h).that(reach(h)).isEqualTo(PRIVATE)
        }
    }

    @Test
    fun `everything else is public`() {
        for (h in listOf("8.8.8.8", "172.32.0.1", "192.169.0.1", "203.0.113.7", "2001:db8::1", "example.com",
            "my.server.example.org", "::ffff:8.8.8.8", "1.2.3", "1.2.3.256", "fd7a:115c:a1e0", "12345::1", "-1::1")) {
            assertWithMessage(h).that(reach(h)).isEqualTo(PUBLIC)
        }
    }

    @Test
    fun `a blank address points nowhere`() {
        assertThat(reach("")).isNull()
        assertThat(reach("   ")).isNull()
        assertThat(EngineEndpoint().isConfigured).isFalse()
        assertThat(EngineEndpoint(" ").isConfigured).isFalse()
        assertThat(EngineEndpoint("100.64.0.1").isConfigured).isTrue()
    }

    /** No private address ships in the source any more. */
    @Test
    fun `there is no default server`() {
        assertThat(EngineEndpoint.DEFAULT_HOST).isEmpty()
        assertThat(EngineEndpoint.DEFAULT_PORT).isEqualTo(5050)
    }
}
