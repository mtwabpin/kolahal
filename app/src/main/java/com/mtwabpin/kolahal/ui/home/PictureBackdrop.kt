/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.pow

/**
 * What a see-through picture's lines look like: their [luminance], from 0 (black) to 1 (white), as
 * the eye weighs it. Many pictures from the web (Wikipedia's diagrams, maps and logos) are dark
 * lines with nothing behind them: on a dark theme they all but vanish.
 */
data class PictureInk(val luminance: Float) {
    companion object {
        /** A picture with at least this share of see-through pixels has no background of its own. */
        private const val SEE_THROUGH = 0.05f

        /**
         * The ink of a picture from its [pixels] (ARGB, as `Bitmap.getPixels` gives them), or null
         * when the picture has a background of its own: a photo, or a drawing on white. Such a
         * picture reads on any theme, so it needs no backdrop.
         */
        fun of(pixels: IntArray): PictureInk? {
            if (pixels.isEmpty()) return null
            var clear = 0
            var weight = 0.0
            var sum = 0.0
            for (p in pixels) {
                val alpha = (p ushr 24) and 0xFF
                if (alpha < 128) clear++
                if (alpha == 0) continue
                val a = alpha / 255.0
                sum += a * luminance((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
                weight += a
            }
            if (clear < pixels.size * SEE_THROUGH || weight == 0.0) return null
            return PictureInk((sum / weight).toFloat())
        }

        /** The relative luminance of an sRGB color, as WCAG counts it. */
        private fun luminance(r: Int, g: Int, b: Int): Double {
            fun linear(c: Int): Double =
                (c / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
        }
    }
}

/**
 * The backdrop a picture with [ink] needs on a background [bg]: null when it reads as it is, else a
 * light or a dark one, whichever its lines stand out on more. A picture with a background of its
 * own (no [ink]) never gets one.
 */
fun pictureBackdrop(ink: PictureInk?, bg: Color): Color? {
    if (ink == null) return null
    fun contrast(a: Float, b: Float) = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    if (contrast(ink.luminance, bg.luminance()) >= MIN_CONTRAST) return null
    return if (contrast(ink.luminance, PICTURE_LIGHT.luminance()) >= contrast(
            ink.luminance,
            PICTURE_DARK.luminance()
        )
    ) PICTURE_LIGHT else PICTURE_DARK
}

/** Below this contrast with the background, a picture's lines are hard to see: as for large text and icons (WCAG). */
private const val MIN_CONTRAST = 3f

/** The backdrops: near white, and near black, so neither glares. */
val PICTURE_LIGHT = Color(0xFFF4F4F2)
val PICTURE_DARK = Color(0xFF1E1E1E)
