package dev.gomoku.rapfidroid.core.joseki

import dev.gomoku.rapfidroid.core.model.Move
import kotlin.math.roundToInt

/**
 * Learning a lesson by playing it (RapfiWeb `core/curriculum/quiz.ts`), as pure
 * functions: the screen holds a [QuizState] and gets the next one per tap.
 *
 *  - On the learner's turn, the **recommended** move (`[0]`) or another **good**
 *    one (`!!` `!` `!?`, not a trap) is right: the line advances and the
 *    opponent's main-line reply is played automatically.
 *  - A **known bad** move (`?!` `?` `??`) or a **trap** is wrong: its comment and
 *    the refutation (that node's `[0]`) are shown, and the learner tries again.
 *  - A move not in the tree is «outside the joseki»: try again.
 *  - First-try answers make the score; two misses open the hint.
 */
sealed interface QuizFeedback {
    data object Start : QuizFeedback
    data class Correct(val node: JosekiNode, val alternative: Boolean) : QuizFeedback
    data class Mistake(val node: JosekiNode, val refute: Move?) : QuizFeedback
    data class Outside(val move: Move) : QuizFeedback
}

data class QuizState(
    val blackLearner: Boolean,
    val path: Path = emptyList(),
    val answered: Int = 0,
    val firstTry: Int = 0,
    /** Learner moves on the line being followed — the progress bar's total. */
    val total: Int = 0,
    val misses: Int = 0,
    val hint: Boolean = false,
    val feedback: QuizFeedback = QuizFeedback.Start,
    val done: Boolean = false,
) {
    /** 0..100, the share answered right first time. */
    val score: Int get() = if (answered == 0) 0 else (firstTry * 100.0 / answered).roundToInt()
}

object Quiz {

    private fun learnerToMove(black: Boolean, ply: Int) = (ply % 2 == 0) == black

    private fun nodeAt(root: JosekiNode, path: Path): JosekiNode {
        var n = root
        for (i in path) n = n.children.getOrNull(i) ?: n
        return n
    }

    /** Is this child a right answer for the learner? */
    fun acceptable(child: JosekiNode, index: Int): Boolean {
        if (child.role == JosekiRole.TRAP) return false
        if (child.mark?.isBad == true) return false
        return index == 0 || child.mark?.isGood == true
    }

    fun learnerCount(root: JosekiNode, black: Boolean, from: Path = emptyList()): Int {
        var n = nodeAt(root, from)
        var ply = from.size
        var count = 0
        while (n.children.isNotEmpty()) {
            if (learnerToMove(black, ply)) count++
            n = n.children[0]
            ply++
        }
        return count
    }

    /** On the opponent's turn, play its main line until it is the learner's turn or the end. */
    private fun autoplay(root: JosekiNode, s: QuizState): QuizState {
        val path = s.path.toMutableList()
        while (true) {
            val n = nodeAt(root, path)
            if (n.children.isEmpty()) return s.copy(path = path, done = true)
            if (learnerToMove(s.blackLearner, path.size)) return s.copy(path = path)
            path.add(0)
        }
    }

    fun start(root: JosekiNode, blackLearner: Boolean): QuizState =
        autoplay(root, QuizState(blackLearner = blackLearner, total = learnerCount(root, blackLearner)))

    /** The recommended move here (the hint). */
    fun expected(root: JosekiNode, s: QuizState): Move? = nodeAt(root, s.path).children.firstOrNull()?.move

    fun answer(root: JosekiNode, s: QuizState, m: Move): QuizState {
        if (s.done) return s
        val here = nodeAt(root, s.path)
        val i = here.children.indexOfFirst { it.move == m }
        val child = here.children.getOrNull(i)
        if (child != null && acceptable(child, i)) {
            val next = s.copy(
                path = s.path + i,
                answered = s.answered + 1,
                firstTry = s.firstTry + if (s.misses == 0) 1 else 0,
                // Another good move leads down its own branch: recount from there.
                total = if (i == 0) s.total else s.answered + 1 + learnerCount(root, s.blackLearner, s.path + i),
                misses = 0,
                hint = false,
                feedback = QuizFeedback.Correct(child, i != 0),
            )
            val after = autoplay(root, next)
            return if (after.done) after.copy(feedback = next.feedback) else after
        }
        val misses = s.misses + 1
        val feedback = if (child != null) {
            QuizFeedback.Mistake(child, child.children.firstOrNull()?.move)
        } else {
            QuizFeedback.Outside(m)
        }
        return s.copy(misses = misses, hint = s.hint || misses >= 2, feedback = feedback)
    }

    fun showHint(s: QuizState): QuizState = s.copy(hint = true)
}
