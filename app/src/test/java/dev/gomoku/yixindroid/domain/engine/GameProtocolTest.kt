package dev.gomoku.yixindroid.domain.engine

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.AppSettings
import dev.gomoku.yixindroid.core.model.ComputerSide
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.OpeningProtocol
import dev.gomoku.yixindroid.core.model.StoneColor
import org.junit.Test

/**
 * P5 wire formats and rule decoding, checked against main.c: the game commands
 * (`INFO time_left`, `yxdraw`/`yxresign`, the Swap2 and Soosorv steps) and the
 * opening messages the engine answers with.
 */
class GameProtocolTest {

    private val coord = CoordMapper()
    private fun line(text: String) = ResponseParser.parse(text, coord)

    private val path = listOf(Move(x = 7, y = 7), Move(x = 8, y = 6))

    // ---- commands -----------------------------------------------------------

    @Test
    fun timeLeftDrawAndResign() {
        assertThat(EngineCommand.InfoTimeLeft(295_000).serialize(coord))
            .isEqualTo("INFO time_left 295000")
        // A negative budget would be a protocol error; the desktop never sends one.
        assertThat(EngineCommand.InfoTimeLeft(-1).serialize(coord)).isEqualTo("INFO time_left 0")
        assertThat(EngineCommand.YxDraw.serialize(coord)).isEqualTo("yxdraw")
        assertThat(EngineCommand.YxResign.serialize(coord)).isEqualTo("yxresign")
    }

    @Test
    fun swap2StepsAreBareCommands() {
        // The board goes over separately with YXBOARD (main.c:2701).
        assertThat(EngineCommand.YxSwap2Step(1).serialize(coord)).isEqualTo("yxswap2step1")
        assertThat(EngineCommand.YxSwap2Step(2).serialize(coord)).isEqualTo("yxswap2step2")
        assertThat(EngineCommand.YxSwap2Step(3).serialize(coord)).isEqualTo("yxswap2step3")
    }

    @Test
    fun soosorvStepsCarryTheirOwnMoveList() {
        assertThat(EngineCommand.YxSoosorvStep(1).serialize(coord)).isEqualTo("yxsoosorvstep1")
        assertThat(EngineCommand.YxSoosorvStep(2, moves = path).serialize(coord))
            .isEqualTo("yxsoosorvstep2\n7,7\n6,8\ndone")
        assertThat(EngineCommand.YxSoosorvStep(4, fifthCount = 3, moves = path).serialize(coord))
            .isEqualTo("yxsoosorvstep4 3\n7,7\n6,8\ndone")
        assertThat(EngineCommand.YxSoosorvStep(5, fifthCount = 1, moves = path).serialize(coord))
            .isEqualTo("yxsoosorvstep5 1\n7,7\n6,8\ndone")
        assertThat(EngineCommand.YxSoosorvStep(6, moves = path).serialize(coord))
            .isEqualTo("yxsoosorvstep6\n7,7\n6,8\ndone")
    }

    // ---- opening messages ---------------------------------------------------

    /** `MESSAGE SWAP2 MOVE1 7 7` — space separated, unlike every other coordinate. */
    @Test
    fun swap2MovesParseWithSpaceSeparatedCoordinates() {
        val response = line("MESSAGE SWAP2 MOVE1 7 7")
        assertThat(response).isInstanceOf(EngineResponse.OpeningMove::class.java)
        val move = response as EngineResponse.OpeningMove
        assertThat(move.swap2).isTrue()
        assertThat(move.index).isEqualTo(1)
        assertThat(move.move).isEqualTo(Move(x = 7, y = 7))
        assertThat(move.fifthCount).isNull()
    }

    @Test
    fun swap2SwapAnswers() {
        val no = line("MESSAGE SWAP2 SWAP1 NO") as EngineResponse.OpeningSwap
        assertThat(no.swap2).isTrue()
        assertThat(no.which).isEqualTo(1)
        assertThat(no.yes).isFalse()

        val yes = line("MESSAGE SWAP2 SWAP2 YES") as EngineResponse.OpeningSwap
        assertThat(yes.which).isEqualTo(2)
        assertThat(yes.yes).isTrue()
    }

    /** Soosorv MOVE4 appends N: `sscanf(p, "%d %d %d", &y, &x, &move5N)`. */
    @Test
    fun soosorvMove4CarriesTheFifthCount() {
        val move = line("MESSAGE SOOSORV MOVE4 6 8 3") as EngineResponse.OpeningMove
        assertThat(move.swap2).isFalse()
        assertThat(move.index).isEqualTo(4)
        assertThat(move.move).isEqualTo(Move(x = 8, y = 6))
        assertThat(move.fifthCount).isEqualTo(3)
    }

