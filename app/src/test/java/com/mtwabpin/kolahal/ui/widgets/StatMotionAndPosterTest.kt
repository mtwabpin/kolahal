package com.mtwabpin.kolahal.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rolling number's digits ([RollingDigits]) and the Poster look's crop ([PosterFit]). */
class StatMotionAndPosterTest {
    @Test
    fun onlyTheChangedDigitsOfANumberOfOneShapeRoll() {
        assertEquals(listOf(3, 4), RollingDigits.changed("6,238", "6,240"))
        assertEquals(listOf(5), RollingDigits.changed("1.0824", "1.0825"))
        assertEquals(listOf(2), RollingDigits.changed("-31%", "-32%"))
        assertEquals(listOf(1), RollingDigits.changed("27°", "28°"))
        assertEquals(emptyList<Int>(), RollingDigits.changed("118", "118"))
    }

    /** Anything else changes at once: another length, a separator that moved, a value that is no number. */
    @Test
    fun anyOtherChangeDoesNotRoll() {
        assertNull(RollingDigits.changed("999", "1,000"))
        assertNull(RollingDigits.changed("9.5", "95."))
        assertNull(RollingDigits.changed("2h 12", "2h 14"))
        assertNull(RollingDigits.changed("Sunny", "Rainy"))
        assertNull(RollingDigits.changed("18:41", "18:42"))
        assertNull(RollingDigits.changed("", ""))
    }

    private val digit = 56f // a digit's width at the measured size
    private val ink = 72f // a digit's height at that size
    private val step = 82f

    private fun check(roomW: Float, roomH: Float, widths: List<Float>, lasts: List<Float>, s: Step = Step.ANY) {
        val scale = PosterFit.scale(roomW, roomH, widths, lasts, ink, step)
        assertTrue("a value with room is drawn", scale > 0f)
        val crop = PosterFit.crop(scale, roomW, roomH, widths, lasts, ink, step)
        val e = 1e-3f
        assertTrue("end crop ${crop.end}", crop.end <= PosterFit.CROP_END + e)
        assertTrue("bottom crop ${crop.bottom}", crop.bottom <= PosterFit.CROP_BOTTOM + e)
        // As large as allowed: one of the two edges crops by its whole share.
        assertTrue(
            "one edge crops fully: $crop",
            kotlin.math.abs(crop.end - PosterFit.CROP_END) < e || kotlin.math.abs(crop.bottom - PosterFit.CROP_BOTTOM) < e
        )
        when (s) {
            Step.END -> assertEquals(PosterFit.CROP_END, crop.end, e)
            Step.BOTTOM -> assertEquals(PosterFit.CROP_BOTTOM, crop.bottom, e)
            Step.ANY -> Unit
        }
    }

    private enum class Step { END, BOTTOM, ANY }

    /** Never more of a digit than the set shares goes past the card's end or bottom, and the value is as large as that allows. */
    @Test
    fun theEdgesCropADigitByASetShareAtMost() {
        // "36" with room across: the bottom crops it.
        check(500f, 76f * 3, listOf(2 * digit), listOf(digit), Step.BOTTOM)
        // "6,240" in the same room: too wide for the bottom to reach; the end crops it.
        check(110f * 3, 76f * 3, listOf(4.4f * digit), listOf(digit), Step.END)
        // "8" in a large card.
        check(170f * 3, 160f * 3, listOf(digit), listOf(digit))
        // A clock's two lines: the wider line meets the end, or the second line the bottom.
        check(170f * 3, 160f * 3, listOf(2 * digit, 2 * digit), listOf(digit, digit))
        check(80f * 3, 60f * 3, listOf(3 * digit), listOf(digit))
    }

    @Test
    fun noRoomOrNoValueDrawsNothing() {
        assertEquals(0f, PosterFit.scale(0f, 100f, listOf(digit), listOf(digit), ink, step))
        assertEquals(0f, PosterFit.scale(100f, -4f, listOf(digit), listOf(digit), ink, step))
        assertEquals(0f, PosterFit.scale(100f, 100f, emptyList(), emptyList(), ink, step))
    }

    /** The value is measured with its digits as 0, so a value that ticks keeps its size. */
    @Test
    fun aTemplateKeepsEverythingButTheDigits() {
        assertEquals("00:00 PM", PosterFit.template("12:41 PM"))
        assertEquals("0,000", PosterFit.template("6,240"))
        assertEquals("today", PosterFit.template("today"))
    }

    /** TalkBack says a stat's recent numbers as a chart says them; its scene is a picture, with nothing to say. */
    @Test
    fun aStatsPointsAreSaid() {
        val spec = com.mtwabpin.kolahal.widgets.WidgetSpec.Stat(
            com.mtwabpin.kolahal.widgets.WidgetBase(), value = "6,240", label = "Steps today",
            points = listOf(5f, 7f, 6f), scene = "pond",
        )
        assertEquals(
            "Steps: 6,240, Steps today, 3 values, from 5 to 7, the latest 6",
            WidgetWords.description("Steps", spec, 0, java.time.ZoneOffset.UTC)
        )
    }

    /** A large Poster clock has the hours over the rest. */
    @Test
    fun aClocksHoursGoOverItsMinutes() {
        assertEquals(listOf("21", "08"), posterClockLines("21:08"))
        assertEquals(listOf("9", "08 PM"), posterClockLines("9:08 PM"))
        assertEquals(listOf("21", "08:45"), posterClockLines("21:08:45"))
        assertEquals(listOf("today"), posterClockLines("today"))
    }
}
