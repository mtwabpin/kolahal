package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.control.ControlCommands
import org.junit.Assert.assertTrue
import org.junit.Test

/** A built-in always wins, so every name it answers to is kept from aliases and scripts. */
class ReservedNamesTest {
    @Test fun everyNameACommandAnswersToIsReserved() {
        for (name in AppCommands.NAMES + ControlCommands.NAMES + CommandCatalog.PRODUCTIVITY) {
            assertTrue("$name isn't reserved", name in CommandCatalog.RESERVED_NAMES)
        }
    }

    /** `help favs` shows the command it's another name for, so that has to be a command. */
    @Test fun everyOtherNameIsForACommand() {
        for ((other, name) in InstantRun.SYNONYMS) assertTrue("$other names $name", name in CommandCatalog.COMMAND_NAMES)
    }
}
