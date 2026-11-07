package com.mtwabpin.kolahal.gesture

import android.content.Context

/** Built-in gesture actions. Any other gesture value runs as a terminal command. */
@android.annotation.SuppressLint("WrongConstant")
object Gestures {
    const val LOCK = "lock"
    const val SHADE = "shade"
    const val NONE = "none"

    /**
     * Pulls down the notification shade. It uses a hidden API that most launchers use.
     * If the system blocks the call, nothing happens.
     */
    fun expandShade(context: Context) {
        runCatching {
            val sbm = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
        }
    }
}
