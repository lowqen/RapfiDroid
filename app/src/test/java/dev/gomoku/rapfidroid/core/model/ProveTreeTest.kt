package dev.gomoku.rapfidroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The AND/OR proof search against main.c:8918-9979. Every case here pins a rule
 * that decides either *what the search concludes* or *what gets written to the
 * shared database*, so a drift shows up as a wrong verdict or an inverted mate
 * record rather than as a slow search.
 */
class ProveTreeTest {

    private val size = 15

    private fun tree(
        options: ProveOptions = ProveOptions(),
        log: (String) -> Unit = {},
    ) = ProveTree(options.sanitized(), size, log)

    private fun m(label: String) = Move.fromLabel(label)!!

    private fun pv(label: String, wr: Double?, mate: Int = 0, depth: Int = 10) =
        ProvePv(m(label), wr, mate, depth)

    // ---- shape --------------------------------------------------------------

    @Test
    fun `the root is an attacker node and the sides alternate below it`() {
        val t = tree()
        assertThat(t.root.isOr).isTrue()
        val defense = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        val attack = t.addChild(defense, m("I9"), 0.6, verify = false, pvMate = 0)
        assertThat(t[defense].isOr).isFalse()
        assertThat(t[attack].isOr).isTrue()
        assertThat(t.path(attack)).isEqualTo("h8 i9")
        assertThat(t.path(0)).isEqualTo("(root)")
    }

    @Test
    fun `a fresh node starts on the initial budget, a verification on the cheap one`() {
        val t = tree(ProveOptions(budget0Sec = 5))
        val normal = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        val verify = t.addChild(0, m("I9"), 0.98, verify = true, pvMate = 0)
        assertThat(t[normal].budget).isEqualTo(5000)
        assertThat(t[verify].budget).isEqualTo(ProveOptions.VERIFY_MS)
    }

    @Test
    fun `in depth mode the budget is a depth and verification is capped at 8`() {
        val t = tree(ProveOptions(byDepth = true, depth0 = 12))
        val normal = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        val verify = t.addChild(0, m("I9"), 0.98, verify = true, pvMate = 0)
        assertThat(t[normal].budget).isEqualTo(12)
        assertThat(t[verify].budget).isEqualTo(ProveOptions.VERIFY_DEPTH)
    }

    @Test
    fun `the tree refuses to grow past the ply cap`() {
        val t = tree()
        var parent = 0
        var added = 0
        // Deep enough to hit MAX_PLIES; the moves only have to be distinct.
        for (i in 0 until ProveTree.MAX_PLIES + 4) {
            val child = t.addChild(parent, Move(x = i % size, y = i / size), 0.5, false, 0)
            if (child < 0) break
            parent = child
            added++
        }
        assertThat(added).isEqualTo(ProveTree.MAX_PLIES - 1)
    }

    // ---- cost / scheduling --------------------------------------------------

    @Test
    fun `a mate visible in the creating PV outranks every other node`() {
        val t = tree()
        val plain = t.addChild(0, m("H8"), 0.90, verify = false, pvMate = 0)
        val mating = t.addChild(0, m("I9"), 0.40, verify = false, pvMate = 7)
        assertThat(t.cost(t[mating])).isLessThan(t.cost(t[plain]))
        assertThat(t.pickNext()).isEqualTo(mating)
    }

    @Test
    fun `an easier attack goes first`() {
        val t = tree()
        val easy = t.addChild(0, m("H8"), 0.80, verify = false, pvMate = 0)
        val hard = t.addChild(0, m("I9"), 0.55, verify = false, pvMate = 0)
        assertThat(t.cost(t[easy])).isWithin(1e-9).of(0.22 * 1.15)
        assertThat(t.cost(t[hard])).isGreaterThan(t.cost(t[easy]))
    }

    /**
     * A defense that looks like it holds gets one cheap probe, because a single
     * holding defense refutes its parent and would make every easier proof under
     * it wasted work. Once probed it drops back to the bottom of the list.
     */
    @Test
    fun `a likely-holding defense is probed early, once`() {
        val t = tree(ProveOptions(probe = true))
        val holding = t.addChild(0, m("H8"), 0.20, verify = false, pvMate = 0)
        val probedCost = t.cost(t[holding])
        assertThat(probedCost).isWithin(1e-9).of(0.12 * 1.15)
        t[holding].probed = true
        assertThat(t.cost(t[holding])).isWithin(1e-9).of(0.82 * 1.15)
    }

