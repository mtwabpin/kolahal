package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Commands the Play build doesn't offer. Unit tests run on the debug build, which has every feature. */
class FeatureCommandsTest {
    @Test fun everyFeatureCommandIsACommand() {
        for ((_, names) in CommandCatalog.FEATURE_COMMANDS) {
            for (name in names) assertTrue("$name isn't a command", name in CommandCatalog.COMMAND_NAMES)
        }
    }

    @Test fun unavailableFollowsTheBuild() {
        assertEquals(!BuildConfig.LOCK_SERVICE, "lock" in CommandCatalog.UNAVAILABLE)
        // Every build has notification access.
        assertTrue("notifs" !in CommandCatalog.UNAVAILABLE)
        assertTrue("media" !in CommandCatalog.UNAVAILABLE)
        assertEquals(!BuildConfig.CALL_LOG, "calls" in CommandCatalog.UNAVAILABLE)
        assertTrue("call" !in CommandCatalog.UNAVAILABLE)
    }
}
