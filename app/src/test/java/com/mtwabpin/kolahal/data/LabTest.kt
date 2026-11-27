package com.mtwabpin.kolahal.data

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.commands.LabCommands
import com.mtwabpin.kolahal.ui.settings.LAB_CATEGORY
import com.mtwabpin.kolahal.ui.settings.allCategories
import com.mtwabpin.kolahal.ui.settings.shownCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lab ([Lab]): one setting, off by default, and nothing of it shows while it is off. */
class LabTest {
    private val wallpaper = CommandCatalog.COMMANDS.single { it.name == "wallpaper" }

    @Test
    fun itIsOneSettingOffByDefaultAndInBackups() {
        assertEquals("lab.on", Keys.LAB_ON.key)
        assertFalse(Keys.LAB_ON.default)
        // In Keys.ALL, so a backup has it once it is on, and `config set lab.on on` typed in full works.
        assertTrue(Keys.LAB_ON in Keys.ALL)
        assertSame(Keys.LAB_ON, Keys.byName("lab.on"))
        assertFalse(Keys.LAB_ON in Keys.CONFIG_ONLY)
        assertEquals("Lab", SettingTitles["lab.on"])
    }

    @Test
    fun whileOffConfigAndCompletionLeaveItOut() {
        assertFalse(Lab.shows(Keys.LAB_ON, on = false))
        assertTrue(Lab.shows(Keys.LAB_ON, on = true))
        // Every other setting shows either way.
        for (key in Keys.ALL - Keys.LAB_ON) {
            assertTrue(key.key, Lab.shows(key, on = false))
            assertTrue(key.key, Lab.shows(key, on = true))
        }
        assertTrue(Keys.ALL.filter { Lab.shows(it, on = false) }.none { it.key.startsWith("lab.") })
    }

    @Test
    fun settingsHasItsPageLastAndOnlyWhileOn() {
        assertTrue(shownCategories(labOn = false).none { it.id == LAB_CATEGORY })
        assertEquals(LAB_CATEGORY, shownCategories(labOn = true).last().id)
        // The rest are as they were.
        assertEquals(shownCategories(labOn = false).map { it.id }, shownCategories(labOn = true).dropLast(1).map { it.id })
        assertEquals(listOf(Keys.LAB_ON), allCategories().single { it.id == LAB_CATEGORY }.keys)
    }

    @Test
    fun aFeatureIsOnOnlyWithLabOnAndAnAndroidNewEnough() {
        assertEquals(emptyList<Lab.Feature>(), Lab.features(on = false, sdk = 36))
        assertEquals(listOf(Lab.Feature.PICTURE), Lab.features(on = true, sdk = 36))
        assertTrue(Lab.has(on = true, Lab.Feature.PICTURE, sdk = 33))
        assertFalse(Lab.has(on = true, Lab.Feature.PICTURE, sdk = 32))
        assertFalse(Lab.has(on = false, Lab.Feature.PICTURE, sdk = 36))
    }

    @Test
    fun helpAndCompletionAreAsBeforeWhileOff() {
        assertSame(wallpaper, LabCommands.spec(wallpaper, Lab.features(on = false, sdk = 36)))
        assertEquals(emptyList<String>(), LabCommands.words("wallpaper", Lab.features(on = false, sdk = 36)))
        // Other commands never change.
        for (spec in CommandCatalog.COMMANDS.filter { it.name != "wallpaper" }) {
            assertSame(spec.name, spec, LabCommands.spec(spec, Lab.Feature.entries))
        }
    }

    @Test
    fun withPicturesOnWallpaperHasPicture() {
        val on = Lab.features(on = true, sdk = 36)
        val spec = LabCommands.spec(wallpaper, on)
        assertEquals("${wallpaper.args} · picture <name>", spec.args)
        assertEquals(wallpaper.uses, spec.uses.dropLast(1))
        assertEquals("wallpaper picture <name>", spec.uses.last().first)
        assertEquals(wallpaper.usage, spec.usage)
        assertEquals(listOf("picture"), LabCommands.words("wallpaper", on))
        assertEquals(emptyList<String>(), LabCommands.words("shader", on))
    }
}
