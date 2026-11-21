/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import java.time.ZoneId
import java.util.Locale
import kotlin.random.Random

/**
 * A full-screen program on the character grid (`matrix`, `clock`, `pipes`, `sl`, `boot`), like a
 * terminal program that takes the whole screen until you leave it.
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
     * One frame that stands for the screen, for when animations are off. By default, the frame
     * 2.5 s in (30 frames a second), or the last one if the program ends sooner.
     */
    fun still(grid: CellBuffer) {
        var t = 0L
        while (t < 2_500L && frame(grid, t, 33L)) t += 33L
    }

    /** A key typed on a hardware keyboard. True if the screen used it; otherwise the host closes it. */
    fun key(key: TtyKey): Boolean = false

    /** A tap on cell [col], [row]. True if the screen used it; otherwise the host closes it. */
    fun tap(col: Int, row: Int): Boolean = false

    fun stop() {}
}

/** A key, for [TtyScreen.key]: a typed character, or one of the keys that type none. */
sealed interface TtyKey {
    data class Typed(val char: Char) : TtyKey
    enum class Named : TtyKey { ENTER, ESCAPE, UP, DOWN, LEFT, RIGHT, TAB, DELETE, OTHER }
}

/**
 * What a screen starts with.
 * - [random]: all chance comes from here, so a seeded one gives the same frames each time.
 * - [clock]: the wall clock in ms since 1970 (the clock screen); [zone] and [locale] format it.
 * - [clock24]: 24-hour time (`clock.24h`).
 * - [data]: real facts for `boot`.
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
)

/**
 * Facts `boot` shows, read once when the screen opens. Only what is cheap to know: counts the app
 * already holds in memory, and settings. A count that is 0 is left out.
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
)

/** The screens by command name. */
object TtyScreens {
    val names: List<String> = listOf("matrix", "clock", "pipes", "sl", "boot")

    /** A new screen for [name], or null when there is none by that name. */
    fun named(name: String): TtyScreen? = when (name) {
        "matrix" -> MatrixScreen()
        "clock" -> ClockScreen()
        "pipes" -> PipesScreen()
        "sl" -> SlScreen()
        "boot" -> BootScreen()
        else -> null
    }
}
