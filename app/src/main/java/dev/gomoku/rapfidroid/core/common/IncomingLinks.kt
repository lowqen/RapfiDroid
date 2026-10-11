package dev.gomoku.rapfidroid.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A RapfiWeb position link the app was opened with (`…/board?p=h8i9j10`),
 * waiting for the board to take it. The activity receives the intent; the
 * board screen, whenever it next exists, consumes it.
 */
@Singleton
class IncomingLinks @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(text: String) {
        _pending.value = text
    }

    fun consume(): String? = _pending.value.also { _pending.value = null }
}
