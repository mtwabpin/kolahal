package com.mtwabpin.kolahal.terminal

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/**
 * Unit conversion for the `=` calculator: `= 5 km in mi`, `= 72 f to c`, `= 3 cups in ml`.
 * Every unit maps to a factor of its dimension's base unit (temperature is special-cased).
 */
object Units {
    private class Unit(val dimension: String, val factor: Double, val label: String)

    private val units: Map<String, Unit> = buildMap {
        fun add(dimension: String, factor: Double, label: String, vararg names: String) {
            // What an answer prints has to read back, so the label is a name too.
            (names.asList() + label.lowercase()).forEach { put(it, Unit(dimension, factor, label)) }
        }
        // length (m)
        add("length", 1e-3, "mm", "mm", "millimeter", "millimeters", "millimetre")
        add("length", 1e-2, "cm", "cm", "centimeter", "centimeters", "centimetre")
        add("length", 1.0, "m", "m", "meter", "meters", "metre", "metres")
        add("length", 1e3, "km", "km", "kilometer", "kilometers", "kilometre", "kms")
        add("length", 0.0254, "in", "inch", "inches", "\"")
        add("length", 0.3048, "ft", "ft", "foot", "feet", "'")
        add("length", 0.9144, "yd", "yd", "yard", "yards")
        add("length", 1609.344, "mi", "mi", "mile", "miles")
        add("length", 1852.0, "nmi", "nmi", "nautical")
        // mass (kg)
        add("mass", 1e-6, "mg", "mg", "milligram", "milligrams")
        add("mass", 1e-3, "g", "g", "gram", "grams")
        add("mass", 1.0, "kg", "kg", "kilogram", "kilograms", "kilo", "kilos")
        add("mass", 1000.0, "t", "t", "tonne", "tonnes", "ton")
        add("mass", 0.028349523125, "oz", "oz", "ounce", "ounces")
        add("mass", 0.45359237, "lb", "lb", "lbs", "pound", "pounds")
        add("mass", 6.35029318, "st", "st", "stone")
        // volume (l)
        add("volume", 1e-3, "ml", "ml", "milliliter", "milliliters", "millilitre")
        add("volume", 1.0, "l", "l", "liter", "liters", "litre", "litres")
        add("volume", 0.0049289215938, "tsp", "tsp", "teaspoon", "teaspoons")
        add("volume", 0.014786764781, "tbsp", "tbsp", "tablespoon", "tablespoons")
        add("volume", 0.24, "cup", "cup", "cups")
        add("volume", 0.0295735295625, "floz", "floz", "fl-oz")
        add("volume", 0.473176473, "pt", "pt", "pint", "pints")
        add("volume", 3.785411784, "gal", "gal", "gallon", "gallons")
        // speed (m/s)
        add("speed", 1.0, "m/s", "m/s", "mps")
        add("speed", 1 / 3.6, "km/h", "km/h", "kmh", "kph")
        add("speed", 0.44704, "mph", "mph")
        add("speed", 0.514444, "kn", "kn", "knot", "knots")
        // data (bytes)
        add("data", 1.0, "B", "b", "byte", "bytes")
        add("data", 1e3, "kB", "kb", "kilobyte", "kilobytes")
        add("data", 1e6, "MB", "mb", "megabyte", "megabytes")
        add("data", 1e9, "GB", "gb", "gigabyte", "gigabytes")
        add("data", 1e12, "TB", "tb", "terabyte", "terabytes")
        add("data", 1024.0, "KiB", "kib")
        add("data", 1048576.0, "MiB", "mib")
        add("data", 1073741824.0, "GiB", "gib")
        // time (s)
        add("time", 1e-3, "ms", "ms", "millisecond", "milliseconds")
        add("time", 1.0, "s", "s", "sec", "secs", "second", "seconds")
        add("time", 60.0, "min", "min", "mins", "minute", "minutes")
        add("time", 3600.0, "h", "h", "hr", "hrs", "hour", "hours")
        add("time", 86400.0, "d", "d", "day", "days")
        add("time", 604800.0, "wk", "wk", "week", "weeks")
        add("time", 31557600.0, "yr", "yr", "year", "years")
        // area (m²)
        add("area", 1.0, "m²", "m2", "sqm")
        add("area", 1e6, "km²", "km2", "sqkm")
        add("area", 0.09290304, "ft²", "ft2", "sqft")
        add("area", 4046.8564224, "ac", "acre", "acres")
        add("area", 10000.0, "ha", "ha", "hectare", "hectares")
        // temperature (handled by convertTemperature)
        add("temp", 1.0, "°C", "c", "°c", "celsius")
        add("temp", 1.0, "°F", "f", "°f", "fahrenheit")
        add("temp", 1.0, "K", "k", "kelvin")
    }

