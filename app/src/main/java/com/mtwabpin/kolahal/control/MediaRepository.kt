package com.mtwabpin.kolahal.control

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import com.mtwabpin.kolahal.notifications.NotificationActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the active media session is playing. Times are wall-clock millis. */
data class NowPlaying(
    val pkg: String,
    val app: String,
    val title: String?,
    val artist: String?,
    val state: Int,
    /** Position at [positionAt]; extrapolate with [positionNow] while playing. */
    val position: Long,
    val positionAt: Long,
    val speed: Float,
    val duration: Long,
    /** When playback last stopped being active (now for a playing session). */
    val idleSince: Long,
) {
    val playing: Boolean get() = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

    val stateLabel: String get() = when (state) {
        PlaybackState.STATE_PLAYING -> "playing"
        PlaybackState.STATE_PAUSED -> "paused"
        PlaybackState.STATE_STOPPED -> "stopped"
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> "buffering"
        PlaybackState.STATE_ERROR -> "error"
        PlaybackState.STATE_FAST_FORWARDING -> "seeking"
        PlaybackState.STATE_REWINDING -> "rewinding"
        else -> "idle"
    }

    /** "Title — Artist", falling back to the app name. */
    val line: String get() = listOfNotNull(title?.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() })
        .joinToString(" — ").ifEmpty { app }

    fun positionNow(now: Long = System.currentTimeMillis()): Long {
        if (position < 0) return -1
        val p = if (state == PlaybackState.STATE_PLAYING) position + ((now - positionAt) * speed).toLong() else position
        return if (duration > 0) p.coerceAtMost(duration) else p
    }

    /** The header shows it while playing and up to [HIDE_AFTER_MS] after it was paused. */
    fun showInHeader(now: Long = System.currentTimeMillis()): Boolean =
        playing || (state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_ERROR && now - idleSince < HIDE_AFTER_MS)

    companion object {
        const val HIDE_AFTER_MS = 30 * 60_000L
    }
}

/**
 * Wall-clock time each session (by package) was last seen playing. A player can stay silent for the
 * whole of a long track, so the update that finds it stopped counts too: otherwise a pause would
 * date from the track's start, and the line would leave the header at once.
 */
internal class LastPlaying {
    private val times = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var playing = emptySet<String>()

    /** Main thread. [playingNow]: the packages playing at [now]. */
    fun update(playingNow: Set<String>, now: Long) {
        (playingNow + playing).forEach { times[it] = now }
        playing = playingNow
    }

    operator fun get(pkg: String): Long? = times[pkg]
}

/**
 * Follows the most relevant media session (a playing one first, else the most recent) through
 * [MediaSessionManager]. Needs notification access, since our listener's component is the key
 * to [MediaSessionManager.getActiveSessions]. UI process only; [start] is idempotent and cheap
 * to call again (e.g. on resume, once access was granted), and stops listening without access.
 */
class MediaRepository private constructor(private val context: Context) {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val manager by lazy { context.getSystemService(MediaSessionManager::class.java) }
    private val component by lazy { ComponentName(context, KolahalNotificationListener::class.java) }

    @Volatile private var listening = false
    private var controllers: List<MediaController> = emptyList()
    @Volatile private var current: MediaController? = null

    private val lastPlaying = LastPlaying()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> onSessions(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = recompute()
        override fun onMetadataChanged(metadata: MediaMetadata?) = recompute()
        override fun onSessionDestroyed() { main.post { refresh() } }
    }

    fun hasAccess(): Boolean = NotificationActions.isEnabled(context)

    fun start() {
        if (!hasAccess()) {
            if (listening) main.post { stop() }
            return
        }
        if (listening) return
        main.post { listen() }
    }

