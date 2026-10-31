package dev.gomoku.zenyth.feature.connection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConnectionUiStateTest {

    private val server = ConnectionUiState(serverEnabled = true, localMode = false)

    @Test
    fun `the on-device engine starts without an address`() {
        assertThat(ConnectionUiState().canStart).isTrue()
        assertThat(ConnectionUiState(serverEnabled = true, localMode = true).canStart).isTrue()
        // Server switched off in settings: the address field is not even shown.
        assertThat(ConnectionUiState(serverEnabled = false, localMode = false).canStart).isTrue()
    }

    @Test
    fun `the server engine needs an address`() {
        assertThat(server.canStart).isFalse()
        assertThat(server.copy(host = "  ").canStart).isFalse()
        assertThat(server.copy(host = "100.64.0.9").canStart).isTrue()
    }

    @Test
    fun `only a public address on the chosen server warns`() {
        assertThat(server.copy(host = "100.64.0.9").plaintextWarning).isFalse()
        assertThat(server.copy(host = "192.168.0.4").plaintextWarning).isFalse()
        assertThat(server.copy(host = "").plaintextWarning).isFalse()
        assertThat(server.copy(host = "example.com").plaintextWarning).isTrue()
        assertThat(server.copy(host = "8.8.8.8").plaintextWarning).isTrue()
        // On-device chosen: the address is not used, so it does not warn.
        assertThat(server.copy(host = "8.8.8.8", localMode = true).plaintextWarning).isFalse()
    }
}
