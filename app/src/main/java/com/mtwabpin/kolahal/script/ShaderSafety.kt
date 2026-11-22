/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.data.sha256Hex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Keeps a shader that crashes, hangs or slows down home from doing it again (pure, over a [Store];
 * [get] gives it the phone's).
 * - A shader is named with its code ([id]): a changed file is a new shader, and starts over.
 * - While a shader that isn't [proven] yet is on screen, it is [arm]ed in the store before its first
 *   frame. If Kolahal then ends by a crash or a hang, the mark is still there at the next start, and
 *   [recover] turns the shader off: a safe mode.
 * - A shader that is too slow ([ShaderWatchdog]) is [trip]ped too.
 * - A tripped shader isn't drawn until its file changes or the user turns a shader on again
 *   ([turnedOn]). After [MAX_TRIPS] trips in a row with none [proven] in between, `screen.shader` goes off.
 */
class ShaderSafety(private val store: Store) {
    interface Store {
        fun get(key: String): String?

        /** Writes [value] (null removes it). With [now], it is on disk when this returns. */
        fun set(key: String, value: String?, now: Boolean = false)
    }

    /** How the process that held the mark ended. */
    enum class Exit { CRASH, HANG, OTHER, UNKNOWN }

    private val _version = MutableStateFlow(0)

    /** Grows at each change of what is tripped. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun tripped(): String? = store.get(TRIPPED)

    fun isTripped(id: String): Boolean = tripped() == id

    /** Why the [tripped] shader was turned off, when that is known. */
    fun why(): String? = store.get(WHY)

    /** The shader named last, so `shader on` turns it on again. */
    var last: String?
        get() = store.get(LAST)
        set(name) = store.set(LAST, name)

    /** How many surfaces draw each shader now (home, and a screen over it). */
    private val shown = HashMap<String, Int>()

    /**
     * [id] is about to be compiled: it is marked, on disk, unless it is [proven] already, so a crash
     * while compiling is caught too. [arm] follows when it is drawn, [cancel] when it isn't.
     */
    @Synchronized
    fun begin(id: String) {
        if (store.get(PROVEN) == id || store.get(ARMED) == id) return
        store.set(ARMED, id, now = true)
    }

    /** [id] was [begin]ned and won't be drawn: its mark goes, unless a surface draws it. */
    @Synchronized
    fun cancel(id: String) {
        if (id !in shown) unmark(id)
    }

    /** A surface starts drawing [id]: it is marked until it is [proven]. Each needs its [disarm]. */
    @Synchronized
    fun arm(id: String) {
        shown[id] = (shown[id] ?: 0) + 1
        begin(id)
    }

    /** A surface stopped drawing [id] without trouble. The mark goes with the last of them. */
    @Synchronized
    fun disarm(id: String) {
        val left = (shown[id] ?: return) - 1
        if (left > 0) {
            shown[id] = left
            return
        }
        shown.remove(id)
        unmark(id)
    }

    /** [id] ran well for long enough. Its mark goes, and so does the count of trips. */
    @Synchronized
    fun proven(id: String) {
        store.set(PROVEN, id)
        store.set(TRIPS, null)
        unmark(id)
    }

    private fun unmark(id: String) {
        if (store.get(ARMED) == id) store.set(ARMED, null)
    }

    /**
     * Turns [id] off: too slow, or gone with a crash ([why], for the Shaders screen). Returns how many
     * trips in a row this makes, or 0 when it was tripped already.
     */
    @Synchronized
    fun trip(id: String, why: String? = null): Int {
        if (isTripped(id)) return 0
        val count = (store.get(TRIPS)?.toIntOrNull() ?: 0) + 1
        unmark(id)
        store.set(TRIPPED, id)
        store.set(WHY, why)
        store.set(TRIPS, count.toString())
        _version.update { it + 1 }
        return count
    }

    /** The user turned a shader on: it may be drawn again, and tripped again. */
    fun turnedOn() {
        if (tripped() == null) return
        store.set(TRIPPED, null)
        store.set(WHY, null)
        _version.update { it + 1 }
    }

    /**
     * At the first use of shaders in a process: the shader that an earlier process was drawing when
     * it ended, if that ended by a crash or a hang (or for a reason nobody knows). The mark is
     * removed either way; the caller trips the shader.
     */
    @Synchronized
    fun recover(exit: Exit): String? {
        val id = store.get(ARMED) ?: return null
        store.set(ARMED, null)
        return id.takeIf { exit != Exit.OTHER }
    }

    companion object {
        /** After this many trips in a row, `screen.shader` goes off. */
        const val MAX_TRIPS = 3

        private const val ARMED = "shader.armed"
        private const val TRIPPED = "shader.tripped"
        private const val WHY = "shader.why"
        private const val TRIPS = "shader.trips"
        private const val PROVEN = "shader.proven"
        private const val LAST = "shader.last"

        /** The shader [name] whose code is [source], as the store names it. */
        fun id(name: String, source: String): String = name + "@" + sha256Hex(source.toByteArray()).take(12)

        fun nameOf(id: String): String = id.substringBefore('@')

        private val holder = Singleton(::create, mainOnly = true)

        fun get(context: Context): ShaderSafety = holder.get(context)

        private fun create(context: Context) = ShaderSafety(PrefsStore(Stores.CRASH.open(context)))

        /** How the last process of Kolahal's that is gone ended: the exit that left a mark. */
        fun lastExit(context: Context): Exit {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Exit.UNKNOWN
            return runCatching { exit(context) }.getOrDefault(Exit.UNKNOWN)
        }

        @RequiresApi(Build.VERSION_CODES.R)
        private fun exit(context: Context): Exit {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return Exit.UNKNOWN
            // The main process only: the :script process ends all the time, without home.
            val info = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
                .filter { it.processName == context.packageName }.maxByOrNull { it.timestamp }
                ?: return Exit.UNKNOWN
            return when (info.reason) {
                ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> Exit.CRASH

                ApplicationExitInfo.REASON_ANR -> Exit.HANG
                // The user, an update, a settings change, low memory or Kolahal itself ended it: no sign of the shader's fault.
                ApplicationExitInfo.REASON_USER_REQUESTED, ApplicationExitInfo.REASON_USER_STOPPED,
                ApplicationExitInfo.REASON_PACKAGE_UPDATED, ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
                ApplicationExitInfo.REASON_EXIT_SELF, ApplicationExitInfo.REASON_LOW_MEMORY,
                ApplicationExitInfo.REASON_SIGNALED, ApplicationExitInfo.REASON_PERMISSION_CHANGE,
                ApplicationExitInfo.REASON_FREEZER -> Exit.OTHER

                else -> Exit.UNKNOWN
            }
        }
    }

    private class PrefsStore(private val prefs: SharedPreferences) : Store {
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun set(key: String, value: String?, now: Boolean) =
            prefs.edit(commit = now) { if (value == null) remove(key) else putString(key, value) }
    }
}
