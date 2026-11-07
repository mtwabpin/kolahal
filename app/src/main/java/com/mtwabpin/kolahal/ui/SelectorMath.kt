package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import java.text.Normalizer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** One row of the selector list: an app and its section (a letter, or the favourites section). */
internal data class SelectorEntry<T>(
    val item: T,
    val key: String,
    /** The section letter shown in the gutter: `★`, `#` or A–Z. */
    val section: Char,
    /** Index of the section in the list. Used for the haptic tick when the section changes. */
    val sectionIndex: Int,
    /** True for the first row of its section. The gutter shows the letter on this row. */
    val sectionStart: Boolean,
    val favourite: Boolean,
)

/**
 * Pure maths for the app selector: building the list, mapping the scrub, and the fisheye. Unit-tested.
 * Positions are in rows unless the name says `px`. Row `i` covers `[i, i + 1)` of the list.
 */
internal object SelectorMath {
    const val OTHER = '#'
    const val FAV = '★'

    /** Section order: `#` first (digits, symbols, other scripts), then A–Z. */
    val LETTERS: List<Char> = listOf(OTHER) + ('A'..'Z').toList()

    /** Returns the section letter of a label: its first letter or digit, without accents. Anything outside A–Z gives `#`. */
    fun letterOf(label: String): Char {
        val first = label.firstOrNull { it.isLetterOrDigit() } ?: return OTHER
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first().uppercaseChar()
        return if (base in 'A'..'Z') base else OTHER
    }

    /** An item with what it is sorted by: its letter's place, its label in lower case and its profile's rank. */
    private class SortKeys<T>(val item: T, val letter: Int, val lower: String, val rank: Int)

    /**
     * Builds the whole list:
     * - the favourites (if any) as a `★` section at the top;
     * - then every item in alphabetical order, in the sections `#`, A–Z.
     * Items with the same label are sorted by [profileRank] (personal first).
     * [favouritesLast] puts the `★` section at the end instead (Plan F). When the strip is at the
     * bottom of the edge, a resting thumb is there.
     */
    fun <T> build(
        items: List<T>,
        label: (T) -> String,
        key: (T) -> String,
        pinned: (T) -> Boolean,
        profileRank: (T) -> Int,
        favouritesLast: Boolean = false,
    ): List<SelectorEntry<T>> {
        // The sort keys are worked out once for each item, not on every comparison:
        // letterOf allocates, and a sort compares each item many times.
        val keyed = items.map { SortKeys(it, LETTERS.indexOf(letterOf(label(it))), label(it).lowercase(), profileRank(it)) }
        val byLabel = compareBy<SortKeys<T>>({ it.lower }, { it.rank })
        val favs = keyed.filter { pinned(it.item) }.sortedWith(byLabel).map { it.item }
        val all = keyed.sortedWith(compareBy<SortKeys<T>> { it.letter }.then(byLabel)).map { it.item }
        val out = ArrayList<SelectorEntry<T>>(favs.size + all.size)
        var section = -1
        fun favourites() = favs.forEachIndexed { i, it ->
            if (i == 0) section++
            out += SelectorEntry(it, "f:" + key(it), FAV, section, i == 0, true)
        }
        if (!favouritesLast) favourites()
        var prev: Char? = null
        for (it in all) {
            val letter = letterOf(label(it))
            val start = letter != prev
            if (start) section++
            prev = letter
            out += SelectorEntry(it, "a:" + key(it), letter, section, start, false)
        }
        if (favouritesLast) favourites()
        return out
    }

    /** Dead band at each end of the strip. The first and last few percent give the first and last letter (with apps: the ends of the list). */
    const val DEAD = 0.04f

