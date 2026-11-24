/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.data

/**
 * What the home style (`home.style`: gui or tty) changes outside home's drawing. The style can
 * change while the app runs (`config set home.style tty`), so each check reads it when it is made.
 */
object HomeStyles {
    /** `home.style`'s value for the character grid. */
    const val TTY = "tty"

    /** Whether `home.style`'s [value] is the character grid. Anything else is the gui style. */
    fun isTty(value: String): Boolean = value == TTY

    /** Whether home is drawn on the character grid now (`home.style tty`). */
    fun ttyNow(config: Config): Boolean = isTty(config[Keys.HOME_STYLE])

    /**
     * Settings that do nothing in the tty style. There Settings doesn't show them and `config`
     * doesn't offer them; their stored values stay for the gui style.
     * - `log.item-spacing`: the grid has no gap between a list's items ([TTY_ITEM_GAP_DP]).
     * - `log.taller-taps`: a line of the grid is one row of cells, tappable or not.
     */
    val GUI_ONLY: Set<String> = setOf(Keys.LOG_ITEM_SPACING.key, Keys.LOG_TALLER_TAPS.key)

    /** Whether [key] is a setting of the style: every setting is, but [GUI_ONLY] in the tty style. */
    fun applies(key: ConfigKey<*>, tty: Boolean): Boolean = !tty || key.key !in GUI_ONLY

    /**
     * The gap between a list's items in the tty style's log, in dp, whatever `log.item-spacing`
     * says: none, so a list prints line under line, as in a terminal. The gap above each command
     * and its output (`log.spacing`) still applies.
     */
    const val TTY_ITEM_GAP_DP = 0

    /**
     * The gap above each command and its output in the tty style's log, in rows of the grid: one
     * empty line when `log.spacing` ([spacing], in dp) is anything but 0, and none at 0. The grid
     * has whole lines only, so the setting's dp are the gui style's.
     */
    fun ttyGroupGapRows(spacing: Int): Int = if (spacing > 0) 1 else 0
}
