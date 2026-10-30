package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import dev.gomoku.zenyth.core.designsystem.theme.BoardSkin
import dev.gomoku.zenyth.core.designsystem.theme.ZenythTheme
import dev.gomoku.zenyth.core.model.Move

/**
 * @param skin how the board is painted in the current theme; see [BoardSkin].
 *   A parameter rather than a field of [BoardRender] because a render comes from
 *   a view model, which has no theme.
 * @param onLongPress the desktop opens its "board text" dialog on Ctrl+click or
 *   a middle click (main.c:2677); on a phone a long press is the natural stand-in.
 */
@androidx.compose.runtime.Composable
fun GomokuBoard(
    render: BoardRender,
    modifier: Modifier = Modifier,
    skin: BoardSkin = ZenythTheme.board,
    onTap: ((Move) -> Unit)? = null,
    onLongPress: ((Move) -> Unit)? = null,
    provePulse: (() -> Boolean)? = null,
) {
    val n = render.size
    BoardType.ensure(androidx.compose.ui.platform.LocalContext.current)
    // The handlers are read through state: keyed on the lambdas themselves, the
    // gesture detector restarted on every recomposition — several times a second
    // while an analysis streams — and a tap landing in that gap was lost.
    val tap = androidx.compose.runtime.rememberUpdatedState(onTap)
    val longPress = androidx.compose.runtime.rememberUpdatedState(onLongPress)
    val interactive = onTap != null || onLongPress != null
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(
                if (interactive) {
                    Modifier.pointerInput(n, onLongPress != null) {
                        fun cellAt(offset: Offset): Move =
                            BoardGeometry(this.size.width.toFloat(), n).cellAt(offset.x, offset.y)
                        detectTapGestures(
                            onTap = { offset -> tap.value?.invoke(cellAt(offset)) },
                            // Only when there is one: otherwise a slow tap would be
                            // taken for a long press and do nothing.
                            onLongPress = if (onLongPress != null) {
                                { offset -> longPress.value?.invoke(cellAt(offset)) }
                            } else {
                                null
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        drawBoard(render, skin, provePulse?.invoke() ?: render.provePulse)
    }
}
