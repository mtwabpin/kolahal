/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.core.content.ContextCompat
import com.mtwabpin.kolahal.script.ScreenProtocol
import com.mtwabpin.kolahal.script.ScriptScreenService
import com.mtwabpin.kolahal.terminal.tty.screens.ScriptScreen
import com.mtwabpin.kolahal.terminal.tty.screens.TtyScreen

/**
 * Hears a script that opens a screen of its own (`tty.screen`), and shows it over home with [show],
 * under a name of its own ([PREFIX]). The broadcast comes from the `:script` process
 * ([ScreenProtocol]), which is this app's own. The view model keeps one of these for home.
 */
internal class ScriptScreenRequests(private val context: Context, private val show: (String) -> Unit) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val request = ScreenProtocol.request(intent) ?: return
            latest = request
            show(PREFIX + request.token)
        }
    }

    fun start() {
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(ScreenProtocol.ACTION_OPEN),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    companion object {
        /** A script's screen is called this, then its token: home has no other screen by such a name. */
        const val PREFIX = "script:"

        /** The last screen a script asked for: the one home shows, or will. */
        @Volatile
        private var latest: ScreenProtocol.Request? = null

        /** The screen called [name] when a script asked for it, or null when it isn't a script's. */
        fun screen(context: Context, name: String): TtyScreen? {
            if (!name.startsWith(PREFIX)) return null
            val request = latest?.takeIf { name == PREFIX + it.token } ?: return null
            val link = MessengerLink(context.applicationContext, request.token)
            return ScriptScreen(name, request.title, request.fps, request.keys, request.taps, link)
                .also { link.screen = it }
        }
    }
}

/**
 * A script's screen in the `:script` process, reached through [ScriptScreenService] with the [token]
 * the script's run gave. The service is bound once the screen starts, and the messages wait until it
 * answers. What comes back is handled on the main thread. If the process goes away, the screen ends.
 */
private class MessengerLink(private val context: Context, private val token: String) : ScriptScreen.Link {
    /** The screen of this link. Set right after it is made. */
    lateinit var screen: ScriptScreen

    private val inbox = Messenger(Handler(Looper.getMainLooper()) { msg -> receive(msg); true })
    private var service: Messenger? = null
    private val waiting = ArrayList<Message>()
    private var bound = false
    private var closed = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = Messenger(binder)
            val messages = waiting.toList()
            waiting.clear()
            messages.forEach { deliver(it) }
        }

        /** The `:script` process was stopped, or crashed. */
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            screen.ended()
        }

        override fun onBindingDied(name: ComponentName) = screen.ended()
        override fun onNullBinding(name: ComponentName) = screen.ended()
    }

    private fun receive(msg: Message) {
        when (msg.what) {
            ScreenProtocol.DRAW -> ScreenProtocol.frame(msg)?.let(screen::received)
            ScreenProtocol.END -> screen.ended()
        }
    }

    /** Sends [message], or keeps it until the service is connected. */
    private fun send(message: Message) {
        if (closed) return
        if (service != null) deliver(message) else waiting += message
    }

    private fun deliver(message: Message) {
        try {
            service?.send(message)
        } catch (_: RemoteException) {
            screen.ended()
        }
    }

    override fun attach(cols: Int, rows: Int) {
        bound = context.bindService(
            Intent(context, ScriptScreenService::class.java), connection, Context.BIND_AUTO_CREATE
        )
        if (!bound) return screen.ended()
        send(ScreenProtocol.attach(token, cols, rows, inbox))
    }

    override fun resize(cols: Int, rows: Int) = send(ScreenProtocol.resize(token, cols, rows))
    override fun tick(timeMs: Long, deltaMs: Long) = send(ScreenProtocol.tick(token, timeMs, deltaMs))
    override fun key(key: String) = send(ScreenProtocol.key(token, key))
    override fun tap(col: Int, row: Int) = send(ScreenProtocol.tap(token, col, row))
    override fun alive() = send(ScreenProtocol.alive(token))

    override fun close() {
        if (closed) return
        send(ScreenProtocol.close(token))
        closed = true
        waiting.clear()
        if (bound) runCatching { context.unbindService(connection) }
    }
}