    private val pattern = Regex("""^\s*(-?[\d.,]+)\s*([^\s\d][^\s]*)\s+(?:in|to|as|->)\s+([^\s]+)\s*$""", RegexOption.IGNORE_CASE)

    /**
     * Commas group digits in Western (`1,000,000`) or Indian style (`12,34,567`), and a dot may
     * follow (`1,000.5`). A number can't start with a zero that way, so `0,500` has a decimal comma,
     * as has any other single comma with no dot (`1,5`, `2,50`). Anything else ambiguous (`01,500`,
     * `1,5,3`) is rejected rather than misread.
     */
    internal fun number(s: String): Double? = when {
        ',' !in s -> s.toDoubleOrNull()
        Regex("""^-?[1-9](\d{0,2}(,\d{3})+|\d?(,\d{2})+,\d{3})(\.\d+)?$""").matches(s) -> s.replace(",", "").toDoubleOrNull()
        Regex("""^-?(0|[1-9]\d*),\d+$""").matches(s) -> s.replace(',', '.').toDoubleOrNull()
        else -> null
    }

    /**
     * Parsed `amount from to` if [text] looks like a conversion (units may be unknown, e.g. currencies).
     * A word's place says what it is, so `in` can be the unit and the connector (`5 in in cm`). `5 in`
     * alone has nothing to convert to, like `5 km`, so it isn't one.
     */
    fun parse(text: String): Triple<Double, String, String>? {
        val m = pattern.find(text) ?: return null
        val amount = number(m.groupValues[1]) ?: return null
        return Triple(amount, m.groupValues[2].lowercase(), m.groupValues[3].lowercase())
    }

    /** "5 km = 3.107 mi", or null if either unit is unknown or the dimensions differ. */
    fun convert(amount: Double, from: String, to: String): String? {
        val a = units[from] ?: return null
        val b = units[to] ?: return null
        if (a.dimension != b.dimension) return null
        val result = if (a.dimension == "temp") convertTemperature(amount, a.label, b.label) else amount * a.factor / b.factor
        return "${format(amount)} ${a.label} = ${format(result)} ${b.label}"
    }

    private fun convertTemperature(v: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "°F" -> (v - 32) * 5 / 9
            "K" -> v - 273.15
            else -> v
        }
        return when (to) {
            "°F" -> celsius * 9 / 5 + 32
            "K" -> celsius + 273.15
            else -> celsius
        }
    }

    /**
     * An amount as a conversion prints it: in full with separators up to 10^15, as far as the
     * calculator writes numbers in full (`1 GB = 1,000,000,000 B`); a whole number exactly, others
     * to at most 4 decimals and 15 significant digits, past which a double's digits are noise.
     * Smaller than 0.0001, or from 10^15, in scientific notation.
     */
    fun format(v: Double): String = when {
        v == 0.0 -> "0"
        abs(v) !in 1e-4..<1e15 -> String.format(Locale.ROOT, "%.4g", v)
        v % 1.0 == 0.0 -> String.format(Locale.ROOT, "%,.0f", v)
        else -> BigDecimal(v).round(MathContext(15)).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
            .let { String.format(Locale.ROOT, "%,.${it.scale().coerceAtLeast(0)}f", it) }
    }

    /** Three-letter currency codes, for deciding whether to try an exchange-rate lookup. */
    fun looksLikeCurrency(code: String) = code.length == 3 && code.all { it.isLetter() }
}
