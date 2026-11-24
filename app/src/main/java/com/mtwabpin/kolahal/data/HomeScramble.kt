/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/**
 * Whether `scramble` is on: home's log shows no real text, for a screen recording. It is on while
 * there is a [seed], which the scrambled text comes from; each time it is turned on, a new one.
 * - It is kept ([Stores.SCRAMBLE]) and written at once, so a process killed and started again
 *   comes back scrambled. When it can't be written (a full disk), it isn't turned on.
 * - It is read when it is made, before home's first frame: home is never drawn unscrambled first.
 * - It is a way of showing only. The log, its searches, scripts and backups stay as they are.
 */
class HomeScramble internal constructor(
    private val read: () -> Long?,
    /** Keeps the seed, or that there is none. False when it could not be written. */
    private val write: (Long?) -> Boolean,
    private val newSeed: () -> Long = { randomSeed() },
) {
    private val _seed = MutableStateFlow(read())

    /** The seed the log is scrambled with, or null while the log shows as it is. */
    val seed: StateFlow<Long?> = _seed.asStateFlow()

    val on: Boolean get() = _seed.value != null

    /**
     * Turns it on with a new seed. False when it was on: then it keeps its seed, so nothing changes
     * on screen. Null when the seed could not be kept: then it stays off, as it would be after a restart.
     */
    @Synchronized
    fun turnOn(): Boolean? {
        if (_seed.value != null) return false
        val s = newSeed()
        if (!write(s)) return null
        _seed.value = s
        return true
    }

    /** Turns it off. False when it was off. It goes off even when that can't be saved: then a restart brings it back on. */
    @Synchronized
    fun turnOff(): Boolean {
        if (_seed.value == null) return false
        write(null)
        _seed.value = null
        return true
    }

    /**
     * `scramble [on|off]`: turns it on or off, and returns the line it leaves in the log. Null for
     * anything else, which gets the command's usage.
     */
    fun run(args: List<String>): String? = when (args.map { it.lowercase() }) {
        emptyList<String>(), listOf("on") -> when (turnOn()) {
            true -> ON
            false -> ALREADY_ON
            null -> NOT_KEPT
        }

        listOf("off") -> if (turnOff()) OFF else ALREADY_OFF
        else -> null
    }

    companion object {
        const val ON = "Scramble on: home shows no real text\n'scramble off' turns it off"
        const val ALREADY_ON = "Scramble is on\n'scramble off' turns it off"

        /** What Copy gives on the lines [ON] and [ALREADY_ON]: the command they name. */
        const val COPY = "scramble off"
        const val OFF = "Scramble off"
        const val ALREADY_OFF = "Scramble is off"
        const val NOT_KEPT = "Scramble is off: it could not be saved"

        private const val SEED = "seed"

        @Volatile
        private var instance: HomeScramble? = null

        fun get(context: Context): HomeScramble = instance ?: synchronized(this) {
            instance ?: run {
                val prefs = Stores.SCRAMBLE.open(context.applicationContext)
                HomeScramble(
                    read = { if (prefs.contains(SEED)) prefs.getLong(SEED, 0L) else null },
                    // At once, not later: the mode must survive the process being killed right after.
                    write = { s ->
                        val edit = prefs.edit()
                        if (s == null) edit.remove(SEED) else edit.putLong(SEED, s)
                        edit.commit()
                    },
                )
            }.also { instance = it }
        }

        /** A seed nobody can guess from the log, never 0. */
        private fun randomSeed(): Long {
            val random = SecureRandom()
            var s: Long
            do {
                s = random.nextLong()
            } while (s == 0L)
            return s
        }
    }
}