    /**
     * Places labels that want to be at [wanted] (px, top to bottom):
     * - no two labels are closer than [gap];
     * - all labels stay within [top]..[bottom];
     * - each label moves as little as possible, and the order stays the same. A crowd spreads around its middle.
     * If they can't all fit, some labels are left out (NaN). Each label kept is at least [gap] below the one before.
     */
    fun labelPositions(wanted: FloatArray, gap: Float, top: Float, bottom: Float): FloatArray {
        val n = wanted.size
        if (n == 0) return wanted
        if ((n - 1) * gap > bottom - top) {
            val out = FloatArray(n) { Float.NaN }
            var last = Float.NEGATIVE_INFINITY
            for (i in 0 until n) {
                val p = wanted[i].coerceIn(top, bottom)
                if (p - last >= gap) {
                    out[i] = p
                    last = p
                }
            }
            return out
        }
        // Least squares with gaps. With q = p - i * gap, the gap rule becomes "q never goes down".
        // Pooling adjacent violators solves this exactly. The bounds then only clamp q.
        val value = FloatArray(n)
        val weight = IntArray(n)
        var blocks = 0
        for (i in 0 until n) {
            value[blocks] = wanted[i] - i * gap
            weight[blocks] = 1
            blocks++
            while (blocks > 1 && value[blocks - 2] > value[blocks - 1]) {
                val w = weight[blocks - 2] + weight[blocks - 1]
                value[blocks - 2] = (value[blocks - 2] * weight[blocks - 2] + value[blocks - 1] * weight[blocks - 1]) / w
                weight[blocks - 2] = w
                blocks--
            }
        }
        val hi = bottom - (n - 1) * gap
        val out = FloatArray(n)
        var k = 0
        for (b in 0 until blocks) {
            repeat(weight[b]) {
                out[k] = value[b].coerceIn(top, hi) + k * gap
                k++
            }
        }
        return out
    }

    /** Number of entries in each section (★, #, A–Z, in list order). [Sections] shares out the strip by these numbers. */
    fun <T> sectionSizes(entries: List<SelectorEntry<T>>): List<Int> {
        val sizes = IntArray((entries.lastOrNull()?.sectionIndex ?: -1) + 1)
        for ((_, _, _, sectionIndex) in entries) sizes[sectionIndex]++
        return sizes.toList()
    }

    /** Start index of each section in [entries], then the end index. Section `i` is `entries[starts[i] until starts[i + 1]]`. */
    fun <T> sectionStarts(entries: List<SelectorEntry<T>>): IntArray {
        val sizes = sectionSizes(entries)
        val out = IntArray(sizes.size + 1)
        for (i in sizes.indices) out[i + 1] = out[i] + sizes[i]
        return out
    }

    /** `selector.browse letters`: the letter changes only when the thumb is this far (in letters) past the end of its stretch. This stops a thumb resting on the border from flickering between two letters. */
    const val STICK = 0.25f

    /**
     * `selector.browse letters`: returns the letter at [pos] along the edge. [pos] is in letters
     * (letter `i` covers `[i, i + 1)`), as [alongLetters] returns it. The result stays [current]
     * until [pos] is [STICK] past either end of its stretch.
     */
    fun letterAt(pos: Float, current: Int, count: Int): Int {
        if (count <= 0) return -1
        val raw = floor(pos).toInt().coerceIn(0, count - 1)
        if (current !in 0 until count) return raw
        return if (pos >= current - STICK && pos < current + 1 + STICK) current else raw
    }

    /**
     * Returns where a letter's [size] apps start (px, the top of the first app), for the letter at [letterY]:
     * - the first app is level with the letter, so the apps run down from the thumb;
     * - if they would go below [bottom], they move up just enough to fit;
     * - they never go above [top].
     */
    fun letterTop(letterY: Float, size: Int, rowPx: Float, top: Float, bottom: Float): Float {
        var start = letterY - rowPx / 2
        if (start + size * rowPx > bottom) start = bottom - size * rowPx
        return max(start, top)
    }

    /**
     * Keeps a list in place when it stays open [shiftPx] away from its normal place. The panel moves
     * back, and extra room at the start of the list (if it was moved down) or at the end (if moved up)
     * makes up the difference. So the list does not move, and it can still scroll to every app.
     * Returns (room at the start, room at the end, the scroll that keeps it in place).
     */
    fun keepInPlace(scrollPx: Float, shiftPx: Float): Triple<Float, Float, Float> =
        Triple(max(0f, shiftPx), max(0f, -shiftPx), scrollPx - min(0f, shiftPx))

    /**
     * `selector.filter`: limits the panel's move so the whole panel stays on screen. The panel is
     * [panelH] tall. [shiftPx] is how far it moved down from its place at the bottom of [listH], the
     * height it may use.
     */
    fun keepOnScreen(shiftPx: Float, panelH: Float, listH: Float): Float = shiftPx.coerceIn(-max(0f, listH - panelH), 0f)

    /** The wave of letters next to the thumb reaches this many letters on each side of the bubble. */
    const val WAVE_REACH = 3f

    /** Letters grow by up to this much toward the peak of the wave. */
    const val WAVE_GROW = 0.6f

