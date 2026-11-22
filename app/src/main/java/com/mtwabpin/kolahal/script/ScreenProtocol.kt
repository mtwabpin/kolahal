/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.os.Messenger
import android.os.RemoteException

/**
 * The messages between home and a script's screen (`tty.screen`), both ways.
 * - The `:script` process asks home to open one with a broadcast ([ask]).
 * - Home then binds [ScriptScreenService] and sends it messages ([toScript]), with the token that names
 *   the screen. The first is [ATTACH], which carries the messenger that home hears on.
 * - The screen answers there with what it drew ([DRAW]) and when it ended ([END]).
 * Every value is a number or a text, so nothing here depends on a class of either process.
 */
internal object ScreenProtocol {
    const val ATTACH = 1
    const val RESIZE = 2
    const val TICK = 3
    const val KEY = 4
    const val TAP = 5
    const val ALIVE = 6
    const val CLOSE = 7

    const val DRAW = 11
    const val END = 12

    private const val TOKEN = "token"
    private const val COLS = "cols"
    private const val ROWS = "rows"
    private const val TIME = "time"
    private const val DELTA = "delta"
    private const val KEY_NAME = "key"
    private const val COL = "col"
    private const val ROW = "row"
    private const val OPS = "ops"
    private const val TEXTS = "texts"
    private const val IS_TICK = "tick"
    private const val NAME = "name"
    private const val TITLE = "title"
    private const val FPS = "fps"
    private const val KEYS = "keys"
    private const val TAPS = "taps"

    /** A script opened a screen. Sent within the app only. */
    const val ACTION_OPEN = "com.mtwabpin.kolahal.OPEN_SCREEN"

    /** What home needs to show a script's screen. */
    class Request(
        val token: String,
        val script: String,
        val title: String,
        val fps: Int,
        val keys: Boolean,
        val taps: Boolean,
    )

    /** Asks home to show [session]. */
    fun ask(context: Context, session: ScreenSession) {
        context.sendBroadcast(
            Intent(ACTION_OPEN).setPackage(context.packageName)
                .putExtra(TOKEN, session.token)
                .putExtra(NAME, session.name)
                .putExtra(TITLE, session.spec.title)
                .putExtra(FPS, session.spec.fps)
                .putExtra(KEYS, session.spec.keys)
                .putExtra(TAPS, session.spec.taps)
        )
    }

    /** The request in [intent], or null when it isn't one. */
    fun request(intent: Intent): Request? {
        val token = intent.getStringExtra(TOKEN) ?: return null
        val script = intent.getStringExtra(NAME) ?: return null
        return Request(
            token = token,
            script = script,
            title = intent.getStringExtra(TITLE) ?: script,
            fps = intent.getIntExtra(FPS, ScreenSession.DEFAULT_FPS),
            keys = intent.getBooleanExtra(KEYS, false),
            taps = intent.getBooleanExtra(TAPS, false),
        )
    }

    // ---- home to the script ---------------------------------------------------------------

    fun attach(token: String, cols: Int, rows: Int, replyTo: Messenger): Message =
        toScript(ATTACH, token) {
            putInt(COLS, cols)
            putInt(ROWS, rows)
        }.apply { this.replyTo = replyTo }

    fun resize(token: String, cols: Int, rows: Int): Message = toScript(RESIZE, token) {
        putInt(COLS, cols)
        putInt(ROWS, rows)
    }

    fun tick(token: String, timeMs: Long, deltaMs: Long): Message = toScript(TICK, token) {
        putLong(TIME, timeMs)
        putLong(DELTA, deltaMs)
    }

    fun key(token: String, key: String): Message = toScript(KEY, token) { putString(KEY_NAME, key) }

    fun tap(token: String, col: Int, row: Int): Message = toScript(TAP, token) {
        putInt(COL, col)
        putInt(ROW, row)
    }

    fun alive(token: String): Message = toScript(ALIVE, token) {}
    fun close(token: String): Message = toScript(CLOSE, token) {}

    private fun toScript(what: Int, token: String, fill: Bundle.() -> Unit): Message =
        Message.obtain().also {
            it.what = what
            it.data = Bundle().apply {
                putString(TOKEN, token)
                fill()
            }
        }

    /** Which screen [msg] is for. */
    fun token(msg: Message): String? = msg.data?.getString(TOKEN)

    /** What home says with [msg], or null when it is none of these. */
    fun event(msg: Message): ScreenSession.Event? {
        val data = msg.data ?: return null
        return when (msg.what) {
            ATTACH -> ScreenSession.Event.Attach(
                data.getInt(COLS), data.getInt(ROWS), HomeOut(msg.replyTo ?: return null)
            )

            RESIZE -> ScreenSession.Event.Resize(data.getInt(COLS), data.getInt(ROWS))
            TICK -> ScreenSession.Event.Tick(data.getLong(TIME), data.getLong(DELTA))
            KEY -> ScreenSession.Event.Key(data.getString(KEY_NAME) ?: return null)
            TAP -> ScreenSession.Event.Tap(data.getInt(COL), data.getInt(ROW))
            ALIVE -> ScreenSession.Event.Alive
            CLOSE -> ScreenSession.Event.Close
            else -> null
        }
    }

    // ---- the script to home ---------------------------------------------------------------

    /** Home, as the messenger it gave when it attached. */
    private class HomeOut(private val home: Messenger) : ScreenSession.Out {
        override fun draw(frame: ScreenFrame): Boolean = send(Message.obtain().also {
            it.what = DRAW
            it.data = Bundle().apply {
                putInt(COLS, frame.cols)
                putInt(ROWS, frame.rows)
                putBoolean(IS_TICK, frame.tick)
                putIntArray(OPS, frame.ops)
                putStringArray(TEXTS, frame.texts.toTypedArray())
            }
        })

        override fun end() {
            send(Message.obtain().also { it.what = END })
        }

        private fun send(msg: Message): Boolean = try {
            home.send(msg)
            true
        } catch (_: RemoteException) {
            false
        }
    }

    /** The frame in a [DRAW] message, or null when it isn't one. */
    fun frame(msg: Message): ScreenFrame? {
        val data = msg.data ?: return null
        return ScreenFrame(
            cols = data.getInt(COLS),
            rows = data.getInt(ROWS),
            ops = data.getIntArray(OPS) ?: return null,
            texts = data.getStringArray(TEXTS)?.toList() ?: return null,
            tick = data.getBoolean(IS_TICK),
        )
    }
}
