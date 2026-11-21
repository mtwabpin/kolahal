/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.util.SparseIntArray
import com.mtwabpin.kolahal.terminal.tty.BoxShapes
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.CellStyle

/**
 * Paints a [CellBuffer] on an Android canvas, cell by cell on the grid of [GridMetrics].
 *
 * For each row: the backgrounds that are not the plain background (so a wallpaper or the host's
 * color shows through), then the glyphs, then underlines and strike-throughs.
 * - A run of ASCII characters in one style is one drawText call: their advances are exactly a
 *   cell, by [GridMetrics]. In a monospace font the spaces between them stay in the run.
 * - Box drawing and blocks are drawn as geometry ([BoxGlyphs]), so they join.
 * - Any other character is drawn alone and made to fit its cells: squeezed when the font Android
 *   picks for it (an emoji or CJK font) is wider, centered when it is narrower. So no glyph can
 *   push its neighbours.
 *
 * Nothing in [draw] allocates once the paints have seen each character: widths are kept per
 * character.
 */
class GridPainter(private val m: GridMetrics, private val palette: TtyPalette) {
    private val fill = Paint()
    private val box = BoxGlyphs(m)
    private var chars = CharArray(128)
    private val widths = SparseIntArray()
    private val boldWidths = SparseIntArray()

    /**
     * Paints [buffer] with its top left corner at [x0], [y0] (whole pixels), and clears its dirty
     * rows. Only rows [rowFrom] to [rowTo] (exclusive) are painted: a tall log row that is partly
     * on screen paints only what shows.
     */
    fun draw(canvas: Canvas, buffer: CellBuffer, x0: Float, y0: Float, rowFrom: Int = 0, rowTo: Int = buffer.rows) {
        if (chars.size < buffer.cols + 2) chars = CharArray(buffer.cols + 2)
        for (row in rowFrom.coerceAtLeast(0) until rowTo.coerceAtMost(buffer.rows)) {
            val top = y0 + row * m.cellH
            backgrounds(canvas, buffer, row, x0, top)
            glyphs(canvas, buffer, row, x0, top)
            decorations(canvas, buffer, row, x0, top)
        }
        buffer.dirty.clear()
    }

    /**
     * Paints [buffer] at [x0], [y0] as [draw] does, inside a band from [bandTop] to [bandBottom]
     * that is taller than its cells: the box lines that leave its first row upward go on up to
     * [bandTop], and those that leave its last row downward go on down to [bandBottom]. So a box
     * whose rows are finger-sized still closes, and a `│` between two keys spans the key's height.
     */
    fun drawBand(canvas: Canvas, buffer: CellBuffer, x0: Float, y0: Float, bandTop: Float, bandBottom: Float) {
        draw(canvas, buffer, x0, y0)
        val last = buffer.rows - 1
        val bottom = y0 + buffer.rows * m.cellH
        for (col in 0 until buffer.cols) {
            val x = x0 + col * m.cellW
            if (y0 > bandTop) {
                val g = buffer.glyphAt(col, 0)
                if (g > 0) box.arm(canvas, g, true, x, bandTop, y0, palette.text(buffer.styleAt(col, 0)))
            }
            if (bandBottom > bottom) {
                val g = buffer.glyphAt(col, last)
                if (g > 0) box.arm(canvas, g, false, x, bottom, bandBottom, palette.text(buffer.styleAt(col, last)))
            }
        }
    }

    /** Runs of cells with the same painted background, as one rectangle each. */
    private fun backgrounds(canvas: Canvas, buffer: CellBuffer, row: Int, x0: Float, top: Float) {
        var start = -1
        var color = 0
        for (col in 0..buffer.cols) {
            val c = if (col < buffer.cols) palette.fill(buffer.styleAt(col, row)) else 0
            if (c == color) continue
            if (color != 0) {
                fill.color = color
                canvas.drawRect(x0 + start * m.cellW, top, x0 + col * m.cellW, top + m.cellH, fill)
            }
            start = col
            color = c
        }
    }

