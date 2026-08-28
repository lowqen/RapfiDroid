package dev.gomoku.yixindroid.core.designsystem.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

/**
 * The app's movement, in one place — four rules, no exceptions.
 *
 * Not a single file imported `androidx.compose.animation` before this: panels
 * appeared and vanished between frames, tabs cut, bars jumped to their new
 * length. That reads as *cheap* long before anyone can say why, because nothing
 * physical changes state instantly and the eye knows it.
 *
 * The restraint matters as much as the movement. This is a board being read
 * while an engine searches, so:
 *
 *  - **nothing slides.** Height and opacity only. A panel that flies in from the
 *    side drags the reader's eye off the position.
 *  - **nothing lasts longer than 200 ms.** Below that a transition is felt
 *    rather than watched, which is the point of it.
 *  - **live numbers are never interpolated** — depth, nodes, speed change
 *    several times a second, and an animated count is a lie about a value that
 *    has already moved on. Only *summaries* animate: the evaluation bar, a win
 *    rate, a progress ring.
 *  - **the prove heartbeat stays as it is.** That half-second blink is not
 *    decoration; it is the board saying the search is alive (main.c:9206).
 */

/** Panel opening: 180 ms, height and opacity together. */
val expandFadeIn: EnterTransition =
    expandVertically(animationSpec = tween(MOTION_PANEL)) + fadeIn(animationSpec = tween(MOTION_PANEL))

/** Panel closing: slightly faster than opening, the way a drawer shuts. */
val shrinkFadeOut: ExitTransition =
    shrinkVertically(animationSpec = tween(MOTION_QUICK)) + fadeOut(animationSpec = tween(MOTION_QUICK))

/** Tab and screen changes — a crossfade, never a slide. */
const val MOTION_QUICK = 150

/** Opening and closing a section. */
const val MOTION_PANEL = 180

/** A summary value moving to a new figure (evaluation bar, win rate, progress). */
const val MOTION_VALUE = 200
