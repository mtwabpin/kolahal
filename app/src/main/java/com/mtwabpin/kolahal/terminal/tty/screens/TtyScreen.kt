/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random

/**
 * A full-screen program on the character grid (`matrix`, `clock`, `pipes`, `decode`, `sl`, `top`,
 * `boot`), like a terminal program that takes the whole screen until you leave it.
 *
 * Screens are pure and deterministic: time and chance come in through [start] and [frame], so a
 * JVM test can step one frame by frame and read the grid. They draw with theme roles only
 * ([com.mtwabpin.kolahal.terminal.Ink]), so every theme works, light ones too.
 *
 * The host (ui/tty/TtyScreenHost) owns the grid and the frame clock. It calls [start] once, then
 * [frame] up to [fps] times a second while the screen is visible, and [stop] when it closes.
 * A screen allocates what it needs in [start] and [resize]; a frame only changes cells.
 */
interface TtyScreen {
    /** The command that opens it. */
    val name: String

    /** What TalkBack says for the screen, as a noun phrase ("Matrix rain"). */
    val title: String

    /** The most frames a second it wants. The host may give fewer (battery saver). */
    val fps: Int

    /**
     * False for a screen whose changes are information, not motion (the clock). It keeps running
     * when Android's "Remove animations" is on; the others then show one still frame ([still]).
     */
    val animated: Boolean get() = true

    fun start(env: ScreenEnv)

    /** The grid's size changed (rotation, a new font size). The grid is blank now. */
    fun resize(cols: Int, rows: Int)

    /**
     * Draws into [grid]: [timeMs] is the time since the screen started, [deltaMs] since the last
     * frame. Returns false when the program has ended (sl, boot); the host then closes it.
     * Leaving the grid as it is costs nothing: the host repaints only when a row changed.
     */
    fun frame(grid: CellBuffer, timeMs: Long, deltaMs: Long): Boolean

    /**
     * How long the screen needs no frame after the one it just drew, in ms; 0 for its usual pace
     * ([fps]). For a screen that rests now and then; the host sleeps that long, 5 s at most.
     */
    val restMs: Long get() = 0L

    /** Home is in front again after the screen was paused. [frame] comes next. */
    fun resumed() {}

    /**
     * One frame that stands for the screen, for when animations are off. By default, the frame
     * 2.5 s in (30 frames a second), or the last one if the program ends sooner.
     */
    fun still(grid: CellBuffer) {
        var t = 0L
        while (t < 2_500L && frame(grid, t, 33L)) t += 33L
    }

    /**
     * With animations off, how often [still] draws again, in ms, for a screen whose still frame
     * shows something that changes; 0 for once.
     */
    val stillEveryMs: Long get() = 0L

    /** A key typed on a hardware keyboard. True if the screen used it; otherwise the host closes it. */
    fun key(key: TtyKey): Boolean = false

    /** A tap on cell [col], [row]. True if the screen used it; otherwise the host closes it. */
    fun tap(col: Int, row: Int): Boolean = false

    fun stop() {}
}

/**
 * A screen that puts its grid's rows where it wants them: each row of cells is drawn at its own
 * place in the host's area instead of right under the one above (`decode` draws home's lines where
 * home does, with the gaps between them). Columns are still whole cells.
 */
interface PlacedRows {
    /** Where the first column of the placed rows starts, in px from the area's left. */
    val rowLeft: Int

    /** Where [row] starts, in px from the area's top, or [UNPLACED] for its usual place. */
    fun rowTop(row: Int): Int

    companion object {
        const val UNPLACED = Int.MIN_VALUE
    }
}

/** A key, for [TtyScreen.key]: a typed character, or one of the keys that type none. */
sealed interface TtyKey {
    data class Typed(val char: Char) : TtyKey
    enum class Named : TtyKey { ENTER, ESCAPE, SPACE, UP, DOWN, LEFT, RIGHT, TAB, DELETE, OTHER }
}

/**
 * What a screen starts with.
 * - [random]: all chance comes from here, so a seeded one gives the same frames each time.
 * - [clock]: the wall clock in ms since 1970 (the clock screen); [zone] and [locale] format it.
 * - [clock24]: 24-hour time (`clock.24h`).
 * - [data]: real facts for `boot` and `top`.
 * - [cellW], [cellH]: a cell's size in px, for a screen that places its rows ([PlacedRows]).
 * - [log]: the log lines home shows now, in px from the area's top left (`decode`).
 */
class ScreenEnv(
    val cols: Int,
    val rows: Int,
    val random: Random,
    val clock: () -> Long,
    val clock24: Boolean,
    val data: ScreenData = ScreenData(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val locale: Locale = Locale.getDefault(),
    val cellW: Int = 10,
    val cellH: Int = 20,
    val log: () -> ShownLog? = { null },
)

/**
 * Facts `boot` shows, read once when the screen opens. Only what is cheap to know: counts the app
 * already holds in memory, and settings. A count that is 0 is left out.
 *
 * [top] is where `top` reads its live facts, each second ([TopSource]); [log] is where `decode`
 * reads the log lines home shows.
 */
data class ScreenData(
    val version: String = "",
    val android: String = "",
    val apps: Int = 0,
    val workApps: Int = 0,
    val privateApps: Int = 0,
    val scripts: Int = 0,
    val scriptsOn: Int = 0,
    val theme: String = "",
    val dark: Boolean = true,
    val font: String = "",
    val fontSize: Int = 0,
    val unlocks: Int = 0,
    val top: TopSource = TopSource.NONE,
    val log: ShownLogSource? = null,
)

/** The screens by command name. */
object TtyScreens {
    val names: List<String> = listOf("matrix", "clock", "pipes", "decode", "sl", "top", "boot")

    /** A new screen for [name], or null when there is none by that name. */
    fun named(name: String): TtyScreen? = when (name) {
        "matrix" -> MatrixScreen()
        "clock" -> ClockScreen()
        "pipes" -> PipesScreen()
        "decode" -> DecodeScreen()
        "sl" -> SlScreen()
        "top" -> TopScreen()
        "boot" -> BootScreen()
        else -> null
    }
}
