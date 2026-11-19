package com.mtwabpin.kolahal.control

import android.app.NotificationManager
import android.content.Context

/**
 * Do Not Disturb, the same from the `dnd` command and from scripts (`device.setDnd`): the mode
 * words, and a change that is checked.
 */
object Dnd {
    /** Android applies a change a moment after the request: how long [set] waits to see "off". */
    private const val SETTLE_MS = 1_000L

    /** What [set] did. */
    enum class Result {
        DONE,

        /** Still on after "off": since Android 15, Kolahal can turn off only the Do Not Disturb it turned on. */
        STILL_ON,

        /** Kolahal has no Do Not Disturb access. */
        NO_ACCESS,

        /** Android refused the change. */
        FAILED,
    }

    /** A mode word as `dnd` and scripts name it, as Android's filter; null for a word that isn't one. */
    fun filterOf(word: String): Int? = when (word.trim().lowercase()) {
        "on", "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
        "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
        "none", "total", "silence" -> NotificationManager.INTERRUPTION_FILTER_NONE
        "off" -> NotificationManager.INTERRUPTION_FILTER_ALL
        else -> null
    }

    /** Android's filter as one word: "off", "priority", "alarms" or "none"; null for one Android adds later. */
    fun wordOf(filter: Int): String? = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "none"
        else -> null
    }

    /**
     * Sets Do Not Disturb to [filter]. Turning it off waits up to a second to see it go off, as
     * Do Not Disturb turned on elsewhere (quick settings, a schedule, another app) stays on.
     */
    fun set(context: Context, filter: Int): Result {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return Result.NO_ACCESS
        if (runCatching { nm.setInterruptionFilter(filter) }.isFailure) return Result.FAILED
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL) return Result.DONE
        val until = System.currentTimeMillis() + SETTLE_MS
        while (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            if (System.currentTimeMillis() >= until) return Result.STILL_ON
            Thread.sleep(50)
        }
        return Result.DONE
    }
}
