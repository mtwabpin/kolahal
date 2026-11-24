package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.ui.graphics.lerp
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.ui.theme.Palettes
import com.mtwabpin.kolahal.ui.theme.accents
import com.mtwabpin.kolahal.ui.theme.contrast
import com.mtwabpin.kolahal.ui.theme.schemeFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A big value drawn in a card's color reads on the card ([valueInk]): in every theme, version and color. */
class ValueInkTest {
    /**
     * For every built-in theme, dark and light, and every color a card can have, the value reaches
     * 3:1 on each look's ground: the card's surface (Plain, Bold, Poster, Split's tile) and Tonal's
     * container of that color. A color that reads already is kept as it is.
     */
    @Test
    fun aValueReachesThreeToOneOnEveryGround() {
        for (name in Keys.BUILT_IN_THEMES) for (dark in listOf(true, false)) {
            val p = Palettes.named(name, dark)
            val scheme = schemeFrom(p)
            for ((role, color) in accents(p) + ("muted" to p.dim) + ("text" to p.fg)) {
                val grounds = listOf(
                    "card" to scheme.surfaceContainerHigh,
                    "tonal" to lerp(p.bg, color, 0.30f),
                )
                for ((where, ground) in grounds) {
                    val what = "$name, ${if (dark) "dark" else "light"}, $role on the $where"
                    val ink = valueInk(color, ground)
                    assertTrue("$what: ${contrast(ink, ground)}", contrast(ink, ground) >= INK_MIN)
                    if (contrast(color, ground) >= INK_MIN) assertEquals(what, color, ink)
                }
            }
        }
    }

    /** Rosé Pine's good role is dim on its dark card: the value keeps the hue, lighter, not the text color. */
    @Test
    fun aDimColorKeepsItsHue() {
        val p = Palettes.named("rose-pine", true)
        val card = schemeFrom(p).surfaceContainerHigh
        assertTrue(contrast(p.good, card) < INK_MIN)
        val ink = valueInk(p.good, card)
        assertTrue(contrast(ink, card) >= INK_MIN)
        assertNotEquals(p.fg, ink)
        // Lighter, as the card is dark: each channel moved toward white.
        assertTrue(ink.red >= p.good.red && ink.green >= p.good.green && ink.blue >= p.good.blue)
    }
}
