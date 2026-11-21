/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import com.mtwabpin.kolahal.terminal.Ink
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * How strong each part of `tty.crt` is for one theme, worked out once per palette ([of]). Pure, so
 * a JVM test checks it for every theme.
 * - Scanlines: every [period]th row of pixels is the background color at [scanAlpha], over
 *   everything. Over the background it changes nothing; across text and filled cells it shows as
 *   a gap between scan lines, dark on a dark theme and light on a light one.
 * - Vignette: black at [vignetteAlpha] in the corners, fading to nothing in the middle. It is drawn
 *   on the background, under the grid, so it never dims text: on a dark theme the corners only
 *   get darker behind it.
 * - [glow]: a soft halo around bright text, on dark themes only (on a light one no text is bright).
 *   The text itself is drawn over its halo, unchanged.
 *
 * The strengths keep text readable: averaged over a scanline period (the eye can't tell its
 * 1-pixel lines apart at a phone's density), each ink keeps at least [KEEP] of its contrast with
 * the background, and so does it against the vignette's corners. On a light theme that leaves a
 * faint look; on a dark one the vignette darkens only the background. The look is subtle by design.
 */
class CrtLook(
    val period: Int,
    val scanAlpha: Float,
    val vignetteAlpha: Float,
    val glow: Boolean,
    private val background: Int,
) {
    /** The scanline's color: the background at [scanAlpha]. */
    val scanColor: Int get() = withAlpha(background, scanAlpha)

    /** The vignette's color in the corners: black at [vignetteAlpha]. */
    val vignetteColor: Int get() = withAlpha(0xFF000000.toInt(), vignetteAlpha)

    companion object {
        /** The share of each ink's contrast the look keeps, at least. */
        const val KEEP = 0.9

        /** The most the scanlines and the vignette ever take, on any theme. */
        const val MAX_SCAN = 0.3f
        const val MAX_VIGNETTE = 0.45f

        /** Where the vignette starts, as a part of the distance from the middle to a corner. */
        const val VIGNETTE_FROM = 0.55f

        /** The inks text is drawn in: the look is checked against each of them. */
        val TEXT_INKS = listOf(
            Ink.FG, Ink.DIM, Ink.PROMPT, Ink.ERROR, Ink.INFO, Ink.LINK, Ink.SCRIPT,
            Ink.PERSONAL, Ink.WORK, Ink.PRIVATE, Ink.GOOD, Ink.WARN,
        )

        /** The look for [palette], with a scanline every [period] rows of pixels. */
        fun of(palette: TtyPalette, period: Int): CrtLook {
            val bg = palette.background
            val inks = TEXT_INKS.map { palette.color(it) }
            val dark = luminance(bg) < luminance(palette.color(Ink.FG))
            val p = period.coerceAtLeast(2)
            val scan = strongest(MAX_SCAN) { a -> inks.all { scanned(it, bg, a, p) >= KEEP * contrast(it, bg) } }
            val vignette = strongest(MAX_VIGNETTE) { v ->
                val corner = over(0xFF000000.toInt(), bg, v)
                inks.all { contrast(it, corner) >= KEEP * contrast(it, bg) }
            }
            return CrtLook(p, scan, vignette, glow = dark, background = bg)
        }

        /** The largest strength up to [max], in steps of 0.01, that [fits]; 0 when none does. */
        private inline fun strongest(max: Float, fits: (Float) -> Boolean): Float {
            var steps = (max * 100).roundToInt()
            while (steps > 0 && !fits(steps / 100f)) steps--
            return steps / 100f
        }

        /**
         * [ink]'s contrast on [bg] with a scanline every [period] rows: its rows of pixels averaged,
         * one of them [ink] with the background over it at [alpha].
         */
        fun scanned(ink: Int, bg: Int, alpha: Float, period: Int): Double {
            val line = luminance(over(bg, ink, alpha))
            val average = ((period - 1) * luminance(ink) + line) / period
            return ratio(average, luminance(bg))
        }

        /** WCAG's contrast ratio of two colors, 1 to 21. */
        fun contrast(a: Int, b: Int): Double = ratio(luminance(a), luminance(b))

        private fun ratio(x: Double, y: Double): Double = (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)

        /** WCAG's relative luminance of an opaque sRGB color. */
        fun luminance(argb: Int): Double {
            fun channel(shift: Int): Double {
                val c = ((argb shr shift) and 0xFF) / 255.0
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }

        /** [top] drawn at [alpha] over the opaque [under], as the canvas blends them. */
        fun over(top: Int, under: Int, alpha: Float): Int = TtyPalette.blend(top or (0xFF shl 24), under, alpha) or (0xFF shl 24)

        private fun withAlpha(color: Int, alpha: Float): Int =
            ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (color and 0xFFFFFF)
    }
}

/**
 * `tty.crt` for a TTY surface (home on the grid, a full-screen screen), or nothing when [on] is
 * false. Put it right after the surface's background in the modifier chain:
 * - the vignette goes on the background, under everything the surface draws: an ellipse the
 *   shape of the surface, clear in the middle and darkest in the corners;
 * - from Android 12, on a dark theme, the glow: what the surface draws, blurred and faint, under
 *   itself, so only its own text and fills glow, and the background only right around them;
 * - the scanlines go over everything.
 *
 * The scanline tile is made once per look and the vignette once per size, so a frame allocates
 * nothing; nothing here moves, so "Remove animations" needs nothing.
 */
@Composable
internal fun rememberCrt(on: Boolean, palette: TtyPalette, cellH: Int): Modifier {
    if (!on) return Modifier
    val density = LocalDensity.current.density
    // A scanline about every dp: 3 pixels on most phones.
    val period = density.roundToInt().coerceIn(2, 4)
    val look = remember(palette, period) { CrtLook.of(palette, period) }
    val glowRadius = (cellH / 6f).coerceAtLeast(2f)
    return remember(look, glowRadius) {
        val tile = Bitmap.createBitmap(1, look.period, Bitmap.Config.ARGB_8888)
        tile.setPixel(0, look.period - 1, look.scanColor)
        val scan = Paint().apply { shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        val draw = Modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val vignette = if (w > 0f && h > 0f && look.vignetteAlpha > 0f) Paint().apply {
                // A circle through the corners of an h × h square, stretched across to the width.
                shader = RadialGradient(
                    w / 2f, h / 2f, h * SQRT_HALF,
                    intArrayOf(0, 0, look.vignetteColor),
                    floatArrayOf(0f, CrtLook.VIGNETTE_FROM, 1f),
                    Shader.TileMode.CLAMP,
                ).apply { setLocalMatrix(Matrix().apply { setScale(w / h, 1f, w / 2f, h / 2f) }) }
            } else null
            onDrawWithContent {
                if (vignette != null) drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, w, h, vignette) }
                drawContent()
                if (look.scanAlpha > 0f) drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, w, h, scan) }
            }
        }
        if (look.glow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val glow = glow(glowRadius)
            draw.graphicsLayer { renderEffect = glow }
        } else draw
    }
}

/** The surface's own drawing, blurred by [radius] px at [GLOW_ALPHA], under itself. */
@RequiresApi(Build.VERSION_CODES.S)
private fun glow(radius: Float): androidx.compose.ui.graphics.RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
    val faint = android.graphics.RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(ColorMatrix().apply { setScale(1f, 1f, 1f, GLOW_ALPHA) }),
        blur,
    )
    // No offset: the drawing itself, over its halo.
    val sharp = android.graphics.RenderEffect.createOffsetEffect(0f, 0f)
    return android.graphics.RenderEffect.createBlendModeEffect(faint, sharp, BlendMode.SRC_OVER).asComposeRenderEffect()
}

/**
 * How much of the blurred drawing shows as the glow. Low, so the halo lifts the background next to
 * text only a little: a glow, not a blur.
 */
private const val GLOW_ALPHA = 0.35f

/** Half the square root of 2: from the middle of a square to a corner, per side. */
private const val SQRT_HALF = 0.70710677f
