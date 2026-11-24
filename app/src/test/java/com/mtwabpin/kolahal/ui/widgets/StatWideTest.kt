package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.ui.widgets.StatWide.Split
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where a wide stat puts its smaller values ([StatWide]): always in the order the script gave them. */
class StatWideTest {
    /** A 4×1 card with three: two beside the value, in order, and the third under the label at the end. */
    @Test
    fun theSmallerValuesKeepTheirOrder() {
        assertEquals(Split(column = true, between = 2, inColumn = true), StatWide.split(350f, 3, label = true))
        // Room for all three beside the value: the label is alone in its column.
        assertEquals(Split(column = true, between = 3, inColumn = false), StatWide.split(400f, 3, label = true))
    }

    /** A 2×1 card: no room beside the value, so the first smaller value is under the label, as before. */
    @Test
    fun aNarrowCardHasTheFirstUnderTheLabel() {
        assertEquals(Split(column = true, between = 0, inColumn = true), StatWide.split(152f, 2, label = true))
        assertEquals(Split(column = true, between = 0, inColumn = false), StatWide.split(152f, 0, label = true))
    }

    @Test
    fun withoutALabelAndWithRoomThereIsNoColumn() {
        assertEquals(Split(column = false, between = 2, inColumn = false), StatWide.split(300f, 2, label = false))
        assertEquals(Split(column = true, between = 0, inColumn = true), StatWide.split(152f, 1, label = false))
    }

    /** Under 150 dp there is no column: the values that fit go beside the value, the label under it. */
    @Test
    fun underTheColumnsWidthThereIsNoColumn() {
        assertEquals(Split(column = false, between = 0, inColumn = false), StatWide.split(140f, 3, label = true))
    }
}
