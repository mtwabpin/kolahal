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
import androidx.compose.ui.test.hasAnyAncestor
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
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A scene chosen on a card's settings page ([LocalChosenScene]) is the ground of any kind, a list
 * here, in a look whose card has a fill ([CardScene]), from Android 13; Split keeps its tiles. The
 * corners widgets.corners gives every card ([rememberCardCorners]) round it.
 */
@RunWith(AndroidJUnit4::class)
class CardSceneTest {
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

    /** A 4×2 card's size on a phone. */
    private val width = 368.dp
    private val height = 184.dp

    /** A list with one short row: the card's lower half has no words. */
    private val list = WidgetSpec.Rows(WidgetBase(), listOf(WidgetSpec.Row("Milk")))

    /** Cards of [list], by tag: the scene chosen for each, in [look]. */
    private fun show(look: WidgetLook, vararg cards: Pair<String, String?>) {
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    CompositionLocalProvider(LocalWidgetLook provides look) {
                        Column {
                            cards.forEach { (tag, scene) ->
                                CompositionLocalProvider(LocalChosenScene provides scene) {
                                    Box(Modifier.width(width).testTag(tag)) {
                                        WidgetCard(
                                            title = "",
                                            height = height,
                                            spec = list,
                                            note = null,
                                            description = WidgetWords.description(null, list, 0, ZoneId.of("UTC")),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pixels(tag: String): PixelMap = compose.onNodeWithTag(tag).captureToImage().toPixelMap()

    /** The card's lower part, clear of its row and its corners. */
    private fun lowerOf(tag: String): List<Triple<Float, Float, Float>> {
        val map = pixels(tag)
        val xs = (map.width * 0.2f).roundToInt() until (map.width * 0.8f).roundToInt()
        val ys = (map.height * 0.6f).roundToInt() until (map.height * 0.85f).roundToInt()
        return xs.flatMap { x -> ys.map { y -> map[x, y].let { Triple(it.red, it.green, it.blue) } } }
    }

    private fun sceneDrawn(tag: String): Boolean =
        compose.onAllNodes(hasTestTag(STAT_SCENE_TAG) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aChosenSceneIsAListsGround() {
        assumeTrue("shaders need Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        show(WidgetLook.PLAIN, "plain" to null, "pond" to "pond")
        // The shader is read and compiled off the main thread; the card draws again once it is ready.
        compose.waitFor("the list never drew its scene", 10_000) { sceneDrawn("pond") }
        compose.waitForIdle()
        assertTrue(!sceneDrawn("plain"))
        val bare = lowerOf("plain")
        val scene = lowerOf("pond")
        val differ = scene.indices.count { i ->
            val (r, g, b) = scene[i]
            val (r0, g0, b0) = bare[i]
            maxOf(abs(r - r0), abs(g - g0), abs(b - b0)) > 0.006f
        }
        assertTrue("only $differ of ${scene.size} pixels differ from the plain card", differ >= scene.size / 20)
    }

    /** Split's tiles keep their own ground: no scene is drawn. */
    @Test
    fun splitKeepsItsTiles() {
        assumeTrue("shaders need Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        show(WidgetLook.SPLIT, "split" to "pond")
        // Time for a shader to compile, were it drawn.
        Thread.sleep(2_000)
        compose.waitForIdle()
        assertTrue(!sceneDrawn("split"))
    }

    /** widgets.corners rounds the card: Square fills its corner, which its own leave to the page; Very rounded leaves more of it. */
    @Test
    fun theCornersSettingRoundsTheCard() {
        val config = Config.get(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            show(WidgetLook.PLAIN, "card" to null)
            compose.waitForIdle()
            val own = pixels("card")
            compose.runOnIdle { config.set(Keys.WIDGET_CORNERS, "square") }
            compose.waitForIdle()
            val square = pixels("card")
            compose.runOnIdle { config.set(Keys.WIDGET_CORNERS, "very") }
            compose.waitForIdle()
            val very = pixels("card")
            val middle = square[square.width / 2, square.height - 4]
            // At the very corner: the card's color only when square.
            assertEquals(middle, square[1, 1])
            assertNotEquals(middle, own[1, 1])
            // 10 dp in along the diagonal: inside the card with its own 28 dp corners, outside it at 40 dp.
            val d = (10 * compose.density.density).roundToInt()
            assertEquals(middle, own[d, d])
            assertNotEquals(middle, very[d, d])
        } finally {
            config.reset(Keys.WIDGET_CORNERS)
        }
    }
}
