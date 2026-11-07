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
     * The two items (0.4.14), and the full header's carried as them, become the right side: those
     * items and then the battery, as the line showed them. The full header's were the first two that
     * were on, in the order it showed them. With none changed, the new defaults stand, unsaved.
     */
    @Test fun theOldItemsBecomeTheRightSide() {
        fun right(vararg saved: Pair<String, String>) = Keys.migrate(saved.toMap()).filterKeys { it.startsWith("header.") }
        assertEquals(emptyMap<String, String>(), right())
        assertEquals(emptyMap<String, String>(), right("header.date" to "off", "header.battery" to "off"))
        // Unlocks off, the rest as they were: the alarm and todos, as the defaults.
        assertEquals(mapOf("header.right" to "alarm,todo,battery"), right("header.unlocks" to "off"))
        // Unlocks on by default comes first.
        assertEquals(mapOf("header.right" to "unlocks,network,battery"), right("header.network" to "on"))
        assertEquals(
            mapOf("header.right" to "settings,battery"),
            right("header.unlocks" to "off", "header.alarm" to "off", "header.todo" to "false", "header.profiles" to "off"),
        )
        // The two items, chosen in 0.4.14 or 0.4.15: one changed keeps the other's default; none empties a place.
        assertEquals(mapOf("header.right" to "network,todo,battery"), right("header.item-1" to "network"))
        assertEquals(mapOf("header.right" to "battery"), right("header.item-1" to "none", "header.item-2" to "none"))
        assertEquals(mapOf("header.right" to "todo,battery"), right("header.item-1" to "todo", "header.item-2" to "todo"))
        // A choice already made wins over the full header's, the old names go, and a right side already set stays.
        assertEquals(mapOf("header.right" to "network,todo,battery"), right("header.unlocks" to "off", "header.item-1" to "network", "header.compact.clock" to "on"))
        assertEquals(mapOf("header.right" to "battery,time"), right("header.item-1" to "network", "header.right" to "battery,time"))
    }

    /** A side is parts separated by commas or spaces, each once; none, or nothing, is an empty side. */
    @Test fun aSideIsAListOfParts() {
        assertEquals("time,date", Keys.HEADER_LEFT.parse("time, date"))
        assertEquals("battery,time", Keys.HEADER_RIGHT.parse(" Battery  TIME "))
        assertEquals("none", Keys.HEADER_LEFT.parse("none"))
        assertEquals("none", Keys.HEADER_LEFT.parse(""))
        assertNull(Keys.HEADER_LEFT.parse("time,clock"))
        assertNull(Keys.HEADER_LEFT.parse("time,date,time"))
        assertEquals(listOf("alarm", "todo", "battery"), headerSide(Keys.HEADER_RIGHT.default))
        assertEquals(emptyList<String>(), headerSide("none"))
    }

    @Test fun noNameIsBothASettingAndAGroup() {
        val names = Keys.ALL.map { it.key }
        for (name in names) assertTrue("$name is also a group", names.none { it.startsWith("$name.") })
    }
}
