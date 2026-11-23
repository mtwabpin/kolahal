/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import java.util.Locale
import kotlin.math.roundToInt

/**
 * A number typed in place of a slider's value ([TypeValueDialog]): what the text reads as, and the line under
 * the box that says the range.
 * - Spaces around the number are ignored; spaces inside it are not a number.
 * - A comma reads as a decimal point, as some keyboards give it, and the minus sign (−) as a hyphen.
 * - A whole number has no decimal point and no comma: `1,000` for a whole number is not read as 1.
 */
internal object TypedNumber {
    private val WHOLE = Regex("""^-?\d{1,9}$""")
    private val DECIMAL = Regex("""^-?(\d{1,9}([.,]\d*)?|[.,]\d+)$""")

    /** The whole number [text] says, or null when it says none or one outside [range]. For a setting. */
    fun whole(text: String, range: IntRange): Int? =
        clean(text).takeIf(WHOLE::matches)?.toIntOrNull()?.takeIf { it in range }

    /**
     * The number [text] says, or null when it says none or one outside [min] to [max]. With [whole], it can't
     * have decimals. For a shader's parameter.
     */
    fun read(text: String, min: Float, max: Float, whole: Boolean): Float? {
        val t = clean(text)
        if (!(if (whole) WHOLE else DECIMAL).matches(t)) return null
        val n = t.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
        if (n !in min..max) return null
        // "-0" is 0.
        return if (n == 0f) 0f else n
    }

    /** The line under the box: `0 to 60`. */
    fun range(range: IntRange): String = "${range.first} to ${range.last}"

    /** The line under the box: `0 to 8`, or `0.0 to 2.0` when it can have decimals. */
    fun range(min: Float, max: Float, whole: Boolean): String = "${end(min, whole)} to ${end(max, whole)}"

    /** One end of a range: whole, or with one or two decimals. */
    private fun end(value: Float, whole: Boolean): String {
        if (whole) return value.roundToInt().toString()
        val text = String.format(Locale.ROOT, "%.2f", value).trimEnd('0')
        return if (text.endsWith('.')) text + "0" else text
    }

    private fun clean(text: String) = text.trim().replace('−', '-')
}
