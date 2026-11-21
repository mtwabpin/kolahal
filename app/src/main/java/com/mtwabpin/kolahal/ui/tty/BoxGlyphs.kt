/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.mtwabpin.kolahal.terminal.tty.BoxShapes
import com.mtwabpin.kolahal.terminal.tty.BoxShapes.DOUBLE
import com.mtwabpin.kolahal.terminal.tty.BoxShapes.HEAVY
import com.mtwabpin.kolahal.terminal.tty.BoxShapes.NONE
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws box-drawing and block characters (U+2500–259F) as lines and rectangles that fill their
 * cell to its edges, so neighbouring cells join without gaps at any font, size and line height,
 * as kitty, Alacritty and WezTerm do. The shapes come from [BoxShapes].
 *
 * A light line is [GridMetrics.line] wide, a heavy one twice that; a double line is two light
 * lines with a light line's gap between them. Lines sit on whole pixels, so they stay sharp.
 * Nothing here allocates.
 */
internal class BoxGlyphs(private val m: GridMetrics) {
    private val fill = Paint()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = m.line.toFloat()
    }
    private val oval = RectF()

    private val light = m.line

    /** From the middle line to each of a double line's two lines. */
    private val d = m.line

    /** From a line's middle to its top or left edge. */
    private val a = m.line / 2

    /** Draws [cp] in the cell at [x], [y] in [color]; [behind] is the color under it, for the shades. */
    fun draw(canvas: Canvas, cp: Int, x: Float, y: Float, color: Int, behind: Int) {
        fill.color = color
        val w = m.cellW
        val h = m.cellH
        when {
            cp >= 0x2580 -> block(canvas, cp, x, y, w, h, color, behind)
            BoxShapes.isArc(cp) -> arc(canvas, BoxShapes.arms(cp), x, y, w, h, color)
            BoxShapes.diagonal(cp) != 0 -> diagonal(canvas, BoxShapes.diagonal(cp), x, y, w, h, color)
            BoxShapes.dashes(cp) != 0 -> dashed(canvas, BoxShapes.arms(cp), BoxShapes.dashes(cp), x, y, w, h)
            else -> lines(canvas, BoxShapes.arms(cp), x, y, w, h)
        }
    }

    /**
     * Carries [cp]'s arm up (or down) on from [y1] to [y2], in the column of the cell at [x]: a
     * box's side through the room a row has above or below its cells. Nothing when [cp] has no
     * such arm.
     */
    fun arm(canvas: Canvas, cp: Int, up: Boolean, x: Float, y1: Float, y2: Float, color: Int) {
        if (cp !in 0x2500..0x257F || y2 <= y1) return
        val arms = BoxShapes.arms(cp)
        val weight = if (up) BoxShapes.up(arms) else BoxShapes.down(arms)
        if (weight == NONE) return
        fill.color = color
        val cx = x + m.cellW / 2
        if (weight == DOUBLE) {
            vLine(canvas, y1, y2, cx - d, light)
            vLine(canvas, y1, y2, cx + d, light)
        } else vLine(canvas, y1, y2, cx, thickness(weight))
    }

    private fun thickness(weight: Int) = if (weight == HEAVY) light * 2 else light

    /** A horizontal line from [x1] to [x2] centered on [cy] (whole pixels). */
    private fun hLine(canvas: Canvas, x1: Float, x2: Float, cy: Float, t: Int) {
        if (x2 <= x1) return
        val top = cy - t / 2
        canvas.drawRect(x1, top, x2, top + t, fill)
    }

    private fun vLine(canvas: Canvas, y1: Float, y2: Float, cx: Float, t: Int) {
        if (y2 <= y1) return
        val left = cx - t / 2
        canvas.drawRect(left, y1, left + t, y2, fill)
    }

    /**
     * Lines from the middle to the sides that have arms. Where arms meet, each reaches just far
     * enough to close the corner; double lines make an outer and an inner corner, and a single
     * line meets a double one at its near line or spans both, as the font designs draw them.
     */
    private fun lines(canvas: Canvas, arms: Int, x: Float, y: Float, w: Int, h: Int) {
        val up = BoxShapes.up(arms)
        val right = BoxShapes.right(arms)
        val down = BoxShapes.down(arms)
        val left = BoxShapes.left(arms)
        val cx = x + w / 2
        val cy = y + h / 2
        // The edges of the two lines of a double line, on either side of the middle.
        val nearX = cx + d - a      // the right line's left edge
        val farX = cx - d - a       // the left line's left edge
        val nearY = cy + d - a
        val farY = cy - d - a

        // Horizontal arms.
        val vertical = maxOf(if (up == NONE) 0 else thickness(up), if (down == NONE) 0 else thickness(down))
        val verticalDouble = up == DOUBLE || down == DOUBLE
        if (right != NONE) {
            if (right == DOUBLE) {
                val upper = when {
                    verticalDouble -> if (up == DOUBLE) nearX else farX
                    else -> cx - vertical / 2
                }
                val lower = when {
                    verticalDouble -> if (down == DOUBLE) nearX else farX
                    else -> cx - vertical / 2
                }
                hLine(canvas, upper, x + w, cy - d, light)
                hLine(canvas, lower, x + w, cy + d, light)
            } else {
                val start = when {
                    up == DOUBLE && down == DOUBLE -> if (left == NONE) nearX else cx
                    verticalDouble -> farX
                    else -> cx - vertical / 2
                }
                hLine(canvas, start, x + w, cy, thickness(right))
            }
        }
        if (left != NONE) {
            if (left == DOUBLE) {
                val upper = when {
                    verticalDouble -> if (up == DOUBLE) farX + light else nearX + light
                    else -> cx - vertical / 2 + vertical
                }
                val lower = when {
                    verticalDouble -> if (down == DOUBLE) farX + light else nearX + light
                    else -> cx - vertical / 2 + vertical
                }
                hLine(canvas, x, upper, cy - d, light)
                hLine(canvas, x, lower, cy + d, light)
            } else {
                val end = when {
                    up == DOUBLE && down == DOUBLE -> if (right == NONE) farX + light else cx
                    verticalDouble -> nearX + light
                    else -> cx - vertical / 2 + vertical
                }
                hLine(canvas, x, end, cy, thickness(left))
            }
        }

        // Vertical arms, the same turned a quarter.
        val horizontal = maxOf(if (left == NONE) 0 else thickness(left), if (right == NONE) 0 else thickness(right))
        val horizontalDouble = left == DOUBLE || right == DOUBLE
        if (down != NONE) {
            if (down == DOUBLE) {
                val leftLine = when {
                    horizontalDouble -> if (left == DOUBLE) nearY else farY
                    else -> cy - horizontal / 2
                }
                val rightLine = when {
                    horizontalDouble -> if (right == DOUBLE) nearY else farY
                    else -> cy - horizontal / 2
                }
                vLine(canvas, leftLine, y + h, cx - d, light)
                vLine(canvas, rightLine, y + h, cx + d, light)
            } else {
                val start = when {
                    left == DOUBLE && right == DOUBLE -> if (up == NONE) nearY else cy
                    horizontalDouble -> farY
                    else -> cy - horizontal / 2
                }
                vLine(canvas, start, y + h, cx, thickness(down))
            }
        }
        if (up != NONE) {
            if (up == DOUBLE) {
                val leftLine = when {
                    horizontalDouble -> if (left == DOUBLE) farY + light else nearY + light
                    else -> cy - horizontal / 2 + horizontal
                }
                val rightLine = when {
                    horizontalDouble -> if (right == DOUBLE) farY + light else nearY + light
                    else -> cy - horizontal / 2 + horizontal
                }
                vLine(canvas, y, leftLine, cx - d, light)
                vLine(canvas, y, rightLine, cx + d, light)
            } else {
                val end = when {
                    left == DOUBLE && right == DOUBLE -> if (down == NONE) farY + light else cy
                    horizontalDouble -> nearY + light
                    else -> cy - horizontal / 2 + horizontal
                }
                vLine(canvas, y, end, cx, thickness(up))
            }
        }
    }

    /** ┄ ┆ ╌ ╎ …: [n] dashes across the cell, each two thirds of its share. */
    private fun dashed(canvas: Canvas, arms: Int, n: Int, x: Float, y: Float, w: Int, h: Int) {
        val horizontal = BoxShapes.right(arms) != NONE
        val t = thickness(if (horizontal) BoxShapes.right(arms) else BoxShapes.up(arms))
        val length = if (horizontal) w else h
        for (i in 0 until n) {
            val from = (length * i / n.toFloat()).roundToInt()
            val to = (length * (i + 1) / n.toFloat()).roundToInt()
            val dash = ((to - from) * 2 / 3).coerceAtLeast(1)
            val start = from + (to - from - dash) / 2
            if (horizontal) hLine(canvas, x + start, x + start + dash, y + h / 2, t)
            else vLine(canvas, y + start, y + start + dash, x + w / 2, t)
        }
    }

    /** ╭ ╮ ╯ ╰: a quarter circle between two sides' middles, with straight lines to the edges. */
    private fun arc(canvas: Canvas, arms: Int, x: Float, y: Float, w: Int, h: Int, color: Int) {
        stroke.color = color
        // The middle of a light line, so the arc meets the straight lines of its neighbours.
        val mx = x + w / 2 - a + light / 2f
        val my = y + h / 2 - a + light / 2f
        val r = min(w, h) / 2f
        val toRight = BoxShapes.right(arms) != NONE
        val toDown = BoxShapes.down(arms) != NONE
        val ox = if (toRight) mx + r else mx - r
        val oy = if (toDown) my + r else my - r
        oval.set(ox - r, oy - r, ox + r, oy + r)
        val start = when {
            toRight && toDown -> 180f
            !toRight && toDown -> 270f
            !toRight -> 0f
            else -> 90f
        }
        canvas.drawArc(oval, start, 90f, false, stroke)
        val cx = x + w / 2
        val cy = y + h / 2
        if (toRight) hLine(canvas, mx + r, x + w, cy, light) else hLine(canvas, x, mx - r, cy, light)
        if (toDown) vLine(canvas, my + r, y + h, cx, light) else vLine(canvas, y, my - r, cx, light)
    }

    /** ╱ ╲ ╳: corner to corner. */
    private fun diagonal(canvas: Canvas, which: Int, x: Float, y: Float, w: Int, h: Int, color: Int) {
        stroke.color = color
        if (which and 1 != 0) canvas.drawLine(x + w, y, x, y + h, stroke)
        if (which and 2 != 0) canvas.drawLine(x, y, x + w, y + h, stroke)
    }

    /** Blocks: eighths, quadrants and shades. */
    private fun block(canvas: Canvas, cp: Int, x: Float, y: Float, w: Int, h: Int, color: Int, behind: Int) {
        val eighths = BoxShapes.eighths(cp)
        if (eighths >= 0) {
            val l = (w * (eighths and 0xF) / 8f).roundToInt()
            val t = (h * ((eighths shr 4) and 0xF) / 8f).roundToInt()
            val r = (w * ((eighths shr 8) and 0xF) / 8f).roundToInt()
            val b = (h * ((eighths shr 12) and 0xF) / 8f).roundToInt()
            canvas.drawRect(x + l, y + t, x + r, y + b, fill)
            return
        }
        val quadrants = BoxShapes.quadrants(cp)
        if (quadrants != 0) {
            val mx = (w / 2f).roundToInt()
            val my = (h / 2f).roundToInt()
            if (quadrants and 1 != 0) canvas.drawRect(x, y, x + mx, y + my, fill)
            if (quadrants and 2 != 0) canvas.drawRect(x + mx, y, x + w, y + my, fill)
            if (quadrants and 4 != 0) canvas.drawRect(x, y + my, x + mx, y + h, fill)
            if (quadrants and 8 != 0) canvas.drawRect(x + mx, y + my, x + w, y + h, fill)
            return
        }
        val shade = BoxShapes.shade(cp)
        if (shade != 0) {
            fill.color = TtyPalette.blend(color, behind, shade / 4f)
            canvas.drawRect(x, y, x + w, y + h, fill)
        }
    }
}
