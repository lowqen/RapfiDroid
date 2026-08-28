package dev.gomoku.yixindroid.core.designsystem.component

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.gomoku.yixindroid.core.designsystem.theme.BoardSkin
import java.io.ByteArrayOutputStream

/**
 * Export size in pixels. Close to a phone's own board width, so the exported
 * image is the board as the user saw it rather than a re-styled version.
 */
const val BOARD_PNG_SIZE_PX = 1440

/**
 * Draw one board frame off-screen and encode it as PNG — the app's stand-in for
 * the desktop's "share image" card, minus the review text.
 *
 * Safe to call off the main thread: [drawBoard] only reads the [BoardRender]
 * value it is given, and needs no composition or real density (all of its
 * geometry is in pixels).
 */
fun renderBoardPng(
    render: BoardRender,
    skin: BoardSkin = BoardSkin.Light,
    sizePx: Int = BOARD_PNG_SIZE_PX,
): ByteArray {
    val side = sizePx.coerceIn(320, 4096)
    val bitmap = ImageBitmap(side, side)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(bitmap),
        size = Size(side.toFloat(), side.toFloat()),
    ) {
        drawBoard(render, skin)
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}
