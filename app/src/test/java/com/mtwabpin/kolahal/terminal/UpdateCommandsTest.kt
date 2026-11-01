package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCommandsTest {
    @Test fun versionsCompareByNumberNotByText() {
        assertTrue(UpdateCommands.isNewer("0.4.10", "0.4.9"))
        assertFalse(UpdateCommands.isNewer("0.4.9", "0.4.10"))
        assertTrue(UpdateCommands.isNewer("1.0.0", "0.99.99"))
        assertFalse(UpdateCommands.isNewer("0.4.10", "0.4.10"))
    }

    /** A release replaces the -dev build that led up to it, and a -dev build is ahead of the release before it. */
    @Test fun aDevBuildComesBeforeItsRelease() {
        assertTrue(UpdateCommands.isNewer("0.4.11", "0.4.11-dev"))
        assertFalse(UpdateCommands.isNewer("0.4.11-dev", "0.4.11"))
        assertFalse(UpdateCommands.isNewer("0.4.10", "0.4.11-dev"))
        assertTrue(UpdateCommands.isNewer("0.4.11-dev", "0.4.10"))
    }

    @Test fun progressIsInWholePercents() {
        assertEquals(0, UpdateCommands.Progress(0, 1000).percent)
        assertEquals(49, UpdateCommands.Progress(499, 1000).percent)
        assertEquals(100, UpdateCommands.Progress(1000, 1000).percent)
        // The largest APK it accepts: this would overflow in an Int.
        assertEquals(50, UpdateCommands.Progress(50L * 1024 * 1024, 100L * 1024 * 1024).percent)
    }

    /** Play allows no update but its own. */
    @Test fun thePlayBuildDoesntOfferIt() {
        assertEquals(!BuildConfig.SELF_UPDATE, "update" in CommandCatalog.UNAVAILABLE)
        // Unit tests run on the debug build, so the line above can't see an entry that's gone.
        assertTrue(CommandCatalog.FEATURE_COMMANDS.any { (on, names) -> on == BuildConfig.SELF_UPDATE && "update" in names })
    }

    /** Only when you ask: not by a prefix or from a script, with or without a pipe. */
    @Test fun itsSensitive() {
        for (line in listOf("update", "update install", "update|head", "update install | head")) {
            assertTrue(line, InstantRun.isSensitive(line))
        }
    }
}
