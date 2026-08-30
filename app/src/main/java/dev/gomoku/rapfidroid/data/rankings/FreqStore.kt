package dev.gomoku.rapfidroid.data.rankings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gomoku.rapfidroid.core.common.IoDispatcher
import dev.gomoku.rapfidroid.domain.rankings.FreqBundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import javax.inject.Inject
import javax.inject.Singleton

private val Context.freqDataStore by preferencesDataStore(name = "freq")

/**
 * Holds the user-imported freq dataset. **RenjuNet-derived — never bundled.**
 * The user picks `freq_data.json` via the Storage Access Framework; we take a
 * persistable read grant so it reloads on the next launch. Nothing here is ever
 * exported or shared.
 */
@Singleton
class FreqStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val uriKey = stringPreferencesKey("freq_uri")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _bundle = MutableStateFlow<FreqBundle?>(null)
    val bundle: StateFlow<FreqBundle?> = _bundle.asStateFlow()

    /** Reload from the previously imported document, if the grant is still valid. */
    suspend fun restore() {
        if (_bundle.value != null) return
        val saved = context.freqDataStore.data.first()[uriKey] ?: return
        runCatching { parse(Uri.parse(saved)) }
            .onSuccess { _bundle.value = it }
    }

    /**
     * Import a freshly picked document; persists the grant + URI on success.
     *
     * [ownGrant] is false when the URI came out of a folder pick: a document
     * URI derived from a tree cannot be persisted on its own (the platform
     * throws), and it does not need to be — the caller persisted the tree, and
     * that grant covers every child of it.
     */
    suspend fun import(uri: Uri, ownGrant: Boolean = true): Result<FreqBundle> = runCatching {
        if (ownGrant) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val parsed = parse(uri)
        context.freqDataStore.edit { it[uriKey] = uri.toString() }
        _bundle.value = parsed
        parsed
    }

    /** Forget the imported dataset (in-memory + persisted URI). */
    suspend fun clear() {
        _bundle.value = null
        context.freqDataStore.edit { it.remove(uriKey) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun parse(uri: Uri): FreqBundle = withContext(io) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("cannot open $uri")
        stream.use { json.decodeFromStream<FreqDataDto>(it).toBundle() }
    }
}
