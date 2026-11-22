/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

/**
 * Judges a shader by the time the GPU spends on its frames, one [frame] at a time (pure; the host gets
 * the times from the window's frame metrics).
 * - Too slow: for [SLOW_FOR] frames in a row, one in ten of the last [WINDOW] takes the GPU longer
 *   than [budgetNs] ([budgetFor]): the phone can't keep up with the shader, and stays that way.
 * - Stalling: [STALLS] of the last [WINDOW] frames take the GPU longer than [STALL_NS] each.
 * - Proven: [PROVEN] good frames in all. It has then shown that it runs, so a later crash isn't
 *   blamed on it ([ShaderSafety]).
 *
 * A phone that doesn't measure the GPU's time is judged by stalls only, and only very long ones
 * ([UNMEASURED_STALL_NS]): a frame's whole time also has the wait for the screen in it, which a
 * 120 Hz phone does all the time.
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
    private var measured = 0
    private var slowStreak = 0
    private var good = 0
    private var proven = false

    /**
     * Counts one frame: [gpuNs], what the GPU spent on it (null when the phone doesn't measure it),
     * and [totalNs], what it took from its start to its end.
     */
    fun frame(gpuNs: Long?, totalNs: Long): Verdict {
        seen++
        if (seen <= WARMUP) return Verdict.Fine
        val at = (seen - WARMUP - 1) % WINDOW
        costs[at] = gpuNs ?: 0L
        stalls[at] = if (gpuNs != null) gpuNs > STALL_NS else totalNs > UNMEASURED_STALL_NS
        if (stalls.count { it } >= STALLS) return Verdict.Stalled(gpuNs ?: totalNs)
        if (gpuNs != null) measured++
        // Slow only when it stays slow: the percentile of a full window, over the budget for a while.
        if (gpuNs != null && measured >= WINDOW) {
            val slow = percentile()
            slowStreak = if (slow > budgetNs) slowStreak + 1 else 0
            if (slowStreak >= SLOW_FOR) return Verdict.Slow(slow)
        }
        val fine = if (gpuNs != null) gpuNs <= budgetNs else totalNs <= UNMEASURED_STALL_NS
        if (fine) good++
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
        const val WARMUP = 60

        /** How many frames in a row the shader must stay over its budget to be turned off. */
        const val SLOW_FOR = 120

        /** The good frames after which a shader is proven. */
        const val PROVEN = 120

        /** A frame that takes the GPU longer than this is a stall. */
        const val STALL_NS = 250_000_000L

        /** Where the phone doesn't measure the GPU, a whole frame that takes longer than this is a stall. */
        const val UNMEASURED_STALL_NS = 1_000_000_000L

        /** The least a frame may cost the GPU: about 30 frames a second, which still feels smooth. */
        const val LEAST_BUDGET_NS = 33_333_333L

        /** This many stalls among the last [WINDOW] frames turn the shader off. */
        const val STALLS = 3

        /**
         * The GPU budget of a display: two of its frames, as one may be a frame late without the eye
         * noticing, and at least [LEAST_BUDGET_NS]. A fast screen shows a shader at 30 frames a second
         * or more well enough, and drops nothing that a person asked for.
         */
        fun budgetFor(refreshRate: Float): Long =
            maxOf((2_000_000_000.0 / refreshRate.coerceIn(30f, 240f)).toLong(), LEAST_BUDGET_NS)
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
