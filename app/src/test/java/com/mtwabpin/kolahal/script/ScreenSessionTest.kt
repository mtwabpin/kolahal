package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.script.ScreenSession.End
import com.mtwabpin.kolahal.script.ScreenSession.Event
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A script's screen, with home and the script's engine made up: what each event calls, what goes to home, and how it ends. */
class ScreenSessionTest {
    private val sent = ArrayList<ScreenFrame>()
    private var ended = 0

    /** Home, for what the script draws. [alive] is false once home is gone. */
    private inner class Home(private val alive: () -> Boolean = { true }) : ScreenSession.Out {
        override fun draw(frame: ScreenFrame): Boolean {
            if (alive()) sent += frame
            return alive()
        }

        override fun end() {
            ended++
        }
    }

    /** Home as it opens screens: [events] are posted as soon as one asks. */
    private inner class Opens(private val events: List<Event>, private val opens: Boolean = true) : ScreenSession.Home {
        var closed = 0

        override fun open(session: ScreenSession): Boolean {
            events.forEach(session::post)
            return opens
        }

        override fun closed(session: ScreenSession) {
            closed++
        }
    }

    /** A script's functions by name, and what was called. A function that returns false closes the screen. */
    private class Script(vararg functions: Pair<String, (ScreenSession, List<Any?>) -> Any?>) : ScreenSession.Script {
        private val byName = functions.toMap()
        lateinit var session: ScreenSession
        val calls = ArrayList<String>()

        override suspend fun call(callback: String, cols: Int, rows: Int, args: List<Any?>, limitMs: Long): Boolean {
            calls += callback + args.joinToString(prefix = "(", postfix = ")")
            val f = byName[callback] ?: return true
            return f(session, args) != false
        }

        override fun waiting(ms: Long) = Unit
    }

    private val attach = { out: ScreenSession.Out -> Event.Attach(12, 5, out) }

    /** A screen whose home sends [events] after it opened, run with [script]. */
    private fun session(
        script: Script,
        events: (Home) -> List<Event>,
        clock: () -> Long = { 0L },
        limits: ScreenSession.Limits = ScreenSession.Limits(),
    ): Pair<ScreenSession, Opens> {
        val home = Opens(events(Home()))
        val session = ScreenSession("life", ScreenSession.Spec(10, "Life", keys = true, taps = true), home, clock, limits = limits)
        script.session = session
        return session to home
    }

    /** Runs [script] and returns how it ended, with what happened recorded. */
    private fun run(session: ScreenSession, script: Script): End = runBlocking { session.run(script) }

    @Test
    fun eachEventIsACallAndItsDrawingGoesToHome() {
        val script = Script(
            "start" to { s, _ -> s.draw("put", listOf(0, 0, "start")) },
            "frame" to { s, args -> s.draw("put", listOf(0, 0, "t ${args[0]} ${args[1]}")) },
            "key" to { s, args -> s.draw("put", listOf(0, 1, "${args[0]}")) },
            "tap" to { s, args -> s.draw("put", listOf(0, 2, "${args[0]},${args[1]}")) },
            "resize" to { s, _ -> s.draw("put", listOf(0, 3, "resized")) },
            "stop" to { _, _ -> null },
        )
        val (session, home) = session(
            script,
            { out -> listOf(attach(out), Event.Tick(100, 100), Event.Key("up"), Event.Tap(3, 4), Event.Resize(20, 8), Event.Alive, Event.Close) },
        )
        assertEquals(End.CLOSED, run(session, script))
        assertEquals(
            listOf("start()", "frame(100, 100)", "key(up)", "tap(3, 4)", "resize()", "stop()"),
            script.calls
        )
        assertEquals(listOf("start", "t 100 100", "up", "3,4", "resized"), sent.flatMap { it.texts })
        // Only the tick's frame answers it. The others are drawn at their own size.
        assertEquals(listOf(false, true, false, false, false), sent.map { it.tick })
        assertEquals(listOf(12 to 5, 12 to 5, 12 to 5, 12 to 5, 20 to 8), sent.map { it.cols to it.rows })
        assertEquals(1, home.closed)
        assertEquals(1, ended)
    }

