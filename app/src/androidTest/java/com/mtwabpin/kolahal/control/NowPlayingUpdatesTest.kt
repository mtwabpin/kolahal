package com.mtwabpin.kolahal.control

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import com.mtwabpin.kolahal.notifications.NotificationActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** A player repeating itself sends home nothing new: home is built again only when what it shows changes. */
@RunWith(AndroidJUnit4::class)
class NowPlayingUpdatesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }

    private fun waitUntil(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 8_000
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue(what, check())
    }

    @Test fun aRepeatedStateIsNoNewValue() {
        val listener = "${context.packageName}/${KolahalNotificationListener::class.java.name}"
        val had = NotificationActions.isEnabled(context)
        shell("cmd notification allow_listener $listener")
        val session = MediaSession(context, "NowPlayingUpdatesTest")
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            waitUntil("notification access") { NotificationActions.isEnabled(context) }
            fun title(title: String) = session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).build())
            val since = SystemClock.elapsedRealtime()
            // The same state each time, as a player that repeats itself sends it.
            fun playing() = session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 1_000, 1f, since).build())
            title("Song")
            playing()
            session.isActive = true
            val repository = MediaRepository.get(context)
            repository.start()
            waitUntil("playing") { repository.nowPlaying.value?.let { it.title == "Song" && it.playing } == true }

            val values = CopyOnWriteArrayList<NowPlaying?>()
            scope.launch { repository.nowPlaying.collect { values += it } }
            waitUntil("the value as it is") { values.isNotEmpty() }
            repeat(5) { playing() }
            // A change after them: once it's there, the repeats were all heard.
            title("Song again")
            waitUntil("the change") { repository.nowPlaying.value?.title == "Song again" }
            Thread.sleep(200)
            assertEquals(values.joinToString("\n"), listOf("Song", "Song again"), values.map { it?.title })
        } finally {
            scope.cancel()
            session.release()
            if (!had) shell("cmd notification disallow_listener $listener")
        }
    }
}
