package com.mtwabpin.kolahal.ui.widgets

import android.media.session.PlaybackState
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.widgets.WidgetLook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * Now playing in the Split look follows widgets.corners: its tiles are the card's outline. Square
 * makes the song's tile square at the card's corner; its own corners leave it round ([SplitCorners]).
 */
@RunWith(AndroidJUnit4::class)
class SplitCornersTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val config = Config.get(InstrumentationRegistry.getInstrumentation().targetContext)

    @After
    fun tearDown() = config.reset(Keys.WIDGET_CORNERS)

    private val spec = OwnCards.media(
        NowPlaying(
            pkg = "com.example.music",
            app = "Music",
            title = "Gymnopédie No. 1",
            artist = "Erik Satie",
            state = PlaybackState.STATE_PAUSED,
            position = 72_000,
            positionAt = 0,
            speed = 0f,
            duration = 185_000,
            idleSince = 0,
        ),
    )

    private fun show() {
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    CompositionLocalProvider(LocalWidgetLook provides WidgetLook.SPLIT) {
                        Box(Modifier.width(368.dp).testTag("card")) {
                            WidgetCard(title = "Now playing", height = 88.dp, spec = spec, note = null, description = "Now playing")
                        }
                    }
                }
            }
        }
    }

    private fun pixels(): PixelMap = compose.onNodeWithTag("card").captureToImage().toPixelMap()

    @Test
    fun theTilesFollowTheSetting() {
        config.set(Keys.WIDGET_CORNERS, "own")
        show()
        compose.waitForIdle()
        val own = pixels()
        compose.runOnIdle { config.set(Keys.WIDGET_CORNERS, "square") }
        compose.waitForIdle()
        val square = pixels()
        // The song's tile starts at the card's start edge: its color halfway down that edge, clear of its words (16 dp in).
        val edge = (2 * compose.density.density).roundToInt()
        val tile = square[edge, square.height / 2]
        assertEquals("the square tile's corner isn't the tile", tile, square[edge, edge])
        assertNotEquals("the tile's own corner is square too", own[edge, own.height / 2], own[edge, edge])
    }
}
