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

    /**
     * The full header's items become the one line's two (0.4.14): the first two that were on, in the
     * order it showed them; with none changed, the new defaults stand, unsaved.
     */
    @Test fun theOldItemsBecomeTheTwo() {
        fun items(vararg saved: Pair<String, String>) = Keys.migrate(saved.toMap()).filterKeys { it.startsWith("header.item-") }.toSortedMap().toMap()
        assertEquals(emptyMap<String, String>(), items())
        assertEquals(emptyMap<String, String>(), items("header.date" to "off", "header.battery" to "off"))
        // Unlocks off, the rest as they were: the alarm and todos, as the new defaults.
        assertEquals(mapOf("header.item-1" to "alarm", "header.item-2" to "todo"), items("header.unlocks" to "off"))
        // Unlocks on by default comes first.
        assertEquals(mapOf("header.item-1" to "unlocks", "header.item-2" to "network"), items("header.network" to "on"))
        assertEquals(
            mapOf("header.item-1" to "settings", "header.item-2" to "none"),
            items("header.unlocks" to "off", "header.alarm" to "off", "header.todo" to "false", "header.profiles" to "off"),
        )
        // A choice already made under the new names wins, and the old names go.
        val moved = Keys.migrate(mapOf("header.unlocks" to "off", "header.item-1" to "network", "header.compact.clock" to "on"))
        assertEquals(mapOf("header.item-1" to "network", "header.item-2" to "todo"), moved)
    }

    @Test fun noNameIsBothASettingAndAGroup() {
        val names = Keys.ALL.map { it.key }
        for (name in names) assertTrue("$name is also a group", names.none { it.startsWith("$name.") })
    }
}
