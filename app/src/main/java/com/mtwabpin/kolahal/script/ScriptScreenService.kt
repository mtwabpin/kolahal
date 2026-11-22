/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import java.util.concurrent.ConcurrentHashMap

/**
 * Where home reaches a script's screen (`tty.screen`): it runs in the `:script` process with the run,
 * and home binds it while the screen shows. It only passes on messages ([ScriptScreens]).
 */
class ScriptScreenService : Service() {
    private val messenger by lazy {
        Messenger(Handler(Looper.getMainLooper()) { msg -> ScriptScreens.fromHome(msg); true })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder
}

/**
 * The script screens open in this process, by their tokens. A run opens one ([home]), and the
 * messages of home that name its token go to it ([fromHome]).
 */
internal object ScriptScreens {
    private val sessions = ConcurrentHashMap<String, ScreenSession>()

    /** Home, as the runs of this process open screens. */
    fun home(context: Context): ScreenSession.Home {
        val app = context.applicationContext
        return object : ScreenSession.Home {
            override fun open(session: ScreenSession): Boolean {
                // Before the broadcast: home may answer at once.
                sessions[session.token] = session
                ScreenProtocol.ask(app, session)
                return true
            }

            override fun closed(session: ScreenSession) {
                sessions.remove(session.token)
            }
        }
    }

    /** A message from home. One for a screen that ended already is answered with its end. */
    fun fromHome(msg: Message) {
        val token = ScreenProtocol.token(msg) ?: return
        val event = ScreenProtocol.event(msg) ?: return
        val session = sessions[token]
        if (session != null) session.post(event)
        else if (event is ScreenSession.Event.Attach) event.out.end()
    }
}
