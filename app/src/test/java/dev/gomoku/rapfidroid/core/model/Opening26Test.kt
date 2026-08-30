package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Opening26Test {

    private fun moves(vararg labels: String) = labels.map { Move.fromLabel(it)!! }

    @Test
    fun namesAndCountsAreComplete() {
        assertThat(Opening26.abbr).hasSize(26)
        assertThat(Opening26.korean).hasSize(26)
        assertThat(Opening26.romaji).hasSize(26)
        assertThat(Opening26.abbr.first()).isEqualTo("D1")
        assertThat(Opening26.abbr[13]).isEqualTo("I1")
        assertThat(Opening26.romaji.first()).isEqualTo("Kansei")
    }

    @Test
    fun d1IsHhh() {
        // Kansei / D1 = h8 h9 h10 (straight line up the H file).
        assertThat(Opening26.classify(moves("h8", "h9", "h10"))).isEqualTo(0)
        assertThat(Opening26.representative(0)).isEqualTo(moves("h8", "h9", "h10"))
        assertThat(Opening26.isDirect(0)).isTrue()
    }

    @Test
    fun indirectSecondStoneIsI9() {
        // I1 (index 13) is an indirect opening -> 2nd stone on I9.
        assertThat(Opening26.isDirect(13)).isFalse()
        assertThat(Opening26.representative(13)[1]).isEqualTo(Move.fromLabel("i9"))
    }

    @Test
    fun everyRepresentativeRoundTrips() {
        // classify(representative(i)) == i for all 26 openings (canonical form).
        for (i in 0 until Opening26.COUNT) {
            assertThat(Opening26.classify(Opening26.representative(i))).isEqualTo(i)
        }
    }

    @Test
    fun rotatedInputMapsToSameOpening() {
        // A D4-rotated D1 must still classify as D1: rotate h8/h9/h10 90°.
        // Centre stays h8; the straight line just points a different way.
        assertThat(Opening26.classify(moves("h8", "i8", "j8"))).isEqualTo(0)
    }

    @Test
    fun nonCentreFirstMoveIsNonStandard() {
        assertThat(Opening26.classify(moves("a1", "b2", "c3"))).isEqualTo(Opening26.NONSTD)
    }
}
