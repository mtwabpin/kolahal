/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

/**
 * Judges a shader by the time its frames take, one [frame] at a time (pure; the host gets the times
 * from the window's frame metrics).
 * - Too slow: over the last [WINDOW] frames, one in ten takes longer than [budgetNs] (two frames of
 *   the display's refresh rate, [budgetFor]).
 * - Stalling: [STALLS] of them take longer than [STALL_NS] each, from start to end.
 * - Proven: [PROVEN] good frames in all. It has then shown that it runs, so a later crash isn't
 *   blamed on it ([ShaderSafety]).
 *
 * The first [WARMUP] frames don't count: the GPU builds the shader's program in them.
 */
class ShaderWatchdog(val budgetNs: Long) {
    sealed interface Verdict {
        data object Fine : Verdict

        /** It has run well for long enough: said once. */
        data object Proven : Verdict

        /** The time that one frame in ten takes longer than, in ns. */
        data class Slow(val frameNs: Long) : Verdict
        data class Stalled(val frameNs: Long) : Verdict
    }

    private val costs = LongArray(WINDOW)
    private val stalls = BooleanArray(WINDOW)
    private val sorted = LongArray(WINDOW)
    private var seen = 0
    private var good = 0
    private var proven = false

    /**
     * Counts one frame: [costNs], what the GPU spent on it (or what it took in all, when that isn't
     * measured), and [totalNs], what it took from its start to its end.
     */
    fun frame(costNs: Long, totalNs: Long): Verdict {
        seen++
        if (seen <= WARMUP) return Verdict.Fine
        val at = (seen - WARMUP - 1) % WINDOW
        costs[at] = costNs
        stalls[at] = totalNs > STALL_NS
        if (stalls.count { it } >= STALLS) return Verdict.Stalled(totalNs)
        val counted = seen - WARMUP
        if (counted >= WINDOW) {
            val slow = percentile()
            if (slow > budgetNs) return Verdict.Slow(slow)
        }
        if (costNs <= budgetNs && totalNs <= STALL_NS) good++
        if (!proven && good >= PROVEN) {
            proven = true
            return Verdict.Proven
        }
        return Verdict.Fine
    }

    /** The frame cost that one frame in ten of the last [WINDOW] goes over. */
    private fun percentile(): Long {
        costs.copyInto(sorted)
        sorted.sort()
        return sorted[(WINDOW * 9) / 10]
    }

    companion object {
        /** How many frames the rules look back over. */
        const val WINDOW = 60

        /** The frames that don't count after the shader starts: the GPU compiles its program in them. */
        const val WARMUP = 10

        /** The good frames after which a shader is proven. */
        const val PROVEN = 120

        /** A frame that takes longer than this, from start to end, is a stall. */
        const val STALL_NS = 250_000_000L

        /** This many stalls among the last [WINDOW] frames turn the shader off. */
        const val STALLS = 3

        /** The frame budget of a display: two of its frames, as one may be a frame late without the eye noticing. */
        fun budgetFor(refreshRate: Float): Long =
            (2_000_000_000.0 / refreshRate.coerceIn(30f, 240f)).toLong()
    }
}

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
