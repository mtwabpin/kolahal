package com.mtwabpin.kolahal.ui

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.MainActivity
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.shell
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The phone's dark mode picks a theme's version; `theme.always-dark` keeps the dark one. */
@RunWith(AndroidJUnit4::class)
class ThemeModeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val config = Config.get(instrumentation.targetContext)
    private var night = "yes"

    @Before fun setUp() {
        // "Night mode: yes"
        night = shell("cmd uimode night").substringAfter(':').trim()
        config.set(Keys.THEME, "dracula")
    }

    @After fun tearDown() {
        config.reset(Keys.THEME)
        config.reset(Keys.THEME_ALWAYS_DARK)
        shell("cmd uimode night $night")
    }

    private fun shows(palette: Palette) {
        val until = SystemClock.uptimeMillis() + 5_000
        while (Term.palette != palette && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        assertEquals(palette, Term.palette)
    }

    @Test fun thePhonesModePicksTheVersionAndAlwaysDarkKeepsTheDarkOne() {
        shell("cmd uimode night no")
        ActivityScenario.launch(MainActivity::class.java).use {
            shows(Palettes.draculaLight)
            config.set(Keys.THEME_ALWAYS_DARK, "on")
            shows(Palettes.dracula)
            config.set(Keys.THEME_ALWAYS_DARK, "off")
            shows(Palettes.draculaLight)
            // Dark mode turning on while home shows: the dark version, with no restart.
            shell("cmd uimode night yes")
            shows(Palettes.dracula)
            config.set(Keys.THEME, "gruvbox")
            shows(Palettes.gruvbox)
        }
    }
}
