/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.settings

/**
 * Counts taps on Settings → About → Version, which turn Lab on ([com.mtwabpin.kolahal.data.Lab]) as taps on
 * Android's build number turn on Developer options: [TAPS] in one go. A pause longer than [PAUSE_MS] starts the
 * count again. The first taps say nothing; the last few count down ([SHOWN]), and the last one turns Lab on.
 * While Lab is on, each tap says so. Pure: the caller gives the time and whether Lab is on.
 */
internal class LabTaps(private val taps: Int = TAPS, private val pauseMs: Long = PAUSE_MS) {
    private var count = 0
    private var last = 0L

    /** What a tap does: the message it shows (null: none), and whether it turns Lab on. */
    data class Tap(val message: String?, val turnsOn: Boolean = false)

    /** A tap at [nowMs] (a clock in ms) while Lab is [on] or off. */
    fun tap(nowMs: Long, on: Boolean): Tap {
        if (on) {
            count = 0
            return Tap(ALREADY_ON)
        }
        count = if (count > 0 && nowMs - last in 0..pauseMs) count + 1 else 1
        last = nowMs
        val left = taps - count
        return when {
            left <= 0 -> {
                count = 0
                Tap(TURNED_ON, turnsOn = true)
            }
            left <= SHOWN -> Tap(more(left))
            else -> Tap(null)
        }
    }

    companion object {
        /** Taps that turn Lab on. */
        const val TAPS = 8

        /** The longest pause between two taps of one go, in ms. */
        const val PAUSE_MS = 3_000L

        /** How many taps before the last are counted down. */
        const val SHOWN = 3

        const val TURNED_ON = "Lab is on"
        const val ALREADY_ON = "Lab is already on"

        /** What a tap says while [left] more turn Lab on. */
        fun more(left: Int): String = if (left == 1) "1 more tap to turn on Lab" else "$left more taps to turn on Lab"
    }
}