    /** How much the wave pushes out a letter that is [x] letters from its centre: 1 at the centre, easing to 0 at [WAVE_REACH]. */
    fun waveOut(x: Float): Float = if (abs(x) >= WAVE_REACH) 0f else (1f + cos(PI.toFloat() * x / WAVE_REACH)) / 2f

    /** [waveOut] summed from the centre to [x] (in letters): how much further from the centre the spread moves a letter. */
    private fun waveIntegral(x: Float): Float {
        val a = min(abs(x), WAVE_REACH)
        return sign(x) * (a + WAVE_REACH / PI.toFloat() * sin(PI.toFloat() * a / WAVE_REACH)) / 2f
    }

    /** How much the wave spreads letters that are [unit] px apart at rest, so that the letters next to its centre sit [room] px from it. */
    fun waveSpread(unit: Float, room: Float): Float =
        if (unit <= 0f) 0f else max(0f, (room / unit - 1f) / waveIntegral(1f))

    /** Where to draw a letter that rests at [p] (px along the strip) when the wave is centred at [c]. [spread] pushes the letters apart around the centre. */
    fun waveY(p: Float, c: Float, unit: Float, spread: Float): Float {
        if (unit <= 0f) return p
        val x = (p - c) / unit
        return c + unit * (x + spread * waveIntegral(x))
    }

    /**
     * Decides which letters of the wave have room to be drawn. The letters are at [ys], drawn at
     * [scales] times their size; at scale 1 they are [gap] px apart. It works outward from the
     * [active] letter. The active letter is in the bubble, so it is never shown. A letter is shown when:
     * - it is at least [clear] px from the bubble, and
     * - it is at least its own size away from the last letter shown.
     */
    fun waveShown(ys: FloatArray, scales: FloatArray, active: Int, gap: Float, clear: Float): BooleanArray {
        val shown = BooleanArray(ys.size)
        if (active !in ys.indices) return shown
        for (step in intArrayOf(-1, 1)) {
            var last = Float.NaN
            var lastScale = 1f
            var i = active + step
            while (i in ys.indices) {
                val offBubble = abs(ys[i] - ys[active]) >= clear
                val offLast = last.isNaN() || abs(ys[i] - last) >= gap * max(scales[i], lastScale)
                if (offBubble && offLast) {
                    shown[i] = true
                    last = ys[i]
                    lastScale = scales[i]
                }
                i += step
            }
        }
        return shown
    }

    /**
     * Where each of [count] letters sits on the strip (0 top … 1 bottom): the middle of its stretch.
     * Every letter gets the same stretch between the dead bands. So a letter with one app is as easy
     * to hit as a letter with many.
     */
    fun letterMiddles(count: Int): FloatArray = FloatArray(count) { DEAD + (it + 0.5f) / count * (1f - 2 * DEAD) }

    /** Converts strip position [t] (0 top … 1 bottom) to a position in letters (letter `i` covers `[i, i + 1)`). The dead bands pin the ends. */
    fun alongLetters(t: Float, count: Int): Float {
        if (count <= 0) return 0f
        return (((t - DEAD) / (1f - 2 * DEAD)).coerceIn(0f, 1f) * count).coerceAtMost(count - 0.01f)
    }

    /**
     * `selector.browse apps` (Plan D): shares the strip out between the sections of the list.
     * [sectionSizes] gives the number of apps in each section, in list order. Each section gets room
     * in proportion to the square root of its size. So a letter with four times the apps gets twice
     * the room, and a letter with one app does not become a thin sliver.
     */
    class Sections(sectionSizes: List<Int>) {
        private val sizes = sectionSizes.toIntArray()
        private val count = sizes.sum()

        /** How many sections there are. */
        val size: Int get() = sizes.size

        /** Each section's share of the strip between the dead bands. The shares add up to 1. */
        val shares: FloatArray = FloatArray(sizes.size) { sqrt(sizes[it].toFloat()) }.let { roots ->
            val sum = roots.sum()
            FloatArray(roots.size) { if (sum > 0f) roots[it] / sum else 0f }
        }

        /** The centre of each section's room, as strip positions (0 top … 1 bottom), in list order. */
        fun middles(): FloatArray {
            val out = FloatArray(sizes.size)
            var acc = 0f
            for (i in sizes.indices) {
                out[i] = DEAD + (acc + shares[i] / 2f) * (1f - 2 * DEAD)
                acc += shares[i]
            }
            return out
        }

