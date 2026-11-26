/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.gesture

/**
 * The screen lock's disclosure ([com.mtwabpin.kolahal.ui.home.LockAccessDialog], Google Play's prominent disclosure for
 * an accessibility service): when it shows, and what each of its buttons does. Every way to Android's accessibility
 * settings for the lock goes through `allow lock`: the prompt, the setup checklist's row and the tour run it, and a
 * tap on the line that `lock`, the quick action and a lock gesture leave while the service is off runs it too.
 */
object LockAccess {
    enum class Step {
        /** The disclosure shows; nothing opens yet. */
        ASK,

        /** Android's accessibility settings open. */
        OPEN_SETTINGS,

        /** Nothing opens and nothing changes: the lock still says it needs the service. */
        NOTHING,
    }

    /** `allow lock`: with the service off, the disclosure first. With it on, Android's page at once, to turn it off. */
    fun onAllow(serviceOn: Boolean): Step = if (serviceOn) Step.OPEN_SETTINGS else Step.ASK

    /** The disclosure's answer: only Allow opens Android's page. */
    fun onAnswer(allow: Boolean): Step = if (allow) Step.OPEN_SETTINGS else Step.NOTHING
}
