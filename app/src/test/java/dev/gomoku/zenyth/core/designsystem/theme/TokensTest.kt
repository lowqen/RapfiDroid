package dev.gomoku.zenyth.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The scale [ZenythTokens] took over, pinned. Moving the numbers into one object
 * was meant to move nothing on screen; if one of these changes it is a visual
 * change and belongs in the screenshot goldens, not in a refactor.
 */
class TokensTest {

    @Test
    fun `material shapes are the five corner steps`() {
        assertThat(Shapes.extraSmall).isEqualTo(RoundedCornerShape(4.dp))
        assertThat(Shapes.small).isEqualTo(RoundedCornerShape(8.dp))
        assertThat(Shapes.medium).isEqualTo(RoundedCornerShape(12.dp))
        assertThat(Shapes.large).isEqualTo(RoundedCornerShape(20.dp))
        assertThat(Shapes.extraLarge).isEqualTo(RoundedCornerShape(28.dp))
    }

    @Test
    fun `spacing keeps its 4dp grid with the half step`() {
        assertThat(listOf(Spacing.xs, Spacing.s, Spacing.m, Spacing.l, Spacing.xl, Spacing.xxl))
            .containsExactly(2.dp, 4.dp, 8.dp, 12.dp, 16.dp, 24.dp).inOrder()
    }

    @Test
    fun `screen padding and touch size`() {
        // 12 in the first cut, one step roomier since 2026-10-29 (user request).
        assertThat(ZenythTokens.Standard.screenPadding).isEqualTo(16.dp)
        assertThat(ZenythTokens.Standard.cardPadding).isEqualTo(20.dp)
        assertThat(ZenythTokens.Standard.minTouchTarget).isEqualTo(48.dp)
    }
}