    /** Main thread. Android refuses without access, and then it isn't listening. */
    private fun listen() {
        if (listening) return
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            listening = true
            refresh()
        }
    }

    /**
     * Listens again, if it was listening. Turning notification access off drops our listener on
     * Android's side, so [start] alone would go on as if it still listened once access is back.
     * The notification listener calls this when it connects, which it does when access comes back:
     * [hasAccess] can still say no at that moment, so this doesn't ask it.
     */
    fun reconnect() {
        main.post {
            if (!listening) return@post
            runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
            listening = false
            listen()
        }
    }

    /** Main thread. Access was turned off: nothing to follow, and nothing playing. */
    private fun stop() {
        listening = false
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        onSessions(emptyList())
    }

    private fun refresh() {
        val list = runCatching { manager.getActiveSessions(component) }.getOrNull()
        if (list == null) {
            // Access was revoked.
            stop()
            return
        }
        onSessions(list)
    }

    private fun onSessions(list: List<MediaController>) {
        controllers.forEach { c -> runCatching { c.unregisterCallback(callback) } }
        controllers = list
        list.forEach { c -> runCatching { c.registerCallback(callback, main) } }
        recompute()
    }

    /** Main thread. Picks the session to show and publishes it. */
    private fun recompute() {
        val now = System.currentTimeMillis()
        lastPlaying.update(controllers.filter { it.playbackState.isActive() }.map { it.packageName }.toSet(), now)
        val chosen = controllers.firstOrNull { it.playbackState.isActive() }
            ?: controllers.maxByOrNull { lastPlaying[it.packageName] ?: 0L }
        current = chosen
        _nowPlaying.value = chosen?.let { snapshot(it, now) }
    }

    private fun PlaybackState?.isActive() =
        this?.state == PlaybackState.STATE_PLAYING || this?.state == PlaybackState.STATE_BUFFERING

    private fun snapshot(c: MediaController, now: Long): NowPlaying {
        val meta = c.metadata
        val ps = c.playbackState
        val elapsedNow = SystemClock.elapsedRealtime()
        val updatedAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 }?.let { now - (elapsedNow - it) } ?: now
        val idleSince = if (ps.isActive()) now else lastPlaying[c.packageName] ?: updatedAt.coerceAtMost(now)
        return NowPlaying(
            pkg = c.packageName,
            app = appLabel(c.packageName),
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            state = ps?.state ?: PlaybackState.STATE_NONE,
            position = ps?.position ?: -1,
            positionAt = updatedAt,
            speed = ps?.playbackSpeed?.takeIf { it != 0f } ?: 1f,
            duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            idleSince = idleSince,
        )
    }

    private val labels = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun appLabel(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    /** The controller [nowPlaying] describes, read fresh when we aren't listening yet. */
    private fun controller(): MediaController? {
        current?.let { return it }
        val list = runCatching { manager.getActiveSessions(component) }.getOrNull().orEmpty()
        return list.firstOrNull { it.playbackState.isActive() } ?: list.firstOrNull()
    }

    enum class Action { PLAY, PAUSE, TOGGLE, NEXT, PREV, STOP }

    /** Sends a transport control; null when there is no session, else what was done. */
    fun send(action: Action): Action? {
        val c = controller() ?: return null
        val t = c.transportControls
        val effective = if (action == Action.TOGGLE) {
            if (c.playbackState.isActive()) Action.PAUSE else Action.PLAY
        } else action
        when (effective) {
            Action.PLAY -> t.play()
            Action.PAUSE -> t.pause()
            Action.NEXT -> t.skipToNext()
            Action.PREV -> t.skipToPrevious()
            Action.STOP -> t.stop()
            Action.TOGGLE -> Unit
        }
        return effective
    }

    /** Fresh snapshot for the `media` command (positions move while playing). */
    fun snapshotNow(): NowPlaying? {
        val c = controller() ?: return null
        return snapshot(c, System.currentTimeMillis())
    }

    companion object {
        @SuppressLint("StaticFieldLeak") // application context only
        @Volatile
        private var instance: MediaRepository? = null

        fun get(context: Context): MediaRepository = instance ?: synchronized(this) {
            instance ?: MediaRepository(context.applicationContext).also { instance = it }
        }
    }
}
