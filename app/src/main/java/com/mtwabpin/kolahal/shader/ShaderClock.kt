/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

/**
 * When an `@animated` shader's time moves, so a frame loop costs power only when someone watches:
 * not in battery saver, with "Remove animations" on, when the phone is hot or nearly out of battery,
 * and not after a minute without a touch (unless it is charging).
 */
object ShaderClock {
    /** How many frames a second an animated shader draws. */
    const val FPS = 30

    /** Without a touch for this long, it stands still, unless the phone charges. */
    const val IDLE_MS = 60_000L

    /** Below this battery percent, it stands still, unless the phone charges. */
    const val LOW_BATTERY = 15

    data class Phone(
        val batterySaver: Boolean,
        val animationsOff: Boolean,
        val hot: Boolean,
        val charging: Boolean,
        val batteryPercent: Int,
    )

    /** Whether time moves now; [sinceTouchMs] is how long home has been untouched. */
    fun moves(phone: Phone, sinceTouchMs: Long): Boolean = !phone.batterySaver &&
            !phone.animationsOff && !phone.hot &&
            (phone.charging || (phone.batteryPercent >= LOW_BATTERY && sinceTouchMs < IDLE_MS))
}
