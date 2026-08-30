package dev.gomoku.rapfidroid.feature.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.MO_GUI_NODES
import dev.gomoku.rapfidroid.core.model.MO_MAX_STONES
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.MoveOrderChild
import dev.gomoku.rapfidroid.core.model.MoveOrderFormat
import dev.gomoku.rapfidroid.core.model.MoveOrderGhost
import dev.gomoku.rapfidroid.core.model.MoveOrderSet
import dev.gomoku.rapfidroid.core.model.Opening26
import dev.gomoku.rapfidroid.core.model.Position
import dev.gomoku.rapfidroid.domain.repository.ExplorerRepository
import dev.gomoku.rapfidroid.domain.repository.GameRepository
import dev.gomoku.rapfidroid.domain.repository.ProveRepository
import dev.gomoku.rapfidroid.domain.repository.ReviewRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Rule toggles, mirroring the desktop's four check boxes. */
data class MoveOrderOptions(
    /** Tengen / 3×3 / 5×5 / 7×7 on moves 1-4. */
    val openingRule: Boolean = true,
    /** No five before the final move. */
    val noFive: Boolean = true,
    /** Move 2 restricted to H9 / I9 (sub-rule of [openingRule]). */
    val move2Fix: Boolean = true,
    /** Merge the rotated / mirrored placements (환원). */
    val symmetry: Boolean = true,
)

/** One candidate row. */
data class MoveOrderRow(
    val cell: Int,
    val move: Move,
    val label: String,
    val isBlack: Boolean,
    val count: Double,
    val countText: String,
    val sharePercent: Int,
    /** This is the next move of the real game (`moactual`). */
    val actual: Boolean,
)

data class MoveOrderUiState(
    val headline: String = tr("보드에 돌을 먼저 놓으세요.", "Put some stones on the board first."),
    val note: String = "",
    val openingLabel: String? = null,
    val breadcrumb: String = tr("처음부터", "from the start"),
    val orientation: String? = null,
    val rows: List<MoveOrderRow> = emptyList(),
    val prefix: List<Int> = emptyList(),
    val ghosts: List<MoveOrderGhost> = emptyList(),
    val options: MoveOrderOptions = MoveOrderOptions(),
    val selected: Int? = null,
    val canBack: Boolean = false,
    val canApply: Boolean = false,
    val computing: Boolean = false,
    val boardSize: Int = Move.DEFAULT_SIZE,
    /** Non-null while a rearranging replay waits for confirmation. */
    val applyPrompt: String? = null,
    val notice: String? = null,
)

/**
 * 수순 탐색기 — "which move orders produce exactly the stones now on the
 * board?". Port of the desktop window (main.c:5726-6700) over [MoveOrderSet].
 *
 * The set is rebuilt whenever the board line or a rule toggle changes, off the
 * main thread: the shared 300,000-node budget is the desktop's and keeps a
 * rebuild well under a frame budget's worth of jank on a phone.
 */
