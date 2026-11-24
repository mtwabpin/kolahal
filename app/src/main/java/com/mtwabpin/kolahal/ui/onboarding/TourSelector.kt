/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.onboarding

/**
 * The tour turns the app selector on when its page opens, so a new person can try it at once.
 * - It does that only the first time the page opens. After that, the selector stays as the person
 *   leaves it, on the page or in Settings, also when they come back to the page or run the tour again.
 * - Settings can't tell "never set" from "set to off": off is the default, and a default isn't saved.
 *   So the tour keeps a flag of its own, [SHOWN], in home's preferences
 *   ([com.mtwabpin.kolahal.data.Stores.LAUNCHER]).
 */
internal object TourSelector {
    /** Set once the app selector's page has opened. */
    const val SHOWN = "tour_selector_shown"

    /** Whether opening the page turns the selector on: when the page hasn't opened before ([shown]) and the selector is off. */
    fun turnsOn(shown: Boolean, on: Boolean) = !shown && !on
}
