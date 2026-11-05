package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UnitsTest {
    private fun conv(s: String): String? = Units.parse(s)?.let { (a, f, t) -> Units.convert(a, f, t) }

    @Test fun temperature() {
        assertEquals("100 °C = 212 °F", conv("100 c to f"))
        // Where the two scales meet, below zero.
        assertEquals("-40 °C = -40 °F", conv("-40 c to f"))
    }
    @Test fun kelvin() = assertEquals("0 K = -273.15 °C", conv("0 k in c"))
    @Test fun mass() = assertEquals("1 kg = 2.2046 lb", conv("1 kg in lb"))
    @Test fun data() = assertEquals("1 GiB = 1,073.7418 MB", conv("1 gib in mb"))
    @Test fun currencyIsParsedButNotConverted() {
        assertEquals(Triple(100.0, "usd", "inr"), Units.parse("100 usd in inr"))
        assertNull(conv("100 usd in inr"))
    }
    @Test fun notAConversion() = assertNull(Units.parse("2^10 / 4"))

    /** `in` is a unit and the connecting word: where it stands says which. */
    @Test fun inches() {
        assertEquals("5 in = 12.7 cm", conv("5 in to cm"))
        assertEquals("5 in = 12.7 cm", conv("5 in in cm"))
        assertEquals("5 in = 12.7 cm", conv("5 in as cm"))
        assertEquals("5 in = 12.7 cm", conv("5 in -> cm"))
        assertEquals("5 in = 12.7 cm", conv("5in to cm"))
        assertEquals("5 in = 12.7 cm", conv("5 IN TO CM"))
        assertEquals("12 cm = 4.7244 in", conv("12 cm to in"))
        assertEquals("12 cm = 4.7244 in", conv("12 cm in in"))
        assertEquals("1 ft = 12 in", conv("1 ft in in"))
        assertEquals("5 in = 5 in", conv("5 in in in"))
    }

    @Test fun inchesReadBackAsPrinted() {
        val printed = conv("1 ft to inch")!!.substringAfter("= ")
        assertEquals("12 in", printed)
        assertEquals("12 in = 1 ft", conv("$printed to ft"))
    }

    /** What an answer prints is the first thing to copy back, so each label reads as a unit. */
    @Test fun whatAnAnswerPrintsReadsBack() {
        assertEquals("1 ha = 2.4711 ac", conv("1 ha to acre"))
        assertEquals("2 ac = 0.8094 ha", conv("2 ac to ha"))
        for (label in listOf("m²", "km²", "ft²", "ac", "in")) assertEquals("3 $label = 3 $label", conv("3 $label to $label"))
    }

    @Test fun inAsTheConnectingWordAndInchesByOtherNames() {
        assertEquals("5 ft = 152.4 cm", conv("5 ft in cm"))
        assertEquals("5 km = 3.1069 mi", conv("5 km as mi"))
        assertEquals("5 km = 3.1069 mi", conv("5 km -> mi"))
        assertEquals("5 in = 12.7 cm", conv("5 inch to cm"))
        assertEquals("5 in = 12.7 cm", conv("5 inches in cm"))
        assertEquals("5 in = 12.7 cm", conv("5\" in cm"))
        assertEquals("1 ft = 12 in", conv("1 ft to inches"))
    }

    /**
     * Nothing to convert to, a side with no unit, or two units that don't convert: not answered here,
     * so the line goes on to arithmetic and then Lua, as `5 km` alone does.
     */
    @Test fun linesThatAreNoConversionAreLeftAlone() {
        listOf(
            "5 in", "5in", "5 in in", "5 in to", "5 ft in", "5 km", "5 km to",
            "5 in cm", "5 to cm", "in to cm", "in in cm", "in", "in in",
            "3 in a row", "1 in 10", "5 in to go", "5 to in cm", "2 in in 1",
            "5 in to kg", "5 kg in in", "5 in in usd", "100 usd in in", "100 inr to in",
        ).forEach { line ->
            assertNull(line, conv(line))
            assertNull(line, Calc.answer(line))
            Units.parse(line)?.let { (_, from, to) -> assertFalse(line, Units.looksLikeCurrency(from) && Units.looksLikeCurrency(to)) }
        }
    }

    @Test fun commasGroupDigits() {
        assertEquals(1500.0, Units.number("1,500")!!, 1e-9)
        assertEquals(12500.0, Units.number("12,500")!!, 1e-9)
        assertEquals(123500.0, Units.number("123,500")!!, 1e-9)
        assertEquals(1000000.0, Units.number("1,000,000")!!, 1e-9)
        assertEquals(100000.0, Units.number("1,00,000")!!, 1e-9)
        assertEquals(1234567.0, Units.number("12,34,567")!!, 1e-9)
        assertEquals(12345678.0, Units.number("1,23,45,678")!!, 1e-9)
        assertEquals(100000.25, Units.number("1,00,000.25")!!, 1e-9)
        assertEquals(-1500.0, Units.number("-1,500")!!, 1e-9)
    }

    @Test fun aCommaThatCantBeGroupingIsADecimalComma() {
        assertEquals(0.5, Units.number("0,500")!!, 1e-9)
        assertEquals(0.5, Units.number("0,5")!!, 1e-9)
        assertEquals(1.5, Units.number("1,5")!!, 1e-9)
        assertEquals(2.5, Units.number("2,50")!!, 1e-9)
        assertEquals(-0.5, Units.number("-0,500")!!, 1e-9)
        assertEquals(1.5, Units.number("1,5000")!!, 1e-9)
        assertEquals(1000.5, Units.number("1000,5")!!, 1e-9)
        assertEquals(1234.5, Units.number("1234,500")!!, 1e-9)
    }

    @Test fun anyOtherCommaIsRejectedRatherThanMisread() {
        listOf(
            "1,000,00", "1,00,00", "123,45,678", "0,500,000", "0,500.5", "1,5.5", "1.000,5",
            "1,", ",5", "1,,5", "01,500", "00,500", "007,5",
        ).forEach { assertNull(it, Units.number(it)) }
    }

    @Test fun commasInAConversion() {
        assertEquals("0.5 kg = 500 g", conv("0,500 kg in g"))
        assertEquals("1,500 kg = 1,500,000 g", conv("1,500 kg in g"))
        assertEquals("100,000 m = 100 km", conv("1,00,000 m to km"))
        assertEquals("1,234,567 m = 1,234.567 km", conv("12,34,567 m to km"))
        assertNull(Units.parse("1,5,3 kg in g"))
    }
}