    /** A tick is answered even when nothing was drawn: home waits for it. Other calls send nothing then. */
    @Test
    fun anEmptyTickIsStillAnswered() {
        val script = Script()
        val (session, _) = session(script, { out -> listOf(attach(out), Event.Tick(1, 1), Event.Key("x"), Event.Close) })
        run(session, script)
        assertEquals(listOf(true), sent.map { it.tick })
        assertEquals(0, sent.single().ops.size)
        assertEquals(listOf("start()", "frame(1, 1)", "key(x)", "stop()"), script.calls)
    }

    /** A function that returns false closes the screen, and home is told. */
    @Test
    fun returningFalseCloses() {
        for (closing in listOf("start", "frame", "key", "tap", "resize")) {
            sent.clear()
            ended = 0
            val script = Script(closing to { s, _ -> s.draw("put", listOf(0, 0, "bye")); false }, "stop" to { _, _ -> null })
            val (session, home) = session(
                script,
                { out -> listOf(attach(out), Event.Tick(1, 1), Event.Key("x"), Event.Tap(0, 0), Event.Resize(9, 9), Event.Close) },
            )
            assertEquals(closing, End.CLOSED, run(session, script))
            assertEquals(closing, "stop()", script.calls.last())
            assertTrue(closing, "bye" in sent.flatMap { it.texts })
            assertEquals(closing, 1, ended)
            assertEquals(closing, 1, home.closed)
        }
    }

    /** Nothing but false closes: a function that returns nothing keeps the screen open. */
    @Test
    fun onlyFalseCloses() {
        for (result in listOf<Any?>(null, true, 0, "no", Unit)) {
            val script = Script("start" to { _, _ -> result }, "frame" to { _, _ -> result })
            val (session, _) = session(script, { out -> listOf(attach(out), Event.Tick(1, 1), Event.Tick(2, 1), Event.Close) })
            assertEquals("$result", End.CLOSED, run(session, script))
            assertEquals("$result", listOf("start()", "frame(1, 1)", "frame(2, 1)", "stop()"), script.calls)
        }
    }

    /** A script that can't be shown (home isn't there, or closed it first) isn't called at all. */
    @Test
    fun aScreenHomeNeverShowedIsAbsent() {
        val script = Script("start" to { _, _ -> fail("started"); null })
        // Home is asked, but says nothing.
        val (session, home) = session(script, { emptyList() }, limits = ScreenSession.Limits(attachMs = 50))
        assertEquals(End.ABSENT, run(session, script))
        assertEquals(1, home.closed)
        assertEquals(emptyList<String>(), script.calls)
        // Home closed it before it was there.
        val (other, otherHome) = session(script, { listOf(Event.Close) })
        assertEquals(End.ABSENT, run(other, script))
        assertEquals(1, otherHome.closed)
        // Home said no.
        val refusing = ScreenSession("life", ScreenSession.Spec(10, "Life", false, false), Opens(emptyList(), opens = false), { 0L })
        assertEquals(End.ABSENT, run(refusing, script))
    }

    /** Home gone (its process was stopped) closes the screen: nothing more is drawn for it. */
    @Test
    fun aScreenWhoseHomeIsGoneEnds() {
        var alive = true
        val script = Script(
            "start" to { s, _ -> s.draw("put", listOf(0, 0, "x")) },
            "frame" to { s, _ -> alive = false; s.draw("put", listOf(0, 0, "y")) },
        )
        val home = Opens(listOf(Event.Attach(5, 5, Home { alive }), Event.Tick(1, 1), Event.Tick(2, 1)))
        val session = ScreenSession("life", ScreenSession.Spec(10, "Life", false, false), home, { 0L })
        script.session = session
        assertEquals(End.CLOSED, run(session, script))
        assertEquals(listOf("start()", "frame(1, 1)", "stop()"), script.calls)
    }

    /** A screen nobody is heard from ends by itself. */
    @Test
    fun aScreenThatIsQuietEnds() {
        val script = Script("start" to { _, _ -> null }, "stop" to { _, _ -> null })
        val (session, home) = session(script, { out -> listOf(attach(out)) }, limits = ScreenSession.Limits(idleMs = 50))
        assertEquals(End.CLOSED, run(session, script))
        assertEquals(listOf("start()", "stop()"), script.calls)
        assertEquals(1, ended)
        assertEquals(1, home.closed)
    }

