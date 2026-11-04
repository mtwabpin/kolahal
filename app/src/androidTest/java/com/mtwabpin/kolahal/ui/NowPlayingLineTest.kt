package com.mtwabpin.kolahal.ui

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.control.MediaRepository
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import com.mtwabpin.kolahal.notifications.NotificationActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val DURATION = 10_000L

/** The header's media line: what it says, what its taps do, and how far along its bar is. */
@RunWith(AndroidJUnit4::class)
class NowPlayingLineTest {
    @get:Rule val compose = createComposeRule()
    private var toggles = 0
    private var skips = 0

    private fun track(
        state: Int = PlaybackState.STATE_PLAYING,
        position: Long = 0,
        duration: Long = DURATION,
        title: String = "Song",
    ): NowPlaying {
        val now = System.currentTimeMillis()
        return NowPlaying("com.example.player", "Player", title, "Band", state, position, now, 1f, duration, now)
    }

    private fun paused(position: Long = 0, duration: Long = DURATION) =
        track(PlaybackState.STATE_PAUSED, position, duration)

    private fun show(np: () -> NowPlaying) = compose.setContent {
        KolahalTheme {
            Box(Modifier.width(360.dp)) { NowPlayingLine(np(), { toggles++ }, { skips++ }) }
        }
    }

    /** How much of the line's width the bar's filled part takes, 0 to 1, read from its pixels. */
    private fun filled(): Float {
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("header-media").captureToImage().toPixelMap()
        val row = pixels.height - 2
        return (0 until pixels.width).count { pixels[it, row] == Term.prompt }.toFloat() / pixels.width
    }