    private fun glyphs(canvas: Canvas, buffer: CellBuffer, row: Int, x0: Float, top: Float) {
        var runStart = 0
        var runLength = 0
        var runStyle = 0
        for (col in 0 until buffer.cols) {
            val g = buffer.glyphAt(col, row)
            val style = buffer.styleAt(col, row)
            // In a monospace font a space advances one cell too, so words in one style are one
            // call, not one per word: a screen of log rows is far fewer calls.
            if (g == 0x20 && runLength > 0 && m.monospace && style.bits == runStyle) {
                chars[runLength++] = ' '
                continue
            }
            if (g in 0x21..0x7E) {
                if (runLength > 0 && style.bits != runStyle) {
                    text(canvas, runStart, runLength, CellStyle(runStyle), x0, top)
                    runLength = 0
                }
                if (runLength == 0) {
                    runStart = col
                    runStyle = style.bits
                }
                chars[runLength++] = g.toChar()
                continue
            }
            if (runLength > 0) {
                text(canvas, runStart, runLength, CellStyle(runStyle), x0, top)
                runLength = 0
            }
            if (g == CellBuffer.EMPTY || g == CellBuffer.WIDE_TAIL || g == 0x20) continue
            val x = x0 + col * m.cellW
            if (g > 0 && BoxShapes.isDrawn(g)) {
                box.draw(canvas, g, x, top, palette.text(style), palette.behind(style))
            } else {
                single(canvas, buffer, g, x, top, style)
            }
        }
        if (runLength > 0) text(canvas, runStart, runLength, CellStyle(runStyle), x0, top)
    }

    private fun paintFor(style: CellStyle): TextPaint {
        val paint = if (style.has(CellStyle.BOLD)) m.bold else m.regular
        paint.color = palette.text(style)
        paint.textSkewX = if (style.has(CellStyle.ITALIC)) -0.2f else 0f
        return paint
    }

    /** [length] ASCII characters from [chars], from column [col]. */
    private fun text(canvas: Canvas, col: Int, length: Int, style: CellStyle, x0: Float, top: Float) {
        canvas.drawText(chars, 0, length, x0 + col * m.cellW, top + m.baseline, paintFor(style))
    }

    /** One glyph (a code point or a cluster), fitted to its one or two cells. */
    private fun single(canvas: Canvas, buffer: CellBuffer, g: Int, x: Float, top: Float, style: CellStyle) {
        val paint = paintFor(style)
        val target = (buffer.cellsOf(g) * m.cellW).toFloat()
        val y = top + m.baseline
        if (g > 0) {
            val n = Character.toChars(g, chars, 0)
            val cache = if (paint === m.bold) boldWidths else widths
            var bits = cache.get(g, Int.MIN_VALUE)
            if (bits == Int.MIN_VALUE) {
                bits = java.lang.Float.floatToRawIntBits(paint.measureText(chars, 0, n))
                cache.put(g, bits)
            }
            val w = java.lang.Float.intBitsToFloat(bits)
            fitted(canvas, x, w, target) { canvas.drawText(chars, 0, n, it, y, paint) }
        } else {
            val cluster = buffer.clusterOf(g)
            val w = paint.measureText(cluster)
            fitted(canvas, x, w, target) { canvas.drawText(cluster, it, y, paint) }
        }
    }

    /** Draws something [w] wide into [target] pixels from [x]: squeezed if wider, centered if narrower. */
    private inline fun fitted(canvas: Canvas, x: Float, w: Float, target: Float, draw: (Float) -> Unit) {
        if (w > target && w > 0f) {
            canvas.save()
            canvas.scale(target / w, 1f, x, 0f)
            draw(x)
            canvas.restore()
        } else {
            draw(x + (target - w) / 2f)
        }
    }

    private fun decorations(canvas: Canvas, buffer: CellBuffer, row: Int, x0: Float, top: Float) {
        for (col in 0 until buffer.cols) {
            val style = buffer.styleAt(col, row)
            if (!style.has(CellStyle.UNDERLINE) && !style.has(CellStyle.STRIKE)) continue
            if (buffer.glyphAt(col, row) == CellBuffer.EMPTY) continue
            fill.color = palette.text(style)
            val x = x0 + col * m.cellW
            if (style.has(CellStyle.UNDERLINE)) {
                val y = (top + m.baseline + m.line).toInt().toFloat()
                canvas.drawRect(x, y, x + m.cellW, y + m.line, fill)
            }
            if (style.has(CellStyle.STRIKE)) {
                val y = (top + m.strikeY).toInt().toFloat()
                canvas.drawRect(x, y, x + m.cellW, y + m.line, fill)
            }
        }
    }
}
