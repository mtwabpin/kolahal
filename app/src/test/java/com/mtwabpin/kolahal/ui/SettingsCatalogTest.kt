package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Settings pages and the look settings that show home through them. */
class SettingsCatalogTest {
    @Test fun scriptsComeBeforeFeedsInTheTabs() {
        assertEquals(listOf(Screen.SETTINGS, Screen.SCRIPTS, Screen.FEEDS), FEATURE_TABS)
    }

    @Test fun everyEyeIsOnARealPage() {
        val ids = allCategories().map { it.id }
        for (page in PEEK_PAGES.keys) assertTrue("no page called $page", page in ids)
    }

    @Test fun everyPeekSettingIsASliderOnAPage() {
        val keys = allCategories().flatMap { it.keys }
        for (name in PEEK_SLIDERS.keys) {
            val key = keys.firstOrNull { it.key == name }
            assertNotNull("$name is on no page", key)
            assertTrue("$name is not a slider", key is ConfigKey.Num)
        }
    }

    /** Every one-line choice has a row in the table, and every row a setting of its own for each stage. */
    @Test fun theHeaderTableHasEveryOneLineChoice() {
        assertEquals(Keys.ALL.filter { it.key.startsWith("header.compact.") }.toSet(), HEADER_ITEMS.map { it.oneLine }.toSet())
        assertEquals(HEADER_ITEM_KEYS.size, HEADER_ITEM_KEYS.toSet().size)
        assertTrue(HEADER_ITEM_KEYS.all { it in Keys.ALL && it.key in TITLES })
    }

    @Test fun everySettingIsOnAPageButConfigOnlyOnes() {
        val shown = allCategories().flatMap { it.keys }
        assertEquals(Keys.ALL.toSet() - Keys.CONFIG_ONLY, shown.toSet())
        assertTrue(Keys.LOG_KEEP_LINES in Keys.CONFIG_ONLY)
        assertTrue(Keys.LOG_KEEP_LINES in Keys.ALL)
    }
}
