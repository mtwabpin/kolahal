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

    /**
     * The grids keep 5 colors and 6 shapes to a row at the usual text size, and take fewer with a
     * larger font, so "Slanted" and "Warning" keep their room. The width is a phone 411 dp wide
     * inside the page's margins and the grid's own (363 dp); a narrower phone takes fewer.
     */
    @Test
    fun aLargerFontTakesFewerCellsToARow() {
        assertEquals(5, gridColumns(363, 1f, 5))
        assertEquals(6, gridColumns(363, 1f, 6))
        assertEquals(5, gridColumns(363, 1.3f, 6))
        assertEquals(3, gridColumns(363, 2f, 6))
        assertEquals(3, gridColumns(363, 2f, 5))
        // A cell is never under 48 dp, at any font size.
        for (scale in listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)) for (width in 200..600 step 10) {
            val columns = gridColumns(width, scale, 6)
            val cell = (width - (columns - 1) * GRID_GAP_DP) / columns.toFloat()
            assertTrue("$width dp at $scale: $columns", cell >= GRID_CELL_DP * maxOf(1f, scale) || columns == 1)
        }
        assertEquals(1, gridColumns(40, 2f, 6))
    }

    /**
     * Both grids mark a chosen cell one way ([cellMarks]): the primary container, with the ring around
     * the chosen color and the chosen shape in the color on it. In every built-in theme, dark and
     * light, that mark has 3:1 with the cell, and a shape not chosen 3:1 with the grid's ground.
     * The theme's primary on its container, as the chosen shape had it, falls under 3:1 in Solarized's
     * and Gruvbox Material's light versions.
     */
    @Test
    fun theChosenCellIsMarkedOneWayThatReads() {
        var faint = 0
        for (name in themes) for (dark in listOf(true, false)) {
            val scheme = schemeFrom(Palettes.named(name, dark))
            val chosen = cellMarks(scheme, true)
            assertEquals(scheme.primaryContainer, chosen.fill)
            assertTrue("$name dark=$dark: chosen", inkContrast(chosen.ink, chosen.fill) >= DOT_CONTRAST)
            val other = cellMarks(scheme, false)
            assertTrue(
                "$name dark=$dark: not chosen",
                inkContrast(other.ink, scheme.surfaceContainerHigh) >= DOT_CONTRAST
            )
            if (inkContrast(scheme.primary, scheme.primaryContainer) < DOT_CONTRAST) faint++
        }
        assertTrue(faint >= 2)
    }
}
