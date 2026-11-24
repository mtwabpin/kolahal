/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The size of one grid cell, and the paints that draw text in it, for one typeface, text size and
 * line height. Every x on the grid comes from here, so column n always starts at n × [cellW].
 * - [cellW]: the advance of "0" in whole pixels. The paints are stretched or squeezed (under 4 %)
 *   so that every glyph of the font advances exactly [cellW].
 * - [cellH]: the font's height or the line height (`log.line-height`), whichever is taller, in
 *   whole pixels. The text sits in the middle of it.
 * - Ligatures are off (Fira Code, JetBrains Mono), so each character keeps its own cell.
 */
class GridMetrics(regular: Typeface, bold: Typeface, textSizePx: Float, lineHeightPx: Int) {
    val regular: TextPaint = paint(regular, textSizePx)
    val bold: TextPaint = paint(bold, textSizePx)

    val cellW: Int
    val cellH: Int

    /** The text's baseline, from the top of its cell. */
    val baseline: Float

    /** Where a strike-through goes, from the top of the cell. */
    val strikeY: Float

    /** The top and the bottom of a digit, from the top of the cell: a sign drawn as tall as the numbers beside it. */
    val digitTop: Float
    val digitBottom: Float

    /** The width of a light box-drawing line: an eighth of a cell, at least a pixel. */
    val line: Int

    /** True when "i", "M" and a space are equally wide. Other fonts still work: each glyph is centered in its cell. */
    val monospace: Boolean

    init {
        val advance = this.regular.measureText("0").coerceAtLeast(1f)
        cellW = advance.roundToInt().coerceAtLeast(1)
        monospace = listOf("i", "M", " ").map { this.regular.measureText(it) }
            .let { w -> w.all { abs(it - advance) < 0.5f } }
        this.regular.textScaleX = cellW / advance
        this.bold.textScaleX = cellW / this.bold.measureText("0").coerceAtLeast(1f)
        val fm = this.regular.fontMetrics
        val textHeight = fm.descent - fm.ascent
        cellH = maxOf(ceil(textHeight).toInt(), lineHeightPx, 1)
        baseline = (cellH - textHeight) / 2f - fm.ascent
        strikeY = baseline + fm.ascent * 0.3f
        val digit = Rect()
        this.bold.getTextBounds("0", 0, 1, digit)
        // A font that draws no 0: the middle half of the cell.
        digitTop = if (digit.height() > 0) baseline + digit.top else cellH / 4f
        digitBottom = if (digit.height() > 0) baseline + digit.bottom else cellH * 3 / 4f
        line = (cellW / 8).coerceAtLeast(1)
    }

    /** How many whole columns fit in [widthPx]. */
    fun cols(widthPx: Int) = (widthPx / cellW).coerceAtLeast(1)

    /** How many whole rows fit in [heightPx]. */
    fun rows(heightPx: Int) = (heightPx / cellH).coerceAtLeast(1)

    private fun paint(typeface: Typeface, size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        textSize = size
        fontFeatureSettings = "'liga' 0, 'calt' 0"
    }
}
