package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where stages draw a line under each stage's name ([ProgressStyles.detailsFit]), and where a list, a
 * text or a progress card has room for its badge ([badgeShown], [badgeBesideCentered]).
 */
class StageDetailsAndBadgeTest {
    private val parcel = WidgetSpec.Progress(
        WidgetBase(title = "Parcel"), value = 0.5f, label = "By 6 PM",
        stages = listOf("Ordered", "Shipped", "Out for delivery", "Delivered"), stage = 3,
        details = listOf("Mon 09:12", "Tue 18:40", "Today 07:05"),
    )

    /** A 4×2 Plain card's marks: a 28 dp cookie, the names' line (16 sp and 4 dp) and the details' (16 sp). */
    private fun fits(spec: WidgetSpec.Progress, names: Boolean, room: Float) =
        ProgressStyles.detailsFit(spec, names, room, markDp = 28f, namesDp = 20f, lineDp = 16f)

    @Test
    fun detailsNeedTheNamesAndALineForThem() {
        assertTrue(fits(parcel, names = true, room = 64f))
        assertTrue(fits(parcel, names = true, room = Float.POSITIVE_INFINITY))
        // A line short: the marks would get smaller, so the details are left out.
        assertFalse(fits(parcel, names = true, room = 63f))
        // Where the names don't show under the marks (a 2×2, a 2×1), neither do the details.
        assertFalse(fits(parcel, names = false, room = 200f))
    }

    @Test
    fun withoutDetailsThereIsNoLine() {
        assertFalse(fits(parcel.copy(details = emptyList()), names = true, room = 200f))
        assertFalse(fits(parcel.copy(details = listOf("", " ")), names = true, room = 200f))
    }

    @Test
    fun fewerDetailsLeaveTheLaterStagesBlank() {
        assertEquals("Mon 09:12", ProgressStyles.stageDetail(parcel, 0))
        assertEquals("Today 07:05", ProgressStyles.stageDetail(parcel, 2))
        assertNull(ProgressStyles.stageDetail(parcel, 3))
        assertNull(ProgressStyles.stageDetail(parcel.copy(details = listOf("Mon", " ")), 1))
    }

    @Test
    fun theBadgeIsLeftOutAtOneByOnePosterAndOneShape() {
        assertTrue(badgeShown(WidgetSizes.Box.LARGE, WidgetLook.PLAIN, oneShape = false))
        assertTrue(badgeShown(WidgetSizes.Box.WIDE, WidgetLook.BOLD, oneShape = false))
        assertTrue(badgeShown(WidgetSizes.Box.LARGE, WidgetLook.SPLIT, oneShape = false))
        assertFalse(badgeShown(WidgetSizes.Box.SMALL, WidgetLook.PLAIN, oneShape = false))
        assertFalse(badgeShown(WidgetSizes.Box.LARGE, WidgetLook.POSTER, oneShape = false))
        assertFalse(badgeShown(WidgetSizes.Box.LARGE, WidgetLook.SHAPE, oneShape = true))
    }

    @Test
    fun besideSomethingCenteredTheBadgeNeedsTheRoomOnTheEndSide() {
        // A 4×2's ring, 140 dp in 363: 111 dp on each side, room for "live".
        assertTrue(badgeBesideCentered(lineDp = 363f, contentDp = 140f, badgeDp = 40f))
        // A 2×2's ring nearly fills its 161 dp: the badge goes.
        assertFalse(badgeBesideCentered(lineDp = 161f, contentDp = 140f, badgeDp = 40f))
        // Exactly the badge and its gap on the end side still fits.
        assertTrue(badgeBesideCentered(lineDp = 196f, contentDp = 100f, badgeDp = 40f))
        assertFalse(badgeBesideCentered(lineDp = 195f, contentDp = 100f, badgeDp = 40f))
    }
}
