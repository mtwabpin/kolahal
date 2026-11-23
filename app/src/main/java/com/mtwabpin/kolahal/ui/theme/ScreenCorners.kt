/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.theme

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * A rounded corner of the screen as Android reports it (`RoundedCorner`): its [radius] and the
 * center of its circle ([centerX], [centerY]), in the window's pixels.
 */
data class ScreenCorner(val radius: Int, val centerX: Int, val centerY: Int)

/** The screen's two top corners. Each is null where the corner is square or Android doesn't say. */
data class TopCorners(val left: ScreenCorner?, val right: ScreenCorner?) {
    companion object {
        val NONE = TopCorners(null, null)
    }
}

/** The screen's two bottom corners. Each is null where the corner is square or Android doesn't say. */
data class BottomCorners(val left: ScreenCorner?, val right: ScreenCorner?) {
    companion object {
        val NONE = BottomCorners(null, null)
    }
}

/**
 * How much of a line near the top or the bottom of the screen the rounded corners hide. Plain
 * arithmetic, so JVM tests check it; the window's corners come from `windowTopCorners` and
 * `windowBottomCorners`.
 *
 * A top corner hides what lies outside its circle, above the circle's center and toward its side.
 * Over a span of rows the top row loses the most, so of the span only its top counts. A bottom
 * corner is the same turned upside down: it hides below its center, and only the span's bottom
 * counts.
 */
object ScreenCorners {
    /**
     * Half the width of [corner]'s circle at row [y]: the curve is this far from the center, across.
     * Null for a row level with the center or on the inner side of it (below it for a top corner,
     * above it for a bottom corner, [below]), where the corner hides nothing. Past the circle the
     * corner hides all of its square, out to the center: 0.
     */
    private fun halfWidth(corner: ScreenCorner, y: Int, below: Boolean): Double? {
        val dy = (if (below) y - corner.centerY else corner.centerY - y).toDouble()
        if (dy <= 0.0) return null
        val r = corner.radius.toDouble()
        return if (dy >= r) 0.0 else sqrt(r * r - dy * dy)
    }

    /** The pixels in from [edge] that a left [corner] hides at row [y]. */
    private fun left(corner: ScreenCorner?, y: Int, below: Boolean, edge: Int): Int {
        if (corner == null || corner.radius <= 0) return 0
        val half = halfWidth(corner, y, below) ?: return 0
        return (ceil(corner.centerX - half).toInt() - edge).coerceAtLeast(0)
    }

    /** The pixels in from [edge] that a right [corner] hides at row [y]. */
    private fun right(corner: ScreenCorner?, y: Int, below: Boolean, edge: Int): Int {
        if (corner == null || corner.radius <= 0) return 0
        val half = halfWidth(corner, y, below) ?: return 0
        return (edge - floor(corner.centerX + half).toInt()).coerceAtLeast(0)
    }

    /**
     * The pixels in from [edge] that a top-left [corner] hides in the rows [top] to [bottom]. [edge]
     * is the window's left side (0) or the x of a line's left end in the window. 0 with no corner,
     * a radius of 0, or rows below the corner.
     */
    fun hiddenLeft(corner: ScreenCorner?, top: Int, bottom: Int, edge: Int = 0): Int =
        left(corner, minOf(top, bottom), below = false, edge)

    /**
     * The pixels in from [edge] that a top-right [corner] hides in the rows [top] to [bottom]. [edge]
     * is the window's width or the x just past a line's right end in the window. 0 with no corner, a
     * radius of 0, or rows below the corner.
     */
    fun hiddenRight(corner: ScreenCorner?, top: Int, bottom: Int, edge: Int): Int =
        right(corner, minOf(top, bottom), below = false, edge)

    /**
     * The pixels in from [edge] that a bottom-left [corner] hides in the rows [top] to [bottom]
     * ([bottom] is the y just under the lowest row). [edge] is the window's left side (0) or the x
     * of a line's left end in the window. 0 with no corner, a radius of 0, or rows above the corner.
     */
    fun hiddenBottomLeft(corner: ScreenCorner?, top: Int, bottom: Int, edge: Int = 0): Int =
        left(corner, maxOf(top, bottom), below = true, edge)

    /**
     * The pixels in from [edge] that a bottom-right [corner] hides in the rows [top] to [bottom]
     * ([bottom] is the y just under the lowest row). [edge] is the window's width or the x just past
     * a line's right end in the window. 0 with no corner, a radius of 0, or rows above the corner.
     */
    fun hiddenBottomRight(corner: ScreenCorner?, top: Int, bottom: Int, edge: Int): Int =
        right(corner, maxOf(top, bottom), below = true, edge)
}
