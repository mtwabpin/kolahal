/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

/**
 * How a card's one foot line shares its width when it has the script's footer
 * ([com.mtwabpin.kolahal.widgets.WidgetBase.footer]): in order, what comes first (the script's note,
 * a versus card's last line, a picture's title), the footer, and Kolahal's own age or "Failed 10:42"
 * at the end. The age is never cut, so the script's words can't pass for Kolahal's; the footer is
 * cut with "…" first, then what comes first. Pure, so it is unit tested.
 */
internal object FootRoom {
    /** The room between two parts of the line, in dp. */
    const val GAP_DP = 8f

    /** The narrowest a footer is drawn, in dp: cut to less, it says nothing, so it goes. */
    const val FOOTER_LEAST_DP = 24f

    /** The words before the age that its short form leaves out ([shortAge]). */
    private const val UPDATED = "Updated "

    /**
     * The age's short form, for a line that is short: "3 min ago" for "Updated 3 min ago", "just
     * now" for "Updated just now". Null where there is none: a failure ("Failed 10:42") and a day
     * ("Updated yesterday") keep their words, as a day alone would not say what it is.
     */
    fun shortAge(footer: WidgetWords.Footer): String? {
        if (footer.failed || !footer.text.startsWith(UPDATED)) return null
        val rest = footer.text.removePrefix(UPDATED)
        return rest.takeIf { it.endsWith(" ago") || it == "just now" }
    }

    /** What the line draws: the footer or not, and the age whole or in its [short] form. */
    data class Plan(val footer: Boolean, val short: Boolean)

    /**
     * The plan of a foot line [lineDp] wide whose parts want [leadDp] (what comes first, 0 for
     * nothing), [footerDp] (the footer) and [ageDp] (the age, 0 for none) as they are, and whose
     * age is [shortDp] in its short form (null for none).
     * - The age keeps its words whole. It takes its short form only where the line can't hold the
     *   footer whole beside it and leaves it less than half the line: a 2×2 card, not a 4×2 one.
     * - What comes first is cut only where it can't fit beside the age alone.
     * - The footer gets the rest, cut with "…", and goes where that is under [FOOTER_LEAST_DP].
     */
    fun plan(lineDp: Float, leadDp: Float, footerDp: Float, ageDp: Float, shortDp: Float?): Plan {
        fun footerRoom(age: Float): Float {
            val ageRoom = if (age > 0f) age + GAP_DP else 0f
            val lead = leadDp.coerceAtMost((lineDp - ageRoom).coerceAtLeast(0f))
            return lineDp - ageRoom - if (leadDp > 0f) lead + GAP_DP else 0f
        }
        val short = shortDp != null && ageDp > 0f && footerRoom(ageDp) < minOf(footerDp, lineDp / 2)
        val room = footerRoom(if (short) shortDp!! else ageDp)
        return Plan(footer = room >= minOf(footerDp, FOOTER_LEAST_DP), short = short)
    }
}
