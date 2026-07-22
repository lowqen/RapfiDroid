package dev.gomoku.yixindroid.data.engine

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.core.model.EngineEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Integration test against a throwaway local TCP server that stands in for the
 * Rapfi endpoint: confirms open() connects, writes reach the server, and server
 * lines (including startup noise before the handshake) surface on `incoming`.
 */
class EngineConnectionTest {

    @Test
    fun connects_writes_and_reads_lines() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort

        val serverThread = thread(name = "fake-rapfi") {
            server.accept().use { client ->
                val reader = client.getInputStream().bufferedReader()
                val writer = client.getOutputStream().bufferedWriter()
                // startup noise before the client's START, like config/DB load
                writer.write("MESSAGE loading config\n"); writer.flush()
                val startLine = reader.readLine()      // expect "START 15"
                writer.write("OK\n"); writer.flush()
                writer.write("$startLine|echo\n"); writer.flush()
                Thread.sleep(200)
            }
        }

        val conn = EngineConnection(
            writeDispatcher = Dispatchers.IO,
            ioDispatcher = Dispatchers.IO,
        )
        try {
            conn.incoming.test {
                conn.open(EngineEndpoint("127.0.0.1", port))
                assertThat(conn.state.value).isEqualTo(ConnectionState.Handshaking)

                conn.writeLine("START 15")

                withTimeout(3_000) {
                    assertThat(awaitItem()).isEqualTo("MESSAGE loading config")
                    assertThat(awaitItem()).isEqualTo("OK")
                    assertThat(awaitItem()).isEqualTo("START 15|echo")
                }
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            conn.close()
            server.close()
            serverThread.join(1_000)
        }
    }
}
