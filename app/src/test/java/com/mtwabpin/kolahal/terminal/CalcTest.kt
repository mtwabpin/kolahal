package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CalcTest {
    private fun a(text: String) = Calc.answer(text)

    @Test fun arithmetic() {
        assertEquals("1024", a("2^10"))
        assertEquals("52", a("17 * 3 + 1"))
        assertEquals("14", a("2 + 3 × 4"))
        assertEquals("20", a("(2 + 3) * 4"))
        assertEquals("2.5", a("5 ÷ 2"))
        assertEquals("-4", a("-2^2"))
        assertEquals("512", a("2^3^2"))
        assertEquals("0.5", a("2 ** -1"))
        assertEquals("12", a("3 x 4"))
        assertEquals("12", a("3x4"))
        assertEquals("12", a("3X4"))
        assertEquals("12", a("3 x4"))
        assertEquals("24", a("2x3x4"))
        assertEquals("13.5", a("3x4.5"))
        assertEquals("40", a("10x4"))
        assertEquals("6", a("(1+2)x2"))
        assertEquals("6.28318530718", a("pi x2"))
        assertEquals("48", a("4!x2"))
        // Not after a value, or hexadecimal: left to Lua.
        assertNull(a("x4"))
        assertNull(a("0x10"))
        assertEquals("0.3", a("0.1 + 0.2"))
        assertEquals("1000", a("1e3"))
        assertEquals("-1", a("3 − 4"))
    }

    @Test fun functionsAndConstants() {
        assertEquals("1.41421356237", a("sqrt(2)"))
        assertEquals("3", a("sqrt 9"))
        assertEquals("3.14159265359", a("pi"))
        assertEquals("6.28318530718", a("2π"))
        assertEquals("27", a("3(4+5)"))
        assertEquals("120", a("5!"))
        assertEquals("0.5", a("sin 30°"))
        assertEquals("2", a("log 100"))
        assertEquals("7", a("max(1, 7, 3)"))
        assertEquals("3", a("round(2.5)"))
    }

    /** As on a phone's calculator. */
    @Test fun percents() {
        assertEquals("12", a("15% of 80"))
        assertEquals("92", a("80 + 15%"))
        assertEquals("68", a("80 - 15%"))
        assertEquals("0.15", a("15%"))
        assertEquals("1", a("10 % 3"))
        assertEquals("1", a("10 mod 3"))
        assertEquals("12", a("15 % of 80"))
        assertEquals("92", a("80 + 15 %"))
        assertEquals("68", a("80 - 15 %"))
        assertEquals("0.15", a("15 %"))
    }

    /** `%` before a number or parenthesis is the remainder, however it is spaced. */
    @Test fun remainders() {
        assertEquals("1", a("10%3"))
        assertEquals("1", a("10 %3"))
        assertEquals("1", a("10% 3"))
        assertEquals("1", a("10 % (3)"))
        assertEquals("1", a("10%(3)"))
        assertEquals("2", a("-10 % 3"))
        assertEquals("-2", a("10 % (-3)"))
        assertEquals("-2", a("10%(-3)"))
    }

    /** Apart from the number before it, and before a sign stuck to what it signs, `%` is `mod`, sign rule and all. */
    @Test fun remainderBySignedNumber() {
        assertEquals("-2", a("10 mod -3"))
        assertEquals("-2", a("10 % -3"))
        assertEquals("-2", a("10 %-3"))
        assertEquals("-2", a("10  %  -3"))
        assertEquals("-2", a("10 % −3"))
        assertEquals("-2", a("10 % -(3)"))
        assertEquals("1", a("10 % +3"))
        assertEquals("-1", a("-10 % -3"))
        assertEquals("-0.25", a("10.25 % -.5"))
        assertEquals("-1", a("10 % -3 + 1"))
        assertEquals("-1", a("2 * 10 % -3"))
        assertEquals("-4", a("10 % -3x2"))
        assertEquals("1", a("5 % -3 % 2"))
        assertEquals("can't divide by zero", assertThrows(ArithmeticException::class.java) { a("10 % -0") }.message)
    }

    /** Stuck to the number before it, or before a sign that stands alone, `%` is a percent and the sign adds or subtracts. */
    @Test fun percentThenSign() {
        assertEquals("-2.9", a("10% - 3"))
        assertEquals("-2.9", a("10%-3"))
        assertEquals("-2.9", a("10% -3"))
        assertEquals("-2.9", a("10%- 3"))
        assertEquals("-2.9", a("10 % - 3"))
        assertEquals("-2.9", a("10 %- 3"))
        assertEquals("-2.9", a("10%-(3)"))
        assertEquals("3.1", a("10% + 3"))
        assertEquals("3.1", a("10%+3"))
        assertEquals("3.1", a("10% +3"))
        assertEquals("3.1", a("10 % + 3"))
        assertEquals("-9.5", a("50% - 10"))
        assertEquals("-9.5", a("50%-10"))
        assertEquals("65", a("80 - 15% - 3"))
        assertEquals("65", a("80 - 15%-3"))
        assertEquals("65", a("80 - 15% -3"))
        // A word after the sign isn't a number: as with `10 % pi`, it stays a percent.
        assertEquals("-3.04159265359", a("10 % -pi"))
    }

    /** The same numbers as in a conversion: [Units.number]. */
    @Test fun commasInNumbers() {
        assertEquals("1500", a("1,500"))
        assertEquals("1000000", a("1,000,000"))
        assertEquals("100000", a("1,00,000"))
        assertEquals("1234567", a("12,34,567"))
        assertEquals("0.5", a("0,500"))
        assertEquals("1.5", a("1,5"))
        assertEquals("2.5", a("2,50"))
        assertEquals("1000.5", a("1,000.5"))
        for ((typed, value) in listOf(
            "1,500" to 1500.0, "1,000,000" to 1_000_000.0, "1,00,000" to 100_000.0, "12,34,567" to 1_234_567.0, "0,500" to 0.5,
            "1,5" to 1.5, "2,50" to 2.5, "1,000.5" to 1000.5, "1234,500" to 1234.5, "1,23,45,678" to 12_345_678.0,
        )) {
            assertEquals(typed, value, Calc.eval(typed))
            assertEquals(typed, value, Units.number(typed))
        }
        assertEquals("3000", a("1,500 * 2"))
        assertEquals("4", a("1,5 + 2,5"))
        assertEquals("-1500", a("-1,500"))
        assertEquals("4506", a("(1,500 + 2) * 3"))
        assertEquals("1500", a("(1,500)"))
        assertEquals("150", a("10% of 1,500"))
        assertEquals("-2", a("1,000 % -3"))
        assertEquals("38.7298334621", a("sqrt 1,500"))
        assertEquals("6.25", a("2,5^2"))
    }

    @Test fun commasInNumbersBesideATimesSign() {
        assertEquals("4500", a("3x1,500"))
        assertEquals("4500", a("1,500x3"))
        assertEquals("10", a("2,5x4"))
        assertEquals("4500", a("3 x (1,500)"))
        assertEquals("4500", a("3x(1,500)"))
        assertNull(a("0x10,5"))
    }

    /** In a function's parentheses a comma separates values, whatever the digits around it. */
    @Test fun commasBetweenAFunctionsValues() {
        assertEquals("2", a("max(1,2)"))
        assertEquals("500", a("max(1,500)"))
        assertEquals("500", a("max(1, 500)"))
        assertEquals("1", a("min(1,500,2)"))
        assertEquals("2", a("max(1,000,2)"))
        assertEquals("500", a("MAX (1,500)"))
        assertEquals("500", a("max((1),500)"))
        assertEquals("1000", a("2max(1,500)"))
        assertEquals("22.360679775", a("sqrt(max(1,500))"))
        // Parentheses closed, a comma is in a number again.
        assertEquals("2000", a("max(1,500) + 1,500"))
        assertEquals("2000", a("(max(1,500) + 1,500)"))
        // Functions of one value don't take two, nor one with a comma.
        assertNull(a("sqrt(1,500)"))
        assertNull(a("log(8,2)"))
        assertNull(a("atan(1,2)"))
        assertNull(a("max((1,500),2)"))
    }

    /** A comma this can't read as part of a number stays with Lua, code or not. */
    @Test fun otherCommasAreLeftAlone() {
        assertNull(a("1,5,3"))
        assertNull(a("1,000,00"))
        assertNull(a("0,500,000"))
        assertNull(a("1,5.5"))
        assertNull(a("1,"))
        assertNull(a(",5"))
        assertNull(a("1,,5"))
        assertNull(a("1, 500"))
        assertNull(a("1 ,500"))
        assertNull(a("1,5 2,5"))
        assertNull(a("1,500e3"))
        assertNull(a("math.max(1,500)"))
        assertNull(a("tostring(1,500)"))
        assertNull(a("{1,500}"))
    }

    @Test fun formatting() {
        assertEquals("0.333333333333", a("1/3"))
        assertEquals("1e+20", a("10^20"))
        assertEquals("0", a("1 - 1"))
    }

    /** Lua code, a conversion and words are someone else's to read. */
    @Test fun notArithmeticIsLeftAlone() {
        assertNull(a("math.sqrt(2)"))
        assertNull(a("os.date(\"%A\")"))
        assertNull(a("5 km in mi"))
        assertNull(a("5 in to cm"))
        assertNull(a("5 in"))
        assertNull(a("% -3"))
        assertNull(a(" % -3"))
        assertNull(a("2 +"))
        assertNull(a("hello"))
        assertNull(a(""))
    }

    /** Halves go up, as a calculator's do, for numbers of any size and with no answer passed on. */
    @Test fun roundTakesHalvesUp() {
        assertEquals("4", a("round(3.5)"))
        assertEquals("-2", a("round(-2.5)"))
        assertEquals("1e+30", a("round(10^30)"))
        // Adding a half and flooring gets these wrong.
        assertEquals("0", a("round(0.49999999999999994)"))
        assertEquals("0", a("round(4503599627370497) - 4503599627370497"))
        assertEquals("not a real number", assertThrows(ArithmeticException::class.java) { a("round(sqrt(-1))") }.message)
    }

    @Test fun noAnswerSaysWhy() {
        assertEquals("can't divide by zero", assertThrows(ArithmeticException::class.java) { a("1/0") }.message)
        assertEquals("not a real number", assertThrows(ArithmeticException::class.java) { a("sqrt(-1)") }.message)
    }
}
