package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PositionLinkTest {
    private val h8 = Move.fromLabel("H8")!!
    private val i9 = Move.fromLabel("I9")!!
    private val j10 = Move.fromLabel("J10")!!

    @Test
    fun `encodes lower case with no separator`() {
        assertThat(PositionLink.encode(listOf(h8, i9, j10), 15)).isEqualTo("h8i9j10")
        assertThat(PositionLink.url(listOf(h8), 15)).isEqualTo("https://rapfiweb.pages.dev/board?p=h8")
    }

    @Test
    fun `reads any case, spaces and commas`() {
        assertThat(PositionLink.parse("H8, i9 J10", 15)).containsExactly(h8, i9, j10).inOrder()
        assertThat(PositionLink.parse("", 15)).isEmpty()
    }

    @Test
    fun `refuses the whole link on a bad token`() {
        assertThat(PositionLink.parse("h8h8", 15)).isNull()
        assertThat(PositionLink.parse("h8z9", 15)).isNull()
        assertThat(PositionLink.parse("h8-i9", 15)).isNull()
    }

    @Test
    fun `finds the position in a url`() {
        assertThat(PositionLink.fromText("https://rapfiweb.pages.dev/research/explorer?x=1&p=h8i9#a", 15))
            .containsExactly(h8, i9).inOrder()
        assertThat(PositionLink.fromText("https://rapfiweb.pages.dev/board", 15)).isNull()
        assertThat(PositionLink.fromText("h8i9", 15)).containsExactly(h8, i9).inOrder()
    }
}
