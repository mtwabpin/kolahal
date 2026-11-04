package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings saved under the names from before 0.3.0 keep their values under the new names. */
class ConfigNamesTest {
    @Test fun oldNamesMoveToNewOnes() {
        val saved = mapOf("theme" to "amber", "log.lineheight" to "30", "keybar2" to "TAB", "font.size" to "16")
        assertEquals(
            mapOf("theme.name" to "amber", "log.line-height" to "30", "keybar.row2" to "TAB", "font.size" to "16"),
            Keys.migrate(saved),
        )
    }

    @Test fun aValueUnderTheNewNameWins() {
        assertEquals(mapOf("theme.name" to "dracula"), Keys.migrate(mapOf("theme" to "amber", "theme.name" to "dracula")))
    }

    @Test fun swipeUpSwitchFoldsIntoItsAction() {
        assertEquals(mapOf("gesture.swipe-up" to "apps"), Keys.migrate(mapOf("gesture.swipeup.on" to "on")))
        assertEquals(mapOf("gesture.swipe-up" to "quick"), Keys.migrate(mapOf("gesture.swipeup.on" to "on", "gesture.swipeup" to "quick")))
        // An action chosen while the gesture was off stays off.
        assertEquals(emptyMap<String, String>(), Keys.migrate(mapOf("gesture.swipeup" to "quick")))
        assertEquals(emptyMap<String, String>(), Keys.migrate(mapOf("gesture.swipeup.on" to "off")))
    }

    @Test fun everyOldNamePointsAtARealSetting() {
        for ((old, new) in Keys.RENAMED) {
            assertEquals(old, new, Keys.byName(old)?.key)
            assertNull("$old is still a setting of its own", Keys.ALL.firstOrNull { it.key == old })
        }
    }

    /**
     * Settings that are gone (0.3.1: settings colours, home widgets; 0.4.14: now playing with the
     * header off, which the pinned header always shows) leave saved values and backups.
     */
    @Test fun removedSettingsAreDropped() {
        val saved = mapOf(
            "theme.settings-colors" to "system", "gui.colors" to "system", "widgets.home" to "on", "widgets.height" to "200",
            "header.media-always" to "on", "theme" to "amber",
        )
        assertEquals(mapOf("theme.name" to "amber"), Keys.migrate(saved))
    }

    @Test fun aRemovedNameIsNeitherASettingNorARename() {
        for (name in Keys.REMOVED.keys) {
            assertNull("$name is still a setting", Keys.byName(name))
            assertTrue("$name is still a rename", name !in Keys.RENAMED)
        }
    }

    /** What was off before the one-line header had its own choices stays off there, unless it has a choice already. */
    @Test fun theOneLineHeaderKeepsWhatWasOff() {
        fun compact(vararg saved: Pair<String, String>) = (Keys.carryCompact(saved.toMap()) - saved.toMap().keys).toSortedMap().toMap()
        assertEquals(emptyMap<String, String>(), compact())
        assertEquals(emptyMap<String, String>(), compact("header.date" to "off", "header.battery" to "on"))
        assertEquals(mapOf("header.compact.battery" to "off"), compact("header.battery" to "off"))
        assertEquals(
            mapOf("header.compact.battery" to "off", "header.compact.media" to "off", "header.compact.status" to "off"),
            compact("header.battery" to "off", "header.media" to "off", "header.status" to "off"),
        )
        // A backup edited by hand can say it another way.
        assertEquals(mapOf("header.compact.media" to "off"), compact("header.media" to "false"))
        assertEquals(emptyMap<String, String>(), compact("header.battery" to "off", "header.compact.battery" to "on"))
    }

    @Test fun noNameIsBothASettingAndAGroup() {
        val names = Keys.ALL.map { it.key }
        for (name in names) assertTrue("$name is also a group", names.none { it.startsWith("$name.") })
    }
}