    /**
     * Home's events for [hours] of a screen open and never left, at 30 frames a second, on a clock that
     * only these events move. Each tick comes once the last one's frame was sent, as home's do.
     */
    private class LongInbox(private val out: ScreenSession.Out, hours: Int) : ScreenSession.Inbox {
        var now = 0L
        private val until = hours * 3_600_000L
        private var attached = false
        val posted = ArrayList<Event>()

        override fun post(event: Event) {
            posted += event
        }

        override suspend fun next(ms: Long): Event? {
            if (!attached) return Event.Attach(45, 40, out).also { attached = true }
            now += 33
            return if (now >= until) Event.Close else Event.Tick(now, 33)
        }
    }

    /** A screen has no limit of its own: hours of frames, each small and none kept. */
    @Test
    fun aScreenRunsForHours() {
        var frames = 0
        var most = 0
        var ends = 0
        val out = object : ScreenSession.Out {
            override fun draw(frame: ScreenFrame): Boolean {
                frames++
                most = maxOf(most, frame.ops.size + frame.texts.sumOf { it.length })
                return true
            }

            override fun end() {
                ends++
            }
        }
        val inbox = LongInbox(out, hours = 5)
        val session = ScreenSession(
            "life", ScreenSession.Spec(30, "Life", keys = false, taps = false), Opens(emptyList()),
            clock = { inbox.now }, inbox = inbox,
        )
        var called = 0
        var last = 0L
        val script = object : ScreenSession.Script {
            override suspend fun call(callback: String, cols: Int, rows: Int, args: List<Any?>, limitMs: Long): Boolean {
                if (callback == "frame") {
                    called++
                    last = args[0] as Long
                    session.draw("put", listOf(called % cols, called % rows, "x", "good"))
                }
                return true
            }

            override fun waiting(ms: Long) = Unit
        }
        var shown = 0
        assertEquals(End.CLOSED, runBlocking { session.run(script) { shown++ } })
        assertEquals(1, shown)
        assertEquals(1, ends)
        assertEquals(called, frames)
        assertTrue("$called frames", called > 500_000)
        assertTrue("the last at $last ms", last > 5 * 3_600_000L - 100)
        // The last frame is as small as the first: nothing adds up.
        assertTrue("a frame of $most", most < 20)
        assertEquals(emptyList<Event>(), inbox.posted)
    }

    /** Home is told of only once, when it shows the screen, and not at all when it never does. */
    @Test
    fun shownIsToldWhenHomeShowsIt() {
        val script = Script("start" to { _, _ -> false })
        val (session, _) = session(script, { out -> listOf(attach(out)) })
        var shown = 0
        runBlocking { session.run(script) { shown++ } }
        assertEquals(1, shown)
        val (absent, _) = session(script, { listOf(Event.Close) })
        runBlocking { absent.run(script) { shown++ } }
        assertEquals(1, shown)
    }

    /** A slow script holds up keys and taps, but not without end; a tick and a close are never left out. */
    @Test
    fun theQueueHasAnEnd() {
        val queue = ScreenSession.Queue()
        repeat(10_000) { queue.post(Event.Key("x")) }
        queue.post(Event.Tick(1, 1))
        queue.post(Event.Tap(0, 0))
        queue.post(Event.Close)
        val got = ArrayList<Event>()
        runBlocking { while (true) got += queue.next(1) ?: break }
        assertEquals(ScreenSession.MAX_WAITING + 2, got.size)
        assertTrue(got[got.size - 2] is Event.Tick)
        assertEquals(Event.Close, got.last())
    }

