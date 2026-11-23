/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

/**
 * When the idle screensaver opens (`screen.saver`, `screen.saver-after`). Home asks [next] each
 * time something about it changes, and again when the answer says to wait. It is pure, so a JVM
 * test can walk it through time.
 *
 * The screensaver opens after the set minutes (or half a minute, [waitMs]) with no touch and no key, while home is in front
 * with its log page showing and nothing over it. It never opens while the screen is off, in
 * battery saver, or with Android's "Remove animations" on.
 */
object ScreenSaver {
    /** The screens it can be: `screen.saver`'s values other than off. */
    val SCREENS = listOf("matrix", "clock", "pipes", "decode")

    /**
     * What home is doing.
     * - [inFront]: home is resumed and its window has the focus (no other app, no dialog).
     * - [logPage]: the log page shows, not the widgets page.
     * - [sheetOpen]: a long-press sheet, a menu or a box is open over home.
     * - [typing]: the prompt has text in it, or the keyboard is up (except with keyboard.on-home,
     *   which keeps it up on an idle home).
     * - [screenOpen]: a screen (`matrix`, `top`…) already runs.
     */
    data class Home(
        val inFront: Boolean,
        val logPage: Boolean,
        val sheetOpen: Boolean = false,
        val typing: Boolean = false,
        val screenOpen: Boolean = false,
    ) {
        /** Home is idle and nothing covers it, so a screensaver may come. */
        val ready: Boolean get() = inFront && logPage && !sheetOpen && !typing && !screenOpen
    }

    /** The phone's state: the screen is on, battery saver is on, "Remove animations" is on. */
    data class Phone(val screenOn: Boolean, val batterySaver: Boolean, val animationsOff: Boolean) {
        /** Whether the phone allows a screensaver now. */
        val allows: Boolean get() = screenOn && !batterySaver && !animationsOff
    }

    /** What home does next. */
    sealed interface Next {
        /** Open [screen] now. */
        data class Open(val screen: String) : Next

        /** Ask again in [ms]. */
        data class Wait(val ms: Long) : Next

        /** Nothing, until something about home changes (it asks again then). */
        data object Never : Next
    }

    /**
     * What to do at [now] (uptime ms), with [saver] and [afterMinutes] from the settings, when
     * nothing has touched home since [quietSince]: the later of the last touch or key and the
     * moment home became [Home.ready]. [minuteMs] is a minute's length; a test makes it short.
     * - Off, or home not ready: [Next.Never].
     * - Not quiet long enough yet: wait for the rest.
     * - Long enough, but the phone doesn't allow it ([Phone.allows]): nothing opens; it asks again
     *   in a minute, as battery saver may end while home stays idle.
     */
    /**
     * How long home must be quiet: `screen.saver-after` minutes, and half a minute for 0, the least
     * the setting has. [minuteMs] is a minute's length.
     */
    fun waitMs(afterMinutes: Int, minuteMs: Long = 60_000L): Long =
        if (afterMinutes <= 0) minuteMs / 2 else afterMinutes * minuteMs

    fun next(
        saver: String,
        afterMinutes: Int,
        quietSince: Long,
        now: Long,
        home: Home,
        phone: Phone,
        minuteMs: Long = 60_000L,
    ): Next {
        if (saver !in SCREENS || !home.ready) return Next.Never
        val due = quietSince + waitMs(afterMinutes, minuteMs)
        if (now < due) return Next.Wait(due - now)
        if (!phone.allows) return Next.Wait(minuteMs)
        return Next.Open(saver)
    }
}
