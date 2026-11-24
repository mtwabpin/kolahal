package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.MaterialYou
import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.ui.theme.Palette
import com.mtwabpin.kolahal.ui.theme.Palettes
import com.mtwabpin.kolahal.ui.theme.schemeFrom
import com.mtwabpin.kolahal.widgets.WidgetColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Color group of a card's settings page: the roles it offers in each theme, and that its dots show. */
class ColorChoiceTest {
    private val themes = Keys.BUILT_IN_THEMES.filterNot(MaterialYou::isOne)

    /** A role's color in [p], as `colorOf` gives it from the theme's terminal colors. */
    private fun colorIn(p: Palette, role: ColorRole) = when (role) {
        ColorRole.ACCENT -> p.prompt
        ColorRole.LINK -> p.link
        ColorRole.INFO -> p.info
        ColorRole.MUTED -> p.dim
        ColorRole.TEXT -> p.fg
        ColorRole.GOOD -> p.good
        ColorRole.WARN -> p.warn
        ColorRole.BAD -> p.error
    }

    /** In every built-in theme, dark and light, no dot is the twin of another, and the accent comes first. */
    @Test
    fun noTwoDotsAreTwins() {
        assertTrue(themes.size >= 9)
        for (name in themes) for (dark in listOf(true, false)) {
            val p = Palettes.named(name, dark)
            val offered = WidgetColors.offered({ colorIn(p, it) })
            assertEquals("$name dark=$dark", offered.size, offered.map { colorIn(p, it) }.toSet().size)
            assertEquals(ColorRole.ACCENT, offered.first())
        }
    }

    /** Info is left out where it is the link's color (Rosé Pine, the default), and offered where it is not (Solarized). */
    @Test
    fun infoOnlyWhereItIsNotTheLinksColor() {
        for (dark in listOf(true, false)) {
            val rose = Palettes.named(Keys.THEME.default, dark)
            assertEquals(rose.link, rose.info)
            assertFalse(ColorRole.INFO in WidgetColors.offered({ colorIn(rose, it) }))
            val solarized = Palettes.named("solarized", dark)
            assertTrue(ColorRole.INFO in WidgetColors.offered({ colorIn(solarized, it) }))
        }
        // A card that has info chosen still shows it, so the page shows what the card has.
        val rose = Palettes.named(Keys.THEME.default)
        assertTrue(ColorRole.INFO in WidgetColors.offered({ colorIn(rose, it) }, chosen = ColorRole.INFO))
    }

    /**
     * Each dot can be seen, dark and light, in every built-in theme: on the group's ground it has 3:1
     * with it, or else an outline that has; the chosen cell is the primary container, so its dot has
     * a ring in the cell's content color, which has 3:1 with it.
     */
    @Test
    fun everyDotShowsInEveryTheme() {
        var outlined = 0
        for (name in themes) for (dark in listOf(true, false)) {
            val p = Palettes.named(name, dark)
            val scheme = schemeFrom(p)
            val ground = scheme.surfaceContainerHigh
            assertTrue(
                "$name dark=$dark: the ring",
                inkContrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= DOT_CONTRAST
            )
            for (role in WidgetColors.ORDER) {
                val dot = colorIn(p, role)
                if (dotNeedsOutline(dot, ground)) {
                    outlined++
                    assertTrue("$name dark=$dark ${role.role}: the outline", inkContrast(scheme.outline, ground) >= DOT_CONTRAST)
                }
            }
        }
        // Nord's bad, Rosé Pine's good, Solarized's bad: a few dots need their outline.
        assertTrue(outlined > 0)
    }
}
