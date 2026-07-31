package dev.gomoku.yixindroid.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The move-order core, checked the way `Yixin-Board/tests/test_moveorder.c`
 * checks the original: **path counts cannot be eyeballed**, so every number is
 * cross-checked against a brute-force permutation walk that re-applies the
 * rules independently, plus the few closed forms we can derive by hand
 * (개발_핸드북.md §8.4).
 *
 * The brute force below is deliberately a second implementation — it replays a
 * concrete order and validates it from scratch — so a shared misreading of the
 * rules cannot pass both.
 */
class MoveOrderTest {

    private val size = 15

    private fun cell(label: String): Int {
        val m = Move.fromLabel(label, size)!!
        return m.y * size + m.x
    }

    private fun cells(vararg labels: String) = labels.map { cell(it) }

    // ---- independent brute force -------------------------------------------

    private fun orderLegal(
        order: List<Int>,
        rule: Int,
        openingRule: Boolean,
        noFive: Boolean,
        move2Fix: Boolean,
    ): Boolean {
        val occ = IntArray(size * size)
        val ctr = size / 2
        val dy = intArrayOf(1, 0, 1, 1)
        val dx = intArrayOf(0, 1, 1, -1)
        for (p in order.indices) {
            val y = order[p] / size
            val x = order[p] % size
            val col = if (p % 2 == 0) 1 else 2
            if (openingRule && p <= 3 && size % 2 == 1) {
                if (move2Fix && p == 1) {
                    val h9 = (size / 2 - 1) * size + size / 2
                    if (order[p] != h9 && order[p] != h9 + 1) return false
                } else {
                    val ady = if (y > ctr) y - ctr else ctr - y
                    val adx = if (x > ctr) x - ctr else ctr - x
                    if (maxOf(ady, adx) > p) return false
                }
            }
            occ[order[p]] = col
            if (noFive && p + 1 < order.size) {
                for (d in 0 until 4) {
                    var k = 1
                    var ny = y
                    var nx = x
                    for (j in 1 until 6) {
                        ny += dy[d]; nx += dx[d]
                        if (nx < 0 || ny < 0 || nx >= size || ny >= size) break
                        if (occ[ny * size + nx] != col) break
                        k++
                    }
                    ny = y; nx = x
                    for (j in 1 until 6) {
                        ny -= dy[d]; nx -= dx[d]
                        if (nx < 0 || ny < 0 || nx >= size || ny >= size) break
                        if (occ[ny * size + nx] != col) break
                        k++
                    }
                    if (k == 5 || (k > 5 && rule != 1)) return false
                }
            }
        }
        return true
    }

    /** Every interleaving of the two colours' permutations, optionally also
     *  marking which ply each cell was played on. */
    private fun bruteCount(
        cells: List<Int>,
        rule: Int = 2,
        openingRule: Boolean = true,
        noFive: Boolean = true,
        move2Fix: Boolean = false,
        plies: MutableMap<Int, Long>? = null,
    ): Double {
        val black = cells.filterIndexed { i, _ -> i % 2 == 0 }
        val white = cells.filterIndexed { i, _ -> i % 2 == 1 }
        val bUsed = BooleanArray(black.size)
        val wUsed = BooleanArray(white.size)
        val order = IntArray(cells.size)
        var total = 0.0

        fun rec(p: Int) {
            if (p == cells.size) {
                if (!orderLegal(order.toList(), rule, openingRule, noFive, move2Fix)) return
                total += 1.0
                plies?.let { m ->
                    for (q in order.indices) m[order[q]] = (m[order[q]] ?: 0L) or (1L shl q)
                }
                return
            }
            if (p % 2 == 0) {
                for (i in black.indices) {
                    if (bUsed[i]) continue
                    bUsed[i] = true; order[p] = black[i]
                    rec(p + 1)
                    bUsed[i] = false
                }
            } else {
                for (i in white.indices) {
                    if (wUsed[i]) continue
                    wUsed[i] = true; order[p] = white[i]
                    rec(p + 1)
                    wUsed[i] = false
                }
            }
        }
        rec(0)
        return total
    }

    /** Independent symmetry reference: transform with our own formulas, dedupe
     *  identical positions, sum the per-variant brute counts. */
    private fun bruteVariantCount(
        cells: List<Int>,
        openingRule: Boolean = true,
        noFive: Boolean = true,
        move2Fix: Boolean = true,
    ): Pair<Double, Int> {
        val kept = ArrayList<List<Int>>()
        var total = 0.0
        for (t in 0 until 8) {
            val tc = cells.map { MoveOrderSet.xform(t, size, it) }
            val key = tc.mapIndexed { i, c -> c * 2 + (i % 2) }.sorted()
            if (kept.any { it == key }) continue
            kept.add(key)
            total += bruteCount(tc, 2, openingRule, noFive, move2Fix)
        }
        return total to kept.size
    }

