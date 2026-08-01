package dev.gomoku.yixindroid.core.model

/**
 * Position prove (main.c:8918-9979). Proves — or refutes — a win for the side to
 * move by an AND/OR search driven entirely by the engine:
 *
 *  - **OR node** (attacker to move): `yxnbest k` candidates, one winning line is
 *    enough;
 *  - **AND node** (defender to move): `yxsearchdefend`, every viable defense must
 *    lose. Hopeless-looking defenses are deferred and batch-verified once
 *    everything else is proven (optimistic pruning);
 *  - per-node budgets start small and escalate on revisit;
 *  - a conclusion is written back into yixindb, whose labels are **mover
 *    perspective**: side-to-move wins = `L`, side-to-move loses = `W`.
 *
 * The tree itself lives in [ProveTree], which is pure so the whole search order
 * can be unit tested without an engine. These are its inputs and outputs.
 */

/** Tuning knobs — settings_dev.txt lines 5, 6, 12, 13, 16, 17, 18 and 7. */
data class ProveOptions(
    val budget0Sec: Int = 5,
    val budgetMaxSec: Int = 320,
    /** Budget unit: false = wall-clock seconds, true = a fixed search depth. */
    val byDepth: Boolean = false,
    val depth0: Int = 12,
    val depthMax: Int = 30,
    /** Attacker candidates per OR node (`yxnbest k`). */
    val nbest: Int = 4,
    /** Materialize only the best attack move, widen only when it fails. */
    val bestFirst: Boolean = true,
    /** Give a defense that looks like it holds one cheap early probe. */
    val probe: Boolean = true,
) {
    /** Budget of a fresh node: ms, or a depth in depth mode (main.c:9302). */
    val initialBudget: Int get() = if (byDepth) depth0 else budget0Sec * 1000

    val maxBudget: Int get() = if (byDepth) depthMax else budgetMaxSec * 1000

    /**
     * Depth mode's time leash (main.c:9484). A depth bounds the work of an OR
     * node, whose candidate count is capped, but not of an AND node, where
     * `yxsearchdefend` evaluates every defense to that depth. The seconds cap
     * the dialog already carries is the natural bound, and it keeps the two
     * modes on one principle: a budget the search always returns from.
     */
    val depthLeashMs: Int get() = budgetMaxSec.coerceAtLeast(1) * 1000

    /** Budget of a verification node (main.c:9303). */
    val verifyBudget: Int
        get() = if (byDepth) minOf(depth0, VERIFY_DEPTH) else VERIFY_MS

    /** The log line's budget part (main.c:9970 / 9974). */
    val label: String
        get() = if (byDepth) "d$depth0..$depthMax" else "$budget0Sec..${budgetMaxSec}초"

    /**
     * Clamped to the dialog's spin ranges (main.c:9894-9915) with the two
     * "cap not below the start" fixups the dialog applies (main.c:9942).
     */
    fun sanitized(): ProveOptions {
        val b0 = budget0Sec.coerceIn(1, 600)
        val d0 = depth0.coerceIn(4, 64)
        return copy(
            budget0Sec = b0,
            budgetMaxSec = budgetMaxSec.coerceIn(1, 3600).coerceAtLeast(b0),
            depth0 = d0,
            depthMax = depthMax.coerceIn(4, 128).coerceAtLeast(d0),
            nbest = nbest.coerceIn(1, NBEST_MAX),
        )
    }

    companion object {
        /** `PROVE_NBEST_MAX` (main.c:8939) — also the widening cap for `k`. */
        const val NBEST_MAX = 8

        /** `PROVE_VERIFY_MS` / `PROVE_VERIFY_DEPTH` (main.c:8940). */
        const val VERIFY_MS = 3000
        const val VERIFY_DEPTH = 8

        fun of(settings: AppSettings) = ProveOptions(
            budget0Sec = settings.proveBudget0Sec,
            budgetMaxSec = settings.proveBudgetMaxSec,
            byDepth = settings.proveByDepth,
            depth0 = settings.proveDepth0,
            depthMax = settings.proveDepthMax,
            nbest = settings.proveNbest,
            bestFirst = settings.proveBestFirst,
            probe = settings.proveProbe,
        )
    }
}

enum class ProveState { OPEN, RESOLVED, EXHAUSTED }

/** Node outcome, always from the **attacker's** point of view (`PR_*`). */
enum class ProveResult { NONE, WIN, NOWIN }

/**
 * What settled a node (`PK_*`). The declaration order is load-bearing: an AND
 * node's proof is only as strong as its weakest child, and main.c takes that as
 * the numerically largest kind (mate < db < winrate).
 */
enum class ProveKind { NONE, MATE, DB, WR }

enum class ProvePhase { IDLE, QUERY, SEARCH, EDIT }

/** Status drawn on a root candidate (`PM_*`, main.c:9115). */
enum class ProveMark { NONE, OPEN, WIN, LOSS, EXH, LATENT }

