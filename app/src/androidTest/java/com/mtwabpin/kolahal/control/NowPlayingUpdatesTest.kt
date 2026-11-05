package com.mtwabpin.kolahal.control

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.NotificationAccess
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import com.mtwabpin.kolahal.notifications.NotificationActions
import com.mtwabpin.kolahal.waitUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** A player repeating itself sends home nothing new: home is built again only when what it shows changes. */
@RunWith(AndroidJUnit4::class)
class NowPlayingUpdatesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @get:Rule val access = NotificationAccess()

    /**
     * The same state five times, then a position 20 ms off (inside the margin a state told two ways
     * gets) give nothing new; a seek of two seconds with all else the same, and a new title, each give
     * one value. The values are collected unconfined, on the thread that sets them, so none is
     * conflated away.
     */
    @Test fun aRepeatedStateIsNoNewValue() {
        access.set(true)
        val repository = MediaRepository.get(context)
        val session = MediaSession(context, "NowPlayingUpdatesTest")
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            // Bound, and past the reconnect a binding listener asks for, which would ask every session again.
            waitUntil("notification access", ms = 15_000) { NotificationActions.isEnabled(context) && KolahalNotificationListener.connected != null }
            repeat(2) { instrumentation.runOnMainSync {} }
            fun title(title: String) = session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).build())
            val since = SystemClock.elapsedRealtime()
            // The same state each time, as a player that repeats itself sends it.
            fun playing(position: Long = 1_000) =
                session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, position, 1f, since).build())
            title("Song")
            playing()
            session.isActive = true
            repository.start()
            waitUntil("playing") { repository.nowPlaying.value?.let { it.title == "Song" && it.playing } == true }

            val values = CopyOnWriteArrayList<NowPlaying?>()
            scope.launch { repository.nowPlaying.collect { values += it } }
            waitUntil("the value as it is") { values.isNotEmpty() }
            repeat(5) { playing() }
            playing(1_020)
            playing(3_000)
            waitUntil("the seek") { values.lastOrNull()?.position == 3_000L }
            // A change after them: once it's there, the repeats were all heard.
            title("Song again")
            waitUntil("the change") { values.lastOrNull()?.title == "Song again" }
            // The first was asked for, so Android worked its position out to then: only the seek's is exact.
            assertEquals(values.joinToString("\n"), listOf("Song", "Song", "Song again"), values.map { it?.title })
            assertEquals(values.joinToString("\n"), 3_000L, values[1]?.position)
        } finally {
            scope.cancel()
            session.release()
            // No longer following players: access goes back as it was by the rule.
            repository.stop()
        }
    }
}
