/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The arithmetic of the widgets page and of dragging on the Widgets screen, in pixels. Pure, so it
 * can be unit tested.
 * - The page is [pagePx] wide. Widgets have [gapPx] between them, side by side and up and down.
 * - A column is a twelfth of the page and the gap ([cellPx]): a widget `span` columns wide is
 *   [widthPx] wide, as on the widgets page since widths were twelfths.
 * - Columns count from the start edge: from the right in a right-to-left language ([rtl]). Pixels
 *   count from the left, as the screen does.
 */
class WidgetDrag(val pagePx: Int, val gapPx: Int, val density: Float, val rtl: Boolean = false) {
    private val columns = WidgetLayout.COLUMNS

    /** One column and the gap after it, in pixels. */
    val cellPx: Float get() = (pagePx + gapPx).toFloat() / columns

    /** How wide a widget [span] columns wide is. */
    fun widthPx(span: Int): Int = ((pagePx + gapPx) * span / columns - gapPx).coerceAtLeast(0)

    /** Where a widget at [column] starts, from the start edge. */
    fun startPx(column: Int): Int = (pagePx + gapPx) * column / columns

    /** The left edge of a widget at [column], [span] columns wide. */
    fun leftPx(column: Int, span: Int): Int =
        if (rtl) pagePx - startPx(column) - widthPx(span) else startPx(column)

    /** [dp] in pixels. */
    fun px(dp: Int): Float = dp * density

    /** [px] in dp. */
    fun dp(px: Float): Float = px / density

    /**
     * The column a widget [span] columns wide lands in when its left edge is at [leftPx]: the
     * nearest one, kept inside the page.
     */
    fun dropColumn(leftPx: Float, span: Int): Int {
        val fromStart = if (rtl) pagePx - leftPx - widthPx(span) else leftPx
        return (fromStart / cellPx).roundToInt().coerceIn(0, (columns - span).coerceAtLeast(0))
    }

    /**
     * The top a widget lands at when its top edge is at [topPx], not above the page: the nearest of
     * [lines] (in dp: other widgets' tops and bottoms, [WidgetPlacement.lines]) within [PULL_DP],
     * else the nearest 8 dp step.
     */
    fun dropTopDp(topPx: Float, lines: Collection<Int> = emptyList()): Int {
        val at = dp(topPx)
        val line = lines.filter { abs(it - at) <= PULL_DP }.minByOrNull { abs(it - at) }
        return (line ?: snapDp(at)).coerceAtLeast(0)
    }

    /**
     * The width in columns after the end edge of a widget at [column], [span] wide, was dragged by
     * [dxPx] (to the right is more). The nearest column, at least [minSpan], and never past the end
     * of the page.
     */
    fun resizeSpan(column: Int, span: Int, dxPx: Float, minSpan: Int): Int {
        val towardsEnd = if (rtl) -dxPx else dxPx
        val wanted = ((widthPx(span) + gapPx + towardsEnd) / cellPx).roundToInt()
        return wanted.coerceIn(
            minSpan.coerceAtMost(columns),
            (columns - column).coerceAtLeast(minSpan)
        )
    }

    /** The height after the bottom edge of a widget [heightDp] tall was dragged by [dyPx]: the nearest 8 dp step. */
    fun resizeHeightDp(heightDp: Int, dyPx: Float): Int = snapDp(heightDp + dp(dyPx))

    /** Which of [widgets] is drawn at [x], [y] (pixels from the page's top left), if any: the last drawn wins. */
    fun widgetAt(widgets: List<PlacedWidget>, x: Float, y: Float): PlacedWidget? =
        widgets.lastOrNull { w ->
            val left = leftPx(w.column, w.span)
            val top = px(w.topDp)
            x >= left && x < left + widthPx(w.span) && y >= top && y < top + px(w.heightDp)
        }

    /** One of the handles of a chosen widget, which resize it. */
    enum class Handle { END, BOTTOM, CORNER }

    /**
     * The handle of [w] at [x], [y], if any. Each is a square [touchPx] wide, centered on the middle
     * of the end edge, the middle of the bottom edge, and the bottom end corner.
     */
    fun handleAt(w: PlacedWidget, x: Float, y: Float, touchPx: Float): Handle? {
        val left = leftPx(w.column, w.span).toFloat()
        val right = left + widthPx(w.span)
        val end = if (rtl) left else right
        val top = px(w.topDp)
        val bottom = top + px(w.heightDp)
        val half = touchPx / 2
        fun near(cx: Float, cy: Float) =
            x >= cx - half && x < cx + half && y >= cy - half && y < cy + half
        return when {
            near(end, bottom) -> Handle.CORNER
            near(end, (top + bottom) / 2) -> Handle.END
            near((left + right) / 2, bottom) -> Handle.BOTTOM
            else -> null
        }
    }

    companion object {
        /** How near another widget's line a dropped widget's top must be to go onto it ([dropTopDp]). */
        const val PULL_DP = 16

        /** [dp] to the nearest 8 dp step ([WidgetLayout.STEP_DP]). */
        fun snapDp(dp: Float): Int = (dp / WidgetLayout.STEP_DP).roundToInt() * WidgetLayout.STEP_DP

        /**
         * How far to scroll in one frame while a widget is held at [y], in a view from [top] to
         * [bottom]: up (less than 0) within [edge] of the top, down within [edge] of the bottom, faster
         * nearer the edge, at most [step]. 0 elsewhere.
         */
        fun edgeScroll(y: Float, top: Float, bottom: Float, edge: Float, step: Float): Float =
            when {
                edge <= 0f || bottom - top < 2 * edge -> 0f
                y < top + edge -> -step * ((top + edge - y) / edge).coerceIn(0f, 1f)
                y > bottom - edge -> step * ((y - (bottom - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
    }
}