    private fun set(
        cells: List<Int>,
        openingRule: Boolean = true,
        noFive: Boolean = true,
        move2Fix: Boolean = true,
        withSymmetry: Boolean = true,
        maxNodes: Long = MO_GUI_NODES,
    ) = MoveOrderSet.create(
        cells, size, 2, openingRule, noFive, move2Fix, withSymmetry, maxNodes,
    )!!

    // ---- counts vs brute force ---------------------------------------------

    @Test
    fun withoutRulesTheCountIsExactlyBlackFactorialTimesWhiteFactorial() {
        val pts = cells("a1", "c3", "e5", "g7", "a3", "c5", "e7", "g9")
        val s = set(pts, openingRule = false, move2Fix = false, withSymmetry = false)
        assertThat(s.total).isEqualTo(24.0 * 24.0)   // 4! * 4!
        assertThat(s.total).isEqualTo(bruteCount(pts, openingRule = false))
    }

    @Test
    fun theOpeningRuleCollapsesAStandardStartToOneOrder() {
        // h8 i9 j10 k11: move 1 must be tengen and move 2 inside the 3x3, which
        // fixes both colours' order — 4 free orders become exactly 1.
        val pts = cells("h8", "i9", "j10", "k11")
        assertThat(set(pts, withSymmetry = false).total).isEqualTo(1.0)
        assertThat(
            set(pts, openingRule = false, move2Fix = false, withSymmetry = false).total,
        ).isEqualTo(4.0)
    }

    @Test
    fun moveThreeOutsideTheFiveByFiveBoxIsRejected() {
        // distance 3 from tengen at ply 2 fails, distance 2 passes
        assertThat(set(cells("h8", "i9", "k11"), withSymmetry = false).total).isEqualTo(0.0)
        assertThat(set(cells("h8", "i9", "j10"), withSymmetry = false).total).isEqualTo(1.0)
    }

    @Test
    fun moveFourOutsideTheSevenBySevenBoxIsRejected() {
        assertThat(set(cells("h8", "i9", "j10", "l12"), withSymmetry = false).total)
            .isEqualTo(0.0)
        assertThat(set(cells("h8", "i9", "j10", "k11"), withSymmetry = false).total)
            .isEqualTo(1.0)
    }

    @Test
    fun moveFiveIsUnconstrainedByTheOpeningRule() {
        val pts = cells("h8", "i9", "j10", "k11", "a1")
        assertThat(set(pts, withSymmetry = false).total).isGreaterThan(0.0)
    }

    @Test
    fun theCanonicalMoveTwoRuleRejectsOtherRingCells() {
        // g9 sits in the 3x3 ring but is neither H9 nor I9
        assertThat(set(cells("h8", "g9", "j10"), move2Fix = true, withSymmetry = false).total)
            .isEqualTo(0.0)
        assertThat(set(cells("h8", "g9", "j10"), move2Fix = false, withSymmetry = false).total)
            .isGreaterThan(0.0)
    }

    @Test
    fun theFiveRulePrunesOrders() {
        // a black five on the board: without the rule every order counts, with
        // it the completing stone is pinned to the end
        val pts = cells("h8", "a1", "h9", "a2", "h10", "a3", "h11", "a4", "h12", "a5")
        val with = set(pts, openingRule = false, move2Fix = false, withSymmetry = false).total
        val without = set(
            pts, openingRule = false, noFive = false, move2Fix = false, withSymmetry = false,
        ).total
        assertThat(with).isLessThan(without)
        assertThat(with).isEqualTo(bruteCount(pts, openingRule = false))
    }

    @Test
    fun countsMatchBruteForceOnAScatteredPosition() {
        val pts = cells("h8", "i9", "j10", "k11", "g7", "f6", "e5", "l12")
        val s = set(pts, withSymmetry = false)
        assertThat(s.total).isEqualTo(bruteCount(pts, move2Fix = true))
    }

    // ---- children / drilling -----------------------------------------------

    @Test
    fun theOpeningRuleLeavesOneRootChild() {
        val s = set(cells("h8", "i9", "j10", "k11"), withSymmetry = false)
        val ch = s.children()
        assertThat(ch).hasSize(1)
        assertThat(ch[0].cell).isEqualTo(cell("h8"))
        assertThat(ch[0].isBlack).isTrue()
    }

