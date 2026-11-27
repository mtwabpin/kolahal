package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * What a screen reader says for the fields of 0.5.22: the badge after the card's name, the footer
 * last (the card adds the content's age after it), a stat's target with the value and its share,
 * a button's and a switch's value after the label, each stage with its detail, each side's detail.
 */
class WidgetFieldsWordsTest {
    private val utc = ZoneId.of("UTC")
    private val now = 1_800_000_000L

    private fun said(title: String?, spec: WidgetSpec) = WidgetWords.description(title, spec, now, utc)

    @Test
    fun theBadgeFollowsTheNameAndTheFooterComesLast() {
        val base = WidgetBase(badge = "live", note = "Done", footer = "City sensors")
        assertEquals("Visitors, live, Done: 42, City sensors", said("Visitors", WidgetSpec.Stat(base, value = "42")))
        // Without them, as before.
        assertEquals("Visitors: 42", said("Visitors", WidgetSpec.Stat(WidgetBase(), value = "42")))
    }

    @Test
    fun aTargetIsSaidWithTheValueAndItsShare() {
        val stat = WidgetSpec.Stat(WidgetBase(), value = "6,240", label = "Steps", target = "8,000", progress = 0.78f)
        assertEquals("Steps: 6,240 of 8,000, Steps, 78%", said("Steps", stat))
        assertEquals("Steps: 8,960 of 8,000, 112%", said("Steps", stat.copy(value = "8,960", label = null, progress = 1.12f)))
    }

    @Test
    fun aValueIsSaidAfterTheLabel() {
        val toggles = WidgetSpec.Toggles(
            WidgetBase(),
            listOf(
                WidgetSpec.Toggle("Heating", true, WidgetTap(args = "h"), value = "21°"),
                WidgetSpec.Toggle("Fan", false, WidgetTap(args = "f")),
            )
        )
        assertEquals("Home: Heating, 21°, on, Fan off", said("Home", toggles))
        val buttons = WidgetSpec.Buttons(WidgetBase(), listOf(WidgetSpec.Button("Living room", WidgetTap(args = "r"), value = "21°")))
        assertEquals("Home: Living room, 21°", said("Home", buttons))
    }

    @Test
    fun stagesAndSidesSayTheirDetails() {
        val parcel = WidgetSpec.Progress(
            WidgetBase(), value = 0f, stages = listOf("Ordered", "Shipped", "Delivered"), stage = 2,
            details = listOf("Mon 09:12", "Tue 18:40"),
        )
        assertEquals("Parcel: Shipped, stage 2 of 3, Ordered Mon 09:12, Shipped Tue 18:40", said("Parcel", parcel))
        val match = WidgetSpec.Versus(
            WidgetBase(),
            listOf(WidgetSpec.Side("North", detail = "3rd · 41 pts"), WidgetSpec.Side("South")),
            score = "2–1",
        )
        assertEquals("Final: North 2–1 South, North 3rd · 41 pts", said("Final", match))
    }
}
