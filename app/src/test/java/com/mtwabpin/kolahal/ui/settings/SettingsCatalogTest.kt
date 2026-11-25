package com.mtwabpin.kolahal.ui.settings

import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.SettingTitles
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.ui.FEATURE_TABS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Settings pages and the look settings that show home through them. */
class SettingsCatalogTest {
    @Test
    fun theTabsAreSettingsScriptsShadersFeeds() {
        assertEquals(listOf(Screen.SETTINGS, Screen.SCRIPTS, Screen.SHADERS, Screen.FEEDS), FEATURE_TABS)
    }

    @Test
    fun everyEyeIsOnARealPage() {
        val ids = allCategories().map { it.id }
        for (page in PEEK_PAGES.keys) assertTrue("no page called $page", page in ids)
    }

    @Test
    fun everyPeekSettingIsASliderOnAPage() {
        val keys = allCategories().flatMap { it.keys }
        for (name in PEEK_SLIDERS.keys) {
            val key = keys.firstOrNull { it.key == name }
            assertNotNull("$name is on no page", key)
            assertTrue("$name is not a slider", key is ConfigKey.Num)
        }
    }

    /** The header is one line: its settings are a few, each with a title, and the full and one-line headers' are gone. */
    @Test
    fun theHeaderHasAFewSettings() {
        val header = Keys.ALL.filter { it.key.startsWith("header.") }
        assertEquals(
            listOf(
                "header.show",
                "header.below-bar",
                "header.padding",
                "header.left",
                "header.right",
                "header.pinned-seconds",
                "header.pinned-dots",
                "header.pinned-bar-above",
                "header.pinned-bar-below"
            ), header.map { it.key })
        assertTrue(header.all { SettingTitles[it.key] != null })
        assertTrue(Keys.REMOVED.keys.filter { it.startsWith("header.compact.") }.size == 12)
    }

    @Test
    fun everySettingIsOnAPageButConfigOnlyOnes() {
        val shown = allCategories().flatMap { it.keys }.map { it.key }
        // By name, so a failure names the setting that is on no page.
        assertEquals((Keys.ALL.toSet() - Keys.CONFIG_ONLY).map { it.key }.toSet(), shown.toSet())
        assertTrue(Keys.LOG_KEEP_LINES in Keys.CONFIG_ONLY)
        assertTrue(Keys.LOG_KEEP_LINES in Keys.ALL)
    }

    /** The screensaver is on Look & feel, with a title; search finds it. */
    @Test
    fun theScreensaverIsOnLookAndFeel() {
        val look = allCategories().first { it.id == "look" }
        val groups = look.groups.toMap()
        assertEquals(listOf(Keys.SCREEN_SAVER, Keys.SCREEN_SAVER_AFTER), groups["Screensaver"])
        for (key in listOf(Keys.SCREEN_SAVER, Keys.SCREEN_SAVER_AFTER)) {
            assertNotNull(key.key, SettingTitles[key.key])
        }
        assertTrue(matches(Keys.SCREEN_SAVER, "screensaver"))
        assertTrue(matches(Keys.SCREEN_SAVER, "pipes"))
    }

    /** The daily limits are on the Header page, where the unlocks show; their 0 reads Off, and search finds them. */
    @Test
    fun theDailyLimitsAreOnTheHeaderPage() {
        val header = allCategories().first { it.id == HEADER_CATEGORY }
        assertEquals(
            listOf(Keys.USAGE_SCREEN_TIME_LIMIT, Keys.USAGE_UNLOCK_LIMIT),
            header.groups.toMap()["Daily limits"]
        )
        for (key in listOf(Keys.USAGE_SCREEN_TIME_LIMIT, Keys.USAGE_UNLOCK_LIMIT)) {
            assertEquals("Off", ChoiceLabels.of(key, 0))
            assertTrue(key.key, matches(key, "limit"))
        }
        assertEquals("180", ChoiceLabels.of(Keys.USAGE_SCREEN_TIME_LIMIT, 180))
        assertTrue(matches(Keys.USAGE_SCREEN_TIME_LIMIT, "screen time"))
        assertTrue(matches(Keys.USAGE_UNLOCK_LIMIT, "unlocks"))
    }

    /** Shaders is a tab, with no row on the main page; the search finds it by what it does and by its settings. */
    @Test
    fun shadersIsATabTheSearchFinds() {
        val row = SCREEN_ROWS.single { it.screen == Screen.SHADERS }
        assertEquals("Shaders", row.title)
        assertTrue(Screen.SHADERS in FEATURE_TABS)
        assertNull(row.after)
        for (q in listOf("shaders", "shader", "CRT", "effect", "paper effect", "screen.shader", "screen.shader-over-apps", "over other apps")) {
            assertTrue(q, row.matches(q))
        }
        assertFalse(row.matches("weather"))
        // The screen sets it, so no page has it.
        assertTrue(Keys.SCREEN_SHADER in Keys.CONFIG_ONLY)
    }

    /** Settings search finds each word in any order, in a setting's title, name, help or choices. */
    @Test
    fun searchFindsWordsInAnyOrder() {
        assertTrue(matches(Keys.FONT_SIZE, "text size"))
        assertTrue(matches(Keys.FONT_SIZE, "size text"))
        assertTrue(matches(Keys.FONT_SIZE, "  SIZE   font  "))
        assertFalse(matches(Keys.FONT_SIZE, "size weather"))
        // A choice's label: a theme's name finds the theme setting.
        assertTrue(matches(Keys.THEME, "solarized"))
    }
}