/** One PV of a prove search: `provepvmove/wr/mate/depth[i]` in one value. */
data class ProvePv(
    val move: Move,
    /** Mover-perspective win rate, 0..1. */
    val winRate: Double,
    /** Mover-perspective mate distance; 0 = none. */
    val mate: Int = 0,
    val depth: Int = 0,
)

/**
 * The board overlay while proving (main.c:9154 `prove_update_overlay`): the line
 * currently under search as translucent ghost stones, plus a status marker on
 * every root candidate.
 */
data class ProveOverlay(
    /** Move -> ply (1-based) in the line being searched. */
    val ghost: Map<Move, Int> = emptyMap(),
    val marks: Map<Move, ProveMark> = emptyMap(),
    /** Remaining budget shown on an OPEN candidate: seconds, or a depth. */
    val budgets: Map<Move, Int> = emptyMap(),
    /** Plies in the displayed line; the last one is the focus stone. */
    val ghostLen: Int = 0,
    /** Stones on the board at the prove root, so ghost colours can continue. */
    val rootLen: Int = 0,
    /** Budgets are depths, not seconds. */
    val byDepth: Boolean = false,
) {
    val active: Boolean get() = ghost.isNotEmpty() || marks.isNotEmpty()

    /** The prove root is the attacker to move, so odd plies are attacks. */
    fun isAttack(ply: Int): Boolean = ply % 2 == 1

    /** Colour of the ghost stone at [ply] (main.c:9083). */
    fun isBlack(ply: Int): Boolean = (rootLen + ply - 1) % 2 == 0

    /** Budget label of an OPEN candidate (main.c:9124). */
    fun budgetLabel(move: Move): String {
        val v = budgets[move] ?: return ""
        return if (v > 99) "99+" else if (byDepth) "d$v" else "${v}초"
    }

    companion object {
        val EMPTY = ProveOverlay()
    }
}

/** Live prove state, everything the badge and the progress card show. */
data class ProveProgress(
    val running: Boolean = false,
    val phase: ProvePhase = ProvePhase.IDLE,
    val resolved: Int = 0,
    val searches: Int = 0,
    val dbWrites: Int = 0,
    val open: Int = 0,
    /** Node count in the tree, for the "탐색 트리" line. */
    val nodes: Int = 0,
    /** Human notation of the line being worked on, "(root)" at the top. */
    val path: String = "",
    /** The current node is an OR (attacker) node. */
    val attack: Boolean = true,
    /** Budget of the running search: ms, or a depth. */
    val budget: Int = 0,
    val byDepth: Boolean = false,
    val elapsedSec: Int = 0,
    // live search readout, from the same INFO stream the analysis panel uses
    val depth: Int = 0,
    val mate: Int = 0,
    val winRatePct: Int = 0,
    /** Attacker candidate of the parent OR node, 1-based; 0 = not applicable. */
    val candIndex: Int = 0,
    val candTotal: Int = 0,
) {
    /**
     * `prove_badge_lines` (main.c:9223). First line is the run total, second the
     * live search — empty unless one is running.
     */
    fun badgeLines(): Pair<String, String> {
        val first = "증명: 해결 $resolved / 탐색 $searches / 미결 $open"
        if (phase != ProvePhase.SEARCH) return first to ""
        val side = if (attack) "공격" else "방어"
        val value = if (mate != 0) "M${if (mate > 0) mate else -mate}" else "$winRatePct%"
        val second = buildString {
            if (byDepth) {
                append("$side  d$depth/$budget $value  ${elapsedSec}s")
            } else {
                append("$side  d$depth $value  ${elapsedSec}s/${budget / 1000}s")
            }
            if (candTotal > 0) append("  후보 $candIndex/$candTotal")
        }
        return first to second
    }
}

/** The result dialog of a finished run (`prove_finish`, main.c:9514). */
data class ProveOutcome(
    val cancelled: Boolean,
    val resolved: Boolean,
    /** Attacker (= side to move at the root) wins. Only meaningful if resolved. */
    val win: Boolean,
    val kind: ProveKind,
    val blackToMove: Boolean,
    val searches: Int,
    val conclusions: Int,
    val dbWrites: Int,
    /** Attacker win rate estimate when the run ended unresolved. */
    val attackerWinRatePct: Int,
) {
    val title: String get() = if (cancelled) "증명 취소" else "증명 완료"

    /** The dialog body, string for string as main.c builds it. */
    val message: String
        get() = if (resolved) {
            val side = if (blackToMove) "흑 (차례)" else "백 (차례)"
            val verdict = if (win) "승리 증명됨" else "승리 불가 (방어 성립/무승부)"
            val by = when (kind) {
                ProveKind.MATE -> "메이트"
                ProveKind.DB -> "데이터베이스"
                else -> "승률"
            }
            "$side: $verdict ($by)\n탐색 ${searches}회, 결론 ${conclusions}개, DB 기록 ${dbWrites}개"
        } else {
            "미결 (예산 소진), 공격측 승률 ~$attackerWinRatePct%\n" +
                "탐색 ${searches}회, 결론 ${conclusions}개. 다시 실행하면 이어서 계산합니다 (DB 유지)."
        }
}
