package dev.gomoku.rapfidroid.data.rankings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.rapfidroid.core.common.IoDispatcher
import dev.gomoku.rapfidroid.domain.rankings.FreqBundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the rankings dataset the device built from the user's own `.rif`.
 *
 * **RenjuNet-derived — never bundled, never exported.** It used to be a
 * document the user picked and we kept a URI to; now it is a file this app
 * wrote, next to the explorer packs, from the same build. That removes the two
 * failure modes of the old arrangement — a revoked grant taking the data away
 * mid-session, and a dataset that had to be produced on a PC first.
 */
@Singleton
class FreqStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val file: File get() = File(File(context.filesDir, "packs"), FILE_NAME)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _bundle = MutableStateFlow<FreqBundle?>(null)
    val bundle: StateFlow<FreqBundle?> = _bundle.asStateFlow()

    /** Reload what a previous build wrote. */
    suspend fun restore() {
        if (_bundle.value != null) return
        withContext(io) { runCatching { read() }.getOrNull() }?.let { _bundle.value = it }
    }

    /** Take the dataset a build just produced and put it in service. */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun adopt(dto: FreqDataDto): Result<FreqBundle> = withContext(io) {
        runCatching {
            file.parentFile?.mkdirs()
            // Written beside and then moved: a build interrupted half way must
            // not leave a truncated JSON that parses as an empty dataset.
            val staged = File(file.parentFile, "$FILE_NAME.part")
            staged.outputStream().buffered().use { json.encodeToStream(dto, it) }
            file.delete()
            check(staged.renameTo(file)) { "랭킹 데이터를 저장하지 못했습니다" }
            dto.toBundle().also { _bundle.value = it }
        }
    }

    /** Forget the dataset and delete the file. */
    suspend fun clear() = withContext(io) {
        _bundle.value = null
        file.delete()
        Unit
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun read(): FreqBundle? {
        if (!file.isFile) return null
        return file.inputStream().buffered().use { json.decodeFromStream<FreqDataDto>(it) }.toBundle()
    }

    private companion object {
        const val FILE_NAME = "freq_data.json"
    }
}
