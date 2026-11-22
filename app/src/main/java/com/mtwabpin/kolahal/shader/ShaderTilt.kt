/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The `tilt` uniform: how far the phone leans, about -1 to 1 each way. [x] is positive when the
 * screen's right side is lower, [y] when its bottom edge is.
 *
 * It is fed with gravity ([gravity]), as the phone's sensor gives it, and works out the lean:
 * - Compared with how the phone is held: what counts as level follows the phone slowly ([LEVEL_S]),
 *   so a hand, a stand or lying down doesn't leave everything off to one side. How it is held at the
 *   start is level, and turning the screen starts over.
 * - On the screen's own sides, whichever way it is turned.
 * - Smoothed ([SMOOTH_S]), so it glides.
 * - [x] and [y] change only when the lean has changed by more than [DEADBAND], and at most [FPS]
 *   times a second. So a phone on a table changes nothing, whatever noise its sensor has.
 *
 * When nothing feeds it any more, [ease] takes it back to 0.
 *
 * Plain Kotlin, and a reading allocates nothing (ShaderTiltTest). For one thread at a time.
 */
class ShaderTilt {
    /** The uniform's first number: the lean to the right. */
    var x = 0f
        private set

    /** The uniform's second number: the lean toward the bottom edge. */
    var y = 0f
        private set

    /** Nothing leans, and nothing is on its way back to 0. */
    val resting: Boolean get() = x == 0f && y == 0f && smoothX == 0f && smoothY == 0f

    // Gravity as it was last read, less the shakes when it has them. It points up, on the phone's own axes.
    private var pullX = 0f
    private var pullY = 0f
    private var pullZ = 0f

    // The up that counts as level, of length 1, on the phone's own axes.
    private var levelX = 0f
    private var levelY = 0f
    private var levelZ = 0f

    /** Whether there is a level to compare with. */
    private var held = false

    /** For how long, in seconds. */
    private var heldFor = 0f

    /** How the screen is turned: a `Surface.ROTATION_*`. */
    private var turned = 0
    private var readNs = 0L
    private var shownNs = NOT_YET

    private var smoothX = 0f
    private var smoothY = 0f

    /** Starts over: the next reading is level. What it shows stays, so nothing jumps. */
    fun restart() {
        held = false
        shownNs = NOT_YET
    }

    /**
     * One reading at [atNs] (a clock in ns that only goes forward): [gx], [gy] and [gz] in m/s², on
     * the phone's own axes as Android has them. [shaky]: it is the accelerometer's, with the hand's
     * shakes in it, and is steadied first. [rotation]: how the screen is turned, a `Surface.ROTATION_*`.
     *
     * True when [x] and [y] changed.
     */
    fun gravity(gx: Float, gy: Float, gz: Float, shaky: Boolean, rotation: Int, atNs: Long): Boolean {
        if (!(gx + gy + gz).isFinite()) return false
        val seconds = if (held) ((atNs - readNs) / 1e9f).coerceIn(0f, LONGEST_S) else 0f
        readNs = atNs
        if (shaky && held) {
            val steady = gone(seconds, STEADY_S)
            pullX += (gx - pullX) * steady
            pullY += (gy - pullY) * steady
            pullZ += (gz - pullZ) * steady
        } else {
            pullX = gx
            pullY = gy
            pullZ = gz
        }
        val length = sqrt(pullX * pullX + pullY * pullY + pullZ * pullZ)
        // A phone that falls has no up.
        if (length < LEAST_PULL) return false
        val upX = pullX / length
        val upY = pullY / length
        val upZ = pullZ / length
        heldFor += seconds
        // The accelerometer's first reading may be a shake, and so no level.
        val settled = !shaky || heldFor >= SETTLE_S
        follow(upX, upY, upZ, rotation, if (settled) gone(seconds, LEVEL_S) else 1f)

        val right = across(upX, upY)
        val top = along(upX, upY)
        val levelRight = across(levelX, levelY)
        val levelTop = along(levelX, levelY)
        // To the right: how much less of up the right side has than when level. Toward the bottom edge:
        // how far up has turned about the screen's width, which works flat, upright and overhead alike.
        val lean = (levelRight - right) / FULL
        val depth = sqrt(levelTop * levelTop + levelZ * levelZ).coerceAtLeast(LEAST_DEPTH)
        val pitch = (levelZ * top - levelTop * upZ) / depth / FULL
        val glide = gone(seconds, SMOOTH_S)
        smoothX += (lean.coerceIn(-1f, 1f) - smoothX) * glide
        smoothY += (pitch.coerceIn(-1f, 1f) - smoothY) * glide

        if (abs(smoothX - x) <= DEADBAND && abs(smoothY - y) <= DEADBAND) return false
        if (atNs - shownNs < FRAME_NS) return false
        shownNs = atNs
        x = smoothX
        y = smoothY
        return true
    }

