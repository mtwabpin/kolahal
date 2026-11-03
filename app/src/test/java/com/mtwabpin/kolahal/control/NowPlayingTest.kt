package com.mtwabpin.kolahal.control

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the header's media line and its progress bar read from a session. */
class NowPlayingTest {
    private val at = 1_000_000L

    private fun np(
        state: Int = PlaybackState.STATE_PLAYING,
        position: Long = 10_000,
        speed: Float = 1f,
        duration: Long = 60_000,
        title: String? = "Song",
        artist: String? = "Band",
        idleSince: Long = at,
    ) = NowPlaying("com.example.player", "Player", title, artist, state, position, at, speed, duration, idleSince)

    @Test fun thePositionMovesWhilePlaying() {
        assertEquals(10_000, np().positionNow(at))
        assertEquals(15_000, np().positionNow(at + 5_000))
    }

    @Test fun thePositionMovesAtThePlaybackSpeed() {
        assertEquals(20_000, np(speed = 2f).positionNow(at + 5_000))
        assertEquals(12_500, np(speed = 0.5f).positionNow(at + 5_000))
    }

    /** Only a playing session moves: buffering counts as playing for the line, but stands still. */
    @Test fun thePositionStandsStillOtherwise() {
        val still = listOf(
            PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED, PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING, PlaybackState.STATE_ERROR, PlaybackState.STATE_NONE,
        )
        for (state in still) assertEquals("state $state", 10_000, np(state = state).positionNow(at + 5_000))
    }

    @Test fun thePositionStopsAtTheEnd() {
        assertEquals(60_000, np().positionNow(at + 50_000))
        assertEquals(60_000, np().positionNow(at + 3_600_000))
        assertEquals(60_000, np(state = PlaybackState.STATE_PAUSED, position = 90_000).positionNow(at))
    }

    /** A stream has no duration, so nothing to stop at. */
    @Test fun aStreamHasNoEnd() {
        assertEquals(3_610_000, np(duration = 0).positionNow(at + 3_600_000))
    }

    /** -1 is a session with no position; time doesn't make one up, for the bar to show. */
    @Test fun anUnknownPositionStaysUnknown() {
        assertEquals(-1, np(position = -1).positionNow(at + 5_000))
        assertEquals(-1, np(position = -1, state = PlaybackState.STATE_PAUSED).positionNow(at))
    }

    @Test fun playingIncludesBuffering() {
        assertTrue(np(state = PlaybackState.STATE_PLAYING).playing)
        assertTrue(np(state = PlaybackState.STATE_BUFFERING).playing)
        assertFalse(np(state = PlaybackState.STATE_PAUSED).playing)
        assertFalse(np(state = PlaybackState.STATE_STOPPED).playing)
        assertFalse(np(state = PlaybackState.STATE_NONE).playing)
    }

    @Test fun theLineIsTitleAndArtistOrElseTheApp() {
        assertEquals("Song — Band", np().line)
        assertEquals("Song", np(artist = null).line)
        assertEquals("Song", np(artist = "  ").line)
        assertEquals("Band", np(title = "").line)
        assertEquals("Player", np(title = null, artist = null).line)
    }

    @Test fun theHeaderShowsItUntilHalfAnHourAfterAPause() {
        val paused = np(state = PlaybackState.STATE_PAUSED)
        assertTrue(paused.showInHeader(at))
        assertTrue(paused.showInHeader(at + NowPlaying.HIDE_AFTER_MS - 1))
        assertFalse(paused.showInHeader(at + NowPlaying.HIDE_AFTER_MS))
        // Playing, however long ago it started.
        assertTrue(np().showInHeader(at + NowPlaying.HIDE_AFTER_MS * 10))
        assertTrue(np(state = PlaybackState.STATE_BUFFERING).showInHeader(at + NowPlaying.HIDE_AFTER_MS * 10))
    }

    /** A player can say nothing for all of a long track: the half hour starts at the pause. */
    @Test fun aLongTrackPausedStaysInTheHeader() {
        val last = LastPlaying()
        last.update(setOf("player"), at)
        val paused = at + 45 * 60_000
        last.update(emptySet(), paused)
        assertEquals(paused, last["player"])
        // Looking again later doesn't move it.
        last.update(emptySet(), paused + 60_000)
        assertEquals(paused, last["player"])
        val np = np(state = PlaybackState.STATE_PAUSED, idleSince = last["player"]!!)
        assertTrue(np.showInHeader(paused + NowPlaying.HIDE_AFTER_MS - 1))
        assertFalse(np.showInHeader(paused + NowPlaying.HIDE_AFTER_MS))
    }

    @Test fun aSessionNeverSeenPlayingHasNoTime() {
        val last = LastPlaying()
        last.update(setOf("player"), at)
        assertEquals(null, last["other"])
    }

    @Test fun theHeaderLeavesOutASessionWithNothingToShow() {
        assertFalse(np(state = PlaybackState.STATE_NONE).showInHeader(at))
        assertFalse(np(state = PlaybackState.STATE_ERROR).showInHeader(at))
    }

    /** Its time read a moment ahead of the clock (two clocks, read apart): the start, not a negative time. */
    @Test fun neverBeforeTheStart() {
        assertEquals(0L, np(position = 0).positionNow(at - 800))
        assertEquals(0L, np(position = 100).positionNow(at - 800))
    }
}