        /**
         * Converts strip position [t] (0 top … 1 bottom) to a list position (rows). The dead bands pin
         * the ends of the list. Within a section's share, its apps are spread evenly.
         */
        fun along(t: Float): Float {
            if (count == 0) return 0f
            val u = ((t - DEAD) / (1f - 2 * DEAD)).coerceIn(0f, 1f)
            var acc = 0f
            var start = 0
            for (i in sizes.indices) {
                val w = shares[i]
                if (u <= acc + w || i == sizes.lastIndex) {
                    val f = if (w > 0f) ((u - acc) / w).coerceIn(0f, 1f) else 0f
                    return (start + f * sizes[i]).coerceIn(0f, count - 0.01f)
                }
                acc += w
                start += sizes[i]
            }
            return 0f
        }
    }

    /** Plan C: a slide along the strip slower than this (dp per second) aims instead of gliding (`selector.aim-speed`). */
    const val AIM_SPEED = 100

    /** Plan C: while aiming, the list moves one app for every this many dp of slide (`selector.aim`; 0 means it never aims). */
    const val AIM_STEP = 10

    /** At this many times the aim speed, a slide glides through the whole list again. */
    const val GLIDE_FACTOR = 2.5f

    /** How fast the measured slide speed follows the thumb (ms). This smooths out a jittery touch. */
    const val SPEED_TAU_MS = 60f

    /**
     * How much a slide at [speed] (dp per second) aims instead of gliding: 1 below [aimSpeed], 0 from
     * [GLIDE_FACTOR] times [aimSpeed], with a smooth curve between.
     */
    fun aimWeight(speed: Float, aimSpeed: Float): Float {
        if (aimSpeed <= 0f) return 0f
        val t = ((speed - aimSpeed) / (aimSpeed * (GLIDE_FACTOR - 1f))).coerceIn(0f, 1f)
        return 1f - t * t * (3f - 2f * t)
    }

    /** The slide's speed (dp per second): [smoothed] moved toward the last move's [instant] speed, with time constant [SPEED_TAU_MS]. */
    fun smoothSpeed(smoothed: Float, instant: Float, dtMs: Long): Float =
        smoothed + (instant - smoothed) * (dtMs / SPEED_TAU_MS).coerceIn(0f, 1f)

    /**
     * Plan C: returns the list position (rows) after a slide of [dyPx] along the strip, starting at [at].
     * - Glide: the list goes where the strip maps it ([Sections.along]). [glideFrom] is that place
     *   before the move, [glideTo] after it.
     * - Aim ([weight] 1): the list moves one app per [stepPx] of slide, but never faster than the glide.
     * - In between ([weight] between 0 and 1): the list also drifts back toward the glide's place. So
     *   when the slide speeds up again, the list lands where a glide would put it.
     */
    fun aim(at: Float, glideFrom: Float, glideTo: Float, dyPx: Float, stepPx: Float, weight: Float, count: Int): Float {
        val end = max(0f, count - 0.01f)
        // No step means it never aims.
        if (stepPx <= 0f) return glideTo.coerceIn(0f, end)
        val aimed = sign(dyPx) * min(abs(dyPx) / stepPx, abs(glideTo - glideFrom))
        return (at + weight * aimed + (1f - weight) * (glideTo - at)).coerceIn(0f, end)
    }

    /** The list scroll (px) that draws list position [at] (rows) level with [yInPanel]. It can be past either end. */
    fun levelWith(at: Float, yInPanel: Float, rowPx: Float): Float = at * rowPx - yInPanel

    /**
     * Places the list at [listY] (px). The list scrolls as far as 0..[maxScrollPx] allows, and the
     * panel moves the rest of the way (down when positive). Returns (scroll, shift).
     */
    fun place(listY: Float, maxScrollPx: Float): Pair<Float, Float> {
        val scroll = listY.coerceIn(0f, maxScrollPx)
        return scroll to scroll - listY
    }

    fun maxScroll(count: Int, rowPx: Float, viewportPx: Float): Float = max(0f, count * rowPx - viewportPx)

    /**
     * The part of a strip [stripH] long where a touch may start the selector, as (top, height) within it:
     * - with Android's back swipe on this edge ([backGesture]): only the middle [limit]. Android lets
     *   an app turn off the back swipe on at most 200dp of an edge;
     * - otherwise: the whole strip.
     */
    fun startZone(stripH: Float, limit: Float, backGesture: Boolean): Pair<Float, Float> =
        if (!backGesture || stripH <= limit) 0f to stripH else (stripH - limit) / 2f to limit

    /** The list position (rows) under a finger that is [yInPanel] px below the top of the panel. */
    fun contact(scrollPx: Float, yInPanel: Float, rowPx: Float): Float = (scrollPx + yInPanel) / rowPx

