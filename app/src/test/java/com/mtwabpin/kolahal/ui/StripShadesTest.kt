package com.mtwabpin.kolahal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mtwabpin.kolahal.data.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of a clock's day strip ([stripShades]): night, day and working hours stand apart in
 * every theme, dark and light, on a Plain and on a Tonal card, in every colour a script can give.
 */
class StripShadesTest {
    /** Neighbouring parts meet at 06:00 and 22:00 (night and day) and at 09:00 and 18:00 (day and working hours). */
    private val apart = 1.9f

    @Test fun thePartsOfADayStandApartInEveryTheme() {
        for (name in Keys.BUILT_IN_THEMES) for (dark in listOf(true, false)) {
            val p = Palettes.named(name, dark)
            val scheme = schemeFrom(p)
            for ((role, colour) in accents(p) + ("muted" to p.dim) + ("text" to p.fg)) {
                val tonal = androidx.compose.ui.graphics.lerp(scheme.surface, colour, 0.30f)
                for ((look, card) in listOf("Plain" to scheme.surfaceContainerHigh, "Tonal" to tonal)) {
                    val s = stripShades(scheme.surface, card, colour)
                    val what = "$name, ${if (dark) "dark" else "light"}, $look, $role"
                    assertTrue("$what: night and day ${contrast(s.night, s.day)}", contrast(s.night, s.day) >= apart)
                    assertTrue("$what: day and work ${contrast(s.day, s.work)}", contrast(s.day, s.work) >= apart)
                    assertEquals("$what: working hours are the role's colour", colour, s.work)
                    if (dark) {
                        assertTrue("$what: night is the darkest", s.night.luminance() < s.day.luminance() && s.day.luminance() < s.work.luminance())
                        assertTrue("$what: night is darker than the page", s.night.luminance() <= scheme.surface.luminance())
                    } else {
                        assertTrue("$what: day is the lightest, night the darker tint", s.day.luminance() > s.night.luminance() && s.night.luminance() > s.work.luminance())
                        assertTrue("$what: day is lighter than the card", s.day.luminance() > card.luminance())
                    }
                }
            }
        }
    }

    /** Night in a light theme is a tint of the role, not a grey: its colour leans toward the role's. */
    @Test fun nightInALightThemeIsTinted() {
        val p = Palettes.named("green", dark = false)
        val scheme = schemeFrom(p)
        val s = stripShades(scheme.surface, scheme.surfaceContainerHigh, p.prompt)
        fun greenness(c: Color) = c.green - (c.red + c.blue) / 2
        assertTrue("${s.night} leans toward ${p.prompt}", greenness(s.night) > greenness(scheme.surfaceContainerHigh))
    }
}