    @Test
    fun `without the probe option a hopeless-looking defense stays last`() {
        val t = tree(ProveOptions(probe = false))
        val holding = t.addChild(0, m("H8"), 0.20, verify = false, pvMate = 0)
        assertThat(t.cost(t[holding])).isWithin(1e-9).of(0.82 * 1.15)
    }

    @Test
    fun `an escalated budget makes a node proportionally less attractive`() {
        val t = tree()
        val node = t.addChild(0, m("H8"), 0.60, verify = false, pvMate = 0)
        val first = t.cost(t[node])
        t[node].budget *= 4
        assertThat(t.cost(t[node])).isWithin(1e-9).of(first * 4)
    }

    @Test
    fun `verification nodes are cheap so a batch clears quickly`() {
        val t = tree()
        val plain = t.addChild(0, m("H8"), 0.98, verify = false, pvMate = 0)
        val verify = t.addChild(0, m("I9"), 0.98, verify = true, pvMate = 0)
        assertThat(t.cost(t[verify])).isWithin(1e-9).of(t.cost(t[plain]) * 0.3)
    }

    @Test
    fun `nodes under a resolved ancestor are not scheduled`() {
        val t = tree()
        val child = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        val grand = t.addChild(child, m("I9"), 0.6, verify = false, pvMate = 0)
        t.resolve(child, ProveResult.NOWIN, ProveKind.MATE, -100, 3, "search")
        assertThat(t.ancestorResolved(grand)).isTrue()
        assertThat(t.pickNext()).isEqualTo(0) // only the root is left
        assertThat(t.openCount()).isEqualTo(1)
    }

    // ---- expansion ----------------------------------------------------------

    @Test
    fun `an attacker node materializes only its best candidate and keeps the rest`() {
        val t = tree(ProveOptions(bestFirst = true, nbest = 4))
        t.expand(0, listOf(pv("H8", 0.55), pv("I9", 0.72), pv("G7", 0.60)))
        assertThat(t.children(0)).hasSize(1)
        assertThat(t[t.children(0).single()].lastMove).isEqualTo(m("I9"))
        // Latent list keeps the desktop's order: winrate descending.
        assertThat(t.root.alt.map { it.move }).containsExactly(m("G7"), m("H8")).inOrder()
    }

    @Test
    fun `a mate in the candidate PV is expanded before a better winrate`() {
        val t = tree(ProveOptions(bestFirst = true))
        t.expand(0, listOf(pv("H8", 0.95), pv("I9", 0.40, mate = 5), pv("G7", 0.50, mate = 3)))
        assertThat(t[t.children(0).single()].lastMove).isEqualTo(m("G7"))
        assertThat(t.root.alt.map { it.move }).containsExactly(m("I9"), m("H8")).inOrder()
    }

    @Test
    fun `without best-first every candidate becomes a node`() {
        val t = tree(ProveOptions(bestFirst = false, nbest = 4))
        t.expand(0, listOf(pv("H8", 0.55), pv("I9", 0.72), pv("G7", 0.60)))
        assertThat(t.children(0)).hasSize(3)
        assertThat(t.root.alt).isEmpty()
    }

    @Test
    fun `only the first k candidates are considered`() {
        val t = tree(ProveOptions(bestFirst = false, nbest = 2))
        t.expand(0, listOf(pv("H8", 0.55), pv("I9", 0.72), pv("G7", 0.99)))
        assertThat(t.children(0).map { t[it].lastMove }).containsExactly(m("I9"), m("H8"))
    }

    @Test
    fun `re-expanding an attacker node does not duplicate what it already knows`() {
        val t = tree(ProveOptions(bestFirst = true))
        t.expand(0, listOf(pv("H8", 0.55), pv("I9", 0.72)))
        t.expand(0, listOf(pv("I9", 0.74), pv("H8", 0.56), pv("G7", 0.50)))
        assertThat(t.children(0)).hasSize(1)
        assertThat(t.root.alt.map { it.move }).containsExactly(m("H8"), m("G7")).inOrder()
    }

