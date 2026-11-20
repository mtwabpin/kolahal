package com.mtwabpin.kolahal.control

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import com.mtwabpin.kolahal.notifications.NotificationActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the active media session is playing. Times are wall-clock millis. */
data class NowPlaying(
    val pkg: String,
    val app: String,
    val title: String?,
    val artist: String?,
    val state: Int,
    /** Position at [positionAt]. While playing, use [positionNow] to get the current position. */
    val position: Long,
    val positionAt: Long,
    val speed: Float,
    val duration: Long,
    /** When playback last stopped being active. For a playing session, when it was first seen playing. */
    val idleSince: Long,
    /** The player can move to a place in the track ([PlaybackState.ACTION_SEEK_TO]). */
    val canSeek: Boolean = false,
    /** The track's album art, small ([Covers]). Null when the player gives none. */
    val cover: Bitmap? = null,
) {
    val playing: Boolean get() = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

    /** The session has a track to control: it has a state, and no error. */
    val controllable: Boolean get() = state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_ERROR

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

    /** "Title — Artist", or the app name when both are missing. */
    val line: String get() = listOfNotNull(title?.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() })
        .joinToString(" — ").ifEmpty { app }

    fun positionNow(now: Long = System.currentTimeMillis()): Long {
        if (position < 0) return -1
        // Never below 0: [positionAt] can be a moment after [now], because the two clocks are read at different times.
        val p = if (state == PlaybackState.STATE_PLAYING) (position + ((now - positionAt) * speed).toLong()).coerceAtLeast(0) else position
        return if (duration > 0) p.coerceAtMost(duration) else p
    }

    /** True while playing, and up to [HIDE_AFTER_MS] after a pause. Then the header shows it. */
    fun showInHeader(now: Long = System.currentTimeMillis()): Boolean =
        playing || (state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_ERROR && now - idleSince < HIDE_AFTER_MS)

    companion object {
        const val HIDE_AFTER_MS = 30 * 60_000L
    }
}

/**
 * The player that the Now playing card showed last, so that the card can offer it again when
 * nothing plays. [title] and [artist] are of its last track, when known. Only [pkg] is kept over a
 * restart, so after one the track is not known.
 */
data class LastPlayer(val pkg: String, val app: String, val title: String? = null, val artist: String? = null) {
    companion object {
        /**
         * The last player once [shown] is the session that now playing shows: a session with a track
         * to control ([NowPlaying.controllable]) becomes it, with its track. Anything else leaves [last].
         */
        fun after(last: LastPlayer?, shown: NowPlaying?): LastPlayer? =
            if (shown != null && shown.controllable) LastPlayer(shown.pkg, shown.app, shown.title, shown.artist) else last
    }
}

/**
 * A session's album art, as the Now playing card draws it.
 * - The art is the track's picture ([MediaMetadata.METADATA_KEY_ART] or
 *   [MediaMetadata.METADATA_KEY_ALBUM_ART]), else the player's picture for it
 *   ([MediaMetadata.METADATA_KEY_DISPLAY_ICON]). A link to a picture (`ART_URI`) is not followed:
 *   it would read another app's files, or the web.
 * - It is cut to the square in its middle and made [COVER_DP] across, once, when the player sends
 *   it. Only that small copy is kept, one for each session, and never on disk.
 */
internal object Covers {
    /** The cover's side on the card, in dp. */
    const val COVER_DP = 56

    /** The middle square of a picture [width] by [height]: its start across, its start down, and its side. */
    fun square(width: Int, height: Int): Triple<Int, Int, Int> {
        val side = minOf(width, height).coerceAtLeast(0)
        return Triple((width - side) / 2, (height - side) / 2, side)
    }

    /** How much a square [side] across is scaled to be [px] across. Never more than 1: a small picture is not made larger. */
    fun scale(side: Int, px: Int): Float = if (side <= 0) 0f else minOf(1f, px.toFloat() / side)

