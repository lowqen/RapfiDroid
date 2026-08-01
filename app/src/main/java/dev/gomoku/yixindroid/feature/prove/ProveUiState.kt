package dev.gomoku.yixindroid.feature.prove

import dev.gomoku.yixindroid.core.designsystem.component.BoardRender
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.ProveMark
import dev.gomoku.yixindroid.core.model.ProveOptions
import dev.gomoku.yixindroid.core.model.ProveOutcome
import dev.gomoku.yixindroid.core.model.ProveProgress

/** One root candidate as the run card lists it (the desktop draws these on the board). */
data class ProveCandidateRow(
    val move: Move,
    val label: String,
    val mark: ProveMark,
    /** Remaining budget of an open candidate, "" otherwise. */
    val budget: String,
)

data class ProveUiState(
    val progress: ProveProgress = ProveProgress(),
    val options: ProveOptions = ProveOptions(),
    val outcome: ProveOutcome? = null,
    val log: List<String> = emptyList(),
    val candidates: List<ProveCandidateRow> = emptyList(),
    /**
     * The prove root with the live overlay on it. The desktop has one window, so
     * its board is already in front of you while a proof runs; here the run is
     * driven from a tab that has no board, so the card carries its own.
     */
    val render: BoardRender = BoardRender(),
    /** Stones on the board — the prove root. */
    val moveCount: Int = 0,
    val blackToMove: Boolean = true,
    val connected: Boolean = false,
    /** The database is on and writable, without which a proof would be discarded. */
    val dbWritable: Boolean = false,
    val gameOver: Boolean = false,
    val notice: String? = null,
) {
    val running: Boolean get() = progress.running
    val canStart: Boolean get() = !running && connected && dbWritable && !gameOver

    /** Why the button is disabled, in the desktop's words (main.c:9875-9881). */
    val blocker: String?
        get() = when {
            running -> null
            !connected -> "엔진에 연결한 뒤 실행하세요."
            !dbWritable -> "데이터베이스를 켜고 읽기 전용을 해제하세요 — 증명 결과는 DB에 기록됩니다."
            gameOver -> "이미 승부가 결정된 국면입니다."
            else -> null
        }
}
