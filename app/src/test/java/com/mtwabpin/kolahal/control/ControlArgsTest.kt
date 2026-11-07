package com.mtwabpin.kolahal.control

import com.mtwabpin.kolahal.control.ControlArgs.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlArgsTest {
    @Test fun volume() {
        assertEquals(Level.Percent(40), ControlArgs.volume("40"))
        assertEquals(Level.Percent(40), ControlArgs.volume("40%"))
        assertEquals(Level.Percent(0), ControlArgs.volume("0"))
        assertEquals(Level.Percent(100), ControlArgs.volume("100"))
        assertEquals(Level.Up, ControlArgs.volume("up"))
        assertEquals(Level.Down, ControlArgs.volume("-"))
        assertEquals(Level.Mute, ControlArgs.volume("MUTE"))
        assertEquals(Level.Unmute, ControlArgs.volume("unmute"))
        assertNull(ControlArgs.volume(null))
        assertNull(ControlArgs.volume("101"))
        assertNull(ControlArgs.volume("-5"))
        assertNull(ControlArgs.volume("loud"))
    }

    @Test fun brightness() {
        assertEquals(Level.Auto, ControlArgs.brightness("auto"))
        assertEquals(Level.Percent(75), ControlArgs.brightness("75"))
        assertEquals(Level.Up, ControlArgs.brightness("up"))
        assertNull(ControlArgs.brightness("mute"))
        assertNull(ControlArgs.brightness("200"))
    }

    @Test fun steps() {
        assertEquals(0, ControlArgs.toSteps(0, 15))
        assertEquals(15, ControlArgs.toSteps(100, 15))
        assertEquals(8, ControlArgs.toSteps(50, 15))
        assertEquals(128, ControlArgs.toSteps(50, 255))
        assertEquals(100, ControlArgs.toPercent(15, 15))
        assertEquals(53, ControlArgs.toPercent(8, 15))
        assertEquals(0, ControlArgs.toPercent(3, 0))
    }

    @Test fun duration() {
        assertEquals(30_000L, ControlArgs.duration("30s"))
        assertEquals(60_000L, ControlArgs.duration("1m"))
        assertEquals(120_000L, ControlArgs.duration("2min"))
        assertEquals(3_600_000L, ControlArgs.duration("1h"))
        assertEquals(90_000L, ControlArgs.duration("90"))
        assertNull(ControlArgs.duration("0s"))
        assertNull(ControlArgs.duration("soon"))
        assertNull(ControlArgs.duration(null))
    }

    @Test fun formatting() {
        assertEquals("1:23", ControlArgs.clock(83_000))
        assertEquals("1:02:03", ControlArgs.clock(3_723_000))
    }

    @Test fun onOff() {
        assertEquals(true, ControlArgs.onOff("on"))
        assertEquals(false, ControlArgs.onOff("OFF"))
        assertNull(ControlArgs.onOff(null))
        assertNull(ControlArgs.onOff("toggle"))
    }

    @Test fun suggestions() {
        assertEquals(listOf("play" to "media play", "pause" to "media pause", "prev" to "media prev"), ControlCommands.suggest("media", "p"))
        assertEquals(emptyList<Pair<String, String>>(), ControlCommands.suggest("torch", "on"))
    }

    /** A number too big to multiply is refused, not wrapped round to a few minutes. */
    @Test fun aHugeDurationIsRefused() {
        assertEquals(366L * 86_400 * 1000, ControlArgs.duration("8784h"))
        assertNull(ControlArgs.duration("8785h"))
        assertNull(ControlArgs.duration("2562047788015216h"))
        assertNull(ControlArgs.duration("99999999999999999999s"))
    }
}
