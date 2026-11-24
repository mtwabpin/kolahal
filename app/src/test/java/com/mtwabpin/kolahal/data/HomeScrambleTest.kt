package com.mtwabpin.kolahal.data

import com.mtwabpin.kolahal.commands.CommandCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `scramble [on|off]` and the state it keeps ([HomeScramble]). */
class HomeScrambleTest {
    /** What the preferences file would hold, and a store over it as the app makes one at start. */
    private var kept: Long? = null
    private var seeds = 0L

    /** False while the file can't be written (a full disk). */
    private var writes = true
    private fun store() = HomeScramble(
        read = { kept },
        write = { if (writes) kept = it; writes },
        newSeed = { ++seeds * 1_000 },
    )

    /** The line that says it is on names the command that turns it off, on a line of its own: what Copy gives. */
    @Test
    fun theLineOnNamesTheWayOff() {
        for (line in listOf(HomeScramble.ON, HomeScramble.ALREADY_ON)) {
            assertEquals("'${HomeScramble.COPY}' turns it off", line.lines().last())
        }
    }

    @Test
    fun onAndOff() {
        val s = store()
        assertFalse(s.on)
        assertEquals(HomeScramble.ON, s.run(emptyList()))
        assertTrue(s.on)
        assertEquals(1_000L, s.seed.value)
        assertEquals(1_000L, kept)
        // On again: the same seed, so the screen doesn't change.
        assertEquals(HomeScramble.ALREADY_ON, s.run(listOf("on")))
        assertEquals(1_000L, s.seed.value)
        assertEquals(HomeScramble.OFF, s.run(listOf("OFF")))
        assertNull(s.seed.value)
        assertNull(kept)
        assertEquals(HomeScramble.ALREADY_OFF, s.run(listOf("off")))
        // Each time it is turned on, a new seed.
        assertEquals(HomeScramble.ON, s.run(listOf("on")))
        assertNotEquals(1_000L, s.seed.value)
    }

    @Test
    fun anythingElseGetsTheUsage() {
        val s = store()
        assertNull(s.run(listOf("maybe")))
        assertNull(s.run(listOf("on", "now")))
        assertFalse(s.on)
    }

    /** The process was killed and starts again: home comes back scrambled, with the same seed, before anything is drawn. */
    @Test
    fun itIsKeptThroughARestart() {
        store().turnOn()
        val again = store()
        assertTrue(again.on)
        assertEquals(1_000L, again.seed.value)
        again.turnOff()
        assertFalse(store().on)
    }

    /** The seed can't be kept: it stays off and says so, so a restart can't bring home back unscrambled unannounced. */
    @Test
    fun aSeedThatCantBeKeptLeavesItOff() {
        writes = false
        val s = store()
        assertEquals(HomeScramble.NOT_KEPT, s.run(listOf("on")))
        assertFalse(s.on)
        assertNull(s.seed.value)
        assertNull(kept)
        // Once it can be written, it turns on.
        writes = true
        assertEquals(HomeScramble.ON, s.run(listOf("on")))
        assertTrue(store().on)
    }

    /** It is a command of the terminal's section, that never runs without Enter and that a script can't send. */
    @Test
    fun theCommandIsListed() {
        val spec = CommandCatalog.COMMANDS.single { it.name == "scramble" }
        assertEquals("scramble [on|off]", spec.usage)
        assertTrue(spec.sensitive)
        assertTrue("scramble" in CommandCatalog.SENSITIVE)
        assertTrue("scramble" in CommandCatalog.HELP_SECTIONS.getValue("terminal"))
        assertTrue(spec.uses.all { it.first.startsWith("scramble") })
    }
}
