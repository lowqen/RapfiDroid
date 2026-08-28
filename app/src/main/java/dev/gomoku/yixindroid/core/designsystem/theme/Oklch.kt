package dev.gomoku.yixindroid.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * The colour maths the theme is built from.
 *
 * The palette is **derived, not picked**. A hand-written list of hex values is a
 * list of decisions nobody can re-derive: change one and the rest no longer
 * agree with it. Here a whole family of colours comes from two numbers — a hue
 * and a chroma — and a *tone*, so re-tinting the app is one edit and every
 * shade follows.
 *
 * The space is [Oklch]: perceptually uniform, so a step of lightness means the
 * same amount of visible change at every hue, and a fixed chroma does not make
 * yellow scream while blue whispers. This is the same space the board's value
 * ladder was designed in (`TagPalette`), so the two systems speak one language.
 *
 * Tones are **Material's numbering, which is CIE L\***: tone 0 is black, 100 is
 * white, and 50 is the mid grey a person sees as half way. That is a different
 * curve from Oklab's own lightness, so it is converted rather than assumed —
 * for a neutral, Oklab L is the cube root of relative luminance, which is the
 * one identity this file leans on.
 */

/** Relative luminance (WCAG): the Y of linear sRGB. */
fun luminanceOf(color: Color): Float =
    0.2126f * srgbToLinear(color.red) +
        0.7152f * srgbToLinear(color.green) +
        0.0722f * srgbToLinear(color.blue)

/**
 * WCAG contrast ratio between two opaque colours, 1..21. Used by the theme's
 * own test to keep every text/background pair in the app above 4.5:1.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = luminanceOf(a)
    val lb = luminanceOf(b)
    return (max(la, lb) + 0.05f) / (kotlin.math.min(la, lb) + 0.05f)
}

/**
 * One tonal family: every shade of a single hue, addressed by Material tone.
 *
 * [chroma] is a *ceiling*, not a promise — near black and near white the sRGB
 * gamut has no room for it, so [tone] asks for the requested chroma and keeps
 * the most of it that survives inside the gamut. Without that, the light end of
 * a saturated ramp would clip to a different hue than the dark end.
 */
class TonalRamp(val hue: Float, val chroma: Float) {

    private val cache = HashMap<Int, Color>(24)

    /** The shade at Material [tone] (0 = black … 100 = white). */
    fun tone(tone: Int): Color = cache.getOrPut(tone.coerceIn(0, 100)) {
        oklch(lightnessForTone(tone.coerceIn(0, 100)), chroma, hue)
    }
}

/**
 * Oklch → sRGB, **gamut-mapped by chroma**. When [chroma] cannot be shown at
 * this lightness the colour is not clipped channel by channel (which shifts the
 * hue and flattens the ramp's ends); the chroma is reduced until it fits, which
 * keeps hue and lightness exactly and gives up only the saturation that never
 * existed on this display anyway.
 */
fun oklch(lightness: Float, chroma: Float, hueDegrees: Float): Color {
    val l = lightness.coerceIn(0f, 1f)
    if (chroma <= 0f) return linearToColor(oklabToLinear(l, 0f, 0f))
    val rad = Math.toRadians(hueDegrees.toDouble())
    val ca = cos(rad).toFloat()
    val cb = sin(rad).toFloat()
    var lo = 0f
    var hi = chroma
    if (inGamut(oklabToLinear(l, ca * hi, cb * hi))) return linearToColor(oklabToLinear(l, ca * hi, cb * hi))
    repeat(18) {                       // ~1e-5 of chroma: finer than 8-bit output
        val mid = (lo + hi) / 2f
        if (inGamut(oklabToLinear(l, ca * mid, cb * mid))) lo = mid else hi = mid
    }
    return linearToColor(oklabToLinear(l, ca * lo, cb * lo))
}

/** Blend two colours **in linear light**, the honest way to mix (see `TagPalette`). */
fun mixColors(from: Color, to: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float): Float {
        val la = srgbToLinear(a)
        return linearToSrgb(la + (srgbToLinear(b) - la) * f)
    }
    return Color(mix(from.red, to.red), mix(from.green, to.green), mix(from.blue, to.blue))
}

/**
 * Material tone (CIE L\*) → Oklab lightness.
 *
 * L\* is defined through relative luminance Y, and for a neutral colour Oklab's
 * L is the cube root of that same Y (the three cube-rooted cone responses are
 * equal and its weights sum to one). So the conversion is exact, not fitted.
 */
private fun lightnessForTone(tone: Int): Float {
    val lStar = tone.toFloat()
    val y = if (lStar > 8f) ((lStar + 16f) / 116f).pow(3f) else lStar / 903.2963f
    return cbrt(y.toDouble()).toFloat()
}

private fun oklabToLinear(l: Float, a: Float, b: Float): FloatArray {
    val lp = l + 0.3963377774f * a + 0.2158037573f * b
    val mp = l - 0.1055613458f * a - 0.0638541728f * b
    val sp = l - 0.0894841775f * a - 1.2914855480f * b
    val ll = lp * lp * lp
    val mm = mp * mp * mp
    val ss = sp * sp * sp
    return floatArrayOf(
        4.0767416621f * ll - 3.3077115913f * mm + 0.2309699292f * ss,
        -1.2684380046f * ll + 2.6097574011f * mm - 0.3413193965f * ss,
        -0.0041960863f * ll - 0.7034186147f * mm + 1.7076147010f * ss,
    )
}

private fun inGamut(rgb: FloatArray): Boolean =
    rgb.all { it >= -0.0005f && it <= 1.0005f }

private fun linearToColor(rgb: FloatArray) =
    Color(linearToSrgb(rgb[0]), linearToSrgb(rgb[1]), linearToSrgb(rgb[2]))

internal fun srgbToLinear(c: Float): Float {
    val v = c.coerceIn(0f, 1f)
    return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
}

internal fun linearToSrgb(c: Float): Float {
    val v = c.coerceIn(0f, 1f)
    return if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
}

/** Debug helper: `#RRGGBB` of a colour, used by the palette's own test output. */
fun Color.toHex(): String {
    fun ch(v: Float) = ((v * 255f).toInt().coerceIn(0, 255)).toString(16).padStart(2, '0')
    return "#${ch(red)}${ch(green)}${ch(blue)}".uppercase()
}

/** Guards against a ramp silently going flat if the maths above is edited. */
internal fun rampIsMonotone(ramp: TonalRamp): Boolean =
    (0..100 step 5).map { luminanceOf(ramp.tone(it)) }.zipWithNext().all { (a, b) -> b > a - 1e-6f } &&
        abs(luminanceOf(ramp.tone(100)) - 1f) < 0.02f
