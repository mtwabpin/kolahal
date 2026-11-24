package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.MaterialYou
import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.ui.theme.Palette
import com.mtwabpin.kolahal.ui.theme.Palettes
import com.mtwabpin.kolahal.ui.theme.schemeFrom
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetColors
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Color row of Arrange widgets: what it offers and keeps ([ColorChoices]), and that its dots show in every theme. */
class ColorChoiceTest {
    /** The first dot is the card's own: a tap on it keeps nothing, so the card follows its own color. */
    @Test
    fun theRowKeepsOnlyAColorThatIsNotTheCardsOwn() {
        val row = ColorChoices(ColorRole.WARN, null)
        assertEquals(ColorRole.WARN, row.roles.first())
        assertEquals(ColorRole.WARN, row.shown)
        assertNull(row.pick(ColorRole.WARN))
        assertEquals(ColorRole.LINK, row.pick(ColorRole.LINK))
        assertEquals(ColorRole.LINK, row.copy(chosen = ColorRole.LINK).shown)
        // No color of its own: the accent is its own.
        val plain = ColorChoices(null, null)
        assertEquals(ColorRole.ACCENT, plain.shown)
        assertNull(plain.pick(ColorRole.ACCENT))
    }

    @Test
    fun aPlacedWidgetsRow() {
        fun placed(source: String, color: ColorRole? = null) = PlacedWidget(null, source, 0L, 184, 0, color = color)
        val air = WidgetSpec.Stat(WidgetBase(color = ColorRole.BAD), value = "118")
        assertEquals(
            ColorChoices(ColorRole.BAD, ColorRole.LINK),
            colorChoicesFor(placed(WidgetSource.script("air"), ColorRole.LINK), air)
        )
        assertEquals(
            ColorChoices(null, null),
            colorChoicesFor(placed(WidgetSource.builtin(WidgetSource.BATTERY)), null)
        )
        assertNull(colorChoicesFor(placed("com.a/.W"), air))
    }

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

    /**
     * Each dot can be seen, dark and light, in every built-in theme: on an unchosen chip it has 3:1
     * with the chip, or else an outline that has; a chosen chip is the primary color, which can be the
     * dot's own, so its dot has a ring in the chip's content color, which has 3:1 with it.
     */
    @Test
    fun everyDotShowsInEveryTheme() {
        val themes = Keys.BUILT_IN_THEMES.filterNot(MaterialYou::isOne)
        assertTrue(themes.size >= 9)
        var outlined = 0
        for (name in themes) for (dark in listOf(true, false)) {
            val p = Palettes.named(name, dark)
            val scheme = schemeFrom(p)
            val chip = scheme.surfaceContainer
            assertTrue("$name dark=$dark: the ring", inkContrast(scheme.onPrimary, scheme.primary) >= DOT_CONTRAST)
            for (role in WidgetColors.OFFERED) {
                val dot = colorIn(p, role)
                if (dotNeedsOutline(dot, chip)) {
                    outlined++
                    assertTrue("$name dark=$dark ${role.role}: the outline", inkContrast(scheme.outline, chip) >= DOT_CONTRAST)
                }
            }
        }
        // Nord's bad, Rosé Pine's good, Solarized's bad: a few dots need their outline.
        assertTrue(outlined > 0)
    }

    /** Why info is not offered: in the default theme, dark and light, it is the link's color. */
    @Test
    fun infoIsTheLinksColorInTheDefaultTheme() {
        for (dark in listOf(true, false)) {
            val p = Palettes.named(Keys.THEME.default, dark)
            assertEquals(p.link, p.info)
        }
        assertTrue(ColorRole.INFO !in WidgetColors.OFFERED)
    }
}
