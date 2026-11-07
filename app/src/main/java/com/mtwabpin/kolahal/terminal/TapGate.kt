package com.mtwabpin.kolahal.terminal

/**
 * Lets a tap through once per [windowMs] for each item. Quick repeated taps on the same item
 * count as one: five taps on a pinned script in a second run it once, not five times.
 * - Each item has its own window, so tapping one item never holds back another.
 * - [now] is a clock in milliseconds (elapsed time on the phone; a fake one in tests).
 * - Use it from the main thread only.
 */
class TapGate(private val windowMs: Long, private val now: () -> Long) {
    private val last = HashMap<String, Long>()

    /** True if [item]'s tap may run now, and starts its window. False while its window is open. */
    fun allow(item: String): Boolean {
        val t = now()
        val before = last[item]
        if (before != null && t - before < windowMs) return false
        last[item] = t
        return true
    }
}
