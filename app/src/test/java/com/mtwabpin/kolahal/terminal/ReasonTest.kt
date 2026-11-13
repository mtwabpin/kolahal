package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a failed command says went wrong ([reason]). */
class ReasonTest {
    @Test fun itIsTheErrorsMessage() {
        assertEquals("no such file", java.io.IOException("no such file").reason())
    }

    /** An error with no message still says something: what kind of error it was. */
    @Test fun withNoMessageItIsTheErrorsName() {
        assertEquals("IllegalStateException", IllegalStateException().reason())
        assertEquals("SecurityException", SecurityException(null as String?).reason())
    }
}
