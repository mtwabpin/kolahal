package com.mtwabpin.kolahal.ui

import android.media.session.PlaybackState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.StatusLine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The pinned header: what it shows, the turns its items take, the dots, and what a touch does. */
@RunWith(AndroidJUnit4::class)
class PinnedHeaderTest {
    @get:Rule val compose = createComposeRule()
    private val config = Config.get(InstrumentationRegistry.getInstrumentation().targetContext)
    private val taps = mutableListOf<String>()
    private val presses = mutableListOf<String>()
    private var refreshes = 0
    private var pins by mutableStateOf(emptyList<StatusLine>())
    private var np by mutableStateOf<NowPlaying?>(null)
    private var version by mutableIntStateOf(0)

    @After fun tearDown() {
        config.reset(Keys.HEADER_PINNED_SECONDS)
        config.reset(Keys.HEADER_PINNED_DOTS)
    }

    private fun pin(name: String, text: String = "$name text", progress: Float? = null, refresh: Int? = null) =
        StatusLine(name, text, progress, refresh)

    private fun track(): NowPlaying {
        val now = System.currentTimeMillis()
        return NowPlaying("com.example.player", "Player", "Song", "Band", PlaybackState.STATE_PLAYING, 0, now, 1f, 10_000, now)
    }

    /** The header with [items], its clock driven by the test: nothing moves until [advance]. */
    private fun show(vararg items: StatusLine, seconds: Int? = null) {
        seconds?.let { config.set(Keys.HEADER_PINNED_SECONDS, it.toString()) }
        pins = items.toList()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KolahalTheme {
                Box(Modifier.width(360.dp)) {
                    PinnedHeader(np, pins, config, version, {}, {}, { taps += it }, { presses += it }, { refreshes++ })
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun advance(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    /** Only [name]'s item on screen, none of the others in [all]. */
    private fun showing(name: String, vararg all: String) {
        compose.onNodeWithTag(if (name == "media") "header-media" else "pinned-$name").assertExists()
        all.filter { it != name }.forEach { compose.onNodeWithTag("pinned-$it").assertDoesNotExist() }
    }

    @Test fun withNothingItTakesNoRoom() {
        show()
        compose.onNodeWithTag("pinned-header").assertDoesNotExist()
    }

    /** A script's item shows its text alone, for room; a screen reader hears the script's name first. */
    @Test fun aScriptsItemShowsItsTextAlone() {
        show(pin("cricket", "NZ 136/8 (33.2 ov) v IND"))
        compose.onNodeWithText("NZ 136/8 (33.2 ov) v IND").assertExists()
        compose.onNodeWithText("cricket", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("cricket: NZ 136/8 (33.2 ov) v IND").assertExists()
        // One item: nothing marks which.
        compose.onNodeWithTag("pinned-dots").assertDoesNotExist()
    }

    /** Each item for header.pinned-seconds, then the next, back round to the first. */
    @Test fun itemsTakeTurns() {
        show(pin("a"), pin("b"), pin("c"), seconds = 3)
        showing("a", "a", "b", "c")
        advance(2_900)
        showing("a", "a", "b", "c")
        // Its slide takes a moment.
        advance(1_100)
        showing("b", "a", "b", "c")
        advance(3_000)
        showing("c", "a", "b", "c")
        advance(3_000)
        showing("a", "a", "b", "c")
    }

    @Test fun dotsShowUpToTheSetting() {
        show(pin("a"), pin("b"), pin("c"))
        compose.onNodeWithTag("pinned-dots").assertContentDescriptionEquals("1 of 3")

        pins = pins + pin("d")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("pinned-dots").assertDoesNotExist()

        compose.runOnIdle {
            config.set(Keys.HEADER_PINNED_DOTS, "5")
            version++
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("pinned-dots").assertContentDescriptionEquals("1 of 4")

        compose.runOnIdle {
            config.set(Keys.HEADER_PINNED_DOTS, "0")
            version++
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("pinned-dots").assertDoesNotExist()
    }

    /** What's playing goes first, and the dots count it. */
    @Test fun musicGoesFirst() {
        np = track()
        show(pin("cricket"), seconds = 2)
        showing("media", "cricket")
        compose.onNodeWithTag("pinned-dots").assertContentDescriptionEquals("1 of 2")
        advance(3_000)
        showing("cricket")
        compose.onNodeWithTag("header-media").assertDoesNotExist()
        compose.onNodeWithTag("pinned-dots").assertContentDescriptionEquals("2 of 2")
    }

    /** A finger on the line keeps the item showing; its turn starts again when it lifts. */
    @Test fun holdingKeepsTheItem() {
        show(pin("a"), pin("b"), seconds = 2)
        compose.onNodeWithTag("pinned-header").performTouchInput { down(center) }
        advance(6_000)
        showing("a", "a", "b")
        compose.onNodeWithTag("pinned-header").performTouchInput { up() }
        advance(1_500)
        showing("a", "a", "b")
        advance(1_500)
        showing("b", "a", "b")
    }

    /** A new text shows in place, and the item keeps its turn's time. */
    @Test fun aChangedTextShowsInPlace() {
        show(pin("a"), pin("b"), seconds = 3)
        advance(2_000)
        pins = listOf(pin("a", "a moved on"), pin("b"))
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("a moved on").assertExists()
        showing("a", "a", "b")
        advance(2_000)
        showing("b", "a", "b")
    }

    @Test fun aTapRunsItsScriptAndALongPressOpensItsMenu() {
        show(pin("cricket"))
        compose.onNodeWithTag("pinned-cricket").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("cricket"), taps)
        compose.onNodeWithTag("pinned-cricket").performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("cricket"), presses)
    }

    /** How much of the item's width its bar's filled part takes, 0 to 1, read from its pixels. */
    private fun filled(name: String): Float {
        val pixels = compose.onNodeWithTag("pinned-$name").captureToImage().toPixelMap()
        val row = pixels.height - 1
        return (0 until pixels.width).count { pixels[it, row] == Term.prompt }.toFloat() / pixels.width
    }

    @Test fun theBarShowsOnlyWithAProgress() {
        show(pin("cricket", progress = 0.5f))
        val half = filled("cricket")
        assertTrue("$half filled", half in 0.45f..0.55f)
        pins = listOf(pin("cricket"))
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0f, filled("cricket"))
    }

    /** Items that ask to run again are asked for each second; none ask, nothing runs. */
    @Test fun aRefreshIsAskedForOnlyWhenAnItemWantsOne() {
        show(pin("still"))
        advance(3_500)
        assertEquals(0, refreshes)
        pins = listOf(pin("still"), pin("cricket", refresh = 10))
        compose.mainClock.advanceTimeByFrame()
        advance(3_500)
        assertTrue("$refreshes asks", refreshes in 3..5)
    }
}
