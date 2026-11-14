package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** The built-in Battery and network card ([BatteryCards]), and its row in the picker. */
class BatteryCardsTest {
    private fun status(pct: Int?, charging: Boolean = false, network: String = "wifi") = Status(pct, charging, network)

    @Test fun theLevelIsARingWithThePercent() {
        val spec = BatteryCards.spec(status(82))
        assertEquals(82f, spec.value, 0f)
        assertEquals(100f, spec.max, 0f)
        assertEquals("82%", spec.text)
        assertEquals("Wi-Fi", spec.label)
        assertEquals("Battery", spec.base.title)
        // A ring in a wide box too, where a progress card would draw a bar.
        assertEquals("ring", spec.style)
        assertTrue(spec.items.isEmpty())
        assertEquals(0.82f, WidgetWords.fraction(spec.value, spec.max), 0.001f)
    }

    /** A battery is a level that stands. A wave would say "working". */
    @Test fun theRingNeverWaves() {
        assertFalse(BatteryCards.spec(status(82)).moving)
        assertFalse(BatteryCards.spec(status(40, charging = true)).moving)
    }

    @Test fun theColourSaysChargingAndLow() {
        assertNull("the theme's accent", BatteryCards.spec(status(82)).base.color)
        assertNull(BatteryCards.spec(status(16)).base.color)
        assertEquals(StyledText.Colour.BAD, BatteryCards.spec(status(15)).base.color)
        assertEquals(StyledText.Colour.BAD, BatteryCards.spec(status(0)).base.color)
        // On power wins over low, as in the header.
        assertEquals(StyledText.Colour.GOOD, BatteryCards.spec(status(5, charging = true)).base.color)
        assertEquals(StyledText.Colour.GOOD, BatteryCards.spec(status(100, charging = true)).base.color)
    }

    @Test fun theTitleSaysWhenThePhoneIsOnPower() {
        assertEquals("Battery", BatteryCards.title(status(82)))
        assertEquals("Battery", BatteryCards.title(status(100)))
        assertEquals("Charging", BatteryCards.title(status(82, charging = true)))
        assertEquals("Charged", BatteryCards.title(status(100, charging = true)))
        assertEquals("Charging", BatteryCards.title(status(null, charging = true)))
        assertEquals("Charging", BatteryCards.spec(status(82, charging = true)).base.title)
    }

    @Test fun aPhoneThatDoesNotSayItsLevelHasAnEmptyRing() {
        val spec = BatteryCards.spec(status(null, network = "offline"))
        assertEquals(0f, spec.value, 0f)
        assertEquals("–", spec.text)
        assertEquals("Offline", spec.label)
        assertEquals(StyledText.Colour.MUTED, spec.base.color)
        assertEquals("Battery: level unknown, Offline", BatteryCards.said(status(null, network = "offline")))
    }

    @Test fun aLevelOutsideItsRangeIsBroughtIn() {
        assertEquals("100%", BatteryCards.spec(status(140)).text)
        assertEquals(100f, BatteryCards.spec(status(140)).value, 0f)
        assertEquals("0%", BatteryCards.spec(status(-3)).text)
    }

    /** Every value [com.mtwabpin.kolahal.status.DeviceStatus] gives for the network has its words. */
    @Test fun theNetworkIsSaidInWords() {
        assertEquals("Wi-Fi", BatteryCards.network("wifi"))
        assertEquals("Mobile data", BatteryCards.network("cell"))
        assertEquals("Ethernet", BatteryCards.network("eth"))
        assertEquals("VPN", BatteryCards.network("vpn"))
        assertEquals("Online", BatteryCards.network("net"))
        assertEquals("Offline", BatteryCards.network("offline"))
        // A kind from a later version is still a network.
        assertEquals("Online", BatteryCards.network("satellite"))
        assertEquals("Offline", BatteryCards.network(""))
    }

    /** A `?` after the kind: connected, and Android has not found the internet through it. */
    @Test fun aNetworkWithoutInternetSaysSo() {
        assertEquals("Wi-Fi, no internet", BatteryCards.network("wifi?"))
        assertEquals("Mobile data, no internet", BatteryCards.network("cell?"))
        assertEquals("Online, no internet", BatteryCards.network("net?"))
        // Beside the ring there is room for two words.
        assertEquals("No internet", BatteryCards.network("wifi?", short = true))
        assertEquals("No internet", BatteryCards.network("vpn?", short = true))
        assertEquals("Wi-Fi", BatteryCards.network("wifi", short = true))
        assertEquals("Offline", BatteryCards.network("offline", short = true))
        assertEquals("No internet", BatteryCards.spec(status(50, network = "cell?")).label)
    }

    /** A screen reader hears the level, the power and the network, also on a 1×1 card that shows the ring alone. */
    @Test fun theCardIsSaidInOneSentence() {
        assertEquals("Battery: 82%, Wi-Fi", BatteryCards.said(status(82)))
        assertEquals("Battery: 40%, charging, Mobile data", BatteryCards.said(status(40, charging = true, network = "cell")))
        assertEquals("Battery: 100%, charged, Wi-Fi, no internet", BatteryCards.said(status(100, charging = true, network = "wifi?")))
        assertEquals("Battery: 9%, Offline", BatteryCards.said(status(9, network = "offline")))
    }

    /** The next alarm is in the status too, and the card does not show it. */
    @Test fun theAlarmChangesNothing() {
        assertEquals(BatteryCards.spec(status(82)), BatteryCards.spec(status(82).copy(nextAlarm = 1_800_000_000_000L)))
        assertEquals(BatteryCards.said(status(82)), BatteryCards.said(status(82).copy(nextAlarm = 1_800_000_000_000L)))
    }

    /** The card's own words fit what the progress kind draws: the text in the ring, the label beside or under it. */
    @Test fun theSpecReadsAsAProgressCard() {
        assertEquals("Charging: 40%, Mobile data", WidgetWords.description("Charging", BatteryCards.spec(status(40, charging = true, network = "cell")), 0L, ZoneId.of("UTC")))
    }

    /** The picker's Kolahal section lists it first, as a built-in widget that starts 2×1. */
    @Test fun thePickerListsTheBuiltInWidget() {
        val row = pickerBuiltinRows.first()
        assertEquals("battery", row.id)
        assertEquals("builtin:battery", row.source)
        assertEquals(WidgetSource.builtin(WidgetSource.BATTERY), row.source)
        assertTrue(WidgetSource.isSource(row.source))
        assertEquals("Battery and network", row.title)
        assertEquals("Built-in", row.what)
        assertEquals(WidgetSize.S2X1, row.size)
        // The search is in lowercase, as the query is.
        assertEquals(row.search.lowercase(), row.search)
        assertTrue(listOf("battery", "network", "built-in", row.title.lowercase()).all { row.search.contains(it) })
    }
}
