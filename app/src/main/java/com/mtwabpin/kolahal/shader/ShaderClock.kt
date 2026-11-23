/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

/**
 * When an `@animated` shader's time moves, so a frame loop costs power only when someone watches:
 * not in battery saver, with "Remove animations" on, when the phone is hot or nearly out of battery,
 * and not after a minute without a touch (unless it is charging).
 *
 * While it moves, how often it is drawn ([frameMs]): `@fps` times a second, or [FPS] for a while after a
 * touch on a shader that reads the touch (`@settles`, [Pace]).
 */
object ShaderClock {
    /** How many frames a second an animated shader draws at most, and without `@fps`. */
    const val FPS = 30

    /** Without a touch for this long, it stands still, unless the phone charges. */
    const val IDLE_MS = 60_000L

    /** Below this battery percent, it stands still, unless the phone charges. */
    const val LOW_BATTERY = 15

    /** The longest step of the clock from one frame to the next, unless the frames are further apart: a longer gap is a pause. */
    const val PAUSE_MS = 250L

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

    /** How often a shader that moves is drawn, from its header ([ShaderMeta.fps], [ShaderMeta.settles]) and what it reads. */
    data class Pace(
        /** Frames a second while nothing touches it. */
        val fps: Int = FPS,
        /** How long after a touch the touch still changes the picture. */
        val settlesMs: Long = IDLE_MS,
        /** It reads the touch (`touch`, `touchAge` or `pressed`): drawn [FPS] times a second for [settlesMs] after one. */
        val readsTouch: Boolean = false,
        /**
         * Its only clock is the touch (`touchAge`, without `time` or `progress`), and the header says when the
         * touch settles: it is drawn only for [settlesMs] after a touch.
         */
        val onlyAfterTouch: Boolean = false,
    ) {
        companion object {
            /** A shader without `@fps` and `@settles`. */
            val DEFAULT = Pace()

            /** [meta]'s pace. A one-off shader (`@once`) keeps the default. */
            fun of(meta: ShaderMeta): Pace {
                if (meta.once != null) return DEFAULT
                val reads = meta.uses("touch") || meta.uses("touchAge") || meta.uses(ShaderMeta.PRESSED)
                val touchClock = meta.animated && meta.uses("touchAge") && !meta.uses("time") && !meta.uses(ShaderMeta.PROGRESS)
                return Pace(
                    fps = meta.fps ?: FPS,
                    settlesMs = meta.settles?.let { (it * 1000f).toLong() } ?: IDLE_MS,
                    readsTouch = reads,
                    onlyAfterTouch = touchClock && meta.settles != null,
                )
            }
        }
    }

    /**
     * The time from one frame to the next, in ms, for a shader whose time moves ([moves]); null when it stands
     * still until the next touch ([Pace.onlyAfterTouch]). [sinceTouchMs]: since the last touch on it, null
     * before the first (turning the shader on isn't one).
     */
    fun frameMs(pace: Pace, sinceTouchMs: Long?): Long? {
        val settling = sinceTouchMs != null && sinceTouchMs < pace.settlesMs
        if (pace.onlyAfterTouch && !settling) return null
        return 1_000L / (if (pace.readsTouch && settling) FPS else pace.fps)
    }

    /** Whether a touch now would bring the next frame sooner than [frameMs] from the last: then it shouldn't wait. */
    fun touchHurries(pace: Pace, frameMs: Long): Boolean = frameMs(pace, 0L)?.let { it < frameMs } ?: false

    /** How far the clock moves for [gapMs] between two frames [frameMs] apart: a long gap (a pause) is one step, so nothing jumps. */
    fun step(gapMs: Long, frameMs: Long): Long = gapMs.coerceIn(0L, maxOf(PAUSE_MS, 2 * frameMs))
}