    /**
     * The cover of the track in [meta], [px] across, or null when the player gives no picture.
     * [old] is the cover kept until now. When the new one is the same picture, [old] is kept, so
     * the card isn't drawn again for a player that sends its track again.
     */
    fun of(meta: MediaMetadata?, px: Int, old: Bitmap?): Bitmap? {
        val art = meta?.let {
            it.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        } ?: return null
        val made = runCatching { scaled(art, px) }.getOrNull() ?: return null
        return if (old != null && runCatching { old.sameAs(made) }.getOrDefault(false)) old else made
    }

    /**
     * [art] cut and scaled. Android sends a player's picture at 320 dp at most, so this takes about a
     * millisecond, once for each track.
     */
    private fun scaled(art: Bitmap, px: Int): Bitmap? {
        // A picture in graphics memory can't be cut in memory: it is copied out first.
        val source = if (art.config == Bitmap.Config.HARDWARE) art.copy(Bitmap.Config.ARGB_8888, false) ?: return null else art
        val (x, y, side) = square(source.width, source.height)
        if (side <= 0) return null
        val s = scale(side, px)
        return Bitmap.createBitmap(source, x, y, side, side, Matrix().apply { setScale(s, s) }, true)
    }
}

/**
 * Wall-clock time when each session (by package) was last seen playing. A player may send no update
 * during a whole long track. So the update that shows it stopped also counts as "seen playing".
 * Otherwise a pause would seem to start at the track's start, and the line would leave the header at once.
 */
internal class LastPlaying {
    private val times = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var playing = emptySet<String>()

    /** Main thread. [playingNow] is the set of packages playing at [now]. */
    fun update(playingNow: Set<String>, now: Long) {
        (playingNow + playing).forEach { times[it] = now }
        playing = playingNow
    }

    operator fun get(pkg: String): Long? = times[pkg]
}

/**
 * Follows the most relevant media session through [MediaSessionManager]: a playing one first,
 * else the most recent one.
 * - It needs notification access, because our listener's component is the key to
 *   [MediaSessionManager.getActiveSessions].
 * - It runs in the UI process only.
 * - [start] is idempotent and cheap to call again (for example on resume, after access was granted).
 *   Without access, it stops listening.
 * - It keeps the player that was shown last ([lastPlayer]), and its package over a restart.
 */
class MediaRepository private constructor(private val context: Context) {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _lastPlayer = MutableStateFlow<LastPlayer?>(null)

    /**
     * The player that [nowPlaying] showed last with a track ([LastPlayer.after]): while it shows one,
     * that session's player. Null when none was shown since Kolahal was installed. The package kept
     * from an earlier start counts only while its app is installed.
     */
    val lastPlayer: StateFlow<LastPlayer?> = _lastPlayer.asStateFlow()

    /** Reads and writes the last player's package, off the main thread. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val main = Handler(Looper.getMainLooper())
    private val manager by lazy { context.getSystemService(MediaSessionManager::class.java) }
    private val component by lazy { ComponentName(context, KolahalNotificationListener::class.java) }

    @Volatile private var listening = false
    private var watches: List<Watch> = emptyList()
    @Volatile private var current: MediaController? = null

    private val lastPlaying = LastPlaying()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> onSessions(list.orEmpty()) }

    /**
     * A followed session, with the state its updates last reported. An update from any player picks
     * what to show from these. Asking every session again took two system calls per session, plus the
     * shown session's metadata with its artwork, on the main thread at every update.
     */
    private inner class Watch(val controller: MediaController) : MediaController.Callback() {
        var state: PlaybackState? = null
        var track: Track? = null

        /** The track's cover ([Covers]): the one picture kept for this session. */
        var cover: Bitmap? = null

        /** Reads the session's current state. Call it after its updates come here, so no update is missed in between. */
        fun read() {
            state = controller.playbackState
            metadata(controller.metadata)
        }

        private fun metadata(meta: MediaMetadata?) {
            track = Track.of(meta)
            cover = Covers.of(meta, coverPx(), cover)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            this.state = state
            recompute()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            metadata(metadata)
            recompute()
        }

        override fun onSessionDestroyed() { main.post { refresh() } }
    }

