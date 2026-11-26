/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import android.content.Context
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.script.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One-off shaders (`@once`): they run once for some seconds over home, on top of the shader that is on, and
 * go. `shader play hearts` starts one, and so does a script's `shader.play` ([request]).
 * - One plays at a time: another that comes meanwhile is left out.
 * - It waits for home to be in front, for [WAIT_MS] at most.
 */
object ShaderPlay {
    /** One that plays, or waits for home. [seconds]: how long, or null for the shader's own time. */
    data class Playing(val name: String, val seconds: Float?, val at: Long)

    /** A one-off shader runs this long at least and at most, in seconds. */
    const val MIN_SECONDS = 1f
    const val MAX_SECONDS = 10f

    /** What is said when one comes while another plays, and is left out. */
    const val PLAYING = "A shader is playing"

    /** How long one waits for home to be in front. */
    const val WAIT_MS = 30 * 60_000L

    private const val KEY = "next"

    private val _current = MutableStateFlow<Playing?>(null)

    /** The one that plays, if any. Home draws it. */
    val current: StateFlow<Playing?> = _current.asStateFlow()

    private val _covered = MutableStateFlow(0)

    /** How many full-screen screens are over home: one that is there draws it, and home doesn't. */
    val covered: StateFlow<Int> = _covered.asStateFlow()

    fun cover(on: Boolean) = _covered.update { (it + if (on) 1 else -1).coerceAtLeast(0) }

    /** Plays [name], unless one plays already. Main process. False when one does. */
    fun play(name: String, seconds: Float?, at: Long = System.currentTimeMillis()): Boolean {
        val next = Playing(name, seconds?.coerceIn(MIN_SECONDS, MAX_SECONDS), at)
        // One that home never got to show doesn't keep others out.
        _current.value?.let { if (stale(it, at)) _current.compareAndSet(it, null) }
        return _current.compareAndSet(null, next)
    }

    private fun stale(p: Playing, now: Long) = now - p.at > WAIT_MS

    /** [p] has run, or can't. */
    fun done(p: Playing) {
        _current.compareAndSet(p, null)
    }

    /** Asks home to play [name]: from a script's run, in its own process. */
    fun request(context: Context, name: String, seconds: Float?) {
        KvStore(context, Stores.SHADER_PLAY).set(
            KEY, Json.stringify(mapOf("name" to name, "seconds" to (seconds?.toDouble() ?: 0.0), "at" to System.currentTimeMillis().toString())),
        )
        LogRepository.get(context).onStatusChanged()
    }

    /** Plays what a script asked for, if it did. Returns a name that is no one-off shader's, to say so. Main process, not its main thread. */
    fun load(context: Context): String? {
        val store = KvStore(context, Stores.SHADER_PLAY)
        val raw = store.get(KEY) ?: return null
        // Taken away first: a shader that crashes Kolahal doesn't come back.
        store.set(KEY, null)
        val asked = runCatching { Json.parse(raw) as? Map<*, *> }.getOrNull() ?: return null
        val name = asked["name"] as? String ?: return null
        val at = (asked["at"] as? String)?.toLongOrNull() ?: return null
        if (System.currentTimeMillis() - at > WAIT_MS) return null
        if (!Shaders.once(context, name)) return name
        play(name, (asked["seconds"] as? Number)?.toFloat()?.takeIf { it > 0f }, at)
        return null
    }
}
