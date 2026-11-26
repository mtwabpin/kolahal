package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import com.mtwabpin.kolahal.sourceFile
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.ui.theme.Palettes
import com.mtwabpin.kolahal.ui.theme.accents
import com.mtwabpin.kolahal.ui.theme.contrast
import com.mtwabpin.kolahal.ui.theme.schemeFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A card's small parts: a chart's older pills, small words in the card's color, and Shape's rows' taps. */
class CardContrastTest {
    /**
     * Bold's and Shape's older pills are Plain's older bars ([olderBars]): every chart draws them
     * through it, and none has a color of its own for them. The calls are read from the sources.
     */
    @Test
    fun olderPillsAreAsPlainsOlderBars() {
        val dir = sourceFile("src/main/java/com/mtwabpin/kolahal/ui/widgets")
        val plain = java.io.File(dir, "WidgetChart.kt").readText()
        val looks = java.io.File(dir, "WidgetChartLooks.kt").readText()
        assertTrue("drawPath(before, olderBars(color))" in plain)
        assertEquals(2, Regex("""olderBars\(color\)""").findAll(looks).count())
        assertTrue("surfaceContainerHighest" !in looks)
        assertTrue("alpha = 0.4f" !in looks)
        assertEquals(Color.Red.copy(alpha = 0.4f), olderBars(Color.Red))
    }

    /**
     * For every built-in theme, dark and light, and every color a card can have, small words in
     * that color ([smallInk]) reach 4.5:1 on each ground they are drawn on: the card's surface
     * (Plain, Bold), Tonal's container of that color and Tonal's tile on it (an agenda's day). A
     * color that reads already is kept as it is.
     */
    @Test
    fun smallWordsReachFourAndAHalfToOne() {
        for (name in Keys.BUILT_IN_THEMES) for (dark in listOf(true, false)) {
            val p = Palettes.named(name, dark)
            val scheme = schemeFrom(p)
            for ((role, color) in accents(p)) {
                val tonal = lerp(scheme.surface, color, 0.30f)
                val grounds = listOf(
                    "card" to scheme.surfaceContainerHigh,
                    "tonal" to tonal,
                    "tonal tile" to scheme.onSurface.copy(alpha = 0.12f).compositeOver(tonal),
                )
                for ((where, ground) in grounds) {
                    val what = "$name, ${if (dark) "dark" else "light"}, $role on the $where"
                    val ink = smallInk(color, ground)
                    assertTrue("$what: ${contrast(ink, ground)}", contrast(ink, ground) >= TEXT_MIN)
                    if (contrast(color, ground) >= TEXT_MIN) assertEquals(what, color, ink)
                }
            }
        }
    }

    /** Green light's Tonal card: "Up 2.4%" was 3.2:1 there, and is now darker green at 4.5:1 or more. */
    @Test
    fun greenLightsTonalChangeIsDarker() {
        val p = Palettes.named("green", false)
        val tonal = lerp(schemeFrom(p).surface, p.prompt, 0.30f)
        assertTrue(contrast(p.prompt, tonal) < TEXT_MIN)
        val ink = smallInk(p.prompt, tonal)
        assertTrue(contrast(ink, tonal) >= TEXT_MIN)
        // Darker, as the card is light: each channel moved toward black.
        assertTrue(ink.red <= p.prompt.red && ink.green <= p.prompt.green && ink.blue <= p.prompt.blue)
    }

    /**
     * Shape's rows in a small or a wide card ([spreadRows]) share the card's height and its padding
     * above and below: each row's share is a tap's least height or more, and they fill the room.
     */
    @Test
    fun spreadRowsAreTapTall() {
        for (box in 40..149) for (fit in 0..5) {
            val (rows, each) = spreadRows(box.toFloat(), 8f, fit)
            val what = "$box dp, $fit rows"
            assertTrue(what, rows in 0..fit)
            if (rows > 0) {
                assertTrue("$what: $each", each >= TAP_ROW_DP)
                assertEquals(what, box + 16f, rows * each, 0.01f)
            }
        }
    }

    /** A 1×1 card's body is 76 dp: two todos fit, each with a 46 dp tap, as before they had 30 dp rows. */
    @Test
    fun aSmallCardKeepsTwoTodos() {
        assertEquals(2 to 46f, spreadRows(76f, 8f, 2))
    }
}
