package dev.gomoku.yixindroid.core.model

import dev.gomoku.yixindroid.core.i18n.tr

/** One node of the proof tree (`ProveNode`, main.c:8949). Mutable, as there. */
class ProveNode(
    /** The line below the prove root that reaches this node. */
    val moves: List<Move>,
    val parent: Int,
    /** Attacker to move. The root is an OR node, so even ply counts are OR. */
    val isOr: Boolean,
    /** Attacker win rate estimate. */
    var wratt: Double,
    /** Search budget: ms, or a depth in depth mode. */
    var budget: Int,
    /** A batch verification of a deferred defense: cheap budget, cheap priority. */
    val verify: Boolean,
    /** `yxnbest k` for this node, widened on escalation. */
    var k: Int,
    /** Attacker mate visible in the PV that created this node (0 = none). */
    var pvMate: Int,
) {
    var state: ProveState = ProveState.OPEN
    var result: ProveResult = ProveResult.NONE
    var kind: ProveKind = ProveKind.NONE

    /** Side-to-move value, mate-encoded like the engine's EVAL. */
    var value: Int = 0
    var recDepth: Int = 0
    var expanded: Boolean = false
    var retries: Int = 0

    /** Has had at least one search, so its early probe is spent. */
    var probed: Boolean = false

    /**
     * Known to have dropped moves it was told about — a defence past the pending
     * cap, or a PV the engine sent without a coordinate. The node may still be
     * searched and may still be refuted by a single holding defence, but it can
     * never be *proven*: "every defence loses" is a claim about a complete set,
     * and this node knows its set is not complete.
     */
    var incomplete: Boolean = false

    /** Defenses deferred by optimistic pruning, verified in one batch later. */
    val pending: MutableList<Move> = mutableListOf()

    /** Attacker alternatives not yet materialized (lazy OR widening). */
    val alt: MutableList<ProvePv> = mutableListOf()
    var altNext: Int = 0

    val ply: Int get() = moves.size
    val lastMove: Move? get() = moves.lastOrNull()
}

/** What the driver must do next after feeding a search result in. */
enum class ProveStep {
    /** The node is settled; flush the write queue, then carry on. */
    RESOLVED,

    /** Search the same node again (nothing usable came back). */
    RETRY,

    /** Pick the next node by cost. */
    CONTINUE,
}

/**
 * The AND/OR proof search of main.c:8918-9979, minus the engine conversation:
 * node bookkeeping, the cost function that orders the work, propagation of
 * conclusions and the write queue.
 *
 * Pure on purpose — the search *order* is what decides whether a prove finishes
 * in a minute or an hour, and a mis-ported propagation rule would write a wrong
 * mate record into the shared database. Everything here is therefore reachable
 * from unit tests without an engine.
 */
