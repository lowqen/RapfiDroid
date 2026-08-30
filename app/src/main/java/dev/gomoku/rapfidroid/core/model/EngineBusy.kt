package dev.gomoku.rapfidroid.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "A long engine job owns the engine right now."
 *
 * [ConnectionState.Thinking] already says that for an analysis or a balance
 * search, because those go through `EngineRepository.analyze`. A review and a
 * proof do not: they send `YXNBEST` / `YXSEARCHDEFEND` themselves, so the
 * connection stayed `Ready` for hours at a time and the database's auto-save
 * timer read that as "the engine is idle" — then wrote the whole file out from
 * under a search that was still mutating it.
 *
 * They could mark the connection instead, but a proof settles a node between
 * searches, and the connection returns to `Ready` at each of those. This is
 * held for the **whole run**, which is what the question "may I rewrite the
 * database file now?" actually needs.
 *
 * A holder rather than a repository dependency: the database repository is what
 * a proof asks to save, so it cannot ask the proof anything back.
 */
@Singleton
class EngineBusy @Inject constructor() {

    private val _reasons = MutableStateFlow<Set<String>>(emptySet())

    /** The jobs currently holding the engine; empty when nothing is. */
    val reasons: StateFlow<Set<String>> = _reasons.asStateFlow()

    val isBusy: Boolean get() = _reasons.value.isNotEmpty()

    fun acquire(reason: String) {
        _reasons.value = _reasons.value + reason
    }

    fun release(reason: String) {
        _reasons.value = _reasons.value - reason
    }

    companion object {
        const val PROVE = "prove"
        const val REVIEW = "review"
    }
}
