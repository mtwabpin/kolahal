package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.screens.FramePlayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The library's screens (`template-screen`, its JavaScript twin and the `tty-*` scripts), each run as
 * Kolahal runs a script's screen, on a home that is made up: it opens the screen at a size, sends
 * ticks 33 ms apart, then closes it, and keeps what the script drew. At a phone's 60 × 40 cells and a
 * tablet's 200 × 60, each one draws something, draws only on the grid, and keeps up: 30 slow frames
 * in a row would end its run with an error.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScreensTest {
    // top logs a line when it has no usage access; the rule takes the test's lines away.
    @get:Rule
    val logLines = LogLinesSince()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val library = ScriptLibrary(context)

    private val frames = CopyOnWriteArrayList<ScreenFrame>()
    private val ended = AtomicInteger()

    private val out = object : ScreenSession.Out {
        override fun draw(frame: ScreenFrame): Boolean {
            frames.add(frame)
            return true
        }

        override fun end() {
            ended.incrementAndGet()
        }
    }

    /** Home opens the screen at [cols] × [rows], and [events] come after it. */
    private inner class Home(
        private val cols: Int,
        private val rows: Int,
        private val events: List<ScreenSession.Event>,
    ) : ScreenSession.Home {
        override fun open(session: ScreenSession): Boolean {
            session.post(ScreenSession.Event.Attach(cols, rows, out))
            events.forEach(session::post)
            return true
        }

        override fun closed(session: ScreenSession) = Unit
    }

    /**
     * Lua run before the script: its grid is one that says which call draws off it, and the run ends
     * with that error. A put is checked at its first cell and on each of its rows (text past the right
     * edge is cut, as the API says); a fill and a bar as a whole. With [sideways], a put may also
     * start left or right of the grid: sl's train drives in and out across the edges.
     */
    private fun watched(sideways: Boolean) = """
        local sideways = $sideways
        local rawscreen = tty.screen
        tty.screen = function(spec)
          local seen
          local function inside(what, c, r, w, h, g)
            if c < 0 or r < 0 or c + w > g.cols or r + h > g.rows then
              error(string.format("%s at %d,%d (%dx%d) is off the %dx%d grid", what, c, r, w, h, g.cols, g.rows), 0)
            end
          end
          local function watch(g)
            seen = seen or setmetatable({
              put = function(c, r, text, style)
                local lines = select(2, tostring(text):gsub("\n", "")) + 1
                if sideways then inside("g.put", 0, r, 1, lines, g) else inside("g.put", c, r, 1, lines, g) end
                return g.put(c, r, text, style)
              end,
              fill = function(c, r, w, h, char, style)
                inside("g.fill", c, r, w, h, g)
                return g.fill(c, r, w, h, char, style)
              end,
              bar = function(c, r, w, value, style)
                inside("g.bar", c, r, w, 1, g)
                return g.bar(c, r, w, value, style)
              end,
            }, { __index = g })
            return seen
          end
          for _, name in ipairs({ "start", "frame", "resize" }) do
            local f = rawget(spec, name)
            if f then spec[name] = function(g, ...) return f(watch(g), ...) end end
          end
          return rawscreen(spec)
        end
    """.trimIndent() + "\n"

    /**
     * The same as [watched], in JavaScript, for a script in JavaScript. tty.screen is a binding: it
     * is defined over with its own flags, as the engine's screen.js does.
     */
    private fun watchedJs(sideways: Boolean) = """
        {
          const sideways = $sideways;
          const inside = (what, c, r, w, h, g) => {
            if (c < 0 || r < 0 || c + w > g.cols || r + h > g.rows) {
              throw new Error(what + " at " + c + "," + r + " (" + w + "x" + h + ") is off the " + g.cols + "x" + g.rows + " grid");
            }
          };
          let seen, of;
          const watch = (g) => {
            if (of !== g) {
              of = g;
              seen = {
                get cols() { return g.cols; },
                get rows() { return g.rows; },
                put: (c, r, text, style) => {
                  inside("g.put", sideways ? 0 : c, r, 1, String(text).split("\n").length, g);
                  return g.put(c, r, text, style);
                },
                fill: (c, r, w, h, char, style) => {
                  inside("g.fill", c, r, w, h, g);
                  return g.fill(c, r, w, h, char, style);
                },
                bar: (c, r, w, value, style) => {
                  inside("g.bar", c, r, w, 1, g);
                  return g.bar(c, r, w, value, style);
                },
                clear: (style) => g.clear(style),
                box: (c, r, w, h, style) => g.box(c, r, w, h, style),
              };
            }
            return seen;
          };
          const d = Object.getOwnPropertyDescriptor(tty, "screen");
          const open = d.value;
          Object.defineProperty(tty, "screen", {
            value: (spec) => {
              for (const name of ["start", "frame", "resize"]) {
                const f = spec[name];
                if (typeof f === "function") spec[name] = function (g, ...rest) { return f.apply(this, [watch(g), ...rest]); };
              }
              return open(spec);
            },
            writable: d.writable, enumerable: d.enumerable, configurable: d.configurable,
          });
        }
    """.trimIndent() + "\n"

    /** Runs the library's [file] on a [cols] × [rows] grid for [ticks] frames; the run's error, or null. */
    private fun run(file: String, cols: Int, rows: Int, ticks: Int, sideways: Boolean = false): Throwable? {
        frames.clear()
        ended.set(0)
        val entry = library.entries.single { it.file == file }
        val code = library.code(entry)
        val before = if (entry.lang == ScriptLang.JS) watchedJs(sideways) else watched(sideways)
        val events = List(ticks) { ScreenSession.Event.Tick((it + 1) * 33L, 33L) } + ScreenSession.Event.Close
        val host = HostApi(
            context, "screentest", scriptId = null, notifyEnabled = false, interactive = true,
            options = ScriptMeta.options(code).associate { it.name to it.default },
        ).apply { screenHome = Home(cols, rows, events) }
        return try {
            runCatching { runBlocking { ScriptEngine.forLang(entry.lang).run(before + code, "screentest", host, 10_000, repl = false) } }
                .exceptionOrNull()
        } finally {
            host.flush()
        }
    }

    /** The cells that show something after the run's frames, as home shows them. */
    private fun drawn(cols: Int, rows: Int): Int {
        val g = CellBuffer(cols, rows)
        frames.forEach { if (it.cols == cols && it.rows == rows) FramePlayer.play(it, g) }
        return (0 until rows).sumOf { r -> g.rowText(r).count { !it.isWhitespace() } }
    }

    /** About 3 seconds of each screen at both sizes. */
    private fun drawsOnTheGrid(file: String, sideways: Boolean = false) {
        for ((cols, rows) in listOf(60 to 40, 200 to 60)) {
            val error = run(file, cols, rows, ticks = 90, sideways = sideways)
            assertNull("$file at $cols×$rows: $error", error)
            assertTrue("$file at $cols×$rows drew nothing", drawn(cols, rows) > 0)
            assertEquals("$file at $cols×$rows", 1, ended.get())
        }
    }

    @Test
    fun template() = drawsOnTheGrid("template-screen.lua")

    @Test
    fun templateJs() = drawsOnTheGrid("template-screen-js.js")

    @Test
    fun matrix() = drawsOnTheGrid("tty-matrix.lua")

    @Test
    fun pipes() = drawsOnTheGrid("tty-pipes.lua")

    @Test
    fun sl() = drawsOnTheGrid("tty-sl.lua", sideways = true)

    @Test
    fun clock() = drawsOnTheGrid("tty-clock.lua")

    @Test
    fun top() = drawsOnTheGrid("tty-top.lua")

    @Test
    fun decode() = drawsOnTheGrid("tty-decode.lua")

    @Test
    fun boot() = drawsOnTheGrid("tty-boot.lua")

    /** Every screen in the library is tested here: each script with `@uses screen`. */
    @Test
    fun theyAreAllHere() {
        assertEquals(
            listOf("template-screen-js.js", "template-screen.lua") +
                listOf("boot", "clock", "decode", "matrix", "pipes", "sl", "top").map { "tty-$it.lua" },
            library.entries.filter { ScriptMeta.isScreen(library.code(it)) }.map { it.file }.sorted(),
        )
    }

    /**
     * boot and sl end by themselves: boot after 1.2 s, sl once the train has crossed 60 columns (about
     * 4.5 s). Their frames stop before the ticks do.
     */
    @Test
    fun bootAndSlEndByThemselves() {
        assertNull(run("tty-boot.lua", 60, 40, ticks = 60))
        assertTrue("boot: ${frames.count { it.tick }} frames", frames.count { it.tick } in 30 until 60)
        assertNull(run("tty-sl.lua", 60, 40, ticks = 200, sideways = true))
        assertTrue("sl: ${frames.count { it.tick }} frames", frames.count { it.tick } in 100 until 200)
    }
}
