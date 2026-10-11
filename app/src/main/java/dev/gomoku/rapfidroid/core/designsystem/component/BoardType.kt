package dev.gomoku.rapfidroid.core.designsystem.component

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import dev.gomoku.rapfidroid.R

/**
 * The app face for text drawn on the board canvas — coordinates, stone numbers,
 * values. `Paint` knows nothing of Compose's font families, so without this the
 * board fell back to the platform sans while every other line of the app was in
 * Pretendard (RapfiWeb hit the same thing: `ctx.font` could not read the CSS
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
        paint.fontVariationSettings = "'wght' $weight"
    }
}