    @Test
    fun childrenAreSortedLargestFirst() {
        val s = set(
            cells("a1", "c3", "e5", "g7", "a3", "c5", "e7", "g9"),
            openingRule = false, move2Fix = false, withSymmetry = false,
        )
        val ch = s.children()
        assertThat(ch.size).isAtLeast(2)
        assertThat(ch.zipWithNext().all { (a, b) -> a.count >= b.count }).isTrue()
    }

    @Test
    fun childCountsSumToTheBranchCount() {
        val s = set(cells("h8", "i9", "j10", "k11", "g7", "f6"), withSymmetry = false)
        assertThat(s.children().sumOf { it.count }).isEqualTo(s.branchCount())
        s.drill(s.children().first().cell)
        assertThat(s.children().sumOf { it.count }).isEqualTo(s.branchCount())
    }

    @Test
    fun drillRejectsAForeignCell() {
        val s = set(cells("h8", "i9", "j10", "k11"))
        assertThat(s.drill(cell("a1"))).isFalse()
        assertThat(s.prefix).isEmpty()
    }

    @Test
    fun backRestoresTheRootChildrenExactly() {
        val s = set(cells("h8", "i9", "j10", "k11", "g7", "f6"))
        val before = s.children()
        assertThat(s.drill(before.first().cell)).isTrue()
        assertThat(s.prefix).hasSize(1)
        assertThat(s.back()).isTrue()
        assertThat(s.children()).isEqualTo(before)
    }

    @Test
    fun rootClearsTheWholePrefix() {
        val s = set(cells("h8", "i9", "j10", "k11", "g7", "f6"))
        s.drill(s.children().first().cell)
        s.drill(s.children().first().cell)
        assertThat(s.prefix).hasSize(2)
        s.root()
        assertThat(s.prefix).isEmpty()
        assertThat(s.back()).isFalse()
    }

    @Test
    fun aliveVariantsNarrowOrHoldAsWeDrill() {
        val s = set(cells("h8", "i9", "j10", "k11", "g7", "f6"))
        val before = s.aliveCount()
        s.drill(s.children().first().cell)
        assertThat(s.aliveCount()).isAtMost(before)
        assertThat(s.aliveCount()).isAtLeast(1)
    }

    // ---- symmetry (환원) ----------------------------------------------------

    /**
     * `h8 h7 j10 k11`: h7 sits *below* tengen, so the canonical move-2 rule
     * kills the shape where it stands — but the up-down mirror maps h7 to h9
     * and the shape is reachable after all. The desktop calls this 환원.
     */
    @Test
    fun symmetryRescuesAShapeThatIsUnreachableWhereItSits() {
        val pts = cells("h8", "h7", "j10", "k11")
        assertThat(set(pts, withSymmetry = false).total).isEqualTo(0.0)
        assertThat(set(pts, withSymmetry = true).total).isGreaterThan(0.0)
        // …and without the H9/I9 restriction the shape is fine as it lies
        assertThat(set(pts, move2Fix = false, withSymmetry = false).total).isEqualTo(1.0)
    }

    @Test
    fun theRescuedShapeMatchesTheBruteForceSumAndVariantCount() {
        val pts = cells("h8", "h7", "j10", "k11")
        val s = set(pts)
        val (bruteTotal, bruteVariants) = bruteVariantCount(pts)
        assertThat(s.total).isEqualTo(bruteTotal)
        assertThat(s.variantCount).isEqualTo(bruteVariants)
    }

    /** The dedupe follows the stabiliser, so `nv` lands in {1, 2, 4, 8}. */
    @Test
    fun dedupeCountsPlacementsNotTransforms() {
        // tengen alone is fixed by all of D4
        assertThat(set(cells("h8"), openingRule = false, move2Fix = false).variantCount)
            .isEqualTo(1)
        // h8 + i9 lie on the anti-diagonal: stabiliser {id, anti-transpose}
        assertThat(
            set(cells("h8", "i9"), openingRule = false, move2Fix = false).variantCount,
        ).isEqualTo(4)
        // fully asymmetric: all 8 placements distinct
        assertThat(
            set(
                cells("h8", "i9", "j11", "l11"), openingRule = false, move2Fix = false,
            ).variantCount,
        ).isEqualTo(8)
        // symmetry off = exactly one variant, the identity
        val plain = set(
            cells("h8", "i9", "j11", "l11"),
            openingRule = false, move2Fix = false, withSymmetry = false,
        )
        assertThat(plain.variantCount).isEqualTo(1)
        assertThat(plain.transforms).containsExactly(0)
    }

