package dev.gomoku.yixindroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Undo/redo semantics of the toolbar's four navigation buttons. */
class MoveCursorTest {

    private val h8 = Move(x = 7, y = 7)
    private val i9 = Move(x = 8, y = 6)
    private val g7 = Move(x = 6, y = 8)

    @Test
    fun replayingTheStoredMoveKeepsTheRedoTail() {
        // main.c:2182: `movepath[piecenum] == the move` -> the tail stays.
        assertThat(MoveCursor.tailAfter(listOf(i9, g7), i9)).containsExactly(g7)
    }

    @Test
    fun aDivergingMoveDropsTheTail() {
        assertThat(MoveCursor.tailAfter(listOf(i9, g7), h8)).isEmpty()
        assertThat(MoveCursor.tailAfter(emptyList(), h8)).isEmpty()
    }

    @Test
    fun splitMovesTheCursorWithoutLosingMoves() {
        val whole = listOf(h8, i9, g7)
        assertThat(MoveCursor.splitAt(whole, 0)).isEqualTo(emptyList<Move>() to whole)
        assertThat(MoveCursor.splitAt(whole, 2)).isEqualTo(listOf(h8, i9) to listOf(g7))
        assertThat(MoveCursor.splitAt(whole, 3)).isEqualTo(whole to emptyList<Move>())
    }

    /** "First" and "last" pass 0 and a huge number; both must clamp. */
    @Test
    fun theCursorIsClamped() {
        val whole = listOf(h8, i9)
        assertThat(MoveCursor.splitAt(whole, -5)).isEqualTo(emptyList<Move>() to whole)
        assertThat(MoveCursor.splitAt(whole, Int.MAX_VALUE)).isEqualTo(whole to emptyList<Move>())
    }
}
