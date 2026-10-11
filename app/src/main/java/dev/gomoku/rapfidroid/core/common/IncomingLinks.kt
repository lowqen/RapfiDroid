package dev.gomoku.rapfidroid.core.common

import dev.gomoku.rapfidroid.core.joseki.LessonTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RapfiWeb links the app was opened with, waiting for the screen that takes
 * them. The activity receives the intent; the screen, whenever it next exists,
 * consumes it.
 *
 *  - a position (`…/board?p=h8i9j10`) — the board;
 *  - a curriculum lesson or course (`…/joseki?lesson=<id>`) — the shell moves
 *    to the joseki tab and the joseki screen opens it.
 */
@Singleton
class IncomingLinks @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    private val _lesson = MutableStateFlow<LessonTarget?>(null)
    val lesson: StateFlow<LessonTarget?> = _lesson.asStateFlow()

    fun offer(text: String) {
        _pending.value = text
    }

    fun consume(): String? = _pending.value.also { _pending.value = null }

    fun offerLesson(target: LessonTarget) {
        _lesson.value = target
    }

    fun consumeLesson(): LessonTarget? = _lesson.value.also { _lesson.value = null }
}
