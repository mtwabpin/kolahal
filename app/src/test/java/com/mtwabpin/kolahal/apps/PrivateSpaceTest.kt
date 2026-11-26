package com.mtwabpin.kolahal.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a locked private space shows, which copy of an app is taken, and the words for each state. */
class PrivateSpaceTest {
    /** An app: its name, its profile, whether that is paused or locked. */
    private data class App(val name: String, val kind: ProfileKind, val quiet: Boolean = false)

    private fun locked(a: App) = PrivateSpace.locked(a.kind, a.quiet)

    @Test
    fun onlyAPrivateSpaceIsLocked() {
        assertTrue(PrivateSpace.locked(ProfileKind.PRIVATE, quiet = true))
        assertFalse(PrivateSpace.locked(ProfileKind.PRIVATE, quiet = false))
        // A paused work profile still lists its apps, faded.
        assertFalse(PrivateSpace.locked(ProfileKind.WORK, quiet = true))
        assertFalse(PrivateSpace.locked(ProfileKind.CLONE, quiet = true))
    }

    @Test
    fun aLockedSpaceShowsNoneOfItsApps() {
        val maps = App("Maps", ProfileKind.PERSONAL)
        val mail = App("Mail", ProfileKind.WORK, quiet = true)
        val privateMaps = App("Maps", ProfileKind.PRIVATE, quiet = true)
        assertEquals(listOf(maps, mail), PrivateSpace.shown(listOf(maps, mail, privateMaps), ::locked))
        // Unlocked, they are all there, and nothing is copied.
        val unlocked = listOf(maps, mail, privateMaps.copy(quiet = false))
        assertSame(unlocked, PrivateSpace.shown(unlocked, ::locked))
    }

    @Test
    fun androidsHideSettingHidesEvenTheLockedLine() {
        // Locked: one line in apps, and the header's lock, unless Android hides the space when locked.
        assertTrue(PrivateSpace.shows(ProfileKind.PRIVATE, quiet = true, hiddenWhenLocked = false))
        assertFalse(PrivateSpace.shows(ProfileKind.PRIVATE, quiet = true, hiddenWhenLocked = true))
        // Unlocked, it shows whatever the setting says.
        assertTrue(PrivateSpace.shows(ProfileKind.PRIVATE, quiet = false, hiddenWhenLocked = true))
        assertTrue(PrivateSpace.shows(ProfileKind.WORK, quiet = true, hiddenWhenLocked = false))
    }

    @Test
    fun aGridWithoutItsAppsSaysWhy() {
        val known = setOf(0L, 10L, 11L)
        fun gone(serials: List<Long?>, locked: Set<Long> = emptySet(), hidden: Boolean = false) =
            PrivateSpace.gridGone(serials, locked, hidden) { it in known }
        assertEquals(PrivateSpace.GRID_GONE, gone(listOf(0L, null)))
        assertEquals(PrivateSpace.GRID_LOCKED, gone(listOf(11L), locked = setOf(11L)))
        // Not the home app: the private space is hidden from Kolahal, so its serial is unknown.
        assertEquals(PrivateSpace.GRID_NOT_HOME, gone(listOf(12L), hidden = true))
        // The home app: an unknown profile was removed, and its apps with it.
        assertEquals(PrivateSpace.GRID_GONE, gone(listOf(12L)))
        // Known profiles: uninstalled apps, even while Kolahal isn't the home app.
        assertEquals(PrivateSpace.GRID_GONE, gone(listOf(0L, 10L), hidden = true))
    }

    @Test
    fun copiesThatAreOnComeFirst() {
        val privateYt = App("YouTube", ProfileKind.PRIVATE, quiet = true)
        val yt = App("YouTube", ProfileKind.PERSONAL)
        val ytMusic = App("YT Music", ProfileKind.PERSONAL)
        fun order(vararg apps: App) = AppRepository.onCopiesFirst(apps.map { it to 1 }, { it.name.lowercase() }, { !it.quiet })
            .map { it.first }
        // The private copy was used last, so it ranked first: the personal one takes its place.
        assertEquals(listOf(yt, ytMusic, privateYt), order(privateYt, ytMusic, yt))
        // A paused work copy too.
        val workYt = yt.copy(kind = ProfileKind.WORK, quiet = true)
        assertEquals(listOf(yt, workYt), order(workYt, yt))
        // Two copies that are on, or none that is, keep their order.
        val runningWork = yt.copy(kind = ProfileKind.WORK)
        assertEquals(listOf(runningWork, yt), order(runningWork, yt))
        assertEquals(listOf(workYt, privateYt), order(workYt, privateYt))
    }

    @Test
    fun theGuidesWordsForEachProfile() {
        assertEquals("private space", ProfileWords.name(ProfileKind.PRIVATE))
        assertEquals("Work profile", ProfileWords.title(ProfileKind.WORK))
        assertEquals(listOf("locked", "unlocked", "unlock", "lock"), ProfileWords.run {
            listOf(off(ProfileKind.PRIVATE), on(ProfileKind.PRIVATE), resume(ProfileKind.PRIVATE), pause(ProfileKind.PRIVATE))
        })
        assertEquals(listOf("paused", "on", "resume", "pause"), ProfileWords.run {
            listOf(off(ProfileKind.WORK), on(ProfileKind.WORK), resume(ProfileKind.WORK), pause(ProfileKind.WORK))
        })
        assertEquals(
            listOf("Unlocking", "Locking", "Resuming", "Pausing"),
            listOf(
                ProfileWords.changing(ProfileKind.PRIVATE, on = true), ProfileWords.changing(ProfileKind.PRIVATE, on = false),
                ProfileWords.changing(ProfileKind.WORK, on = true), ProfileWords.changing(ProfileKind.WORK, on = false),
            ),
        )
    }
}
