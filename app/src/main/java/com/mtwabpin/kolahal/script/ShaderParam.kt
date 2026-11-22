/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Something about a shader that people can change, from a line of its header:
 * - `// @param amount 0.0..2.0 = 1.0 Amount of snow`: a number in a range. Without a decimal point in the
 *   line, whole numbers.
 * - `// @param flakes round|star = round Shape of the flakes`: one of some words. The shader gets its place, from 0.
 *
 * - `@follow weather.rain` or `@follow battery / 20` at the end of either: the value it follows until people pick another
 *   ([ShaderValues]), and the formula for it.
 *
 * The uniform of its name, a `float`, holds its value.
 */
class ShaderParam private constructor(
    val name: String,
    val title: String,
    val min: Float,
    val max: Float,
    /** The value until it is changed. */
    val default: Float,
    val whole: Boolean,
    /** The words to choose from; empty for a number. */
    val choices: List<String>,
    /** The id of the value it follows at first, if any. */
    val follow: String?,
    /** The formula for that value, if it has one. */
    val formula: String?,
) {
    /** The value that [text] says: a number, taken into the range, or one of the words. Null when it says none. */
    fun parse(text: String): Float? {
        if (choices.isNotEmpty()) {
            return choices.indexOfFirst { it.equals(text, ignoreCase = true) }.takeIf { it >= 0 }?.toFloat()
        }
        val n = text.toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
        return clean(n)
    }

    /** [value] as this can have it: in its range, and whole if it has to be. */
    fun clean(value: Float): Float =
        value.coerceIn(min, max).let { if (whole || choices.isNotEmpty()) it.roundToInt().toFloat() else it }

    /** [value] in words, as [parse] reads it: at most two decimals. */
    fun text(value: Float): String {
        val v = clean(value)
        if (choices.isNotEmpty()) return choices[v.roundToInt()]
        if (whole) return v.roundToInt().toString()
        return String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }
    }

    /** What the value [text] that it follows makes it: a number from 0 to 1 over its range, or its word of that name. Null: it can't have it. */
    fun followed(text: String): Float? {
        val n = text.toFloatOrNull()?.takeIf { it.isFinite() } ?: return if (choices.isEmpty()) null else parse(text)
        return clean(min + n.coerceIn(0f, 1f) * (max - min))
    }

    /** What it can be: `0..2`, or its words. */
    val range: String get() = if (choices.isNotEmpty()) choices.joinToString(" · ") else "${text(min)}..${text(max)}"

    companion object {
        /** The header's word for it. */
        const val DIRECTIVE = "param"

        /** The most a shader can have. */
        const val MAX = 8

        /** The most words to choose from: they share one row. */
        const val MAX_CHOICES = 4

        private val LINE = Regex("""^([A-Za-z_]\w{0,39})\s+(\S+)\s*=\s*(\S+)\s*(.*)$""")
        private val FOLLOW = Regex("""(?:^|\s+)@follow\s+(\S.*?)\s*$""")
        private val RANGE = Regex("""^(-?\d+(?:\.\d+)?)\.\.(-?\d+(?:\.\d+)?)$""")
        private val CHOICE = Regex("""^[A-Za-z0-9][A-Za-z0-9-]{0,19}$""")

        /** Reads what follows `@param`. Null when it can't be read. */
        fun read(line: String): ShaderParam? {
            val (name, values, first, rest) = LINE.find(line.trim())?.destructured ?: return null
            val followed = FOLLOW.find(rest)?.groupValues?.get(1)?.let { ShaderValues.parse(it) }
            val (follow, formula) = followed ?: (null to null)
            val words = rest.replace(FOLLOW, "")
            val title = words.trim().ifEmpty { name.replace('_', ' ').replaceFirstChar { it.uppercase() } }
            RANGE.find(values)?.let { m ->
                val min = m.groupValues[1].toFloat()
                val max = m.groupValues[2].toFloat()
                val default = first.toFloatOrNull() ?: return null
                if (min >= max || default !in min..max) return null
                return ShaderParam(name, title, min, max, default, whole = '.' !in values.replace("..", "") && '.' !in first, choices = emptyList(), follow = follow, formula = formula)
            }
            val choices = values.split('|')
            if (choices.size !in 2..MAX_CHOICES || choices.distinctBy { it.lowercase() }.size != choices.size) return null
            if (!choices.all(CHOICE::matches)) return null
            val default = choices.indexOf(first).takeIf { it >= 0 } ?: return null
            return ShaderParam(name, title, 0f, choices.lastIndex.toFloat(), default.toFloat(), whole = true, choices = choices, follow = follow, formula = formula)
        }
    }
}
