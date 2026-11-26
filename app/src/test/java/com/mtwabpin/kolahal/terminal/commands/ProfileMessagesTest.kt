package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.apps.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the app commands say about the private space and the work profile in each state, in the guide's words. */
class ProfileMessagesTest {
    private val private = ProfileKind.PRIVATE
    private val work = ProfileKind.WORK

    @Test
    fun aProfileThatIsOffSaysSoWithItsTap() {
        assertEquals("Private space is locked\nTap to unlock it and open Maps", ProfileMessages.offOpen(private, "Maps"))
        assertEquals("Work profile is paused\nTap to resume it and open Mail", ProfileMessages.offOpen(work, "Mail"))
        // `apps private`, a shortcut of a locked or paused profile's app.
        assertEquals("Private space is locked\nTap to unlock it", ProfileMessages.off(private))
        assertEquals("Work profile is paused\nTap to resume it", ProfileMessages.off(work))
        assertEquals("private on", ProfileMessages.onCommand(private))
        assertEquals("work on", ProfileMessages.onCommand(work))
    }

    @Test
    fun noPrivateSpaceSaysWhyWhenKolahalIsntHome() {
        assertEquals("No private space on this device", ProfileMessages.none(private, hidden = false))
        assertEquals(
            "Kolahal can't see a private space\nIt must be the home app to see one\n'home' chooses the default launcher",
            ProfileMessages.none(private, hidden = true),
        )
        // Android hides only the private space from a launcher that isn't home.
        assertEquals("No work profile on this device", ProfileMessages.none(work, hidden = true))
        assertEquals("No apps in the private space", ProfileMessages.noApps(private))
    }

    @Test
    fun turningOnAndOff() {
        assertEquals("Private space: ", ProfileMessages.statusName(private))
        assertEquals("Unlocking the private space…", ProfileMessages.changing(private, on = true))
        assertEquals("Locking the private space…", ProfileMessages.changing(private, on = false))
        assertEquals("Resuming the work profile…", ProfileMessages.changing(work, on = true))
        assertEquals("Pausing the work profile…", ProfileMessages.changing(work, on = false))
        assertEquals("Unlocking the private space…\nAndroid asks for your lock first", ProfileMessages.needsLock(private))
        assertEquals("Resuming the work profile…\nAndroid asks for its PIN first", ProfileMessages.needsLock(work))
        assertEquals(
            "Couldn't unlock the private space\nIs Kolahal the default launcher?\n'home' chooses the default launcher",
            ProfileMessages.refused(private, on = true),
        )
        assertEquals("Couldn't pause the work profile\n${ProfileMessages.NOT_HOME}", ProfileMessages.refused(work, on = false))
        assertEquals("Private space didn't unlock in time\nTry again", ProfileMessages.late(private))
        assertEquals("Tap to lock the private space", ProfileMessages.tapToPause(private))
        assertEquals("Tap to pause the work profile", ProfileMessages.tapToPause(work))
    }

    /** The private space is never a "profile" that is "paused" in what a person reads. */
    @Test
    fun oneVocabulary() {
        val texts = listOf(
            ProfileMessages.offOpen(private, "Maps"), ProfileMessages.off(private), ProfileMessages.none(private, true),
            ProfileMessages.none(private, false), ProfileMessages.noApps(private), ProfileMessages.statusName(private),
            ProfileMessages.changing(private, true), ProfileMessages.changing(private, false), ProfileMessages.needsLock(private),
            ProfileMessages.refused(private, true), ProfileMessages.refused(private, false), ProfileMessages.late(private),
            ProfileMessages.tapToPause(private),
        )
        for (t in texts) {
            assertTrue(t, "private profile" !in t.lowercase() && "paused" !in t && "resume" !in t)
            // The form of a log message: a short first line, then hints one to a line.
            assertTrue(t, t.lines().first().length <= 43)
        }
    }
}
