package dev.gomoku.yixindroid.data.explorer

import android.net.Uri
import dev.gomoku.yixindroid.core.common.IoDispatcher
import dev.gomoku.yixindroid.core.model.ExplorerGames
import dev.gomoku.yixindroid.core.model.ExplorerPosition
import dev.gomoku.yixindroid.core.model.ExplorerStatus
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.PackInfo
import dev.gomoku.yixindroid.core.model.Position
import dev.gomoku.yixindroid.core.model.RjGame
import dev.gomoku.yixindroid.domain.repository.ExplorerRepository
import dev.gomoku.yixindroid.domain.repository.GameRepository
import dev.gomoku.yixindroid.domain.repository.ProveRepository
import dev.gomoku.yixindroid.domain.repository.ReviewRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Port of the desktop explorer's data path: `web_poskey` → `rj_stats_lookup` →
 * next-move rows mapped back through `web_tf_inv` → games pane
 * (main.c:5301-5492).
 *
 * The desktop recomputes on the draw path and defers the widget work to an
 * idle; here the position flow does that job — a new line in, a new
 * [ExplorerPosition] out.
 */
@Singleton
class ExplorerRepositoryImpl @Inject constructor(
    private val store: ExplorerPackStore,
    private val game: GameRepository,
    private val review: ReviewRepository,
    // Lazy: prove takes ReviewRepository, review resolves prove — the explorer
    // only needs both to refuse while either runs, so it resolves them late.
    private val prove: Provider<ProveRepository>,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ExplorerRepository {

    private val scope = CoroutineScope(SupervisorJob() + io)

    private val _packs = MutableStateFlow<PackInfo?>(null)
    override val packs: StateFlow<PackInfo?> = _packs.asStateFlow()

    private val _position = MutableStateFlow<ExplorerPosition?>(null)
    override val position: StateFlow<ExplorerPosition?> = _position.asStateFlow()

    private val _status = MutableStateFlow(ExplorerStatus.NO_PACKS)
    override val status: StateFlow<ExplorerStatus> = _status.asStateFlow()

    /** The lookup behind the games pane (`rjcurstat`). */
    private var current: RjStat? = null

    /** Tests drive their own scheduler; the board-following collector must
     *  stop with them. */
    fun shutdown() {
        scope.cancel()
    }

    init {
        // the opening tables are a third input: importing names or grades has
        // to redraw the current position, not wait for the next move
        combine(game.position, store.packs, store.tables) { pos, packs, _ ->
            pos to packs
        }
            .onEach { (pos, packs) -> sync(pos, packs) }
            .launchIn(scope)
    }

    override suspend fun restore() = store.restore()

    override suspend fun importPacks(uris: List<Uri>): Result<String> = store.import(uris)

    override suspend fun clearPacks() = store.clear()

    private suspend fun sync(pos: Position, packs: ExplorerPackStore.Packs?) =
        withContext(io) {
            _packs.value = packs?.info
            current = null
            if (packs == null) {
                _status.value = ExplorerStatus.NO_PACKS
                _position.value = null
                return@withContext
            }
            if (pos.size != RjGame.PACK_SIZE) {
                _status.value = ExplorerStatus.WRONG_SIZE
                _position.value = null
                return@withContext
            }
            val found = ExplorerLookup.lookup(packs.stats, packs.games, pos)
            if (found == null) {
                // no statistics is not no information: a grade is a fact about
                // the position, so the graded moves still get their rows
                _status.value = ExplorerStatus.NO_STATS
                _position.value = ExplorerLookup.theoryOnly(pos)
                return@withContext
            }
            current = found.stat
            _status.value = ExplorerStatus.OK
            _position.value = found.position
        }

    override suspend fun games(filter: String): ExplorerGames = withContext(io) {
        val packs = store.packs.value ?: return@withContext ExplorerGames()
        val stat = current ?: return@withContext ExplorerGames()
        ExplorerLookup.gameList(stat, packs.games, filter)
    }

    override fun game(id: Int): RjGame? = store.packs.value?.games?.game(id)

    override fun ruleName(rule: Int): String? = store.packs.value?.games?.ruleName(rule)

    override fun openingLabel(opening: Int): String? {
        val games = store.packs.value?.games ?: return null
        return ExplorerLookup.gameOpeningLabel(games, opening)
    }

    override suspend fun playNext(move: Move): String? {
        busy()?.let { return it }
        val pos = game.position.value
        if (!move.isInside(pos.size) || pos.moves.contains(move)) {
            return "익스플로러: 이 수는 현재 국면에 맞지 않습니다"
        }
        game.replaceLine(pos.moves + move)
        return null
    }

    override suspend fun loadGame(id: Int): String? {
        busy()?.let { return it }
        val g = game(id) ?: return "기보를 찾을 수 없습니다 (#$id)"
        val size = game.position.value.size
        // Stop at the first illegal / repeated cell, exactly like the desktop's
        // replay loop — a truncated pack must not silently drop stones later on.
        val moves = ArrayList<Move>(g.cells.size)
        for (m in g.moves(size)) {
            if (moves.contains(m)) break
            moves.add(m)
        }
        if (moves.isEmpty()) return "기보에 둘 수 있는 수가 없습니다 (#$id)"
        game.replaceLine(moves)
        return null
    }

    /** Board writes are refused while a research run owns the engine
     *  conversation (main.c:5434/5465 — the same pair of flags). */
    private fun busy(): String? = when {
        review.progress.value.running -> "게임 리뷰가 진행 중입니다 — 먼저 중지하세요"
        prove.get().progress.value.running -> "국면 증명이 진행 중입니다 — 먼저 중지하세요"
        else -> null
    }
}