    /** Too many slow frames in a row close the screen with an error. A quick one between them starts the count over. */
    @Test
    fun slowFramesCloseIt() {
        var now = 0L
        val slow = listOf(true, true, false, true, true, true)
        val at = ArrayDeque(slow)
        val script = Script("frame" to { _, _ -> now += if (at.removeFirst()) 60 else 10; null })
        val (session, _) = session(
            script, { out -> listOf(attach(out)) + List(slow.size) { Event.Tick(it * 10L, 10) } },
            clock = { now }, limits = ScreenSession.Limits(frameMs = 50, slowFrames = 3),
        )
        try {
            run(session, script)
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message, "Its frames took over 50 ms, 3 in a row" in e.message.orEmpty())
        }
        // The screen was closed with home told; the script had no last word after an error.
        assertEquals(1, ended)
        assertFalse("stop()" in script.calls)
    }

    @Test
    fun aCallThatRunsTooLongClosesIt() {
        val script = Script("key" to { _, _ -> throw ScreenSession.TimedOut() })
        val (session, _) = session(script, { out -> listOf(attach(out), Event.Key("x")) })
        try {
            run(session, script)
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertEquals("tty.screen closed\nkey took over 1 s", e.message)
        }
        assertEquals(1, ended)
    }

    /** The script's own error ends the run, and the screen is closed for home. */
    @Test
    fun theScriptsErrorEndsTheRun() {
        val script = Script("frame" to { _, _ -> throw IllegalArgumentException("boom") })
        val (session, home) = session(script, { out -> listOf(attach(out), Event.Tick(1, 1)) })
        try {
            run(session, script)
            fail("expected an error")
        } catch (e: IllegalArgumentException) {
            assertEquals("boom", e.message)
        }
        assertEquals(1, ended)
        assertEquals(1, home.closed)
    }

    /** Drawing is for the script's functions only: not before the screen runs, and not between them. */
    @Test
    fun drawingIsForItsFunctionsOnly() {
        val session = ScreenSession("life", ScreenSession.Spec(10, "Life", false, false), Opens(emptyList()), { 0L })
        try {
            session.draw("put", listOf(0, 0, "x"))
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertEquals("g.put works only inside the functions of tty.screen", e.message)
        }
    }

    // ---- what tty.screen takes ----------------------------------------------------------------

    private fun spec(vararg names: String, functions: List<String> = names.filter { it in ScreenSession.Spec.FUNCTIONS }, more: Map<String, Any?> = emptyMap()) =
        mapOf("names" to names.toList(), "functions" to functions) + more

    private fun specError(raw: Map<*, *>?): String =
        runCatching { ScreenSession.Spec.of(raw, "life") }.exceptionOrNull()?.message.orEmpty()

    @Test
    fun theSpecTakesItsFieldsAndDefaults() {
        val s = ScreenSession.Spec.of(spec("frame", "key"), "life")
        assertEquals(ScreenSession.DEFAULT_FPS, s.fps)
        assertEquals("life", s.title)
        assertTrue(s.keys)
        assertFalse(s.taps)
        val all = ScreenSession.Spec.of(
            spec("fps", "title", "start", "frame", "key", "tap", "resize", "stop", more = mapOf("fps" to 24.0, "title" to "Game of life\nsecond line")),
            "life"
        )
        assertEquals(24, all.fps)
        assertEquals("Game of life", all.title)
        assertTrue(all.taps)
    }

    /** A Lua table with no functions, or no fields, arrives as an empty map. */
    @Test
    fun theSpecReadsEmptyTables() {
        assertEquals("tty.screen needs start or frame\nWithout them it has nothing to show", specError(mapOf("names" to emptyMap<Any, Any>(), "functions" to emptyMap<Any, Any>())))
        assertEquals("tty.screen needs start or frame\nWithout them it has nothing to show", specError(null))
    }

    @Test
    fun theSpecSaysWhatIsWrong() {
        assertEquals(
            "tty.screen: no function frames\nFunctions: start, frame, key, tap, resize, stop",
            specError(spec("frame", "frames", functions = listOf("frame", "frames")))
        )
        assertEquals("tty.screen: frame must be a function", specError(spec("frame", functions = emptyList())))
        // A field that isn't a function is the script's own.
        assertEquals(10, ScreenSession.Spec.of(spec("frame", "n", more = mapOf("n" to 3)), "x").fps)
        assertEquals("tty.screen: fps is a number from 0 to 30", specError(spec("frame", "fps", more = mapOf("fps" to 31))))
        assertEquals("tty.screen: fps is a number from 0 to 30", specError(spec("frame", "fps", more = mapOf("fps" to "fast"))))
        assertEquals("tty.screen: fps is a number from 0 to 30", specError(spec("frame", "fps", more = mapOf("fps" to -1))))
        assertEquals(0, ScreenSession.Spec.of(spec("frame", "fps", more = mapOf("fps" to 0)), "x").fps)
    }
}
