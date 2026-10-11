package dev.gomoku.rapfidroid.data.settings

import com.google.common.truth.Truth.assertThat
import dev.gomoku.rapfidroid.core.model.AppSettings
import kotlinx.serialization.json.Json
import org.junit.Test

class DefaultsMigrationTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun stored(s: AppSettings) = json.encodeToString(AppSettings.serializer(), s)
    private fun read(text: String) = json.decodeFromString(AppSettings.serializer(), text)

    @Test
    fun `the old multi-PV default moves to one, the rest stays`() {
        val before = AppSettings(multiPv = 3, threadNum = 7, darkMode = false)
        val after = read(DefaultsMigration.moveOldMultiPv(stored(before)))
        assertThat(after).isEqualTo(before.copy(multiPv = 1))
    }

    @Test
    fun `a value the user chose is kept`() {
        for (n in listOf(1, 2, 5, 8)) {
            val text = stored(AppSettings(multiPv = n))
            assertThat(DefaultsMigration.moveOldMultiPv(text)).isEqualTo(text)
        }
    }

    @Test
    fun `a broken blob is left alone`() {
        assertThat(DefaultsMigration.moveOldMultiPv("{not json")).isEqualTo("{not json")
    }

    @Test
    fun `the new default is one`() {
        assertThat(AppSettings().multiPv).isEqualTo(1)
    }
}