class ProveTree(
    val options: ProveOptions,
    val size: Int = Move.DEFAULT_SIZE,
    /** `printf_log`; the repository forwards these to its log flow. */
    private val log: (String) -> Unit = {},
) {
    private val nodes = mutableListOf<ProveNode>()

    /** Nodes whose conclusion still has to be written to the database (LIFO). */
    private val writeQueue = mutableListOf<Int>()

    var resolvedCount: Int = 0
        private set

    init {
        nodes += ProveNode(
            moves = emptyList(), parent = -1, isOr = true, wratt = 0.5,
            budget = options.initialBudget, verify = false, k = options.nbest, pvMate = 0,
        )
    }

    val count: Int get() = nodes.size
    val root: ProveNode get() = nodes[0]

    operator fun get(i: Int): ProveNode = nodes[i]

    fun children(i: Int): List<Int> = nodes.indices.filter { nodes[it].parent == i }

    /** Human notation of a node's line (`prove_fmt_path`, main.c:9002). */
    fun path(i: Int): String {
        val n = nodes[i]
        if (n.moves.isEmpty()) return "(root)"
        return n.moves.joinToString(" ") { MoveGrader.coord(it, size) }
    }

    /** Child value -> parent value; a mate moves one ply farther (main.c:9018). */
    fun negamaxUp(v: Int): Int {
        val up = -v
        return when {
            up >= 29500 -> up - 1
            up <= -29500 -> up + 1
            else -> up
        }
    }

    fun ancestorResolved(i: Int): Boolean {
        var p = nodes[i].parent
        while (p >= 0) {
            if (nodes[p].state == ProveState.RESOLVED) return true
            p = nodes[p].parent
        }
        return false
    }

    fun openCount(): Int =
        nodes.indices.count { nodes[it].state == ProveState.OPEN && !ancestorResolved(it) }

    // ---- scheduling ---------------------------------------------------------

    /**
     * `prove_cost` (main.c:9264): prove the win along the most promising line
     * first. A mate already visible in a PV outranks everything (shortest first),
     * otherwise the branch where the win comes easiest goes first. A defense that
     * looks like it actually holds gets one cheap probe, because a single holding
     * defense refutes its parent outright and would make every easier proof under
     * it wasted work.
     */
    fun cost(n: ProveNode): Double {
        val unit = if (options.byDepth) options.depth0 else options.budget0Sec * 1000
        val scale = (n.budget / unit.toDouble()).coerceAtLeast(1.0)
        val depth = 1.0 + 0.15 * n.ply
        var base: Double
        if (n.pvMate > 0) {
            base = 0.001 * n.pvMate // tier 0: finish visible mates first
        } else {
            base = 1.0 - n.wratt
            if (options.probe && !n.probed && n.wratt <= REFUTE_WR && 0.5 * n.wratt < base) {
                base = 0.5 * n.wratt
            }
            base += 0.02
        }
        base *= scale * depth
        if (n.verify) base *= 0.3
        return base
    }

    /** `prove_continue`'s selection (main.c:9581): cheapest open node. */
    fun pickNext(): Int? {
        var best = -1
        var bestCost = 0.0
        for (i in nodes.indices) {
            val n = nodes[i]
            if (n.state != ProveState.OPEN || ancestorResolved(i)) continue
            val c = cost(n)
            if (best < 0 || c < bestCost) {
                best = i
                bestCost = c
            }
        }
        return best.takeIf { it >= 0 }
    }

    // ---- growing ------------------------------------------------------------

    /** `prove_add_child` (main.c:9287); -1 when a cap is hit. */
    fun addChild(parent: Int, move: Move, wratt: Double, verify: Boolean, pvMate: Int): Int {
        val p = nodes[parent]
        if (nodes.size >= MAX_NODES || p.ply + 1 >= MAX_PLIES) return -1
        val child = ProveNode(
            moves = p.moves + move,
            parent = parent,
            // main.c: `c->is_or = c->nmoves % 2 == 0` — the root's side moves again
            // after an even number of plies.
            isOr = (p.ply + 1) % 2 == 0,
            wratt = wratt,
            budget = if (verify) options.verifyBudget else options.initialBudget,
            verify = verify,
            k = options.nbest,
            pvMate = pvMate,
        )
        nodes += child
        return nodes.size - 1
    }

    /**
     * `prove_or_widen` (main.c:9318): an attacker candidate failed, so materialize
     * the next latent alternative — unless another candidate of this node is still
     * open. With the list spent the node just stays OPEN and the scheduler
     * re-searches it with a doubled budget and a wider `k`.
     */
    fun orWiden(parent: Int) {
        if (parent < 0) return
        val p = nodes[parent]
        if (!p.isOr || p.state != ProveState.OPEN) return
        if (children(parent).any { nodes[it].state == ProveState.OPEN }) return
        if (p.altNext < p.alt.size) {
            val a = p.alt[p.altNext++]
            val c = addChild(parent, a.move, a.winRate ?: UNKNOWN_WR, verify = false, pvMate = a.mate)
            if (c >= 0) {
                log(tr("증명: 공격 후보 ${p.altNext + 1}/${p.alt.size + 1} ${path(c)}", "Prove: attack candidate ${p.altNext + 1}/${p.alt.size + 1} ${path(c)}"))
            }
        }
    }

    /**
     * `prove_expand` (main.c:9676). OR nodes materialize only the best candidate
     * and keep the rest latent; AND nodes expand the viable defenses and defer the
     * hopeless ones.
     */
    fun expand(i: Int, pvs: List<ProvePv>) {
        val n = nodes[i]
        n.expanded = true
        if (n.ply >= TREE_PLIES) return
        if (n.isOr) {
            val limit = if (n.k > 0) n.k else options.nbest
            val ordered = order(pvs.take(limit))
            var live = children(i).any { nodes[it].state == ProveState.OPEN }
            for (pv in ordered) {
                val mate = if (pv.mate > 0) pv.mate else 0
                if (knownMove(i, pv.move)) continue
                if (!options.bestFirst || !live) {
                    if (addChild(i, pv.move, pv.winRate ?: UNKNOWN_WR, verify = false, pvMate = mate) >= 0 &&
                        options.bestFirst
                    ) {
                        live = true
                    }
                } else if (n.alt.size < ProveOptions.NBEST_MAX) {
                    n.alt += pv.copy(mate = mate)
                } else {
                    // An attacker candidate we cannot even keep latent. Harmless
                    // on its own — one winning move is all an OR node needs — so
                    // it does not make the node incomplete.
                    log(tr("증명: 공격 후보 ${MoveGrader.coord(pv.move, size)} 보관 한도 초과", "Prove: attack candidate ${MoveGrader.coord(pv.move, size)} past the latent cap"))
                }
            }
        } else {
            // An AND node is re-expanded now, so the caps count what is already
            // here and a defence that is already a child, latent or deferred is
            // skipped — otherwise a second search would double every defence.
            var kept = children(i).size
            var dropped = 0
            // Defenses in the defender's own order: the ones that look like they
            // hold come first. A defence with no win rate is *not* a hopeless
            // one — unknown is not zero — so it is expanded, not deferred.
            for (pv in order(pvs)) {
                if (knownMove(i, pv.move) || pv.move in n.pending) continue
                val defenderWr = pv.winRate ?: UNKNOWN_WR
                if ((pv.winRate ?: 1.0) > PRUNE_WR && kept < DEFEND_KEEP) {
                    // A defender-perspective mate below zero means the attacker mates.
                    addChild(
                        i, pv.move, 1.0 - defenderWr, verify = false,
                        pvMate = if (pv.mate < 0) -pv.mate else 0,
                    )
                    kept++
                } else if (n.pending.size < MAX_PENDING) {
                    n.pending += pv.move
                } else {
                    // Neither a child nor deferred: this defence has been thrown
                    // away, and with it any claim that every defence loses.
                    dropped++
                    n.incomplete = true
                }
            }
            if (dropped > 0) {
                log(tr("증명: 방어 ${dropped}개를 담지 못해 이 국면은 «모든 방어가 진다» 로 결론내지 않는다", "Prove: $dropped defences did not fit, so this node can no longer be proven"))
            }
            if (kept == 0 && n.pending.isNotEmpty()) {
                // Nothing viable: verify the deferred moves right away, otherwise
                // no child would ever propagate into this node.
                val deferred = n.pending.toList()
                n.pending.clear()
                deferred.forEach { addChild(i, it, 0.98, verify = true, pvMate = 0) }
            } else if (n.pending.isNotEmpty()) {
                log(tr("증명: 약한 방어 ${n.pending.size}개 보류 (나중에 검증)", "Prove: ${n.pending.size} weak defences deferred (verified later)"))
            }
        }
    }

    /** Already a child of [i], or waiting in its latent list (main.c:9663). */
    fun knownMove(i: Int, move: Move): Boolean =
        children(i).any { nodes[it].lastMove == move } || nodes[i].alt.any { it.move == move }

    /**
     * The one order in this file, from the mover's point of view — `prove_pv_before`
     * (main.c:9653) widened until it can rank every PV against every other.
     *
     * The desktop had two: the expansion sorted mates ahead of everything, while
     * the "which line is this node's value?" pick looked at win rate alone. So a
     * mate and a non-mate were never compared on the same scale, and inside one
     * state machine the same set of PVs had two different best elements.
     *
     * Three tiers, and within them:
     *  - **the mover mates** — soonest first;
     *  - **no mate either way** — highest win rate first, and a PV whose win
     *    rate is *unknown* sorts below every known one. It may be the best move
     *    on the board; it is not evidence that it is;
     *  - **the mover is mated** — latest first, because if everything loses then
     *    lasting longest is the best of them.
     */
    private fun order(pvs: List<ProvePv>): List<ProvePv> =
        pvs.sortedWithStable { a, b -> if (before(a, b)) -1 else 1 }

    /** Is [a] strictly ahead of [b] in that order? Equal keys answer false. */
    private fun before(a: ProvePv, b: ProvePv): Boolean {
        val ta = tier(a)
        val tb = tier(b)
        if (ta != tb) return ta < tb
        return when (ta) {
            // +M: sooner is better.  -M: later is better (mate is negative, so
            // "more negative" is "further away") — one comparison serves both.
            TIER_WIN, TIER_LOSS -> a.mate < b.mate
            else -> (a.winRate ?: UNKNOWN_RANK) > (b.winRate ?: UNKNOWN_RANK)
        }
    }

    private fun tier(pv: ProvePv): Int = when {
        pv.mate > 0 -> TIER_WIN
        pv.mate < 0 -> TIER_LOSS
        else -> TIER_OPEN
    }

    /**
     * main.c orders with an insertion sort over a "x before y?" predicate, which
     * keeps equal elements in engine order. Kotlin's sort is stable too, but only
     * for a comparator that never claims `a < b` *and* `b < a`; the desktop's
     * predicate is asymmetric for equal keys, so the insertion sort is reproduced
     * literally instead of trusting the comparator.
     */
    private fun List<ProvePv>.sortedWithStable(before: (ProvePv, ProvePv) -> Int): List<ProvePv> {
        val out = toMutableList()
        for (a in 1 until out.size) {
            var b = a
            while (b > 0 && before(out[b], out[b - 1]) < 0) {
                val t = out[b]
                out[b] = out[b - 1]
                out[b - 1] = t
                b--
            }
        }
        return out
    }

    // ---- concluding ---------------------------------------------------------

    /** `prove_resolve` (main.c:9353). */
    fun resolve(i: Int, result: ProveResult, kind: ProveKind, value: Int, recDepth: Int, note: String) {
        val n = nodes[i]
        if (n.state == ProveState.RESOLVED) return
        n.state = ProveState.RESOLVED
        n.result = result
        n.kind = kind
        n.value = value
        n.recDepth = recDepth
        n.wratt = if (result == ProveResult.WIN) 1.0 else 0.0
        resolvedCount++
        // The verdict tokens stay in main.c's spelling (WIN/NOWIN, mate/db/wr) so
        // an app log can be diffed against the desktop's line for line.
        val verdict = if (result == ProveResult.WIN) "WIN" else "NOWIN"
        val by = when (kind) {
            ProveKind.MATE -> "mate"
            ProveKind.DB -> "db"
            else -> "wr"
        }
        log(tr("증명: $verdict $by($note) ${path(i)}", "Prove: $verdict $by($note) ${path(i)}"))
        // Records read out of the database need no re-write.
        if (kind != ProveKind.DB) queueWrite(i)
        if (n.parent >= 0) propagate(n.parent)
    }

    /** Only proven mates are worth a database record (main.c:9343). */
    private fun queueWrite(i: Int) {
        if (nodes[i].kind != ProveKind.MATE) return
        if (writeQueue.size < WQ_MAX) writeQueue += i
    }

    /** `prove_propagate` (main.c:9375). */
    fun propagate(parent: Int) {
        val p = nodes[parent]
        if (p.state == ProveState.RESOLVED) return
        var allWin = true
        var kids = 0
        var weakest = ProveKind.MATE
        var bestValue = -2_000_000
        for (i in children(parent)) {
            val c = nodes[i]
            kids++
            if (c.state != ProveState.RESOLVED) {
                allWin = false
                continue
            }
            if (p.isOr) {
                if (c.result == ProveResult.WIN) { // one winning attack suffices
                    resolve(parent, ProveResult.WIN, c.kind, negamaxUp(c.value), c.recDepth + 1, "via child")
                    return
                }
            } else {
                if (c.result == ProveResult.NOWIN) { // one holding defense refutes
                    resolve(parent, ProveResult.NOWIN, c.kind, negamaxUp(c.value), c.recDepth + 1, "defense holds")
                    return
                }
                if (c.result != ProveResult.WIN) {
                    allWin = false
                    continue
                }
                if (c.kind > weakest) weakest = c.kind
                if (negamaxUp(c.value) > bestValue) bestValue = negamaxUp(c.value)
            }
        }
        if (p.isOr) {
            orWiden(parent) // every candidate failed: try the next alternative
            return
        }
        if (kids > 0 && allWin) {
            if (p.pending.isNotEmpty()) {
                // Optimistic pruning payoff: batch-verify the deferred defenses.
                val deferred = p.pending.toList()
                p.pending.clear()
                deferred.forEach { addChild(parent, it, 0.98, verify = true, pvMate = 0) }
                log(tr("증명: 보류한 방어 ${deferred.size}개 검증", "Prove: verifying ${deferred.size} deferred defences"))
                return
            }
            if (p.incomplete) {
                // Every defence we know of loses — but this node knows it was
                // never told all of them, so "all defenses lose" is not a
                // sentence it is allowed to say. It stays open for a wider
                // search; a single holding defence can still refute it.
                log(tr("증명: 방어 목록이 불완전해 결론 보류 ${path(parent)}", "Prove: defence list incomplete, no conclusion at ${path(parent)}"))
                return
            }
            resolve(parent, ProveResult.WIN, weakest, bestValue, p.recDepth + 1, "all defenses lose")
        }
    }

    // ---- search results -----------------------------------------------------

    /**
     * `prove_on_bestmove`'s bookkeeping (main.c:9756). Only a real mate settles a
     * node: a 99 % win rate is evidence, not proof, so it merely keeps the search
     * going with a bigger budget.
     */
    fun onSearchResult(i: Int, pvs: List<ProvePv>, complete: Boolean = true): ProveStep {
        val n = nodes[i]
        if (pvs.isEmpty()) return onTimeout(i)
        n.retries = 0
        if (!complete) n.incomplete = true
        // The mover's *best* line, not the engine's first, and "best" by the one
        // order in this class — a node judged by a worse line than the mover
        // actually has available says nothing about the node, and a node judged
        // by a *better* one says something false about it.
        var bestIndex = 0
        for (j in 1 until pvs.size) if (before(pvs[j], pvs[bestIndex])) bestIndex = j
        val best = pvs[bestIndex]
        val mate = best.mate
        val value = when {
            mate > 0 -> 30000 - mate
            mate < 0 -> -30000 - mate
            else -> (((best.winRate ?: UNKNOWN_WR) - 0.5) * 2000).toInt()
        }
        // An unknown win rate leaves the estimate where it was rather than
        // rewriting it with a number nobody reported.
        best.winRate?.let { n.wratt = if (n.isOr) it else 1.0 - it }
        if (mate > 0) {
            resolve(
                i, if (n.isOr) ProveResult.WIN else ProveResult.NOWIN,
                ProveKind.MATE, value, best.depth, "search",
            )
            return ProveStep.RESOLVED
        }
        // The mover is being mated in the best line it has. Concluding from that
        // is a claim about *every* move, so it needs the whole set:
        //
        //  - every PV that came back is refuted too. One line that is merely bad
        //    is not a refutation of it, however bad;
        //  - nothing was dropped on the way here (`complete`), because a PV with
        //    no coordinate leaves the "every" satisfied by an empty seat;
        //  - and at an OR node, the losing line must be the engine's own first
        //    choice. In multi-PV, PV k's score is the value of *playing that
        //    move*, not the value of the position; the moment the pick lands
        //    anywhere but PV 0, a third-choice move's loss is about to be
        //    written down as the position's. main.c guarded only AND nodes here,
        //    and this is the shape the false mate took.
        val everyLineRefuted = complete && !n.incomplete && pvs.all { it.mate < 0 }
        val orderAgreesWithEngine = !n.isOr || (bestIndex == 0 && best.winRate != null)
        if (mate < 0 && everyLineRefuted && orderAgreesWithEngine) {
            resolve(
                i, if (n.isOr) ProveResult.NOWIN else ProveResult.WIN,
                ProveKind.MATE, value, best.depth, "search",
            )
            return ProveStep.RESOLVED
        }
        // Unresolved: expand, escalate, requeue (or exhaust). The probe is spent
        // and a mate the creating PV promised did not appear.
        n.probed = true
        n.pvMate = 0
        // Both kinds re-expand. main.c expanded an AND node once and never
        // again, so a first `yxsearchdefend` cut short by its leash froze that
        // node's defence set for the rest of the run — no later, richer search
        // could put the missing defences back. `knownMove` does the deduplication.
        expand(i, pvs)
        if (n.budget >= options.maxBudget) {
            n.state = ProveState.EXHAUSTED
            log(tr("증명: 예산 소진 EXHAUSTED wr_att=${fixed2(n.wratt)} ${path(i)}", "Prove: EXHAUSTED wr_att=${fixed2(n.wratt)} ${path(i)}"))
            orWiden(n.parent)
        } else if (options.byDepth) {
            // +2 plies costs roughly what a time doubling does.
            n.budget = (n.budget + 2).coerceAtMost(options.maxBudget)
            if (n.isOr && n.k < ProveOptions.NBEST_MAX) n.k++
        } else {
            n.budget = (n.budget * 2).coerceAtMost(options.maxBudget)
            if (n.isOr && n.k < ProveOptions.NBEST_MAX) n.k++
        }
        return ProveStep.CONTINUE
    }

    /**
     * Nothing usable came back — a stalled engine or an empty PV set. Three tries,
     * then the node is given up on (main.c:9768 / 9844).
     */
    fun onTimeout(i: Int): ProveStep {
        val n = nodes[i]
        n.retries++
        if (n.retries < RETRY_MAX) return ProveStep.RETRY
        n.state = ProveState.EXHAUSTED
        orWiden(n.parent)
        return ProveStep.CONTINUE
    }

    // ---- write queue --------------------------------------------------------

    val hasWrites: Boolean get() = writeQueue.isNotEmpty()

    /** The desktop pops from the end (`provewq[--provewqn]`). */
    fun popWrite(): Int? = writeQueue.removeLastOrNull()

    // ---- overlay ------------------------------------------------------------

    /** `prove_update_overlay` (main.c:9154). */
    fun overlay(current: Int?, rootLen: Int): ProveOverlay {
        val ghost = LinkedHashMap<Move, Int>()
        val marks = LinkedHashMap<Move, ProveMark>()
        val budgets = LinkedHashMap<Move, Int>()
        var ghostLen = 0
        if (current != null && current in nodes.indices) {
            nodes[current].moves.forEachIndexed { p, move -> ghost[move] = p + 1 }
            ghostLen = nodes[current].moves.size
        }
        for (i in 1 until nodes.size) {
            val c = nodes[i]
            if (c.parent != 0) continue
            val move = c.moves.firstOrNull() ?: continue
            when (c.state) {
                ProveState.RESOLVED ->
                    marks[move] = if (c.result == ProveResult.WIN) ProveMark.WIN else ProveMark.LOSS
                ProveState.EXHAUSTED -> marks[move] = ProveMark.EXH
                ProveState.OPEN -> {
                    marks[move] = ProveMark.OPEN
                    val b = if (options.byDepth) c.budget else c.budget / 1000
                    budgets[move] = b.coerceAtMost(255)
                }
            }
        }
        // Root attacker alternatives still waiting for their turn.
        for (a in root.altNext until root.alt.size) {
            val move = root.alt[a].move
            if (marks[move] == null) marks[move] = ProveMark.LATENT
        }
        return ProveOverlay(
            ghost = ghost, marks = marks, budgets = budgets, ghostLen = ghostLen,
            rootLen = rootLen, byDepth = options.byDepth,
        )
    }

    /** Locale-free `%.2f` — a Korean device must log the same text as the PC. */
    private fun fixed2(v: Double): String = String.format(java.util.Locale.ROOT, "%.2f", v)

    companion object {
        const val MAX_NODES = 4096
        const val MAX_PLIES = 32
        const val MAX_PENDING = 224
        const val WQ_MAX = 64

        /** `PROVE_TREE_PLIES` — no expansion below this depth (main.c:8938). */
        const val TREE_PLIES = 24

        /** Defenses at or below this win rate are deferred (`PROVE_PRUNE_WR`). */
        const val PRUNE_WR = 0.02

        /** At most this many defenses are expanded per AND node. */
        const val DEFEND_KEEP = 16

        /** A defense at or below this gets one early probe (`PROVE_REFUTE_WR`). */
        const val REFUTE_WR = 0.35

        /** Searches of one node before it is given up on. */
        const val RETRY_MAX = 3

        /**
         * Stand-in win rate when the engine reported none. Even odds: it is a
         * prior for scheduling, never evidence — nothing concludes from it.
         */
        const val UNKNOWN_WR = 0.5

        /** …but in a *ranking*, an unknown win rate goes below every known one. */
        private const val UNKNOWN_RANK = -1.0

        private const val TIER_WIN = 0
        private const val TIER_OPEN = 1
        private const val TIER_LOSS = 2
    }
}
