package com.mtwabpin.kolahal.ui.settings

import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.commands.Accesses
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings that need an access Android hasn't given: a line and a button in their place ([groupShows]). */
class SettingsAccessTest {
    private val usage = setOf(Accesses.USAGE)
    private val limits = listOf(Keys.USAGE_SCREEN_TIME_LIMIT, Keys.USAGE_UNLOCK_LIMIT)

    /** A group whose settings need no access shows them all, whatever is missing. */
    @Test
    fun noSettingNeedsAnAccess() {
        val keys = listOf<ConfigKey<*>>(Keys.CLOCK_24H, Keys.USER_NAME)
        assertEquals(GroupShows(keys, emptyList()), groupShows(keys, usage))
        assertEquals(GroupShows(keys, emptyList()), groupShows(keys, emptySet()))
    }

    /** Without usage access, the daily limits are one line and a button: the group still shows. */
    @Test
    fun everySettingOfTheGroupNeedsTheMissingOne() {
        val shows = groupShows(limits, usage)
        assertEquals(GroupShows(emptyList(), listOf(Accesses.USAGE)), shows)
        assertFalse(shows.isEmpty)
    }

    /** The settings that have what they need stay, and the line comes once, after them. */
    @Test
    fun someSettingsNeedIt() {
        val keys = listOf<ConfigKey<*>>(Keys.USAGE_SCREEN_TIME_LIMIT, Keys.CLOCK_24H, Keys.USAGE_UNLOCK_LIMIT)
        assertEquals(GroupShows(listOf(Keys.CLOCK_24H), listOf(Accesses.USAGE)), groupShows(keys, usage))
    }

    /** With the access, everything is as it is without the rule. */
    @Test
    fun theAccessIsThere() {
        assertEquals(GroupShows(limits, emptyList()), groupShows(limits, emptySet()))
        // Another access missing changes nothing here.
        assertEquals(GroupShows(limits, emptyList()), groupShows(limits, setOf(Accesses.READ_NOTIFICATIONS)))
    }

    /** A group with nothing that applies shows nothing, not even a line. */
    @Test
    fun anEmptyGroupShowsNothing() {
        assertTrue(groupShows(emptyList(), usage).isEmpty)
    }

    /** A setting that needs two accesses hides while either is missing, and asks for that one only. */
    @Test
    fun aSettingThatNeedsTwo() {
        val needs = mapOf<ConfigKey<*>, List<String>>(
            Keys.CLOCK_24H to listOf(Accesses.READ_NOTIFICATIONS, Accesses.SETTINGS),
            Keys.USER_NAME to listOf(Accesses.USAGE),
        )
        val keys = listOf<ConfigKey<*>>(Keys.CLOCK_24H, Keys.USER_NAME)
        assertEquals(
            GroupShows(listOf(Keys.USER_NAME), listOf(Accesses.SETTINGS)),
            groupShows(keys, setOf(Accesses.SETTINGS), needs),
        )
        assertEquals(
            GroupShows(emptyList(), listOf(Accesses.READ_NOTIFICATIONS, Accesses.SETTINGS, Accesses.USAGE)),
            groupShows(keys, setOf(Accesses.USAGE, Accesses.SETTINGS, Accesses.READ_NOTIFICATIONS), needs),
        )
        assertEquals(GroupShows(keys, emptyList()), groupShows(keys, emptySet(), needs))
    }

    /** The Header page's Daily limits group is the one the rule applies to. */
    @Test
    fun theDailyLimitsNeedUsageAccess() {
        for (key in limits) assertEquals(key.key, listOf(Accesses.USAGE), NEEDS_ACCESS[key])
        val group = allCategories().first { it.id == HEADER_CATEGORY }.groups.toMap().getValue("Daily limits")
        assertEquals(GroupShows(emptyList(), listOf(Accesses.USAGE)), groupShows(group, usage))
    }

    /** The lists name only accesses this build has, settings there are and parts the header has. */
    @Test
    fun theListsNameOnlyWhatThereIs() {
        for ((key, words) in NEEDS_ACCESS) {
            assertTrue(key.key, key in Keys.ALL)
            assertTrue(key.key, words.isNotEmpty())
            for (word in words) assertTrue("${key.key}: $word", Accesses.find(word) in Accesses.OFFERED)
        }
        for ((part, words) in HEADER_PART_NEEDS) {
            assertTrue(part, part in Keys.HEADER_PARTS)
            for (word in words) assertTrue("$part: $word", Accesses.find(word) in Accesses.OFFERED)
        }
    }

    /**
     * Play over other apps needs notification access, as no word plays a shader without it, and Display over
     * other apps: its switch shows with both, and each missing one is asked for. A build without the feature
     * has neither the setting nor a rule for it.
     */
    @Test
    fun playOverOtherAppsNeedsTwoAccesses() {
        val key = Keys.SHADER_OVER_APPS
        if (!BuildConfig.SHADER_OVER_APPS) {
            assertFalse(key in NEEDS_ACCESS)
            assertFalse(key in Keys.ALL)
            return
        }
        val both = listOf(Accesses.READ_NOTIFICATIONS, Accesses.OVERLAY)
        assertEquals(both, NEEDS_ACCESS[key])
        val keys = listOf<ConfigKey<*>>(key)
        assertEquals(GroupShows(keys, emptyList()), groupShows(keys, emptySet()))
        assertEquals(GroupShows(keys, emptyList()), groupShows(keys, usage))
        assertEquals(GroupShows(emptyList(), listOf(Accesses.OVERLAY)), groupShows(keys, setOf(Accesses.OVERLAY)))
        assertEquals(GroupShows(emptyList(), listOf(Accesses.READ_NOTIFICATIONS)), groupShows(keys, setOf(Accesses.READ_NOTIFICATIONS)))
        assertEquals(GroupShows(emptyList(), both), groupShows(keys, both.toSet()))
    }

    /** Without usage access, Unlocks can't be put on the header, but one that is on can be taken off. */
    @Test
    fun theUnlocksPartWithoutUsageAccess() {
        val off = HeaderArrangement.rows(listOf("time", "date"), listOf("alarm"))
        val hidden = off.indexOf(ArrangeRow.Part("unlocks"))
        assertTrue(stuck(off, hidden, usage))
        assertFalse(stuck(off, hidden, emptySet()))
        // Another part that isn't shown moves as before, and so do the headings' rows (none move).
        assertFalse(stuck(off, off.indexOf(ArrangeRow.Part("network")), usage))
        assertFalse(stuck(off, 0, usage))
        val on = HeaderArrangement.rows(listOf("time", "unlocks"), listOf("alarm"))
        assertFalse(stuck(on, on.indexOf(ArrangeRow.Part("unlocks")), usage))
    }
}
