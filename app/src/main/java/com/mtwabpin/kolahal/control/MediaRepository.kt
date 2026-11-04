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
    /** When playback last stopped being active; for a playing session, when it was first seen playing. */
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
        // Never before the start: [positionAt] can be a moment ahead of [now] (two clocks, read apart).
        val p = if (state == PlaybackState.STATE_PLAYING) (position + ((now - positionAt) * speed).toLong()).coerceAtLeast(0) else position
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
    private var watches: List<Watch> = emptyList()
    @Volatile private var current: MediaController? = null

    private val lastPlaying = LastPlaying()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> onSessions(list.orEmpty()) }

    /**
     * A session followed, with what its updates last said. An update from any player picks what to
     * show from these: asking every session again was two system calls each, and the shown one's
     * metadata with its artwork, on the main thread at every update.
     */
    private inner class Watch(val controller: MediaController) : MediaController.Callback() {
        var state: PlaybackState? = null
        var track: Track? = null

        /** What the session says now: read once its updates come here, so none falls in between. */
        fun read() {
            state = controller.playbackState
            track = Track.of(controller.metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            this.state = state
            recompute()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            track = Track.of(metadata)
            recompute()
        }

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
            // Every session is asked again, as at the first start.
            watches.forEach { w -> runCatching { w.controller.unregisterCallback(w) } }
            watches = emptyList()
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
        // A session followed already keeps its callback and what it last said: only new ones are asked.
        val kept = watches.associateBy { it.controller.sessionToken }
        val next = list.map { c -> kept[c.sessionToken] ?: Watch(c).also { w -> runCatching { c.registerCallback(w, main) }; w.read() } }
        val still = next.toHashSet()
        watches.filterNot { it in still }.forEach { w -> runCatching { w.controller.unregisterCallback(w) } }
        watches = next
        recompute()
    }

    /** Main thread. Picks the session to show and publishes it. */
    private fun recompute() {
        val now = System.currentTimeMillis()
        lastPlaying.update(watches.filter { it.state.isActive() }.map { it.controller.packageName }.toSet(), now)
        val chosen = watches.firstOrNull { it.state.isActive() }
            ?: watches.maxByOrNull { lastPlaying[it.controller.packageName] ?: 0L }
        current = chosen?.controller
        _nowPlaying.value = chosen?.let { snapshot(it.controller.packageName, it.track, it.state, now) }
    }

    private fun PlaybackState?.isActive() =
        this?.state == PlaybackState.STATE_PLAYING || this?.state == PlaybackState.STATE_BUFFERING

    /** What now playing shows of a track, kept without its artwork, which can be MB a session. */
    private class Track(val title: String?, val artist: String?, val duration: Long) {
        companion object {
            fun of(meta: MediaMetadata?): Track? = meta?.let {
                Track(
                    title = it.getString(MediaMetadata.METADATA_KEY_TITLE) ?: it.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                    artist = it.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: it.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                        ?: it.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                    duration = it.getLong(MediaMetadata.METADATA_KEY_DURATION),
                )
            }
        }
    }

    /**
     * Wall-clock time at boot, as last worked out from the two clocks. Kept while a new reading agrees
     * within a second (setting the clock moves it more), so an unchanged state gives an equal value.
     */
    @Volatile private var bootedAt = 0L

    /** The wall-clock time of [elapsed], a time on the boot clock. */
    private fun wallTime(elapsed: Long, now: Long): Long {
        val booted = now - SystemClock.elapsedRealtime()
        if (kotlin.math.abs(booted - bootedAt) > 1_000) bootedAt = booted
        return bootedAt + elapsed
    }

    /**
     * What [pkg]'s session says, for [nowPlaying]. An update that changes nothing shown gives a value
     * equal to the last one, so home isn't built again for it: players repeat themselves.
     */
    private fun snapshot(pkg: String, track: Track?, ps: PlaybackState?, now: Long): NowPlaying {
        val updatedAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 }?.let { wallTime(it, now) } ?: now
        val idleSince = if (ps.isActive()) {
            _nowPlaying.value?.takeIf { it.pkg == pkg && it.playing }?.idleSince ?: now
        } else {
            lastPlaying[pkg] ?: updatedAt.coerceAtMost(now)
        }
        val fresh = NowPlaying(
            pkg = pkg,
            app = appLabel(pkg),
            title = track?.title,
            artist = track?.artist,
            state = ps?.state ?: PlaybackState.STATE_NONE,
            position = ps?.position ?: -1,
            positionAt = updatedAt,
            speed = ps?.playbackSpeed?.takeIf { it != 0f } ?: 1f,
            duration = track?.duration ?: 0L,
            idleSince = idleSince,
        )
        // The same playback told two ways (asked, Android works the position out to now; in a
        // callback it's the player's own, from earlier): the last value stands.
        val last = _nowPlaying.value
        val sameTrack = last != null && last.copy(position = fresh.position, positionAt = fresh.positionAt) == fresh
        return if (sameTrack && kotlin.math.abs(last.positionNow(now) - fresh.positionNow(now)) < SAME_POSITION_MS) last else fresh
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
        return snapshot(c.packageName, Track.of(c.metadata), c.playbackState, System.currentTimeMillis())
    }

    companion object {
        /** Two positions this close, worked out to the same moment, are the same. */
        private const val SAME_POSITION_MS = 50L

        @SuppressLint("StaticFieldLeak") // application context only
        @Volatile
        private var instance: MediaRepository? = null

        fun get(context: Context): MediaRepository = instance ?: synchronized(this) {
            instance ?: MediaRepository(context.applicationContext).also { instance = it }
        }
    }
}