    /** Real time for the position (it reads the wall clock), then the line's next look at it. */
    private fun play(millis: Long) {
        Thread.sleep(millis)
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    @Test fun itSaysWhatPlays() {
        show { track() }
        compose.onNodeWithText("Song — Band").assertExists()
        compose.onNodeWithContentDescription("Playing").assertExists()
        compose.onNodeWithContentDescription("Paused").assertDoesNotExist()
    }

    @Test fun itSaysWhenPaused() {
        show { paused() }
        compose.onNodeWithText("Song — Band").assertExists()
        compose.onNodeWithContentDescription("Paused").assertExists()
        compose.onNodeWithContentDescription("Playing").assertDoesNotExist()
    }

    @Test fun aTapTogglesAndALongPressSkips() {
        show { track() }
        compose.onNodeWithText("Song — Band").performClick()
        compose.runOnIdle { assertEquals(1, toggles); assertEquals(0, skips) }
        compose.onNodeWithText("Song — Band").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, toggles); assertEquals(1, skips) }
    }

    @Test fun theNextButtonSkips() {
        show { track() }
        compose.onNodeWithContentDescription("Next").performClick()
        compose.runOnIdle { assertEquals(0, toggles); assertEquals(1, skips) }
    }

    /** The bar goes under the line: the whole width, and the taps keep their 48 dp. */
    @Test fun theBarLeavesTheTapsTheirRoom() {
        show { track() }
        compose.onNodeWithTag("header-media").assertWidthIsEqualTo(360.dp).assertHeightIsAtLeast(50.dp)
        compose.onNodeWithContentDescription("Next").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Song — Band").assertHeightIsAtLeast(48.dp)
    }

    @Test fun theBarIsEmptyAtTheStart() {
        show { paused(position = 0) }
        assertEquals(0f, filled(), 0.01f)
    }

    /** Some players report a position past the duration they gave. */
    @Test fun theBarStopsAtFull() {
        show { paused(position = DURATION * 3) }
        assertEquals(1f, filled(), 0.02f)
    }

    /** A stream has no duration, so no way to say how far along it is. */
    @Test fun theBarStaysEmptyWithoutADuration() {
        show { track(position = 5_000, duration = 0) }
        play(300)
        assertEquals(0f, filled(), 0f)
    }

    @Test fun theBarStaysEmptyWithoutAPosition() {
        show { track(position = -1) }
        play(300)
        assertEquals(0f, filled(), 0f)
    }

    @Test fun theBarMovesWhilePlaying() {
        show { track() }
        val start = filled()
        play(1_000)
        val later = filled()
        assertTrue("$start, then $later", later >= start + 0.05f)
    }

    /** A duration some players send only after the title. */
    @Test fun aDurationThatComesLaterCounts() {
        var np by mutableStateOf(paused(position = 5_000, duration = 0))
        show { np }
        assertEquals(0f, filled(), 0f)
        compose.runOnIdle { np = paused(position = 5_000) }
        assertEquals(0.5f, filled(), 0.02f)
    }

    @Test fun aPauseKeepsThePlaceAndPlayGoesOnFromIt() {
        var np by mutableStateOf(track())
        show { np }
        compose.runOnIdle { np = paused(position = DURATION / 2) }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(0.5f, filled(), 0.02f)
        play(600)
        assertEquals(0.5f, filled(), 0.02f)

        compose.runOnIdle { np = track(position = DURATION / 2) }
        play(1_000)
        assertTrue(filled() >= 0.55f)
    }

    /** All at once: a new title with its own position. */
    @Test fun aNewTrackStartsOver() {
        var np by mutableStateOf(paused(position = DURATION / 2))
        show { np }
        assertEquals(0.5f, filled(), 0.02f)
        compose.runOnIdle { np = track(position = 0, title = "Other") }
        compose.onNodeWithText("Other — Band").assertExists()
        assertTrue(filled() < 0.1f)
    }

    /**
     * A track starting: buffering, then playing, the same title throughout. Both count as `playing`,
     * so the bar must still notice the change, or it stays at the start for the whole track.
     */
    @Test fun theBarMovesOnceBufferingEnds() {
        var np by mutableStateOf(track(PlaybackState.STATE_BUFFERING))
        show { np }
        assertEquals(0f, filled(), 0.01f)
        compose.runOnIdle { np = track(PlaybackState.STATE_PLAYING) }
        play(1_000)
        assertTrue("${filled()}", filled() >= 0.05f)
    }

    @Test fun theBarFollowsASeekWhilePlaying() {
        var np by mutableStateOf(track())
        show { np }
        compose.runOnIdle { np = track(position = DURATION * 3 / 4) }
        play(100)
        assertTrue("${filled()}", filled() >= 0.74f)
    }

    @Test fun theBarFollowsASeekWhilePaused() {
        var np by mutableStateOf(paused())
        show { np }
        compose.runOnIdle { np = paused(position = DURATION * 3 / 4) }
        play(100)
        assertEquals(0.75f, filled(), 0.02f)
    }

    /** Repeat one: the same title again, from the start, after reaching the end. */
    @Test fun theBarStartsOverWhenATrackRepeats() {
        var np by mutableStateOf(track(position = DURATION - 100))
        show { np }
        play(300)
        assertTrue(filled() >= 0.98f)
        compose.runOnIdle { np = track(position = 0) }
        play(100)
        assertTrue("${filled()}", filled() < 0.2f)
    }

    /**
     * A player's next track often arrives in two callbacks, each published on its own: the new title
     * still with the old track's position, then the new position.
     */
    @Test fun aNewTitleThenItsPositionStartsOver() {
        var np by mutableStateOf(track(position = DURATION * 3 / 4))
        show { np }
        compose.runOnIdle { np = track(position = DURATION * 3 / 4, title = "Other") }
        compose.waitForIdle()
        compose.runOnIdle { np = track(position = 0, title = "Other") }
        play(100)
        assertTrue("${filled()}", filled() < 0.2f)
    }

    /**
     * Off screen the line stops looking at the clock: music often plays with the screen off, and a
     * launcher is always around. Back on screen, it's where it should be at once.
     */
    @Test fun theBarOnlyMovesOnScreen() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                KolahalTheme { Box(Modifier.width(360.dp)) { NowPlayingLine(track(), {}, {}) } }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        val stopped = filled()
        play(1_000)
        assertEquals(stopped, filled(), 0f)

        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        play(100)
        assertTrue("${filled()}", filled() >= stopped + 0.05f)
    }

    private fun shell(command: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }

    /**
     * The whole way, from a real media session through [MediaRepository]: a track that buffers, plays,
     * is sought in and pauses, each a separate update, as players send them.
     */
    @Test fun aRealSessionMovesTheBar() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val listener = "${context.packageName}/${KolahalNotificationListener::class.java.name}"
        val had = NotificationActions.isEnabled(context)
        shell("cmd notification allow_listener $listener")
        val session = MediaSession(context, "NowPlayingLineTest")
        fun state(state: Int, position: Long) = session.setPlaybackState(
            PlaybackState.Builder().setState(state, position, if (state == PlaybackState.STATE_PLAYING) 1f else 0f, SystemClock.elapsedRealtime()).build(),
        )
        try {
            compose.waitUntil(5_000) { NotificationActions.isEnabled(context) }
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Song")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Band")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, DURATION)
                    .build(),
            )
            state(PlaybackState.STATE_BUFFERING, 0)
            session.isActive = true
            val repository = MediaRepository.get(context)
            repository.start()
            compose.setContent {
                val np by repository.nowPlaying.collectAsState()
                KolahalTheme { Box(Modifier.width(360.dp)) { np?.let { NowPlayingLine(it, {}, {}) } } }
            }
            compose.waitUntil(5_000) { repository.nowPlaying.value?.state == PlaybackState.STATE_BUFFERING }
            compose.onNodeWithText("Song — Band").assertExists()
            assertTrue("${filled()}", filled() < 0.01f)

            state(PlaybackState.STATE_PLAYING, 0)
            compose.waitUntil(5_000) { repository.nowPlaying.value?.state == PlaybackState.STATE_PLAYING }
            play(1_000)
            assertTrue("after buffering: ${filled()}", filled() >= 0.05f)

            state(PlaybackState.STATE_PLAYING, DURATION / 2)
            compose.waitUntil(5_000) { (repository.nowPlaying.value?.position ?: 0) >= DURATION / 2 }
            play(100)
            assertTrue("after a seek: ${filled()}", filled() >= 0.49f)

            val pausing = System.currentTimeMillis()
            state(PlaybackState.STATE_PAUSED, DURATION * 3 / 4)
            compose.waitUntil(5_000) { repository.nowPlaying.value?.state == PlaybackState.STATE_PAUSED }
            assertTrue("idle since the pause, not since it last said it was playing", repository.nowPlaying.value!!.idleSince >= pausing)
            compose.waitForIdle()
            assertEquals("paused", 0.75f, filled(), 0.02f)
        } finally {
            session.release()
            if (!had) shell("cmd notification disallow_listener $listener")
        }
    }

    /** Notification access turned off and on again while Kolahal runs: it follows sessions again, with no restart. */
    @Test fun nowPlayingComesBackWhenAccessIsTurnedBackOn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val listener = "${context.packageName}/${KolahalNotificationListener::class.java.name}"
        val had = NotificationActions.isEnabled(context)
        shell("cmd notification allow_listener $listener")
        val session = MediaSession(context, "NowPlayingLineTest")
        fun title(title: String) = session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).build())
        try {
            compose.waitUntil(5_000) { NotificationActions.isEnabled(context) }
            title("Before")
            session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f, SystemClock.elapsedRealtime()).build())
            session.isActive = true
            val repository = MediaRepository.get(context)
            repository.start()
            compose.waitUntil(5_000) { repository.nowPlaying.value?.title == "Before" }

            shell("cmd notification disallow_listener $listener")
            compose.waitUntil(5_000) { !NotificationActions.isEnabled(context) }
            shell("cmd notification allow_listener $listener")
            compose.waitUntil(5_000) { NotificationActions.isEnabled(context) }
            title("After")
            compose.waitUntil(10_000) { repository.nowPlaying.value?.title == "After" }
        } finally {
            session.release()
            if (!had) shell("cmd notification disallow_listener $listener")
        }
    }

    /** The pinned header shows it, as long as the header's rule says: until half an hour after a pause. */
    @Test fun thePinnedHeaderShowsItUnderTheHeadersRule() {
        val config = Config.get(InstrumentationRegistry.getInstrumentation().targetContext)
        var np by mutableStateOf(track())
        compose.setContent { KolahalTheme { PinnedHeader(np, emptyList(), config, 0, {}, {}, {}, {}) } }
        compose.onNodeWithTag("header-media").assertExists()

        compose.runOnIdle { np = paused(position = DURATION / 2).copy(idleSince = System.currentTimeMillis() - NowPlaying.HIDE_AFTER_MS - 1_000) }
        compose.onNodeWithTag("header-media").assertDoesNotExist()
        compose.onNodeWithTag("pinned-header").assertDoesNotExist()
    }
}