    /** Soosorv spells its answers Y / N. */
    @Test
    fun soosorvSwapAnswers() {
        val yes = line("MESSAGE SOOSORV SWAP1 Y") as EngineResponse.OpeningSwap
        assertThat(yes.which).isEqualTo(1)
        assertThat(yes.yes).isTrue()
        val no = line("MESSAGE SOOSORV SWAP2 N") as EngineResponse.OpeningSwap
        assertThat(no.which).isEqualTo(2)
        assertThat(no.yes).isFalse()
    }

    @Test
    fun soosorvFifthMoveStage() {
        val chosen = line("MESSAGE SOOSORV MOVE5 C 5 9") as EngineResponse.SoosorvFifth
        assertThat(chosen.kind).isEqualTo(EngineResponse.SoosorvFifth.Kind.CHOOSE)
        assertThat(chosen.move).isEqualTo(Move(x = 9, y = 5))

        assertThat((line("MESSAGE SOOSORV MOVE5 REFRESH") as EngineResponse.SoosorvFifth).kind)
            .isEqualTo(EngineResponse.SoosorvFifth.Kind.REFRESH)
        assertThat((line("MESSAGE SOOSORV MOVE5 DONE") as EngineResponse.SoosorvFifth).kind)
            .isEqualTo(EngineResponse.SoosorvFifth.Kind.DONE)

        val offer = line("MESSAGE SOOSORV MOVE5 4 9") as EngineResponse.SoosorvFifth
        assertThat(offer.kind).isEqualTo(EngineResponse.SoosorvFifth.Kind.OFFER)
        assertThat(offer.move).isEqualTo(Move(x = 9, y = 4))
    }

    @Test
    fun lowerCaseIsAcceptedLikeTheDesktopsUppercasing() {
        assertThat(line("message swap2 move1 7 7")).isInstanceOf(EngineResponse.OpeningMove::class.java)
    }

    @Test
    fun ordinaryMessagesAreStillMessages() {
        assertThat(line("MESSAGE Loaded config")).isInstanceOf(EngineResponse.Message::class.java)
    }

    // ---- rule decoding ------------------------------------------------------

    /**
     * settings.txt line 3 as `load_setting` decodes it (main.c:14070): the
     * opening rules sit on a base rule of freestyle / renju / renju / standard.
     */
    @Test
    fun rulesDecodeToTheDesktopsBaseAndOpening() {
        val expected = listOf(
            Triple(0, 0, OpeningProtocol.NONE),
            Triple(1, 1, OpeningProtocol.NONE),
            Triple(2, 2, OpeningProtocol.NONE),
            Triple(3, 0, OpeningProtocol.SWAP_FIRST),
            Triple(4, 2, OpeningProtocol.RIF),
            Triple(5, 2, OpeningProtocol.SOOSORV),
            Triple(6, 1, OpeningProtocol.SWAP2),
        )
        for ((rule, engineRule, opening) in expected) {
            val settings = AppSettings(rule = rule)
            assertThat(settings.engineRule).isEqualTo(engineRule)
            assertThat(settings.opening).isEqualTo(opening)
        }
    }

    @Test
    fun onlyStandardForbidsTheOverlineWin() {
        assertThat(AppSettings(rule = 1).allowsOverlineWin).isFalse()
        assertThat(AppSettings(rule = 6).allowsOverlineWin).isFalse()   // swap2 = standard base
        assertThat(AppSettings(rule = 0).allowsOverlineWin).isTrue()
        assertThat(AppSettings(rule = 2).allowsOverlineWin).isTrue()
    }

    @Test
    fun openingRulesNeedAnOddBoard() {
        assertThat(AppSettings(rule = 5, boardSize = 15).openingNeedsOddSize).isFalse()
        assertThat(AppSettings(rule = 5, boardSize = 20).openingNeedsOddSize).isTrue()
        assertThat(AppSettings(rule = 2, boardSize = 20).openingNeedsOddSize).isFalse()
    }

    @Test
    fun computerSideIsTheDesktopBitmask() {
        assertThat(AppSettings(computerBlack = false, computerWhite = false).computerSide)
            .isEqualTo(ComputerSide.NONE)
        assertThat(AppSettings(computerBlack = true, computerWhite = false).computerSide)
            .isEqualTo(ComputerSide.BLACK)
        assertThat(AppSettings(computerBlack = false, computerWhite = true).computerSide)
            .isEqualTo(ComputerSide.WHITE)
        assertThat(AppSettings(computerBlack = true, computerWhite = true).computerSide)
            .isEqualTo(ComputerSide.BOTH)

        assertThat(ComputerSide.BOTH.plays(StoneColor.BLACK)).isTrue()
        assertThat(ComputerSide.WHITE.plays(StoneColor.BLACK)).isFalse()
        assertThat(ComputerSide.WHITE.swapped()).isEqualTo(ComputerSide.BLACK)
        assertThat(ComputerSide.BLACK.swapped()).isEqualTo(ComputerSide.WHITE)
    }
}