    fun hasAccess(): Boolean = NotificationActions.isEnabled(context)

    fun start() {
        if (!hasAccess()) {
            if (listening) stop()
            return
        }
        if (listening) return
        main.post { listen() }
    }

    /** Main thread. Without access, Android refuses, and then it doesn't listen. */
    private fun listen() {
        if (listening) return
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            listening = true
            refresh()
        }
    }

    /**
     * Listens again, if it was listening. When notification access is turned off, Android drops our
     * listener. So after access returns, [start] alone would act as if it still listened.
     * The notification listener calls this when it connects, which happens when access returns.
     * At that moment [hasAccess] can still return false, so this doesn't check it.
     */
    fun reconnect() {
        main.post {
            if (!listening) return@post
            runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
            listening = false
            // Read every session again, as at the first start.
            watches.forEach { w -> runCatching { w.controller.unregisterCallback(w) } }
            watches = emptyList()
            listen()
        }
    }

    /** Stops following players. Used when access is turned off, and when a test is done with them. */
    fun stop() {
        main.post { unlisten() }
    }

    /** Main thread. Stops listening: nothing to follow, and nothing playing. */
    private fun unlisten() {
        listening = false
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        onSessions(emptyList())
    }

    private fun refresh() {
        val list = runCatching { manager.getActiveSessions(component) }.getOrNull()
        if (list == null) {
            // Access was revoked.
            unlisten()
            return
        }
        onSessions(list)
    }

    private fun onSessions(list: List<MediaController>) {
        // A session already followed keeps its callback and last state. Only new sessions are read.
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
        val shown = chosen?.let { snapshot(it.controller.packageName, it.track, it.state, now, it.cover) }
        _nowPlaying.value = shown
        keepLast(LastPlayer.after(_lastPlayer.value, shown))
    }

    /** Main thread. Keeps [last] as the last player. Its package is written down when it changes. */
    private fun keepLast(last: LastPlayer?) {
        val before = _lastPlayer.value
        if (last == null || last == before) return
        _lastPlayer.value = last
        if (last.pkg != before?.pkg) background.launch { runCatching { Stores.MEDIA.open(context).edit { putString(LAST_PLAYER, last.pkg) } } }
    }

    /** The cover's side in pixels on this screen ([Covers.COVER_DP]). */
    private fun coverPx(): Int = (Covers.COVER_DP * context.resources.displayMetrics.density).roundToInt()

    private fun PlaybackState?.isActive() =
        this?.state == PlaybackState.STATE_PLAYING || this?.state == PlaybackState.STATE_BUFFERING

    /** The track details that now playing shows. It doesn't keep the artwork, which can be MBs per session: [Watch.cover] is a small copy. */
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
     * Wall-clock time at boot, as last computed from the two clocks. It is kept while a new reading is
     * within one second of it (setting the clock moves it more). So an unchanged state gives an equal value.
     */
    @Volatile private var bootedAt = 0L

    /** Converts [elapsed], a time on the boot clock, to wall-clock time. */
    private fun wallTime(elapsed: Long, now: Long): Long {
        val booted = now - SystemClock.elapsedRealtime()
        if (kotlin.math.abs(booted - bootedAt) > 1_000) bootedAt = booted
        return bootedAt + elapsed
    }

    /**
     * Builds the [nowPlaying] value for [pkg]'s session, with its [cover]. Players often repeat the same
     * update. An update that changes nothing shown gives a value equal to the last one, so home isn't
     * built again.
     */
    private fun snapshot(pkg: String, track: Track?, ps: PlaybackState?, now: Long, cover: Bitmap?): NowPlaying {
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
            canSeek = ps != null && (ps.actions and PlaybackState.ACTION_SEEK_TO) != 0L,
            cover = cover,
        )
        // The same playback can be reported in two ways. When we ask, Android computes the position
        // for now. In a callback, it is the player's own position from earlier. Keep the last value.
        val last = _nowPlaying.value
        val sameTrack = last != null && last.copy(position = fresh.position, positionAt = fresh.positionAt) == fresh
        return if (sameTrack && kotlin.math.abs(last.positionNow(now) - fresh.positionNow(now)) < SAME_POSITION_MS) last else fresh
    }


    /** The player's name as the app list shows it ([AppNames]): a rename counts, and an update is seen. */
    private fun appLabel(pkg: String): String = com.mtwabpin.kolahal.apps.AppNames.label(context, pkg) ?: pkg

    /** The controller that [nowPlaying] describes. When we aren't listening yet, it is read fresh. */
    private fun controller(): MediaController? {
        current?.let { return it }
        val list = runCatching { manager.getActiveSessions(component) }.getOrNull().orEmpty()
        return list.firstOrNull { it.playbackState.isActive() } ?: list.firstOrNull()
    }

    enum class Action { PLAY, PAUSE, TOGGLE, NEXT, PREV, STOP }

    /** Sends a transport control. Returns the action done, or null when there is no session. */
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

    /** Moves the session that [nowPlaying] describes to [ms] in its track. False when there is no session. */
    fun seekTo(ms: Long): Boolean {
        val c = controller() ?: return false
        c.transportControls.seekTo(ms.coerceAtLeast(0))
        return true
    }

    /** What [resume] did. */
    enum class Resumed {
        /** The last player's session got play. */
        SESSION,

        /** Android got a Play key, which goes to the last player. */
        KEY,

        /** Nothing was sent: there is no last player, or Android would give the key to another app. */
        NOTHING,
    }

    /**
     * Plays the last player's track again ([lastPlayer]), with nothing playing.
     * - Its session gets play, when it is still there.
     * - Else Android gets a Play key, as a headset's button sends it. With no session to take it,
     *   Android gives it to the app that played last, and that app starts its track again. From
     *   Android 13 on, Android says which app that is, and the key is sent only when it is the last player.
     */
    fun resume(): Resumed {
        val last = _lastPlayer.value ?: return Resumed.NOTHING
        val session = runCatching { manager.getActiveSessions(component) }.getOrNull().orEmpty().firstOrNull { it.packageName == last.pkg }
        if (session != null) {
            session.transportControls.play()
            return Resumed.SESSION
        }
        // Without its session, a Play key goes to whichever app Android picks. Only from Android 13 can
        // Kolahal tell that it is the last player's; before that, or when it can't tell, the app opens instead.
        if (Build.VERSION.SDK_INT < 33) return Resumed.NOTHING
        val taker = runCatching { manager.mediaKeyEventSessionPackageName }.getOrNull()
        if (taker != last.pkg) return Resumed.NOTHING
        val audio = context.getSystemService(AudioManager::class.java) ?: return Resumed.NOTHING
        val at = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(at, at, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(at, at, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        return Resumed.KEY
    }

    /** A fresh snapshot for the `media` command, because positions move while playing. It has no cover. */
    fun snapshotNow(): NowPlaying? {
        val c = controller() ?: return null
        return snapshot(c.packageName, Track.of(c.metadata), c.playbackState, System.currentTimeMillis(), cover = null)
    }

    // Last in the class: everything above is set when this starts.
    init {
        // The last player of an earlier start, until a session shows. An app that is gone is no player.
        background.launch {
            val pkg = runCatching { Stores.MEDIA.open(context).getString(LAST_PLAYER, null) }.getOrNull() ?: return@launch
            val app = runCatching { com.mtwabpin.kolahal.apps.AppNames.label(context, pkg) }.getOrNull() ?: return@launch
            _lastPlayer.compareAndSet(null, LastPlayer(pkg, app))
        }
    }

    companion object : Singleton<MediaRepository>(::MediaRepository) {
        /** Two positions this close, computed for the same moment, count as the same. */
        private const val SAME_POSITION_MS = 50L

        /** The last player's package, in its own preferences ([Stores.MEDIA]): not for the cloud. */
        private const val LAST_PLAYER = "last-player"
    }
}