    /** Row index at [contact], clamped to the list. Returns -1 when there is no contact (NaN) or the list is empty. */
    fun focusedIndex(contact: Float, count: Int): Int =
        if (contact.isNaN() || count <= 0) -1 else floor(contact).toInt().coerceIn(0, count - 1)

    /** Peak magnification of the focused row, and how far (rows) the fisheye reaches on each side. */
    const val PEAK = 0.35f
    const val REACH = 3f

    /** Fisheye bell: 1 at the contact point, falling smoothly to 0 at ±[REACH] rows. */
    private fun bell(x: Float): Float =
        if (abs(x) >= REACH) 0f else (1f + cos(PI.toFloat() * x / REACH)) / 2f

    /** Scale of a row whose centre is [x] rows from the contact point. */
    fun fisheyeScale(x: Float): Float = 1f + PEAK * bell(x)

    /**
     * Extra vertical move (rows) of a row whose centre is [x] rows from the contact. Magnified rows
     * push their neighbours apart instead of overlapping. The value is the integral of the extra scale.
     */
    fun fisheyeShift(x: Float): Float {
        val a = min(abs(x), REACH)
        val integral = (a + REACH / PI.toFloat() * sin(PI.toFloat() * a / REACH)) / 2f
        return sign(x) * PEAK * integral
    }

    /**
     * Auto-scroll speed at the panel's ends (px/ms, negative = up) for a finger [y] px below the top
     * of the panel. The speed ramps up across a [zone] at each end of the panel, which is [height] px tall.
     */
    fun edgeSpeed(y: Float, height: Float, zone: Float, maxSpeed: Float): Float {
        val z = min(zone, height / 4f)
        if (z <= 0f) return 0f
        return when {
            y < z -> -maxSpeed * ((z - y) / z).coerceIn(0f, 1f)
            y > height - z -> maxSpeed * ((y - (height - z)) / z).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    /** While picking, if the finger holds still on an app this long, the app launches, as if the finger lifted. */
    const val HOLD_MS = 800L

    /** The held row starts to fill this long into a hold, so the launch is never a surprise (Plan B). */
    const val FILL_FROM_MS = 300L

    /** When the fill stops rising, it drains away in at most this long (ms, for a full row). */
    const val DRAIN_MS = 120f

    /** With `selector.actions far-side`: the panel's far side is this share of its width, on the side away from the strip. */
    const val FAR_SHARE = 0.3f

    /** How full the held row is after holding still for [heldMs]: empty until [FILL_FROM_MS], full at [HOLD_MS]. */
    fun holdFill(heldMs: Long): Float = ((heldMs - FILL_FROM_MS).toFloat() / (HOLD_MS - FILL_FROM_MS)).coerceIn(0f, 1f)

    /** The fill to draw. It rises to [target] at once, but falls from [shown] no faster than a full row per [DRAIN_MS]. */
    fun holdShown(shown: Float, target: Float, dtMs: Long): Float =
        if (target >= shown) target else max(target, shown - dtMs / DRAIN_MS)

    /** Whether [fingerX] is on the far side of the panel from the strip, in its last [FAR_SHARE] of width. The panel starts at [panelX] and is [panelW] wide. */
    fun farSide(fingerX: Float, panelX: Float, panelW: Float, left: Boolean): Boolean =
        if (left) fingerX > panelX + panelW * (1f - FAR_SHARE) else fingerX < panelX + panelW * FAR_SHARE

    /** How a move feels: nothing, a letter passed along the edge, an app passed in the list, or a switch between the two. */
    enum class Feel { NONE, LETTER, APP, SWITCH }

    /**
     * The feel of a move to [row] in [section], from [lastRow] in [lastSection] (-1 means the first
     * move). [selecting] is true in the list and false along the letters; [wasSelecting] is its value
     * before the move. When [aiming] along the edge (apps, Plan C), each app passed gives a light
     * tick, as in picking.
     */
    fun feel(selecting: Boolean, wasSelecting: Boolean, row: Int, lastRow: Int, section: Int, lastSection: Int, aiming: Boolean = false): Feel = when {
        selecting != wasSelecting -> Feel.SWITCH
        selecting -> if (row != lastRow && lastRow != -1) Feel.APP else Feel.NONE
        section != lastSection && lastSection != -1 -> Feel.LETTER
        aiming && row != lastRow && lastRow != -1 -> Feel.APP
        else -> Feel.NONE
    }
}