    /** One step of [seconds] back to 0, for when nothing is read any more. True while there is more to do. */
    fun ease(seconds: Float): Boolean {
        val left = 1f - gone(seconds, EASE_S)
        smoothX *= left
        smoothY *= left
        if (abs(smoothX) <= DEADBAND && abs(smoothY) <= DEADBAND) {
            smoothX = 0f
            smoothY = 0f
        }
        x = smoothX
        y = smoothY
        return !resting
    }

    /** Takes what counts as level [nearer] to up, 0 to 1; right there when there is none yet or the screen turned. */
    private fun follow(upX: Float, upY: Float, upZ: Float, rotation: Int, nearer: Float) {
        if (held && rotation == turned) {
            levelX += (upX - levelX) * nearer
            levelY += (upY - levelY) * nearer
            levelZ += (upZ - levelZ) * nearer
            val length = sqrt(levelX * levelX + levelY * levelY + levelZ * levelZ)
            if (length > LEAST_LEVEL) {
                levelX /= length
                levelY /= length
                levelZ /= length
                return
            }
            // The phone was turned right over, and halfway there is no level between the two.
        }
        levelX = upX
        levelY = upY
        levelZ = upZ
        turned = rotation
        held = true
        heldFor = 0f
    }

    /** How far ([px], [py]), on the phone's own axes, points to the screen's right. */
    private fun across(px: Float, py: Float): Float = when (turned) {
        1 -> -py
        2 -> -px
        3 -> py
        else -> px
    }

    /** How far ([px], [py]), on the phone's own axes, points to the screen's top. */
    private fun along(px: Float, py: Float): Float = when (turned) {
        1 -> px
        2 -> -py
        3 -> -px
        else -> py
    }

    companion object {
        /** The uniform's name. */
        const val NAME = "tilt"

        /** How many numbers it holds. */
        const val FLOATS = 2

        /** The most changes a second: as many as a moving shader is drawn. */
        const val FPS = ShaderClock.FPS

        /** What counts as level follows the phone with this time constant, in seconds. */
        const val LEVEL_S = 6f

        /** The lean follows the phone with this time constant, in seconds. */
        const val SMOOTH_S = 0.12f

        /** The accelerometer's readings are steadied with this time constant, in seconds. */
        const val STEADY_S = 0.25f

        /** For so long at the start, in seconds, a phone with only an accelerometer is level as it is. */
        const val SETTLE_S = 1f

        /** Back to 0 with this time constant, in seconds: about two thirds of a second from 1. */
        const val EASE_S = 0.15f

        /** A change of the lean this small is none: about a third of a degree. */
        const val DEADBAND = 0.012f

        /** The lean that counts as 1, as a sine: 30 degrees. */
        const val FULL = 0.5f

        /** The time between two readings never counts as longer than this, in seconds: a pause isn't a leap. */
        private const val LONGEST_S = 0.25f

        /** Less of a pull than this, in m/s², is no gravity. */
        private const val LEAST_PULL = 1f

        /** A level shorter than this points nowhere. */
        private const val LEAST_LEVEL = 0.1f

        /** A phone that stands on its long edge has no lower bottom edge, so `y` fades there. */
        private const val LEAST_DEPTH = 0.35f
        private const val FRAME_NS = 1_000_000_000L / FPS

        /** Long before any reading. Half of the least number, so the time since it can be counted. */
        private const val NOT_YET = Long.MIN_VALUE / 2

        /** How much of a way is gone after [seconds], with the time constant [constant]. */
        private fun gone(seconds: Float, constant: Float): Float = 1f - exp(-seconds / constant)
    }
}
