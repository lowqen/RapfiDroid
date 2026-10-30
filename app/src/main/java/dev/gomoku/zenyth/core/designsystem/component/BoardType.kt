package dev.gomoku.zenyth.core.designsystem.component

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import dev.gomoku.zenyth.R

/**
 * The app face for text drawn on the board canvas — coordinates, stone numbers,
 * values. `Paint` knows nothing of Compose's font families, so without this the
 * board fell back to the platform sans while every other line of the app was in
 * Pretendard (Zenyth Web hit the same thing: `ctx.font` could not read the CSS
 * variable and drew everything in 10px sans-serif).
 *
 * One variable font, so the weight is a variation setting rather than a file.
 */
object BoardType {
    @Volatile
    private var face: Typeface? = null

    fun ensure(context: Context) {
        if (face != null) return
        face = runCatching { ResourcesCompat.getFont(context, R.font.rapfi_sans) }.getOrNull()
    }

    fun apply(paint: Paint, weight: Int) {
        val f = face
        if (f == null) {
            paint.isFakeBoldText = weight >= 650
            return
        }
        paint.typeface = f
        // Cleared first: `Paint` ignores variation settings equal to the ones it
        // already has, even though the typeface was just reset to the plain
        // face - so a paint given the same weight twice was left on the plain
        // face, and the bold value labels came out lighter than the regular ones.
        paint.fontVariationSettings = null
        paint.fontVariationSettings = "'wght' $weight"
    }
}
