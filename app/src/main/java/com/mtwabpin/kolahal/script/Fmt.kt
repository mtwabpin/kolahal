package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.StyledText

/**
 * `fmt.tag`, `fmt.link`, `fmt.bar`, `fmt.bars` and `fmt.ranges`, for both engines. They pass
 * their arguments as Kotlin values (strings, numbers, lists and maps) and a way to turn a value
 * into text as the script would show it ([text]). A wrong argument is an error for the script that
 * says what to pass.
 */
internal class Fmt(private val text: (Any?) -> String) {

    /** `fmt.tag(text)` in the accent colour, or `fmt.tag(role, text)`. */
    fun tag(args: List<Any?>): String {
        if (args.size < 2) return StyledText.tag(StyledText.Colour.ACCENT, text(args.getOrNull(0)))
        return StyledText.tag(role("fmt.tag", args[0]), text(args[1]))
    }

    /** `fmt.link(text, url)`. The address is checked when the line is logged. */
    fun link(args: List<Any?>): String {
        val url = args.getOrNull(1) as? String ?: throw IllegalArgumentException("fmt.link expects (text, url): the text it shows, then the link's address")
        return StyledText.link(url, text(args.getOrNull(0)))
    }

    /** `fmt.bar(value, { max, color })`. */
    fun bar(args: List<Any?>): String {
        val value = number("fmt.bar", args.getOrNull(0), "a value")
        val opts = options("fmt.bar", args.getOrNull(1), setOf("max", "color"))
        val max = opts["max"]?.let { number("fmt.bar", it, "max") } ?: 1.0
        return StyledText.bar(value, max, opts["color"]?.let { role("fmt.bar", it) })
    }

    /** `fmt.bars(rows, { max, color, unit })`: each row `{ label, value, text? }`. */
    fun bars(args: List<Any?>): String {
        val call = "fmt.bars"
        val opts = options(call, args.getOrNull(1), setOf("max", "color", "unit"))
        val rows = rows(call, args.getOrNull(0), "{ label, value, text? }").map { r ->
            StyledText.BarRow(text(r.getOrNull(0)), number(call, r.getOrNull(1), "each row's value"), r.getOrNull(2)?.let(text))
        }
        return StyledText.bars(rows, opts["max"]?.let { number(call, it, "max") }, opts["color"]?.let { role(call, it) }, opts["unit"]?.let(text).orEmpty())
    }

    /** `fmt.ranges(rows, { min, max, color, unit })`: each row `{ label, low, high, lowText?, highText? }`. */
    fun ranges(args: List<Any?>): String {
        val call = "fmt.ranges"
        val opts = options(call, args.getOrNull(1), setOf("min", "max", "color", "unit"))
        val rows = rows(call, args.getOrNull(0), "{ label, low, high }").map { r ->
            StyledText.RangeRow(
                text(r.getOrNull(0)), number(call, r.getOrNull(1), "each row's low"), number(call, r.getOrNull(2), "each row's high"),
                r.getOrNull(3)?.let(text), r.getOrNull(4)?.let(text),
            )
        }
        return StyledText.ranges(
            rows, opts["min"]?.let { number(call, it, "min") }, opts["max"]?.let { number(call, it, "max") },
            opts["color"]?.let { role(call, it) }, opts["unit"]?.let(text).orEmpty(),
        )
    }

    private fun role(call: String, value: Any?): StyledText.Colour = (value as? String)?.let(StyledText.Colour::named)
        ?: throw IllegalArgumentException("$call: no colour '${text(value)}'; use ${StyledText.Colour.entries.joinToString { it.role }}")

    private fun number(call: String, value: Any?, what: String): Double = when (value) {
        is Number -> value.toDouble()
        else -> throw IllegalArgumentException("$call: $what must be a number, not ${if (value == null) "nothing" else "'${text(value)}'"}")
    }

    /** The options table, with only the keys [known]. Another key is an error, so a typo (`colour`) doesn't pass unseen. */
    private fun options(call: String, value: Any?, known: Set<String>): Map<String, Any?> {
        if (value == null) return emptyMap()
        val map = value as? Map<*, *> ?: if (value is List<*> && value.isEmpty()) emptyMap<String, Any?>() else
            throw IllegalArgumentException("$call: its options must be a table/object: { ${known.joinToString()} }")
        return map.entries.associate { (k, v) ->
            val key = k.toString()
            if (key !in known) throw IllegalArgumentException("$call: no option '$key'; use ${known.joinToString()}")
            key to v
        }
    }

    /** A list of rows, each a list. An empty table (Lua can't tell it from an empty map) is no rows. */
    private fun rows(call: String, value: Any?, row: String): List<List<Any?>> {
        val bad = "$call expects a list of rows, each $row"
        val list = when (value) {
            is List<*> -> value
            is Map<*, *> -> if (value.isEmpty()) emptyList<Any?>() else throw IllegalArgumentException(bad)
            else -> throw IllegalArgumentException(bad)
        }
        return list.map { r ->
            when (r) {
                is List<*> -> r
                is Map<*, *> -> if (r.isEmpty()) emptyList() else throw IllegalArgumentException(bad)
                else -> throw IllegalArgumentException(bad)
            }
        }
    }
}
