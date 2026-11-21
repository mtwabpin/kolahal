/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

/**
 * Lets a tap through once per [windowMs] for each item. Quick repeated taps on the same item
 * count as one: five taps on a pinned script in a second run it once, not five times.
 * - Each item has its own window, so tapping one item never holds back another.
 * - [runOrWait] keeps one tap waiting instead, for a button where a second tap means a second run
 *   (a widget's button: two quick taps on a counter's + add two).
 * - [later] runs a block after a delay in milliseconds (on the main thread; a fake one in tests).
 * - [now] is a clock in milliseconds (elapsed time on the phone; a fake one in tests).
 * - Use it from the main thread only.
 */
class TapGate(
    private val windowMs: Long,
    private val later: (Long, () -> Unit) -> Unit = { ms, block -> android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(block, ms) },
    private val now: () -> Long,
) {
    private val last = HashMap<String, Long>()
    /** The items with a tap waiting for their window to end ([runOrWait]). */
    private val waiting = HashSet<String>()

    /** True if [item]'s tap may run now, and starts its window. False while its window is open. */
    fun allow(item: String): Boolean {
        val t = now()
        val before = last[item]
        if (before != null && t - before < windowMs) return false
        last[item] = t
        return true
    }

    /**
     * Runs [action] now if [item]'s window is closed, and starts the window. While it is open, the
     * tap waits: [action] runs when the window ends, and starts a new one. One tap waits at most; a
     * third quick tap is dropped.
     */
    fun runOrWait(item: String, action: () -> Unit) {
        val t = now()
        val before = last[item]
        if (before == null || t - before >= windowMs) {
            last[item] = t
            action()
            return
        }
        if (!waiting.add(item)) return
        later(windowMs - (t - before)) {
            waiting.remove(item)
            last[item] = now()
            action()
        }
    }
}
