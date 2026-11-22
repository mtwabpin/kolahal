/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.Decode
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * `decode` in the gui style: home itself stays, and the characters of its log scramble and settle
 * where they are. Home's texts draw that themselves, from the time and the wave here; nothing is
 * laid out again, so nothing moves.
 *
 * The waves and their pace are [DecodeScreen]'s, which `decode` uses in the tty style.
 */
class LogDecode {
    /** A text of home's log. */
    interface Part {
        /** Where it is in the window, or null while it isn't on screen. */
        fun place(): Place?
    }

    class Place(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** True while `decode` is open: only the log shows, and its texts draw the effect. */
    var active by mutableStateOf(false)
        private set

    /** The screen's time in ms. Texts read it as they draw, so a frame draws again and nothing else. */
    var now by mutableLongStateOf(0L)
        private set

    /** Against burn-in the log moves by a little, now and then: an index into [SHIFTS]. */
    var shift by mutableIntStateOf(0)
        private set

    /** The wave that runs or ran last, and when it began. */
    var wave: DecodeWave? = null
        private set
    var waveAt = 0L
        private set

    private val parts = LinkedHashSet<Part>()
    private var owner: Any? = null
    private var random: Random = Random.Default
    private var rowPx = 1
    private var nextWave = 0L
    private var waves = 0

    fun add(part: Part) {
        parts += part
    }

    fun remove(part: Part) {
        parts -= part
    }

    internal fun start(by: Any, random: Random, rowPx: Int) {
        owner = by
        this.random = random
        this.rowPx = rowPx.coerceAtLeast(1)
        wave = null
        waves = 0
        shift = 0
        now = 0L
        nextWave = DecodeScreen.FIRST_MS
        active = true
    }

    internal fun frame(timeMs: Long) {
        if (timeMs >= nextWave) begin(timeMs)
        now = timeMs
    }

    /** [by] closed. Another one may have started by now. */
    internal fun stop(by: Any) {
        if (owner !== by) return
        owner = null
        active = false
        wave = null
        shift = 0
    }

    private fun begin(timeMs: Long) {
        val places = parts.mapNotNull { it.place() }
        if (places.isEmpty()) return
        if (waves > 0 && waves % SHIFT_EVERY == 0) shift = (shift + 1) % SHIFTS.size
        val others = DecodeWave.Kind.entries.filter { it != DecodeWave.Kind.ALL && it != wave?.kind }
        val one = places[random.nextInt(places.size)]
        val next = DecodeWave(
            kind = if (waves == 0) DecodeWave.Kind.ALL else others[random.nextInt(others.size)],
            seed = random.nextInt(),
            area = Place(places.minOf { it.left }, places.minOf { it.top }, places.maxOf { it.right }, places.maxOf { it.bottom }),
            rowPx = rowPx,
            oneY = (one.top + one.bottom) / 2,
            fromRight = random.nextBoolean(),
        )
        waves++
        wave = next
        waveAt = timeMs
        nextWave = timeMs + next.lengthMs + DecodeScreen.REST_MS + random.nextInt(DecodeScreen.REST_MORE_MS)
    }

    companion object {
        /** Every so many waves, the log moves to the next of these places, in thirds of a row. */
        private const val SHIFT_EVERY = 10
        val SHIFTS: List<Pair<Int, Int>> =
            listOf(0 to 0, 1 to 0, 1 to -1, 0 to -1, -1 to -1, -1 to 0, -1 to 1, 0 to 1, 1 to 1)
    }
}

/**
 * One wave over the log: when each character scrambles and when it settles, from where it is. The
 * same wave gives the same times, so a text can ask on every frame.
 *
 * [area] is what the log's texts cover and [rowPx] about how tall a row is. [oneY] is in a row that
 * takes part in [Kind.SCATTERED] whatever its chance.
 */
class DecodeWave(
    val kind: Kind,
    val seed: Int,
    private val area: LogDecode.Place,
    private val rowPx: Int,
    private val oneY: Float,
    private val fromRight: Boolean,
) {
    enum class Kind { ALL, BAND, UP, SCATTERED, COLUMNS }

    private val height = (area.bottom - area.top).coerceAtLeast(1f)
    private val width = (area.right - area.left).coerceAtLeast(1f)
    private val rows = (height / rowPx).coerceAtLeast(1f)

    /** How long until the last row, or column, starts. */
    private val sweepMs: Float = when (kind) {
        Kind.ALL -> minOf(40f * rows, 1_600f)
        Kind.BAND -> minOf(110f * rows, 4_200f)
        Kind.UP -> minOf(80f * rows, 3_000f)
        Kind.SCATTERED -> 3_000f
        Kind.COLUMNS -> 2_200f
    }

    /** All of it has settled after this. */
    val lengthMs: Int = (sweepMs + ROW_JITTER_MS + Decode.LINE_MS).toInt()

    /** The row at [y], for what its characters share. */
    fun row(y: Float): Int = ((y - area.top) / rowPx).roundToInt()

    /** When the character [i] at [x], [y] starts, in ms into the wave; [NEVER] if it stays as it is. */
    fun start(i: Int, x: Float, y: Float): Int {
        val at = at(i, x, y)
        return if (at == NEVER || kind == Kind.COLUMNS) at else at + mix(i, 1) % START_JITTER_MS
    }

    /** When it settles: a row settles from its first to its last character, a column at once. */
    fun settle(i: Int, x: Float, y: Float): Int {
        val at = at(i, x, y)
        return when {
            at == NEVER -> NEVER
            kind == Kind.COLUMNS -> at + 220 + mix(i, 2) % 260
            else -> at + settles(across(x), mix(i, 2))
        }
    }

    /** The ink row [row] flickers in as it settles: the warning or the accent color, or null for its own. */
    fun tint(row: Int): Ink? = if (mix(row, 3) % 5 > 0) null else if (mix(row, 4) % 2 == 0) Ink.WARN else Ink.PROMPT

    fun across(x: Float): Float = ((x - area.left) / width).coerceIn(0f, 1f)

    private fun at(i: Int, x: Float, y: Float): Int {
        val down = ((y - area.top) / height).coerceIn(0f, 1f)
        val row = row(y)
        return when (kind) {
            Kind.ALL -> (down * sweepMs).toInt() + mix(row, 5) % ROW_JITTER_MS
            Kind.BAND -> (down * sweepMs).toInt()
            Kind.UP -> ((1f - down) * sweepMs).toInt()
            Kind.SCATTERED -> if (row == row(oneY) || mix(row, 6) % 3 == 0) mix(row, 7) % sweepMs.toInt() else NEVER
            Kind.COLUMNS -> ((if (fromRight) 1f - across(x) else across(x)) * sweepMs).toInt() + mix(i, 8) % 200
        }
    }

    private fun mix(a: Int, b: Int): Int = mix(a, b, seed)

    companion object {
        const val NEVER = Int.MAX_VALUE

        /** A scrambling character starts this much late at most, and settles as much late as the other. */
        private const val START_JITTER_MS = 60
        private const val SETTLE_JITTER_MS = 120
        private const val ROW_JITTER_MS = 80

        /** When a character [across] (0 to 1) its row settles, from when the row starts. [Decode.LINE_MS] at most. */
        fun settles(across: Float, chance: Int): Int =
            Decode.LINE_MS - SETTLE_JITTER_MS - SWEEP_MS + (across * SWEEP_MS).toInt() + chance % SETTLE_JITTER_MS

        private const val SWEEP_MS = 660

        /** A number from [a], [b] and [seed], never negative. */
        fun mix(a: Int, b: Int, seed: Int): Int {
            var h = ((a + 1) * -1640531527) xor ((b + 7) * 40503) xor (seed * 31)
            h = (h xor (h ushr 15)) * -2048144789
            return (h xor (h ushr 13)) and 0x7FFFFFFF
        }
    }
}

/**
 * `decode` as the host runs it in the gui style: it draws nothing itself, and gives home's log its
 * time ([LogDecode]). The host is see-through then.
 */
class LogDecodeScreen(private val decode: LogDecode) : TtyScreen {
    override val name = "decode"
    override val title = "Decoding log"
    override val fps = 24

    override fun start(env: ScreenEnv) = decode.start(this, env.random, env.cellH)

    override fun resize(cols: Int, rows: Int) {}

    override fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean {
        decode.frame(timeMs)
        return true
    }

    /** With animations off it is home's log as it is. */
    override fun still(grid: CellBuffer) {}

    override fun stop() = decode.stop(this)
}
