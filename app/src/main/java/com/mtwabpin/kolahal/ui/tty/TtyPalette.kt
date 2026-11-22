/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.ui.Palette
import kotlin.math.pow

/**
 * The theme's colors for the grid: each [Ink] as an ARGB color, worked out once per palette.
 * Every theme arrives as a [Palette] (built-in, your own, Material You, dark or light), so the
 * grid needs nothing per theme.
 *
 * Faint (dim) text is its ink blended toward the background, the same pixels as the log's faded
 * lines over a plain background, but without a layer.
 */
class TtyPalette(palette: Palette) {
    private val plain = IntArray(INKS.size) { colorOf(palette, INKS[it]).toArgb() }
    private val faint = IntArray(INKS.size) { blend(plain[it], plain[Ink.BG.ordinal], DIM_ALPHA) }

    /** The background's color, which the host paints under the whole grid. */
    val background: Int get() = plain[Ink.BG.ordinal]

    fun color(ink: Ink): Int = plain[ink.ordinal]

    /** The color of the text in a cell of [style]: its ink, blended when faint, the background's when inverse. */
    fun text(style: CellStyle): Int =
        if (style.has(CellStyle.INVERSE)) plain[(style.bits shr 4) and 0xF] else ink(style)

    /** The color of a cell's background in [style], or 0 when it is the plain background (not painted). */
    fun fill(style: CellStyle): Int = when {
        style.has(CellStyle.INVERSE) -> ink(style)
        (style.bits shr 4) and 0xF == Ink.BG.ordinal -> 0
        else -> plain[(style.bits shr 4) and 0xF]
    }

    /** The background behind a cell of [style], painted or not: shades blend toward it. */
    fun behind(style: CellStyle): Int {
        val f = fill(style)
        return if (f != 0) f else background
    }

    private fun ink(style: CellStyle): Int {
        val i = style.bits and 0xF
        return if (style.has(CellStyle.FAINT)) faint[i] else plain[i]
    }

    companion object {
        private val INKS = Ink.entries.toTypedArray()

        /** WCAG's relative luminance of an opaque sRGB color. */
        fun luminance(argb: Int): Double {
            fun channel(shift: Int): Double {
                val c = ((argb shr shift) and 0xFF) / 255.0
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }

        /** How much of a faint ink shows over the background: the log's dimmed lines use the same. */
        const val DIM_ALPHA = 0.62f

        fun colorOf(p: Palette, ink: Ink): Color = when (ink) {
            Ink.BG -> p.bg
            Ink.SURFACE -> p.surface
            Ink.FG -> p.fg
            Ink.DIM -> p.dim
            Ink.PROMPT -> p.prompt
            Ink.ERROR -> p.error
            Ink.INFO -> p.info
            Ink.LINK -> p.link
            Ink.SCRIPT -> p.script
            Ink.PERSONAL -> p.personal
            Ink.WORK -> p.work
            Ink.PRIVATE -> p.private
            Ink.GOOD -> p.good
            Ink.WARN -> p.warn
        }

        /** [a] over [b], [t] of [a] showing; [a]'s alpha is kept. */
        fun blend(a: Int, b: Int, t: Float): Int {
            fun channel(shift: Int): Int {
                val x = (a shr shift) and 0xFF
                val y = (b shr shift) and 0xFF
                return (y + (x - y) * t + 0.5f).toInt().coerceIn(0, 255) shl shift
            }
            return (a and (0xFF shl 24)) or channel(16) or channel(8) or channel(0)
        }
    }
}
