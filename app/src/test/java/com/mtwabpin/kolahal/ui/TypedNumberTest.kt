package com.mtwabpin.kolahal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A number typed in place of a slider: whole for a setting, with decimals where a shader's parameter has them. */
class TypedNumberTest {
    private val spacing = 0..40

    @Test
    fun aWholeNumberInTheRange() {
        assertEquals(12, TypedNumber.whole("12", spacing))
        assertEquals(0, TypedNumber.whole("0", spacing))
        assertEquals(40, TypedNumber.whole("40", spacing))
        assertEquals(7, TypedNumber.whole("007", spacing))
    }

    @Test
    fun outsideTheRangeIsNothing() {
        assertNull(TypedNumber.whole("41", spacing))
        assertNull(TypedNumber.whole("-1", spacing))
        assertNull(TypedNumber.whole("2", 5..60))
        assertNull(TypedNumber.whole("9999999999", spacing))
    }

    @Test
    fun emptyIsNothing() {
        assertNull(TypedNumber.whole("", spacing))
        assertNull(TypedNumber.whole("   ", spacing))
        assertNull(TypedNumber.read("", 0f, 2f, whole = false))
        assertNull(TypedNumber.read(" ", 0f, 2f, whole = false))
    }

    @Test
    fun spacesAroundAreIgnoredButNotInside() {
        assertEquals(7, TypedNumber.whole(" 7 ", spacing))
        assertEquals(1.5f, TypedNumber.read(" 1.5 ", 0f, 2f, whole = false))
        assertNull(TypedNumber.whole("1 2", spacing))
        assertNull(TypedNumber.read("1 .5", 0f, 2f, whole = false))
    }

    @Test
    fun aWholeNumberHasNoDecimals() {
        assertNull(TypedNumber.whole("5.0", spacing))
        assertNull(TypedNumber.whole("5,5", spacing))
        // Not 1: a comma that groups thousands is not a decimal point here.
        assertNull(TypedNumber.whole("1,000", 0..5000))
        assertNull(TypedNumber.read("3.5", 1f, 8f, whole = true))
        assertEquals(3f, TypedNumber.read("3", 1f, 8f, whole = true))
    }

    @Test
    fun aCommaIsADecimalPoint() {
        assertEquals(1.5f, TypedNumber.read("1,5", 0f, 2f, whole = false))
        assertEquals(0.5f, TypedNumber.read(",5", 0f, 2f, whole = false))
        assertEquals(1f, TypedNumber.read("1,", 0f, 2f, whole = false))
        assertNull(TypedNumber.read("1,2,3", 0f, 2f, whole = false))
        assertNull(TypedNumber.read("1.2.3", 0f, 2f, whole = false))
    }

    @Test
    fun decimalsAndTheEnds() {
        assertEquals(0.25f, TypedNumber.read("0.25", 0f, 2f, whole = false))
        assertEquals(0f, TypedNumber.read("0", 0f, 2f, whole = false))
        assertEquals(2f, TypedNumber.read("2", 0f, 2f, whole = false))
        assertEquals(0.5f, TypedNumber.read("0.5", 0.5f, 1.5f, whole = false))
        assertEquals(1.5f, TypedNumber.read("1.5", 0.5f, 1.5f, whole = false))
        assertNull(TypedNumber.read("2.01", 0f, 2f, whole = false))
        assertNull(TypedNumber.read("0.4", 0.5f, 1.5f, whole = false))
    }

    @Test
    fun aMinusSignWhereTheRangeStartsBelowZero() {
        assertEquals(-0.5f, TypedNumber.read("-0.5", -1f, 1f, whole = false))
        assertEquals(-0.5f, TypedNumber.read("-,5", -1f, 1f, whole = false))
        // The minus sign some keyboards give.
        assertEquals(-0.5f, TypedNumber.read("−0.5", -1f, 1f, whole = false))
        assertEquals(-3f, TypedNumber.read("-3", -5f, 5f, whole = true))
        assertNull(TypedNumber.read("-0.5", 0f, 2f, whole = false))
        assertNull(TypedNumber.read("--1", -5f, 5f, whole = true))
        assertNull(TypedNumber.read("1-", -5f, 5f, whole = true))
        // Zero, not minus zero.
        assertEquals(0f, TypedNumber.read("-0", -1f, 1f, whole = false))
        assertEquals(0, TypedNumber.whole("-0", spacing))
    }

    @Test
    fun whatIsNoNumber() {
        for (text in listOf("abc", "1e1", "NaN", "Infinity", "+5", "0x10", "5f", "-", ".", ",")) {
            assertNull(text, TypedNumber.read(text, -100f, 100f, whole = false))
            assertNull(text, TypedNumber.whole(text, -100..100))
        }
    }

    @Test
    fun theRangeLine() {
        assertEquals("0 to 60", TypedNumber.range(0..60))
        assertEquals("0.0 to 2.0", TypedNumber.range(0f, 2f, whole = false))
        assertEquals("0.5 to 1.5", TypedNumber.range(0.5f, 1.5f, whole = false))
        assertEquals("0.25 to 0.75", TypedNumber.range(0.25f, 0.75f, whole = false))
        assertEquals("-1 to 8", TypedNumber.range(-1f, 8f, whole = true))
    }
}
