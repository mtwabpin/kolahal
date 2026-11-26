package com.mtwabpin.kolahal.ui.widgets

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.ui.theme.Palettes
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** A stat with a `scene` has a still of that shader as its ground ([CardScene]), from Android 13. */
@RunWith(AndroidJUnit4::class)
class StatSceneCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val paletteBefore = Term.palette

    @Before
    fun setUp() {
        // The light theme: its scenes are pale and colored at every hour, so the picture shows from the page.
        Term.palette = Palettes.named("rose-pine", false)
    }

    @After
    fun tearDown() {
        Term.palette = paletteBefore
    }

    /** A 2×1 card's size on a phone. */
    private val width = 184.dp
    private val height = 92.dp

    /** The same stat with and without a scene: no label and no title, so no words stand at the card's end. */
    private val plain = WidgetSpec.Stat(WidgetBase(), value = "14°")
    private val pond = plain.copy(scene = "pond")

    private fun show() {
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    CompositionLocalProvider(LocalWidgetLook provides WidgetLook.PLAIN) {
                        Column {
                            listOf("plain" to plain, "pond" to pond).forEach { (tag, spec) ->
                                Box(Modifier.width(width).testTag(tag)) {
                                    WidgetCard(
                                        title = "",
                                        height = height,
                                        spec = spec,
                                        note = null,
                                        description = WidgetWords.description(null, spec, 0, ZoneId.of("UTC")),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** The end part of a card, where the scene shows through its lighter scrim: no words and no round corner. */
    private fun endOf(tag: String): List<Triple<Float, Float, Float>> {
        val map: PixelMap = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val xs = (map.width * 0.5f).roundToInt() until (map.width * 0.82f).roundToInt()
        val ys = (map.height * 0.2f).roundToInt() until (map.height * 0.8f).roundToInt()
        return xs.flatMap { x -> ys.map { y -> map[x, y].let { Triple(it.red, it.green, it.blue) } } }
    }

    @Test
    fun aSceneIsTheCardsGround() {
        assumeTrue("shaders need Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        show()
        // The shader is read and compiled off the main thread; the card draws again once it is ready.
        compose.waitFor("the pond's card never drew its scene", 10_000) {
            compose.onAllNodes(hasTestTag(STAT_SCENE_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val bare = endOf("plain")
        val scene = endOf("pond")
        // Not one flat color where the scene shows.
        val colors = scene.map { (r, g, b) -> Triple((r * 64).roundToInt(), (g * 64).roundToInt(), (b * 64).roundToInt()) }.toSet()
        assertTrue("the scene's part of the card is ${colors.size} color(s)", colors.size >= 3)
        // And not the plain card's color: a part of it differs from the card without a scene.
        val differ = scene.indices.count { i ->
            val (r, g, b) = scene[i]
            val (r0, g0, b0) = bare[i]
            maxOf(abs(r - r0), abs(g - g0), abs(b - b0)) > 0.03f
        }
        assertTrue("only $differ of ${scene.size} pixels differ from the plain card", differ >= scene.size / 20)
    }
}