@HiltViewModel
class MoveOrderViewModel @Inject constructor(
    private val game: GameRepository,
    private val review: ReviewRepository,
    private val prove: ProveRepository,
    @Suppress("unused") private val explorer: ExplorerRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(MoveOrderUiState())
    val uiState: StateFlow<MoveOrderUiState> = _ui.asStateFlow()

    private var set: MoveOrderSet? = null
    private var line: List<Move> = emptyList()

    /** [MoveOrderSet] is mutable and the work runs off the main thread, so every
     *  build / drill / read is serialised through here. */
    private val lock = Mutex()

    init {
        game.position.onEach { rebuild(it) }.launchIn(viewModelScope)
    }

    private fun rebuild(pos: Position) {
        line = pos.moves
        val opts = _ui.value.options
        _ui.update { it.copy(computing = true, boardSize = pos.size) }
        viewModelScope.launch {
            lock.withLock {
            if (pos.moves.isEmpty()) {
                set = null
                _ui.update {
                    it.copy(
                        computing = false,
                        headline = tr("보드에 돌을 먼저 놓으세요.", "Put some stones on the board first."),
                        note = "", openingLabel = null, rows = emptyList(),
                        prefix = emptyList(), ghosts = emptyList(),
                        breadcrumb = tr("처음부터", "from the start"), orientation = null,
                        canBack = false, canApply = false, selected = null,
                    )
                }
                return@withLock
            }
            val cells = pos.moves.map { it.y * pos.size + it.x }
            val built = withContext(Dispatchers.Default) {
                MoveOrderSet.create(
                    cells = cells,
                    size = pos.size,
                    openingRule = opts.openingRule,
                    noFive = opts.noFive,
                    move2Fix = opts.openingRule && opts.move2Fix,
                    withSymmetry = opts.symmetry,
                    maxNodes = MO_GUI_NODES,
                )
            }
            set = built
            if (built == null) {
                _ui.update {
                    it.copy(
                        computing = false,
                        headline = tr("국면이 너무 큽니다 — 색깔당 최대 ${MO_MAX_STONES}개까지.", "Too many stones — ${MO_MAX_STONES} per colour at most."),
                        note = "", rows = emptyList(), prefix = emptyList(),
                        ghosts = emptyList(), canBack = false, canApply = false,
                    )
                }
                return@withLock
            }
            fill()
            }
        }
    }

    /** Rebuild the candidate list for the current prefix (`mo_fill`).
     *  Caller holds [lock]. */
    private suspend fun fill() = withContext(Dispatchers.Default) {
        val s = set ?: return@withContext
        val size = _ui.value.boardSize
        val branch = if (s.total > 0.0) s.branchCount() else 0.0
        val children = if (s.total > 0.0) s.children() else emptyList()

        // The real game's next move, only while the drilled path is a prefix of
        // the actual game in its original orientation (main.c:5941-5949).
        val prefixCells = s.prefix
        val actual = if (
            s.identityAlive() && prefixCells.size < line.size &&
            prefixCells.indices.all { line[it].let { m -> m.y * size + m.x } == prefixCells[it] }
        ) {
            line[prefixCells.size].let { it.y * size + it.x }
        } else {
            -1
        }

        _ui.update { st ->
            st.copy(
                computing = false,
                headline = headline(s),
                note = note(s),
                openingLabel = openingLabel(s, size),
                breadcrumb = breadcrumb(s, size),
                orientation = orientation(s),
                rows = children.map { it.toRow(branch, actual, size) },
                prefix = prefixCells.toList(),
                ghosts = s.ghosts(),
                canBack = prefixCells.isNotEmpty(),
                canApply = s.total > 0.0,
                selected = null,
            )
        }
    }

    private fun MoveOrderChild.toRow(branch: Double, actual: Int, size: Int) = MoveOrderRow(
        cell = cell,
        move = Move(cell % size, cell / size),
        label = MoveOrderFormat.cellName(cell, size),
        isBlack = isBlack,
        count = count,
        countText = MoveOrderFormat.count(count),
        sharePercent = if (branch > 0.0) Math.round(100.0 * count / branch).toInt() else 0,
        actual = cell == actual,
    )

    private fun headline(s: MoveOrderSet): String {
        val stones = tr("흑 ${s.blackCount} · 백 ${s.whiteCount}", "Black ${s.blackCount} · White ${s.whiteCount}")
        return when {
            s.overflow -> tr("$stones — 경우의 수가 너무 많아 정확한 집계 생략", "$stones — too many orders to count exactly")
            s.total <= 0.0 -> tr("$stones — 이 규칙에서는 만들 수 없는 배치", "$stones — this rule cannot produce that shape")
            s.variantCount > 1 ->
                tr("$stones — 수순 ${MoveOrderFormat.count(s.total)}가지 (배치 ${s.variantCount}종)", "$stones — ${MoveOrderFormat.count(s.total)} orders (${s.variantCount} placements)")
            else -> tr("$stones — 수순 ${MoveOrderFormat.count(s.total)}가지", "$stones — ${MoveOrderFormat.count(s.total)} orders")
        }
    }

    /** Why it is unreachable is the interesting part: say which rule bites
     *  (main.c:6074-6110). */
    private fun note(s: MoveOrderSet): String {
        val size = _ui.value.boardSize
        val opts = _ui.value.options
        if (s.total > 0.0 && opts.symmetry && s.variantCount > 1) {
            return tr("(회전·반전 ${s.variantCount}종을 같은 모양으로 합산)", "(${s.variantCount} rotations and mirrors counted as one shape)")
        }
        if (s.total > 0.0 || s.overflow || !opts.openingRule) return ""
        if (size % 2 == 0) return tr("(오프닝 규칙은 홀수 판에서만 적용됩니다)", "(opening rules apply on odd-sized boards only)")
        val ctr = size / 2
        val tengen = ctr * size + ctr
        if (line.none { it.y * size + it.x == tengen }) {
            return tr("(천원에 흑돌이 없습니다: 1수는 반드시 중앙이어야 합니다)", "(no black stone on the centre point: move 1 has to be there)")
        }
        val r2 = MoveOrderSet.rule2Cells(size)
        if (opts.move2Fix && r2 != null) {
            val hasR2 = (0 until 8).any { t ->
                line.any { m ->
                    val c = MoveOrderSet.xform(t, size, m.y * size + m.x)
                    c == r2.first || c == r2.second
                }
            }
            if (!hasR2) {
                return if (opts.symmetry) tr("(어떤 배치에서도 2수가 H9/I9에 오지 않습니다)", "(no placement puts move 2 on H9 or I9)")
                else tr("(H9/I9에 백돌이 없습니다 — 환원을 켜 보세요)", "(no white stone on H9 or I9 — try folding rotations)")
            }
        }
        return tr("(3×3 / 5×5 / 7×7 상자를 만족하는 수순이 없습니다)", "(no order satisfies the 3×3 / 5×5 / 7×7 boxes)")
    }

    /** The opening follows the **drilled** order once it is 3+ plies deep —
     *  different orders through the same stones realise different 주형, and this
     *  is where you see it (main.c:5889-5917). */
    private fun openingLabel(s: MoveOrderSet, size: Int): String? {
        val src = when {
            s.prefix.size >= 3 -> s.prefix
            line.size >= 3 -> line.take(3).map { it.y * size + it.x }
            else -> return null
        }
        val id = MoveOrderFormat.opening26(src[0], src[1], src[2], size) ?: return null
        val tag = if (id < 13) "D${id + 1}" else "I${id - 12}"
        return tr("주형: ", "Opening: ") + "${Opening26.name(id)} ($tag)"
    }

    private fun breadcrumb(s: MoveOrderSet, size: Int): String =
        if (s.prefix.isEmpty()) tr("처음부터", "from the start")
        else tr("수순: ", "Order:") + s.prefix.joinToString(" ") { MoveOrderFormat.cellName(it, size) }

    private fun orientation(s: MoveOrderSet): String? {
        if (!_ui.value.options.symmetry || s.variantCount <= 1) return null
        val alive = s.aliveCount()
        return if (alive == 1) MoveOrderSet.xformName(s.soleAliveTransform() ?: 0)
        else tr("배치 ${alive}종 진행 중", "${alive} placements still live")
    }

    // ---- interaction --------------------------------------------------------

    fun onOptionsChange(options: MoveOrderOptions) {
        _ui.update { it.copy(options = options) }
        rebuild(game.position.value)
    }

    fun onDrill(cell: Int) {
        viewModelScope.launch {
            lock.withLock {
                val s = set ?: return@withLock
                if (s.total <= 0.0) return@withLock
                if (s.drill(cell)) fill()
            }
        }
    }

    fun onSelect(cell: Int?) {
        _ui.update { it.copy(selected = cell) }
    }

    fun onBack() {
        viewModelScope.launch {
            lock.withLock { if (set?.back() == true) fill() }
        }
    }

    fun onRoot() {
        viewModelScope.launch {
            lock.withLock {
                set?.root()
                fill()
            }
        }
    }

    /**
     * Replay the drilled prefix (completed with any legal tail) on the board.
     * The completion prefers the original orientation; when only a rotated or
     * mirrored placement is left, ask first — the stones will move.
     */
    fun onApply(confirmed: Boolean = false) {
        val s = set ?: return
        if (s.total <= 0.0) return
        busy()?.let { reason ->
            _ui.update { it.copy(notice = reason) }
            return
        }
        val size = _ui.value.boardSize
        viewModelScope.launch {
            val done = lock.withLock { withContext(Dispatchers.Default) { s.complete() } }
            if (done == null) {
                _ui.update { it.copy(notice = tr("여기서 완성할 수 있는 수순이 없습니다", "No order can be completed from here")) }
                return@launch
            }
            if (done.transform != 0 && !confirmed) {
                _ui.update {
                    it.copy(
                        applyPrompt = tr("이 수순은 ", "This order is in the ") +
                            MoveOrderSet.xformName(done.transform) +
                            tr(" 배치에 ", " placement") +
                            tr("있습니다 — 돌 위치가 지금 보드와 달라집니다. 그래도 놓을까요?", " — the stones will sit differently from the board. Place it anyway?"),
                    )
                }
                return@launch
            }
            game.replaceLine(done.order.map { Move(it % size, it / size) })
            _ui.update {
                it.copy(applyPrompt = null, notice = tr("보드를 이 수순으로 다시 놓았습니다", "The board has been replayed in this order"))
            }
        }
    }

    fun onDismissApplyPrompt() {
        _ui.update { it.copy(applyPrompt = null) }
    }

    fun onNoticeShown() {
        _ui.update { it.copy(notice = null) }
    }

    /** Board writes are refused while a research run owns the engine
     *  (main.c:6225 — `reviewactive || proveactive || queueactive`). */
    private fun busy(): String? = when {
        review.progress.value.running -> tr("게임 리뷰가 진행 중입니다 — 먼저 중지하세요", "A game review is running — stop it first")
        prove.progress.value.running -> tr("국면 증명이 진행 중입니다 — 먼저 중지하세요", "A proof is running — stop it first")
        else -> null
    }
}