    /** The anti-diagonal line has 4 placements and the opening rule pins each
     *  to exactly one order — so the merged total is the variant count. */
    @Test
    fun mergedTotalsAddUpAcrossPlacements() {
        val pts = cells("h8", "i9", "j10", "k11")
        val s = set(pts, move2Fix = false)
        assertThat(s.variantCount).isEqualTo(4)
        assertThat(s.total).isEqualTo(4.0)
        assertThat(s.total).isEqualTo(bruteVariantCount(pts, move2Fix = false).first)
    }

    @Test
    fun theMergedTotalIsInvariantUnderAllEightTransforms() {
        val base = cells("h8", "i9", "j10", "k11", "g7", "f6")
        val want = set(base).total
        assertThat(want).isGreaterThan(0.0)
        for (t in 0 until 8) {
            val moved = base.map { MoveOrderSet.xform(t, size, it) }
            assertThat(set(moved).total).isEqualTo(want)
        }
    }

    @Test
    fun theMergedTotalEqualsTheSumOverDedupedVariants() {
        val pts = cells("h8", "i9", "j10", "k11", "g7", "f6")
        val s = set(pts)
        val (bruteTotal, bruteVariants) = bruteVariantCount(pts)
        assertThat(s.total).isEqualTo(bruteTotal)
        assertThat(s.variantCount).isEqualTo(bruteVariants)
    }

    @Test
    fun selfSymmetricShapesAreCountedOnce() {
        // tengen alone is fixed by every transform: one variant, not eight
        val s = set(cells("h8"))
        assertThat(s.variantCount).isEqualTo(1)
        assertThat(s.total).isEqualTo(1.0)
    }

    @Test
    fun theIdentityVariantIsFirst() {
        val s = set(cells("h8", "i9", "j10", "k11"))
        assertThat(s.transforms.first()).isEqualTo(0)
    }

    // ---- completion ---------------------------------------------------------

    @Test
    fun completeFindsALegalOrderOverTheSameStones() {
        val pts = cells("h8", "i9", "j10", "k11", "g7", "f6")
        val s = set(pts, withSymmetry = false)
        val done = checkNotNull(s.complete())
        assertThat(done.transform).isEqualTo(0)
        assertThat(done.order.toSet()).isEqualTo(pts.toSet())
        assertThat(orderLegal(done.order, 2, true, true, true)).isTrue()
    }

    @Test
    fun completeKeepsTheDrilledPrefix() {
        val s = set(cells("h8", "i9", "j10", "k11", "g7", "f6"), withSymmetry = false)
        s.drill(s.children().first().cell)
        val prefix = s.prefix.toList()
        val done = checkNotNull(s.complete())
        assertThat(done.order.take(prefix.size)).isEqualTo(prefix)
    }

    /**
     * When only a mirrored placement is reachable the completion must come from
     * it and say so — the UI warns that replaying will move the stones
     * (main.c:6236-6244).
     */
    @Test
    fun completeFallsBackToAMirroredPlacementAndReportsIt() {
        val pts = cells("h8", "h7", "j10", "k11")
        val s = set(pts)
        val done = checkNotNull(s.complete())
        assertThat(done.transform).isNotEqualTo(0)
        assertThat(orderLegal(done.order, 2, true, true, true)).isTrue()
        // every ply lands on a stone of that placement, with the right colour
        val variant = pts.map { MoveOrderSet.xform(done.transform, size, it) }
        assertThat(done.order.toSet()).isEqualTo(variant.toSet())
        val blackTargets = variant.filterIndexed { i, _ -> i % 2 == 0 }.toSet()
        assertThat(done.order.filterIndexed { i, _ -> i % 2 == 0 }.toSet())
            .isEqualTo(blackTargets)
    }

    // ---- possible move numbers ---------------------------------------------

    @Test
    fun plyMasksMatchBruteForce() {
        val pts = cells("h8", "i9", "j10", "k11", "g7", "f6")
        val s = set(pts, withSymmetry = false)
        assertThat(s.plyOk).isTrue()
        val brute = HashMap<Int, Long>()
        bruteCount(pts, move2Fix = true, plies = brute)
        val got = s.ghosts().associate { it.cell to it.plies }
        for (c in pts) assertThat(got[c]).isEqualTo(brute[c] ?: 0L)
    }

    @Test
    fun aStoneThatCanOnlyBeMoveOneSaysSo() {
        val s = set(cells("h8", "i9", "j10", "k11"), withSymmetry = false)
        val tengen = s.ghosts().single { it.cell == cell("h8") }
        assertThat(MoveOrderFormat.plies(tengen.plies)).isEqualTo("1")
    }