    @Test
    fun `a defender node expands viable defenses and defers the hopeless ones`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.expand(
            and,
            listOf(pv("I9", 0.30), pv("G7", 0.001), pv("J10", 0.45), pv("F6", 0.0)),
        )
        // Ordered by defender winrate, so the toughest defense is the first child.
        assertThat(t.children(and).map { t[it].lastMove })
            .containsExactly(m("J10"), m("I9")).inOrder()
        assertThat(t[and].pending).containsExactly(m("G7"), m("F6")).inOrder()
        // The attacker's estimate at a defense is the defender's complement.
        assertThat(t[t.children(and).first()].wratt).isWithin(1e-9).of(0.55)
    }

    @Test
    fun `a defender mate in the PV becomes the attacker mate of that branch`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.expand(and, listOf(pv("I9", 0.30, mate = -4)))
        assertThat(t[t.children(and).single()].pvMate).isEqualTo(4)
    }

    @Test
    fun `a defender node with nothing viable verifies its deferred moves at once`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.expand(and, listOf(pv("I9", 0.0), pv("G7", 0.01)))
        assertThat(t[and].pending).isEmpty()
        assertThat(t.children(and).map { t[it].verify }).containsExactly(true, true)
    }

    @Test
    fun `at most sixteen defenses are expanded, the rest wait`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        val pvs = (0 until 20).map { ProvePv(Move(x = it % 15, y = 3 + it / 15), 0.5) }
        t.expand(and, pvs)
        assertThat(t.children(and)).hasSize(ProveTree.DEFEND_KEEP)
        assertThat(t[and].pending).hasSize(4)
    }

    // ---- propagation --------------------------------------------------------

    @Test
    fun `one winning attack resolves the attacker node`() {
        val t = tree()
        val a = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        // A WIN at a defender node means the defender is mated, so its own value
        // is negative (`-30000 - mate`, main.c:9781): mated in 4.
        t.resolve(a, ProveResult.WIN, ProveKind.MATE, -29996, 5, "search")
        assertThat(t.root.state).isEqualTo(ProveState.RESOLVED)
        assertThat(t.root.result).isEqualTo(ProveResult.WIN)
        // The root's own view is a win, one ply farther out.
        assertThat(t.root.value).isEqualTo(29995)
        assertThat(t.root.recDepth).isEqualTo(6)
    }

    @Test
    fun `one holding defense refutes the defender node above it`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        val d1 = t.addChild(and, m("I9"), 0.4, verify = false, pvMate = 0)
        val d2 = t.addChild(and, m("G7"), 0.4, verify = false, pvMate = 0)
        t.resolve(d1, ProveResult.WIN, ProveKind.MATE, 29990, 4, "search")
        assertThat(t[and].state).isEqualTo(ProveState.OPEN) // d2 still unknown
        t.resolve(d2, ProveResult.NOWIN, ProveKind.MATE, -20, 4, "search")
        assertThat(t[and].result).isEqualTo(ProveResult.NOWIN)
    }

    @Test
    fun `a defender node whose every defense loses is a win, at its weakest link`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        val d1 = t.addChild(and, m("I9"), 0.4, verify = false, pvMate = 0)
        val d2 = t.addChild(and, m("G7"), 0.4, verify = false, pvMate = 0)
        t.resolve(d1, ProveResult.WIN, ProveKind.MATE, 29990, 4, "search")
        t.resolve(d2, ProveResult.WIN, ProveKind.DB, 29800, 6, "db")
        assertThat(t[and].result).isEqualTo(ProveResult.WIN)
        // "mate < db < wr": the proof is only as strong as its weakest child.
        assertThat(t[and].kind).isEqualTo(ProveKind.DB)
        assertThat(t[and].value).isEqualTo(t.negamaxUp(29800))
    }

    @Test
    fun `all defenses losing triggers the deferred batch before any conclusion`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.expand(and, listOf(pv("I9", 0.30), pv("G7", 0.001)))
        val defense = t.children(and).single()
        t.resolve(defense, ProveResult.WIN, ProveKind.MATE, 29990, 4, "search")
        // Not resolved yet: the pruned defense has to be verified first.
        assertThat(t[and].state).isEqualTo(ProveState.OPEN)
        assertThat(t[and].pending).isEmpty()
        val verifier = t.children(and).last()
        assertThat(t[verifier].verify).isTrue()
        t.resolve(verifier, ProveResult.WIN, ProveKind.MATE, 29990, 4, "search")
        assertThat(t[and].result).isEqualTo(ProveResult.WIN)
    }

    @Test
    fun `a failed attack candidate makes the next alternative appear`() {
        val t = tree(ProveOptions(bestFirst = true))
        t.expand(0, listOf(pv("I9", 0.72), pv("H8", 0.55)))
        val first = t.children(0).single()
        t.resolve(first, ProveResult.NOWIN, ProveKind.MATE, -30, 4, "search")
        assertThat(t.children(0).map { t[it].lastMove })
            .containsExactly(m("I9"), m("H8")).inOrder()
        assertThat(t.root.altNext).isEqualTo(1)
    }

    @Test
    fun `widening waits while another candidate of the same node is still open`() {
        val t = tree(ProveOptions(bestFirst = false, nbest = 3))
        t.expand(0, listOf(pv("I9", 0.72), pv("H8", 0.55)))
        t.root.alt += ProvePv(m("G7"), 0.5)
        t.resolve(t.children(0).first(), ProveResult.NOWIN, ProveKind.MATE, -30, 4, "search")
        assertThat(t.root.altNext).isEqualTo(0)
        t.resolve(t.children(0)[1], ProveResult.NOWIN, ProveKind.MATE, -30, 4, "search")
        assertThat(t.root.altNext).isEqualTo(1)
    }

    // ---- database writes ----------------------------------------------------

    @Test
    fun `only proven mates are queued for a database record`() {
        val t = tree()
        val a = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.resolve(a, ProveResult.WIN, ProveKind.WR, 900, 4, "wr")
        assertThat(t.hasWrites).isFalse()
        val b = t.addChild(0, m("I9"), 0.6, verify = false, pvMate = 0)
        t.resolve(b, ProveResult.WIN, ProveKind.MATE, 29990, 4, "search")
        assertThat(t.popWrite()).isEqualTo(b)
    }

    @Test
    fun `a conclusion read out of the database is not written back`() {
        val t = tree()
        val a = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.resolve(a, ProveResult.WIN, ProveKind.DB, 29990, 4, "db")
        assertThat(t.hasWrites).isFalse()
    }

    // ---- search results -----------------------------------------------------

    @Test
    fun `a mate found at an attacker node proves the win`() {
        val t = tree()
        val step = t.onSearchResult(0, listOf(pv("H8", 0.99, mate = 4, depth = 21)))
        assertThat(step).isEqualTo(ProveStep.RESOLVED)
        assertThat(t.root.result).isEqualTo(ProveResult.WIN)
        assertThat(t.root.kind).isEqualTo(ProveKind.MATE)
        assertThat(t.root.value).isEqualTo(30000 - 4)
        assertThat(t.root.recDepth).isEqualTo(21)
    }

    @Test
    fun `the same mate at a defender node refutes the attack instead`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.onSearchResult(and, listOf(pv("I9", 0.99, mate = 4, depth = 18)))
        assertThat(t[and].result).isEqualTo(ProveResult.NOWIN)
        assertThat(t.root.state).isEqualTo(ProveState.OPEN) // no candidates left to try
    }

    /**
     * The defect that filled the shared database with mates that were not mates:
     * a defender node was settled by whichever line the engine happened to put
     * first, so one refuted defense "proved" the attack while other defenses were
     * still holding.
     */
    @Test
    fun `one refuted defense does not prove the attack`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        val step = t.onSearchResult(
            and,
            listOf(
                pv("I9", 0.0, mate = -5, depth = 20),  // this defense is lost…
                pv("G7", 0.42, mate = 0, depth = 20),  // …but this one holds
            ),
        )
        assertThat(step).isNotEqualTo(ProveStep.RESOLVED)
        assertThat(t[and].state).isEqualTo(ProveState.OPEN)
        assertThat(t[and].result).isEqualTo(ProveResult.NONE)
    }

    @Test
    fun `every defense refuted does prove the attack`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        val step = t.onSearchResult(
            and,
            listOf(
                pv("I9", 0.0, mate = -5, depth = 20),
                pv("G7", 0.0, mate = -7, depth = 20),
            ),
        )
        assertThat(step).isEqualTo(ProveStep.RESOLVED)
        assertThat(t[and].result).isEqualTo(ProveResult.WIN)
        assertThat(t[and].kind).isEqualTo(ProveKind.MATE)
    }

    /** The node is judged by the mover's best line, not by the engine's first. */
    @Test
    fun `the best line decides, whatever order the engine sent`() {
        val t = tree()
        val step = t.onSearchResult(
            0,
            listOf(
                pv("G7", 0.10, mate = 0, depth = 9),   // engine's first, and worse
                pv("H8", 0.99, mate = 3, depth = 21),  // the mover's actual best
            ),
        )
        assertThat(step).isEqualTo(ProveStep.RESOLVED)
        assertThat(t.root.result).isEqualTo(ProveResult.WIN)
        assertThat(t.root.value).isEqualTo(30000 - 3)
        assertThat(t.root.recDepth).isEqualTo(21)
    }

    @Test
    fun `getting mated at an attacker node ends that branch`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        val or = t.addChild(and, m("I9"), 0.6, verify = false, pvMate = 0)
        t.onSearchResult(or, listOf(pv("G7", 0.01, mate = -6, depth = 12)))
        assertThat(t[or].result).isEqualTo(ProveResult.NOWIN)
        assertThat(t[or].value).isEqualTo(-30000 + 6)
        // One holding defense is enough: the defender node above is refuted too.
        assertThat(t[and].result).isEqualTo(ProveResult.NOWIN)
    }

    @Test
    fun `a high winrate is evidence, not proof, so the budget escalates`() {
        val t = tree(ProveOptions(budget0Sec = 5, budgetMaxSec = 320, nbest = 4))
        val step = t.onSearchResult(0, listOf(pv("H8", 0.97), pv("I9", 0.60)))
        assertThat(step).isEqualTo(ProveStep.CONTINUE)
        assertThat(t.root.state).isEqualTo(ProveState.OPEN)
        assertThat(t.root.budget).isEqualTo(10000)
        assertThat(t.root.k).isEqualTo(5)
        assertThat(t.root.probed).isTrue()
        assertThat(t.root.wratt).isWithin(1e-9).of(0.97)
        assertThat(t.children(0)).hasSize(1) // it expanded on the way
    }

    @Test
    fun `a promised mate that fails to appear loses its priority`() {
        val t = tree()
        val a = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 5)
        t.onSearchResult(a, listOf(pv("I9", 0.60)))
        assertThat(t[a].pvMate).isEqualTo(0)
    }

    @Test
    fun `in depth mode the escalation adds two plies`() {
        val t = tree(ProveOptions(byDepth = true, depth0 = 12, depthMax = 30))
        t.onSearchResult(0, listOf(pv("H8", 0.80)))
        assertThat(t.root.budget).isEqualTo(14)
    }

    @Test
    fun `a node at its budget cap is given up on and the next candidate tried`() {
        val t = tree(ProveOptions(budget0Sec = 5, budgetMaxSec = 5, bestFirst = true))
        t.expand(0, listOf(pv("I9", 0.72), pv("H8", 0.55)))
        val first = t.children(0).single()
        val step = t.onSearchResult(first, listOf(pv("G7", 0.40)))
        assertThat(step).isEqualTo(ProveStep.CONTINUE)
        assertThat(t[first].state).isEqualTo(ProveState.EXHAUSTED)
        assertThat(t.children(0).map { t[it].lastMove })
            .containsExactly(m("I9"), m("H8")).inOrder()
    }

    @Test
    fun `an empty answer is retried three times, then the node is abandoned`() {
        val t = tree()
        val a = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        assertThat(t.onSearchResult(a, emptyList())).isEqualTo(ProveStep.RETRY)
        assertThat(t.onTimeout(a)).isEqualTo(ProveStep.RETRY)
        assertThat(t.onTimeout(a)).isEqualTo(ProveStep.CONTINUE)
        assertThat(t[a].state).isEqualTo(ProveState.EXHAUSTED)
    }

    @Test
    fun `a usable answer clears the retry count`() {
        val t = tree()
        val a = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.onTimeout(a)
        t.onSearchResult(a, listOf(pv("I9", 0.5)))
        assertThat(t[a].retries).isEqualTo(0)
    }

    // ---- the false mate -----------------------------------------------------
    //
    // Every case below is a way the search used to answer "this position is lost
    // in N" about a position that was not lost at all. They share one mistake in
    // three disguises: treating the PVs that came back as the position's whole
    // set of moves, and treating a value the engine did not send as a value of
    // zero.

    /**
     * The report this whole section came from, in its smallest form: a position
     * whose moves are worth 10 %, mate-in-20 and mate-in-40 is a position worth
     * 10 %. It came out as `M40` — the longest of the losses, because among
     * losses the longest is the best one — because a mate found in PV k was read
     * as the value of the position rather than of the move that PV plays.
     */
    @Test
    fun `an attacker node is not lost because some of its candidates are`() {
        val t = tree()
        val step = t.onSearchResult(
            0,
            listOf(
                pv("H8", 0.10, mate = 0, depth = 20),   // still alive
                pv("I9", 0.0, mate = -20, depth = 20),
                pv("G7", 0.0, mate = -40, depth = 20),
            ),
        )
        assertThat(step).isNotEqualTo(ProveStep.RESOLVED)
        assertThat(t.root.state).isEqualTo(ProveState.OPEN)
        assertThat(t.root.result).isEqualTo(ProveResult.NONE)
        assertThat(t.hasWrites).isFalse()
    }

    /**
     * The same position as it actually arrived: with the refuted lines sent
     * first and no `INFO WINRATE` on any of them. Recorded as 0 % all three tied
     * at the bottom, the pick fell back to the order the engine happened to
     * send, and the position was written into the shared database as
     * mate-in-40 — with a move worth 10 % sitting right there.
     */
    @Test
    fun `a position with no win rates is not decided by the order the engine sent`() {
        val t = tree()
        val step = t.onSearchResult(
            0,
            listOf(
                pv("G7", null, mate = -40, depth = 20),
                pv("I9", null, mate = -20, depth = 20),
                pv("H8", null, mate = 0, depth = 20), // still alive, and sent last
            ),
        )
        assertThat(step).isNotEqualTo(ProveStep.RESOLVED)
        assertThat(t.root.result).isEqualTo(ProveResult.NONE)
        assertThat(t.hasWrites).isFalse()
    }

    /**
     * And a node whose first line has no win rate at all cannot be concluded
     * either: the one number that would let us check the engine's order against
     * ours is the one number that is missing.
     */
    @Test
    fun `an attacker node with no win rate on its first line does not conclude`() {
        val t = tree()
        val step = t.onSearchResult(
            0,
            listOf(pv("H8", null, mate = -40), pv("I9", null, mate = -5)),
        )
        assertThat(step).isNotEqualTo(ProveStep.RESOLVED)
        assertThat(t.root.result).isEqualTo(ProveResult.NONE)
    }

    /**
     * Even when every line loses, the loss has to be the engine's own first
     * choice. If our order says PV 2 survives longest while the engine put PV 0
     * first, the two disagree about the position and neither may be written down
     * as its value.
     */
    @Test
    fun `an attacker node refuses a loss its own first line does not agree with`() {
        val t = tree()
        val step = t.onSearchResult(
            0,
            listOf(
                pv("H8", 0.0, mate = -5, depth = 20),
                pv("I9", 0.0, mate = -40, depth = 20), // survives far longer
            ),
        )
        assertThat(step).isNotEqualTo(ProveStep.RESOLVED)
        assertThat(t.root.result).isEqualTo(ProveResult.NONE)
    }

    /** With the engine's order and ours agreeing, the loss is accepted. */
    @Test
    fun `an attacker node whose every line loses is refuted`() {
        val t = tree()
        val step = t.onSearchResult(
            0,
            listOf(
                pv("H8", 0.0, mate = -40, depth = 20),
                pv("I9", 0.0, mate = -5, depth = 20),
            ),
        )
        assertThat(step).isEqualTo(ProveStep.RESOLVED)
        assertThat(t.root.result).isEqualTo(ProveResult.NOWIN)
        assertThat(t.root.value).isEqualTo(-30000 + 40)
    }

    /**
     * A PV block the engine sent and we could not place is a move we know
     * exists and cannot name. Dropping it silently leaves "every defence loses"
     * satisfied by an empty seat.
     */
    @Test
    fun `an unreadable PV blocks the conclusion`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        val step = t.onSearchResult(
            and,
            listOf(pv("I9", 0.0, mate = -5), pv("G7", 0.0, mate = -7)),
            complete = false,
        )
        assertThat(step).isNotEqualTo(ProveStep.RESOLVED)
        assertThat(t[and].result).isEqualTo(ProveResult.NONE)
        assertThat(t[and].incomplete).isTrue()
    }

    /** And it stays blocked afterwards, however the children settle. */
    @Test
    fun `a node that lost a defence is never proven by propagation`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.onSearchResult(and, listOf(pv("I9", 0.30), pv("G7", 0.40)), complete = false)
        t.children(and).forEach {
            t.resolve(it, ProveResult.WIN, ProveKind.MATE, 29990, 4, "search")
        }
        assertThat(t[and].state).isEqualTo(ProveState.OPEN)
        assertThat(t[and].result).isEqualTo(ProveResult.NONE)
    }

    /**
     * Defences past the deferred cap used to be dropped without a word, and the
     * node then reasoned about "every defence" over a set it had thrown part of
     * away. It takes a board bigger than 15×15 for the caps to be reachable at
     * all, which is exactly why nothing noticed.
     */
    @Test
    fun `a node that could not hold every defence marks itself incomplete`() {
        val big = ProveTree(ProveOptions().sanitized(), size = 20)
        val and = big.addChild(0, Move(0, 0), 0.6, verify = false, pvMate = 0)
        val total = ProveTree.DEFEND_KEEP + ProveTree.MAX_PENDING + 5
        val pvs = (0 until total).map { ProvePv(Move(x = it % 20, y = 1 + it / 20), 0.5) }
        big.expand(and, pvs)
        assertThat(big[and].incomplete).isTrue()
        assertThat(big.children(and)).hasSize(ProveTree.DEFEND_KEEP)
        assertThat(big[and].pending).hasSize(ProveTree.MAX_PENDING)
    }

    /**
     * A defender node used to be expanded once and never again, so a first
     * `yxsearchdefend` cut short by its leash froze that node's defence set for
     * the rest of the run — no later search could put the missing ones back.
     */
    @Test
    fun `a defender node takes in defences a later search finds`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.onSearchResult(and, listOf(pv("I9", 0.30), pv("G7", 0.40)))
        assertThat(t.children(and)).hasSize(2)
        // The same two, plus one the first (leashed) search never reported.
        t.onSearchResult(and, listOf(pv("I9", 0.30), pv("G7", 0.40), pv("J10", 0.35)))
        assertThat(t.children(and).map { t[it].lastMove })
            .containsExactly(m("I9"), m("G7"), m("J10"))
    }

    @Test
    fun `re-expanding a defender node does not duplicate what it already knows`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.expand(and, listOf(pv("I9", 0.30), pv("G7", 0.001)))
        val before = t.children(and).size + t[and].pending.size
        t.expand(and, listOf(pv("I9", 0.31), pv("G7", 0.002)))
        assertThat(t.children(and).size + t[and].pending.size).isEqualTo(before)
    }

    /**
     * "No win rate" is not "0 %". Stored as zero it is not merely a lost value
     * but a false one, and a false one that is worse than anything real: several
     * such blocks tie at the bottom and the pick falls back to whatever order
     * the engine happened to send.
     */
    @Test
    fun `a defence with no win rate is expanded, not written off as hopeless`() {
        val t = tree()
        val and = t.addChild(0, m("H8"), 0.6, verify = false, pvMate = 0)
        t.expand(and, listOf(pv("I9", null), pv("G7", 0.001)))
        assertThat(t.children(and).map { t[it].lastMove }).containsExactly(m("I9"))
        assertThat(t[and].pending).containsExactly(m("G7"))
    }

    @Test
    fun `an unknown win rate never outranks a known one`() {
        val t = tree(ProveOptions(bestFirst = true))
        t.expand(0, listOf(pv("H8", null), pv("I9", 0.10)))
        assertThat(t[t.children(0).single()].lastMove).isEqualTo(m("I9"))
    }

    @Test
    fun `an unknown win rate never becomes the node's estimate`() {
        val t = tree()
        val a = t.addChild(0, m("H8"), 0.62, verify = false, pvMate = 0)
        t.onSearchResult(a, listOf(pv("I9", null), pv("G7", null)))
        assertThat(t[a].wratt).isWithin(1e-9).of(0.62)
    }

    /**
     * Mates and non-mates go on one scale, in both directions: a mate for the
     * mover beats any percentage, and any percentage beats being mated.
     */
    @Test
    fun `mates and percentages are ranked against each other`() {
        val t = tree(ProveOptions(bestFirst = false, nbest = 4))
        t.expand(
            0,
            listOf(
                pv("H8", 0.01, mate = -3),  // worst: mated soon
                pv("I9", 0.99, mate = 0),   // good, but only a number
                pv("G7", 0.20, mate = 5),   // best: a mate is a fact
                pv("F6", 0.01, mate = -30), // bad, but lasts
            ),
        )
        assertThat(t.children(0).map { t[it].lastMove })
            .containsExactly(m("G7"), m("I9"), m("F6"), m("H8")).inOrder()
    }

    // ---- overlay ------------------------------------------------------------

    @Test
    fun `the overlay shows the searched line and the state of every root candidate`() {
        val t = tree(ProveOptions(bestFirst = true, budget0Sec = 7))
        t.expand(0, listOf(pv("H8", 0.72), pv("I9", 0.60), pv("G7", 0.50)))
        val open = t.children(0).single()
        val defense = t.addChild(open, m("J10"), 0.4, verify = false, pvMate = 0)
        val overlay = t.overlay(defense, rootLen = 3)

        assertThat(overlay.ghost).containsExactly(m("H8"), 1, m("J10"), 2)
        assertThat(overlay.ghostLen).isEqualTo(2)
        assertThat(overlay.isAttack(1)).isTrue()
        assertThat(overlay.isAttack(2)).isFalse()
        // Ply 1 lands on move 4 of the game, so it is white.
        assertThat(overlay.isBlack(1)).isFalse()
        assertThat(overlay.marks[m("H8")]).isEqualTo(ProveMark.OPEN)
        assertThat(overlay.budgetLabel(m("H8"))).isEqualTo("7초")
        // Latent alternatives are marked as waiting.
        assertThat(overlay.marks[m("I9")]).isEqualTo(ProveMark.LATENT)
        assertThat(overlay.marks[m("G7")]).isEqualTo(ProveMark.LATENT)
    }

    @Test
    fun `resolved and exhausted candidates get their own markers`() {
        val t = tree(ProveOptions(bestFirst = false, nbest = 3))
        t.expand(0, listOf(pv("H8", 0.7), pv("I9", 0.6), pv("G7", 0.5)))
        val (a, b, c) = t.children(0)
        t.resolve(b, ProveResult.NOWIN, ProveKind.MATE, -30, 3, "search")
        t[c].state = ProveState.EXHAUSTED
        val overlay = t.overlay(null, rootLen = 0)
        assertThat(overlay.marks[t[a].lastMove]).isEqualTo(ProveMark.OPEN)
        assertThat(overlay.marks[t[b].lastMove]).isEqualTo(ProveMark.LOSS)
        assertThat(overlay.marks[t[c].lastMove]).isEqualTo(ProveMark.EXH)
        assertThat(overlay.ghost).isEmpty()
    }

    @Test
    fun `a depth budget is labelled as a depth`() {
        val t = tree(ProveOptions(byDepth = true, depth0 = 12))
        t.expand(0, listOf(pv("H8", 0.7)))
        val overlay = t.overlay(null, rootLen = 0)
        assertThat(overlay.budgetLabel(m("H8"))).isEqualTo("d12")
    }

    // ---- values / labels ----------------------------------------------------

    @Test
    fun `negamax moves a mate one ply farther from the winner`() {
        val t = tree()
        assertThat(t.negamaxUp(29996)).isEqualTo(-29995)
        assertThat(t.negamaxUp(-29996)).isEqualTo(29995)
        assertThat(t.negamaxUp(120)).isEqualTo(-120)
    }

    @Test
    fun `the badge line reads like the desktop's`() {
        val running = ProveProgress(
            running = true, phase = ProvePhase.SEARCH, resolved = 3, searches = 11, open = 4,
            attack = true, budget = 20000, depth = 17, winRatePct = 88, elapsedSec = 6,
            candIndex = 2, candTotal = 4,
        )
        val (first, second) = running.badgeLines()
        assertThat(first).isEqualTo("증명: 해결 3 / 탐색 11 / 미결 4")
        assertThat(second).isEqualTo("공격  d17 88%  6s/20s  후보 2/4")
        // A mate replaces the winrate, and depth mode shows the depth target.
        val byDepth = running.copy(byDepth = true, budget = 24, mate = -9, attack = false)
        assertThat(byDepth.badgeLines().second).startsWith("방어  d17/24 M9  6s")
        // Nothing to show while no search runs.
        assertThat(running.copy(phase = ProvePhase.QUERY).badgeLines().second).isEmpty()
    }

    @Test
    fun `the options dialog clamps the caps above the starting values`() {
        val fixed = ProveOptions(
            budget0Sec = 900, budgetMaxSec = 1, depth0 = 200, depthMax = 2, nbest = 99,
        ).sanitized()
        assertThat(fixed.budget0Sec).isEqualTo(600)
        assertThat(fixed.budgetMaxSec).isEqualTo(600)
        assertThat(fixed.depth0).isEqualTo(64)
        assertThat(fixed.depthMax).isEqualTo(64)
        assertThat(fixed.nbest).isEqualTo(ProveOptions.NBEST_MAX)
    }

    @Test
    fun `the outcome message names the side, the verdict and the source`() {
        val proven = ProveOutcome(
            cancelled = false, resolved = true, win = true, kind = ProveKind.MATE,
            blackToMove = true, searches = 12, conclusions = 5, dbWrites = 3,
            attackerWinRatePct = 100,
        )
        assertThat(proven.message)
            .isEqualTo("흑 (차례): 승리 증명됨 (메이트)\n탐색 12회, 결론 5개, DB 기록 3개")
        val refuted = proven.copy(win = false, kind = ProveKind.DB, blackToMove = false)
        assertThat(refuted.message).startsWith("백 (차례): 승리 불가 (방어 성립/무승부) (데이터베이스)")
        val open = proven.copy(resolved = false, attackerWinRatePct = 71)
        assertThat(open.message).startsWith("미결 (예산 소진), 공격측 승률 ~71%")
    }
}
