package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/** The numbers and words of a gauge, nested rings and stages ([ProgressStyles]). */
class ProgressStylesTest {
    private val base = WidgetBase()
    private val utc = ZoneId.of("UTC")
    private val air = listOf(
        WidgetSpec.GaugeZone(0f, ColorRole.GOOD),
        WidgetSpec.GaugeZone(50f, ColorRole.WARN),
        WidgetSpec.GaugeZone(150f, ColorRole.BAD),
    )
    private val gauge = WidgetSpec.Progress(
        base, value = 118f, max = 300f, label = "Unhealthy for some", style = "gauge",
        unit = "US AQI", zones = air,
    )
    private val rings = WidgetSpec.Progress(
        base, value = 0f, style = "rings",
        items = listOf(
            WidgetSpec.Bar("Steps", 6240f, 8000f, null, ColorRole.ACCENT, moving = true),
            WidgetSpec.Bar("Water", 5f, 8f, null, ColorRole.LINK, unit = "glasses"),
            WidgetSpec.Bar("Sleep", 7.2f, 8f, "7:10", ColorRole.WARN, unit = "h"),
        ),
    )
    private val parcel = WidgetSpec.Progress(
        base, value = 0.4f, label = "Arrives Thursday",
        stages = listOf("Ordered", "Shipped", "On its way", "Delivered"), stage = 3,
    )

    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.001f)

    @Test
    fun eachStyleIsKnownByItsFields() {
        assertTrue(ProgressStyles.isGauge(gauge))
        assertTrue(ProgressStyles.isRings(rings))
        assertTrue(ProgressStyles.isStages(parcel))
        // Rings need items; with items, a gauge is bars.
        assertFalse(ProgressStyles.isRings(rings.copy(items = emptyList())))
        assertFalse(ProgressStyles.isGauge(gauge.copy(items = rings.items)))
        assertFalse(ProgressStyles.isGauge(WidgetSpec.Progress(base, value = 1f, style = "ring")))
    }

    @Test
    fun aGaugeSitsOnItsScale() {
        near(0.5f, ProgressStyles.gaugeFraction(15f, -10f, 40f))
        near(0f, ProgressStyles.gaugeFraction(-20f, -10f, 40f))
        near(1f, ProgressStyles.gaugeFraction(90f, -10f, 40f))
        near(0f, ProgressStyles.gaugeFraction(3f, 5f, 5f))
        near(0f, ProgressStyles.gaugeFraction(Float.NaN, 0f, 1f))
    }

    @Test
    fun theValuesZoneGivesItsColor() {
        assertEquals(ColorRole.WARN, ProgressStyles.zoneOf(118f, air)?.color)
        assertEquals(ColorRole.WARN, ProgressStyles.zoneOf(50f, air)?.color)
        assertEquals(ColorRole.BAD, ProgressStyles.zoneOf(300f, air)?.color)
        assertNull(ProgressStyles.zoneOf(5f, air.drop(1)))
        assertEquals(ColorRole.WARN, ProgressStyles.gaugeRole(gauge))
        // Below the first zone, or with none: the card's own color.
        assertEquals(
            ColorRole.INFO,
            ProgressStyles.gaugeRole(gauge.copy(base = WidgetBase(color = ColorRole.INFO), zones = air.drop(1), value = 5f))
        )
        assertNull(ProgressStyles.gaugeRole(gauge.copy(zones = emptyList())))
    }

    /** A dial of 250° from the lower left: each zone has its track, and its fill as far as the value. */
    @Test
    fun aGaugesArcs() {
        val arcs = ProgressStyles.gaugeArcs(0f, 300f, air, 118f, -215f, 250f, gap = 4f)
        // Good is all filled, warn filled to 118, bad empty.
        assertEquals(
            listOf(ColorRole.GOOD, ColorRole.GOOD, ColorRole.WARN, ColorRole.WARN, ColorRole.BAD),
            arcs.map { it.color })
        assertEquals(listOf(false, true, false, true, false), arcs.map { it.filled })
        val goodEnd = -215f + 250f * 50 / 300
        near(-215f, arcs[0].start)
        near(goodEnd - 2f, arcs[0].start + arcs[0].sweep)
        near(goodEnd + 2f, arcs[2].start)
        near(-215f + 250f * 118 / 300, arcs[3].start + arcs[3].sweep)
        // The last zone ends at the end of the dial.
        near(35f, arcs[4].start + arcs[4].sweep)
        // Without zones: one arc in the card's color, filled to the value.
        val plain = ProgressStyles.gaugeArcs(0f, 10f, emptyList(), 5f, -180f, 180f, gap = 4f)
        assertEquals(listOf(null, null), plain.map { it.color })
        near(90f, plain[1].sweep)
        // Below the first zone the arc is in the card's color; at the bottom of the scale nothing is filled.
        val late = ProgressStyles.gaugeArcs(0f, 10f, air.take(1).map { it.copy(from = 4f) }, 0f, -180f, 180f, 0f)
        assertEquals(listOf<ColorRole?>(null, ColorRole.GOOD), late.map { it.color })
        assertTrue(late.none { it.filled })
    }

    @Test
    fun aGaugeFitsItsBox() {
        // Half a circle in a wide box: as tall as the box allows, its center on the bottom.
        val half = ProgressStyles.gaugeFit(100f, 60f, 180f, 10f)
        near(40f, half.radius)
        near(50f, half.cx)
        near(50f, half.cy)
        // A dial of 250°: its ends reach under the center, so in a low box its height decides.
        val round = ProgressStyles.gaugeFit(160f, 120f, 250f, 10f)
        val below = -kotlin.math.cos(Math.toRadians(125.0)).toFloat()
        near(100f / (1 + below), round.radius)
        // Its whole height is centered: as much room over it as under it.
        near(120f - round.cy - round.radius * below, round.cy - round.radius)
        // In a narrow box its width decides.
        near(30f, ProgressStyles.gaugeFit(80f, 160f, 250f, 10f).radius)
    }

    @Test
    fun ringsGetSmallerInwards() {
        val sides = ProgressStyles.ringSides(140f, 4)
        assertEquals(4, sides.size)
        assertEquals(140f, sides[0].first)
        assertTrue(sides.zipWithNext().all { (a, b) -> b.first < a.first - 2 * a.second })
        assertTrue(sides.all { it.second in 5f..13f })
        // A small box keeps only the rings that have a hole.
        assertTrue(ProgressStyles.ringSides(30f, 4).size < 4)
        assertEquals(emptyList<Pair<Float, Float>>(), ProgressStyles.ringSides(140f, 0))
    }

    /** A 4×2's rings leave their rows the room for "6,240 of 8,000" (the picture of 25 Nov had "of 8,…"). */
    @Test
    fun ringsBesideRowsLeaveThemRoom() {
        // A 4×2 on a 395 dp page: 363 by 164 dp inside its padding.
        val side = ProgressStyles.ringsSide(363f, 164f)
        near(137.94f, side)
        // The rows' width after the 20 dp between: a mark, a name, "6,240" and "of 8,000" need about 195.
        assertTrue(363f - side - 20f >= 200f)
        // A low card keeps its rings as tall as it is.
        near(80f, ProgressStyles.ringsSide(363f, 80f))
    }

    @Test
    fun asManyRowsAsFit() {
        assertEquals(4, ProgressStyles.rowsThatFit(listOf(34, 34, 34, 34), 164))
        // At a larger font, a row whose scale went under its value is taller: three fit.
        assertEquals(3, ProgressStyles.rowsThatFit(listOf(57, 44, 44, 44), 164))
        assertEquals(1, ProgressStyles.rowsThatFit(listOf(200, 34), 164))
        assertEquals(0, ProgressStyles.rowsThatFit(emptyList(), 164))
        assertEquals(2, ProgressStyles.rowsThatFit(listOf(40, 40), Int.MAX_VALUE))
    }

    @Test
    fun theScaleGoesUnderTheValueWhenTheLineIsFull() {
        assertTrue(ProgressStyles.fitsInLine(65, 5, 48, 120))
        assertFalse(ProgressStyles.fitsInLine(85, 5, 62, 120))
    }

    @Test
    fun aRingsScaleIsWrittenWithItsUnit() {
        assertEquals("of 8,000", ProgressStyles.ringOf(rings.items[0], Locale.US))
        assertEquals("of 8 glasses", ProgressStyles.ringOf(rings.items[1], Locale.US))
        assertEquals("6,240", ProgressStyles.ringValue(rings.items[0], Locale.US))
        assertEquals("7:10", ProgressStyles.ringValue(rings.items[2], Locale.US))
        assertEquals("2.5", ProgressStyles.grouped(2.5f, Locale.US))
        assertEquals("1.234,5", ProgressStyles.grouped(1234.5f, Locale.GERMANY))
    }

    @Test
    fun stagesCountTheWayDone() {
        near(0f, ProgressStyles.stagesFraction(1, 4, 0f))
        near(0.8f / 3, ProgressStyles.stagesFraction(1, 4, 0.8f))
        near(2.4f / 3, ProgressStyles.stagesFraction(3, 4, 0.4f))
        // At the last stage there is no way further.
        near(1f, ProgressStyles.stagesFraction(4, 4, 0.5f))
        near(2.4f / 3, ProgressStyles.level(parcel))
        // A gauge draws its arc in Bold: the card doesn't fill.
        near(0f, ProgressStyles.level(gauge))
        near(0.5f, ProgressStyles.level(WidgetSpec.Progress(base, value = 5f, max = 10f)))
        assertEquals("3 of 4", ProgressStyles.stageCount(parcel))
    }

    @Test
    fun aScreenReaderHearsEveryValue() {
        assertEquals(
            "Air: 118 US AQI, Unhealthy for some",
            WidgetWords.description("Air", gauge, 0, utc, locale = Locale.US)
        )
        assertEquals(
            "Today: Steps 6,240 of 8,000, Water 5 of 8 glasses, Sleep 7:10 of 8 h",
            WidgetWords.description("Today", rings, 0, utc, locale = Locale.US)
        )
        assertEquals(
            "Parcel: On its way, stage 3 of 4, 40% of the way to Delivered, Arrives Thursday",
            WidgetWords.description("Parcel", parcel, 0, utc, locale = Locale.US)
        )
        // At the last stage, or with no way along yet, nothing is said of the way.
        assertEquals(
            "Parcel: Delivered, stage 4 of 4",
            WidgetWords.description("Parcel", parcel.copy(stage = 4, label = null), 0, utc, locale = Locale.US)
        )
        assertEquals(
            "Parcel: Shipped, stage 2 of 4",
            WidgetWords.description("Parcel", parcel.copy(stage = 2, value = 0f, label = null), 0, utc)
        )
        // A ring and bars are said as they always were.
        assertEquals(
            "Water: 5 / 8, glasses",
            WidgetWords.description(
                "Water",
                WidgetSpec.Progress(base, value = 5f, max = 8f, text = "5 / 8", label = "glasses"),
                0,
                utc
            )
        )
    }
}
