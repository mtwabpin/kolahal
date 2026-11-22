/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Home's coming and going, as the log sees it:
 * - whether the log's page is read now ([reading]);
 * - which script lines arrived while you were away ([seenUpTo], [unreadUntil]);
 * - `log.fresh`'s clean screen after some time away.
 *
 * The view model holds one as `session`, and calls it from MainActivity's onStart, onStop, onPause
 * and onResume. It reaches the log through [Host]. [io] is where the log is read, and [clock] tells
 * the time.
 */
class HomeSession(
    private val host: Host,
    private val io: CoroutineContext = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** What the session needs from the view model. */
    interface Host {
        /** The newest line on the log's page, or null when none is loaded ([LogPager.newest]). */
        val newest: Long?

        /** The newest line seen, kept across restarts ([com.mtwabpin.kolahal.data.LogRepository.seenUpTo]). */
        var storedSeenUpTo: Long

        /** `log.fresh`: the minutes away after which home starts clean. 0 is off. */
        val freshMinutes: Long

        /**
         * Takes the lines up to [seen] off the screen at once, before [hideScreen] hides them in the
         * log ([LogPager.dropUpTo]). It is called on the main thread, from [onResume].
         */
        fun dropScreen(seen: Long)

        /** Hides the lines up to [seen] from the screen. It is called on [io]. */
        fun hideScreen(seen: Long)

        /** The marks (seen up to, unread until) the rows were last built with, or null before the first build ([LogPager.rowsMarks]). */
        val rowsMarks: Pair<Long, Long>?

        /** The newest line's id in the log. It is called on [io]. */
        fun maxId(): Long
        fun requestReload()
    }

    /**
     * True while home is shown: from [onResume] (once a fresh screen is decided) to MainActivity's onStop.
     * The log page is read again, and a `/` search runs, only during this time.
     */
    val reading = MutableStateFlow(true)

    /** True when coming back after a stop. Then the single read of the page on return doesn't wait. */
    @Volatile
    var back = false

    /** True from MainActivity's onStart to its onStop. */
    @Volatile
    private var activityStarted = true

    /** Script lines in (seenUpTo, unreadUntil] arrived while the user was away. Later ones were seen live. */
    @Volatile
    var seenUpTo = Long.MAX_VALUE
        private set

    /** The newest line when home was shown again. See [seenUpTo]. */
    @Volatile
    var unreadUntil = Long.MAX_VALUE
        private set

    @Volatile
    private var pausedAt = 0L

    /** MainActivity's onStart (true) and onStop (false). While stopped, the log page isn't read. On return, [onResume] starts it again. */
    fun onStarted(on: Boolean) {
        activityStarted = on
        if (!on) reading.value = false
    }

    fun onPause() {
        // Use the newest line, not the last shown line. After `fresh` nothing shows, and under `log only`
        // the last shown line can be old. The lines before the newest were seen anyway.
        host.newest?.let { host.storedSeenUpTo = it }
        pausedAt = clock()
    }

    /**
     * Home is shown again. After some time away (log.fresh), only lines you had already seen are
     * hidden: script lines that arrived meanwhile stay. Nothing is deleted. They leave the screen
     * before this returns, so no frame of the return shows them; the hide is written in [scope].
     * Then the page is read, once. A home that wasn't stopped reads it only if its rows don't have
     * the new marks yet: the first read of the process may already have them.
     */
    fun onResume(scope: CoroutineScope) {
        seenUpTo = host.storedSeenUpTo
        val freshMin = host.freshMinutes
        val seen = seenUpTo
        val hide =
            freshMin > 0 && pausedAt > 0 && clock() - pausedAt >= freshMin * 60_000L && seen != Long.MAX_VALUE
        if (hide) host.dropScreen(seen)
        scope.launch {
            withContext(io) {
                if (hide) runCatching { host.hideScreen(seen) }
                unreadUntil = runCatching { host.maxId() }.getOrDefault(unreadUntil)
            }
            // On return after a stop, the page is read here, once, after the fresh screen is applied.
            when {
                reading.value -> if (host.rowsMarks != (seenUpTo to unreadUntil)) host.requestReload()
                activityStarted -> {
                    back = true; reading.value = true
                }
            }
        }
    }
}