    @Test
    fun ghostsDropStonesAlreadyInThePrefix() {
        val s = set(cells("h8", "i9", "j10", "k11"), withSymmetry = false)
        assertThat(s.ghosts()).hasSize(4)
        s.drill(cell("h8"))
        assertThat(s.ghosts().map { it.cell }).doesNotContain(cell("h8"))
        assertThat(s.ghosts()).hasSize(3)
    }

    // ---- budget honesty -----------------------------------------------------

    @Test
    fun anExhaustedBudgetReportsNotCountedRatherThanAWrongNumber() {
        val pts = (0 until 12).map { cell("${'a' + it % 6}${it + 1}") }
        val s = set(pts, openingRule = false, move2Fix = false, withSymmetry = false, maxNodes = 5)
        assertThat(s.overflow).isTrue()
        assertThat(s.total).isEqualTo(0.0)
        assertThat(s.plyOk).isFalse()
    }

    @Test
    fun tooManyStonesIsRefusedRatherThanTruncated() {
        val many = (0 until 2 * MO_MAX_STONES + 2).map { it }
        assertThat(MoveOrderSet.create(many, size)).isNull()
        assertThat(MoveOrderSet.create(emptyList(), size)).isNull()
        assertThat(MoveOrderSet.create(listOf(size * size), size)).isNull()
    }

    // ---- rules and labels ---------------------------------------------------

    @Test
    fun theCanonicalMoveTwoCellsAreH9AndI9() {
        val (a, b) = checkNotNull(MoveOrderSet.rule2Cells(size))
        assertThat(MoveOrderFormat.cellName(a, size)).isEqualTo("h9")
        assertThat(MoveOrderFormat.cellName(b, size)).isEqualTo("i9")
        assertThat(MoveOrderSet.rule2Cells(14)).isNull()
    }

    @Test
    fun transformNamesCoverTheWholeGroup() {
        assertThat((0 until 8).map { MoveOrderSet.xformName(it) }.toSet()).hasSize(8)
        assertThat(MoveOrderSet.xformName(0)).isEqualTo("원래 방향")
    }

    // ---- formatting ---------------------------------------------------------

    @Test
    fun countsAreGroupedAndDegradeToScientificNotation() {
        assertThat(MoveOrderFormat.count(0.0)).isEqualTo("0")
        assertThat(MoveOrderFormat.count(576.0)).isEqualTo("576")
        assertThat(MoveOrderFormat.count(14400.0)).isEqualTo("14,400")
        assertThat(MoveOrderFormat.count(1234567.0)).isEqualTo("1,234,567")
        assertThat(MoveOrderFormat.count(1.3e13)).isEqualTo("1.30e+13")
    }

    @Test
    fun plySetsCollapseOnceTheyGetLong() {
        assertThat(MoveOrderFormat.plies(0L)).isEmpty()
        assertThat(MoveOrderFormat.plies(0b1L)).isEqualTo("1")
        assertThat(MoveOrderFormat.plies(0b10101L)).isEqualTo("1·3·5")
        assertThat(MoveOrderFormat.plies(0b1111L)).isEqualTo("1~4")
    }

    @Test
    fun cellNamesMatchTheDesktop() {
        assertThat(MoveOrderFormat.cellName(cell("h8"), size)).isEqualTo("h8")
        assertThat(MoveOrderFormat.cellName(cell("a1"), size)).isEqualTo("a1")
        assertThat(MoveOrderFormat.cellName(cell("o15"), size)).isEqualTo("o15")
    }

    /** The opening is a property of the **order**, not of the stones: either the
     *  first or the third move may be tengen (`mo_opening26_cells`). */
    @Test
    fun openingClassificationAcceptsTengenFirstOrThird() {
        val h8 = cell("h8")
        val h9 = cell("h9")
        val h10 = cell("h10")
        assertThat(MoveOrderFormat.opening26(h8, h9, h10, size)).isEqualTo(0)     // D1
        assertThat(MoveOrderFormat.opening26(h10, h9, h8, size)).isEqualTo(0)
        assertThat(MoveOrderFormat.opening26(h9, h8, h10, size)).isNull()
    }

    @Test
    fun openingClassificationIsInvariantUnderTheEightTransforms() {
        val order = cells("h8", "i9", "j10")
        val want = MoveOrderFormat.opening26(order[0], order[1], order[2], size)
        assertThat(want).isNotNull()
        for (t in 0 until 8) {
            val m = order.map { MoveOrderSet.xform(t, size, it) }
            assertThat(MoveOrderFormat.opening26(m[0], m[1], m[2], size)).isEqualTo(want)
        }
    }
}
