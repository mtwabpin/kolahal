package com.mtwabpin.kolahal.widgets

/** Sizes of script widgets: the height a size starts with, the smallest a widget may be, and which layout a box gets. Pure. */
object WidgetSizes {
    /** One row is as tall as one cell is wide, and at least this. */
    const val MIN_ROW_DP = 88
    /** A script widget is at least this many of the page's columns wide: one cell. */
    const val MIN_SPAN = 3
    const val MIN_HEIGHT_DP = 72

    /**
     * The height [size] starts with on a page [pageDp] wide (inside its margins).
     * - One row is as tall as one cell is wide ([WidgetLayout.widthDp] of 3 columns), and at least [MIN_ROW_DP].
     * - Rows have the gap between widgets ([WidgetLayout.GAP_DP]) between them, so two 1-row widgets
     *   beside one 2-row widget end at the same line.
     */
    fun heightDp(size: WidgetSize, pageDp: Int): Int {
        val row = maxOf(MIN_ROW_DP, WidgetLayout.widthDp(pageDp, MIN_SPAN))
        return size.rows * row + (size.rows - 1) * WidgetLayout.GAP_DP
    }

    /** The layout a card draws: each kind has one for each. */
    enum class Box { SMALL, WIDE, LARGE }

    /** The layout for a box as measured. A card draws for the box it gets, not for the size it asked for. */
    fun box(widthDp: Int, heightDp: Int): Box = when {
        heightDp >= LARGE_DP -> Box.LARGE
        widthDp >= LARGE_DP -> Box.WIDE
        else -> Box.SMALL
    }

    private const val LARGE_DP = 150
}
