package dev.gomoku.zenyth.feature.data

import com.google.common.truth.Truth.assertThat
import dev.gomoku.zenyth.core.model.PackBuildState
import dev.gomoku.zenyth.feature.data.GameDataPrompt.Form
import org.junit.Test

/** When the research tabs lead to the game data, and in which form (R2). */
class GameDataPromptTest {

    private val idle = PackBuildState.Idle
    private val running = PackBuildState.Running(PackBuildState.Phase.Counting, 10, 100)

    @Test
    fun theCardUntilFoldedThenTheBanner() {
        assertThat(GameDataPrompt.form(missing = true, build = idle, collapsed = false)).isEqualTo(Form.CARD)
        assertThat(GameDataPrompt.form(missing = true, build = idle, collapsed = true)).isEqualTo(Form.BANNER)
    }

    /** Not yet looked on disk: nothing, rather than a card that flashes away. */
    @Test
    fun nothingWhileUnknownOrWithData() {
        assertThat(GameDataPrompt.form(missing = null, build = idle, collapsed = false)).isEqualTo(Form.NONE)
        assertThat(GameDataPrompt.form(missing = false, build = idle, collapsed = false)).isEqualTo(Form.NONE)
    }

    /** A build's progress and its result live in the card, folded or not. */
    @Test
    fun aBuildKeepsTheCard() {
        assertThat(GameDataPrompt.form(missing = true, build = running, collapsed = true)).isEqualTo(Form.CARD)
        assertThat(GameDataPrompt.form(missing = false, build = PackBuildState.Done(10, 20, 0), collapsed = true)).isEqualTo(Form.CARD)
        assertThat(GameDataPrompt.form(missing = true, build = PackBuildState.Failed("x"), collapsed = true)).isEqualTo(Form.CARD)
    }

    @Test
    fun theSheetOnceAndOnlyWithKnownNothing() {
        assertThat(GameDataPrompt.sheet(missing = true, build = idle, seen = false)).isTrue()
        assertThat(GameDataPrompt.sheet(missing = true, build = idle, seen = true)).isFalse()
        assertThat(GameDataPrompt.sheet(missing = null, build = idle, seen = false)).isFalse()
        assertThat(GameDataPrompt.sheet(missing = false, build = idle, seen = false)).isFalse()
        assertThat(GameDataPrompt.sheet(missing = true, build = running, seen = false)).isFalse()
    }
}
