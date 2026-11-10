package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.terminal.ActivityMatch.Found
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `activity`: the names it shows, how a name finds an activity, and how the words split into app and activity. */
class ActivityMatchTest {
    private val wifi = ActivityItem("com.android.settings.Settings\$WifiSettingsActivity", "Wi-Fi")
    private val wifiCalling = ActivityItem("com.android.settings.Settings\$WifiCallingSettingsActivity", "Wi-Fi calling")
    private val bt = ActivityItem("com.android.settings.Settings\$BluetoothSettingsActivity", "Bluetooth")
    private val main = ActivityItem("com.android.settings.Settings", "Settings")
    private val items = listOf(wifi, wifiCalling, bt, main)

    @Test fun labelFallsBackToTheClassName() {
        assertEquals("Wi-Fi", ActivityMatch.shownLabel("Wi-Fi", "Settings", "a.b.Wifi"))
        // No label of its own: Android gives the app's label. Then the class name says more.
        assertEquals("Settings\$Wifi", ActivityMatch.shownLabel("Settings", "Settings", "a.b.Settings\$Wifi"))
        assertEquals("Wifi", ActivityMatch.shownLabel("settings ", "Settings", "a.b.Wifi"))
        assertEquals("Wifi", ActivityMatch.shownLabel("", "Settings", "a.b.Wifi"))
        assertEquals("Wifi", ActivityMatch.shownLabel(null, "Settings", "a.b.Wifi"))
        // A class without a package.
        assertEquals("Wifi", ActivityMatch.shownLabel("y", "y", "Wifi"))
    }

    @Test fun classUnderTheLabelDropsTheAppsPackage() {
        assertEquals(".Settings\$Wifi", ActivityMatch.shownClass("com.android.settings", "com.android.settings.Settings\$Wifi"))
        assertEquals("com.other.Main", ActivityMatch.shownClass("com.android.settings", "com.other.Main"))
        // A package that only starts the same is another package.
        assertEquals("com.android.settingsx.Main", ActivityMatch.shownClass("com.android.settings", "com.android.settingsx.Main"))
    }

    @Test fun exactNamesInAnyCase() {
        assertEquals(Found.One(wifi), ActivityMatch.find(items, "wi-fi"))
        assertEquals(Found.One(wifi), ActivityMatch.find(items, "WifiSettingsActivity"))
        assertEquals(Found.One(wifi), ActivityMatch.find(items, "settings\$wifisettingsactivity"))
        assertEquals(Found.One(wifi), ActivityMatch.find(items, "com.android.settings.Settings\$WifiSettingsActivity"))
        // "Settings" is the main class's short name; the other short names only start with it.
        assertEquals(Found.One(main), ActivityMatch.find(items, "settings"))
    }

    @Test fun aUniquePrefixFindsOne() {
        assertEquals(Found.One(bt), ActivityMatch.find(items, "blue"))
        assertEquals(Found.One(wifiCalling), ActivityMatch.find(items, "wificall"))
    }

    @Test fun severalMatchesAreAllReturned() {
        assertEquals(Found.Many(listOf(wifi, wifiCalling)), ActivityMatch.find(items, "wifi"))
        assertEquals(Found.Many(listOf(wifi, wifiCalling)), ActivityMatch.find(items, "wi-f"))
        assertEquals(Found.Many(listOf(wifi, wifiCalling, bt)), ActivityMatch.find(items, "settings\$"))
    }

    @Test fun aPartOfTheNameIsTheLastResort() {
        assertEquals(Found.One(wifiCalling), ActivityMatch.find(items, "calling"))
        assertEquals(Found.None, ActivityMatch.find(items, "zzqq"))
        assertEquals(Found.None, ActivityMatch.find(items, "  "))
    }

    @Test fun anExactClassBeatsTheSameLabel() {
        val a = ActivityItem("p.Wifi", "Network")
        val b = ActivityItem("p.Network", "Network")
        assertEquals(Found.One(b), ActivityMatch.find(listOf(a, b), "network"))
    }

    @Test fun copiedNamesAreShortButFindTheSameActivity() {
        assertEquals("WifiSettingsActivity", ActivityMatch.nameFor(wifi, items))
        assertEquals("Settings", ActivityMatch.nameFor(main, items))
        // Two inner classes with the same name: the short name tells them apart.
        val x = ActivityItem("p.A\$Main", "A")
        val y = ActivityItem("p.B\$Main", "B")
        assertEquals("A\$Main", ActivityMatch.nameFor(x, listOf(x, y)))
        // Two classes with the same short name in different packages: only the whole name works.
        val m = ActivityItem("p.one.Main", "One")
        val n = ActivityItem("p.two.Main", "Two")
        assertEquals("p.one.Main", ActivityMatch.nameFor(m, listOf(m, n)))
    }

    /** Apps as AppRepository ranks them: 0 whole name, 1 prefix, 2 word start, 3 a part. */
    private val apps = listOf("Settings", "Play Store", "Google Maps", "Maps")
    private fun tier(text: String): Int? {
        val q = text.lowercase()
        return apps.map { it.lowercase() }.mapNotNull { a ->
            when {
                a == q -> 0
                a.startsWith(q) -> 1
                a.split(' ').any { it.startsWith(q) } -> 2
                a.contains(q) -> 3
                else -> null
            }
        }.minOrNull()
    }

    private fun split(line: String) = ActivityMatch.split(line.split(' '), ::tier)

    @Test fun wordsSplitIntoAppAndActivity() {
        assertEquals("settings" to "", split("settings"))
        assertEquals("settings" to "wifi", split("settings wifi"))
        assertEquals("settings" to "wi-fi calling", split("settings wi-fi calling"))
        // An app's name with a space.
        assertEquals("Play Store" to "", split("Play Store"))
        assertEquals("play store" to "Main", split("play store Main"))
        // All the words start an app's name: there is no activity name yet.
        assertEquals("play sto" to "", split("play sto"))
        assertEquals("sett" to "", split("sett"))
        // The most words that start a name.
        assertEquals("sett" to "wifi", split("sett wifi"))
        // Only the first word matches, loosely.
        assertEquals("ettin" to "wifi", split("ettin wifi"))
        assertNull(split("zzqq wifi"))
        assertNull(ActivityMatch.split(emptyList(), ::tier))
    }
}
