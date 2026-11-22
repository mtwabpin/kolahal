/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.StyledText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A script's own full-screen screen (`tty.screen`), on the script's side: in the `:script` process,
 * on the run's thread. It opens once the script's code has run to its end, and ends when it closes.
 *
 * Home shows the screen over its log page and drives it ([Event]): it says when it is on screen and
 * how big its grid is, then sends a tick for each frame it wants, the keys and taps, and that it
 * closed. Each one calls a function of the script's ([Script]), and what that draws goes back as
 * one [ScreenFrame] ([Out]). Home sends the next tick only after it got the frame of the last one, so a
 * slow script drops its own frames and nothing else waits for it. Home is never blocked by it.
 *
 * - Each call of the script's has [CALL_MS]; a call that runs longer is stopped, and the screen closes
 *   with an error.
 * - A frame that takes longer than [FRAME_MS], [SLOW_FRAMES] times in a row, closes the screen too.
 * - A screen runs at most [LONGEST_MS]. If home sends nothing for [IDLE_MS], it ended.
 *
 * [clock] is in ms and never goes back. [text] makes a text fit to show (secrets masked).
 */
internal class ScreenSession(
    val name: String,
    val spec: Spec,
    private val home: Home,
    private val clock: () -> Long,
    text: (String) -> String = { it },
    private val limits: Limits = Limits(),
) {
    /** How long things may take. Tests make them short. */
    class Limits(
        val callMs: Long = CALL_MS,
        val frameMs: Long = FRAME_MS,
        val slowFrames: Int = SLOW_FRAMES,
        val attachMs: Long = ATTACH_MS,
        val idleMs: Long = IDLE_MS,
        val longestMs: Long = LONGEST_MS,
    )

    /** Names this screen in home's messages. */
    val token: String = UUID.randomUUID().toString()

    private val drawing = ScreenDrawing(text)
    private val events = LinkedBlockingQueue<Event>()

    /** True while a call of the script's runs: only then may it draw. */
    @Volatile
    private var calling = false

    /** What `tty.screen` was called with, but for the functions, which stay in the script's engine. */
    class Spec(val fps: Int, val title: String, val keys: Boolean, val taps: Boolean) {
        companion object {
            /** The functions `tty.screen` takes. */
            val FUNCTIONS = listOf("start", "frame", "key", "tap", "resize", "stop")

            /**
             * Reads what an engine passes: [raw] has `fps`, `title`, `names` (every field the script
             * gave) and `functions` (those that are functions). A mistake is an error that says what
             * to change. [name] is the script's, the title when it gives none.
             *
             * Any other field that isn't a function is the script's own, for what it keeps
             * (`this.n` in JavaScript).
             */
            fun of(raw: Map<*, *>?, name: String): Spec {
                val names = strings(raw?.get("names"))
                val functions = strings(raw?.get("functions"))
                val unknown = functions.filter { it !in FUNCTIONS }
                require(unknown.isEmpty()) { "tty.screen: no function ${unknown.joinToString()}; there are ${FUNCTIONS.joinToString()}" }
                val not = FUNCTIONS.filter { it in names && it !in functions }
                require(not.isEmpty()) { "tty.screen: ${not.joinToString()} must be a function" }
                require("start" in functions || "frame" in functions) { "tty.screen needs start or frame, or it has nothing to show" }
                val fps = raw?.get("fps")?.let {
                    (it as? Number)?.toDouble()?.takeIf { f -> f in 0.0..MAX_FPS.toDouble() }?.toInt()
                        ?: throw IllegalArgumentException("tty.screen: fps is a number from 0 to $MAX_FPS")
                } ?: DEFAULT_FPS
                val title = (raw?.get("title") as? String)?.let(StyledText::plain)?.lineSequence()?.firstOrNull()
                    ?.trim()?.take(MAX_TITLE)?.ifEmpty { null }
                return Spec(fps, title ?: name, "key" in functions, "tap" in functions)
            }

            private fun strings(v: Any?): List<String> = Json.listOrNull(v).orEmpty().map { it.toString() }
        }
    }

    /** What home sends. */
    sealed interface Event {
        /** Home shows the screen on a grid of [cols] × [rows]. What the script draws goes to [out]. */
        class Attach(val cols: Int, val rows: Int, val out: Out) : Event
        class Resize(val cols: Int, val rows: Int) : Event

        /** Time for a frame: [timeMs] since the screen started, [deltaMs] since the last one. */
        class Tick(val timeMs: Long, val deltaMs: Long) : Event

        /** A key, by the name a script gets: the character it types, or `up`, `enter`, `space`… */
        class Key(val key: String) : Event
        class Tap(val col: Int, val row: Int) : Event

        /** Home is there, and has nothing else to say. */
        data object Alive : Event
        data object Close : Event
    }

    /** Home, for what the script draws. A frame that can't be sent (home is gone) is false. */
    interface Out {
        fun draw(frame: ScreenFrame): Boolean
        fun end()
    }

    /** Where screens open: home. */
    interface Home {
        /** Asks home to show [session]. Home answers with [Event.Attach]. */
        fun open(session: ScreenSession): Boolean

        /** [session] ended: home sends nothing more to it. */
        fun closed(session: ScreenSession)
    }

    /** The script, in its engine. */
    interface Script {
        /**
         * Calls the script's function [callback] with [args], within [limitMs] ([TimedOut] past it). The
         * grid is [cols] × [rows] cells. False only when the function returned false; a function the
         * script doesn't have is true. What it throws ends the run.
         */
        suspend fun call(callback: String, cols: Int, rows: Int, args: List<Any?>, limitMs: Long): Boolean

        /** The run waits for home now, up to [ms], and runs nothing of the script until the next call. */
        fun waiting(ms: Long)
    }

    /** A call of the script's ran past its time. */
    class TimedOut : RuntimeException()

    /** How a screen ended. */
    enum class End {
        /** It closed itself, or home closed it. */
        CLOSED,

        /** It ran as long as a screen may. */
        LONGEST,

        /** Home never showed it. */
        ABSENT,
    }

    /** Home says [event]. Any thread. */
    fun post(event: Event) {
        events.offer(event)
    }

    /** A drawing call of the script's grid ([ScreenDrawing]). Only inside one of its functions. */
    fun draw(op: String, args: List<Any?>) {
        check(calling) { "g.$op works only inside the functions of tty.screen" }
        drawing.call(op, args)
    }

    /** Opens the screen and runs it until it ends. The script's error, or a limit past which it is stopped, ends the run. */
    suspend fun run(script: Script): End {
        var out: Out? = null
        try {
            if (!home.open(this)) return End.ABSENT
            script.waiting(limits.attachMs)
            val attach = attached() ?: return End.ABSENT
            out = attach.out
            drawing.resize(attach.cols, attach.rows)
            return loop(script, attach.out)
        } finally {
            home.closed(this)
            runCatching { out?.end() }
        }
    }

    private suspend fun attached(): Event.Attach? {
        val until = clock() + limits.attachMs
        while (true) {
            val left = until - clock()
            if (left <= 0) return null
            when (val event = next(left) ?: return null) {
                is Event.Attach -> return event
                is Event.Close -> return null
                else -> Unit
            }
        }
    }

    private suspend fun loop(script: Script, out: Out): End {
        val began = clock()
        var slow = 0
        var end = End.CLOSED
        var going = step(script, out, "start", emptyList())
        while (going) {
            if (clock() - began >= limits.longestMs) {
                end = End.LONGEST
                break
            }
            script.waiting(limits.idleMs)
            val event = next(limits.idleMs) ?: break
            going = when (event) {
                is Event.Tick -> {
                    val at = clock()
                    val kept = step(script, out, "frame", listOf(event.timeMs, event.deltaMs), tick = true)
                    slow = if (clock() - at > limits.frameMs) slow + 1 else 0
                    check(slow < limits.slowFrames) { "tty.screen: its frames took over ${limits.frameMs} ms, ${limits.slowFrames} in a row, so it closed" }
                    kept
                }

                is Event.Key -> step(script, out, "key", listOf(event.key))
                is Event.Tap -> step(script, out, "tap", listOf(event.col, event.row))
                is Event.Resize -> {
                    drawing.resize(event.cols, event.rows)
                    step(script, out, "resize", emptyList())
                }

                is Event.Close -> break
                is Event.Alive, is Event.Attach -> true
            }
        }
        // A last word of the script's (its score, say). What it draws goes nowhere: the screen is gone.
        call(script, "stop", emptyList())
        return end
    }

    /** Calls [callback] and sends home what it drew. False when the script closed its screen, or home is gone. */
    private suspend fun step(script: Script, out: Out, callback: String, args: List<Any?>, tick: Boolean = false): Boolean {
        val going = call(script, callback, args)
        val frame = drawing.take(tick)
        val sent = if (tick || frame.ops.isNotEmpty()) out.draw(frame) else true
        return going && sent
    }

    private suspend fun call(script: Script, callback: String, args: List<Any?>): Boolean {
        calling = true
        try {
            return script.call(callback, drawing.cols, drawing.rows, args, limits.callMs)
        } catch (_: TimedOut) {
            throw IllegalStateException("tty.screen: $callback took over ${limits.callMs / 1_000} s, so it closed")
        } finally {
            calling = false
        }
    }

    /** The next event, or null when none came in [ms]. It holds a thread of the run's own, not home's. */
    private suspend fun next(ms: Long): Event? =
        runInterruptible(Dispatchers.IO) { events.poll(ms.coerceAtLeast(1), TimeUnit.MILLISECONDS) }

    companion object {
        /** The `@uses` item a script needs for `tty.screen`. */
        const val USES = "screen"

        /** The most frames a second a script's screen gets. Home may give fewer. */
        const val MAX_FPS = 30
        const val DEFAULT_FPS = 10

        /** How long one call of the script's may run. */
        const val CALL_MS = 1_000L

        /** A frame that takes longer than this is slow. */
        const val FRAME_MS = 50L

        /** This many slow frames in a row close the screen. */
        const val SLOW_FRAMES = 30

        /** How long home has to show the screen. */
        const val ATTACH_MS = 10_000L

        /** With no word from home for this long, the screen ends. */
        const val IDLE_MS = 60_000L

        /** A screen may run this long, in all. */
        const val LONGEST_MS = 5 * 60_000L

        /** The most a run may take for its screen, on top of its code: the waits for home, the screen, and its last words. */
        const val WHOLE_MS = ATTACH_MS + LONGEST_MS + 2 * IDLE_MS

        private const val MAX_TITLE = 60
    }
}
