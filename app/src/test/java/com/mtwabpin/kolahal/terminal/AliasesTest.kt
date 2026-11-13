package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.terminal.Aliases.Request
import org.junit.Assert.assertEquals
import org.junit.Test

/** How a line expands through aliases, and what an `alias …` line asks for. */
class AliasesTest {
    private val reserved = setOf("help", "echo", "open")

    private fun expand(line: String, vararg aliases: Pair<String, String>) =
        Aliases.expand(line, reserved, aliases.toMap()::get)

    @Test fun onlyTheFirstWordExpands() {
        assertEquals("open maps now", expand("m now", "m" to "open maps"))
        assertEquals("x m", expand("x m", "m" to "open maps"))
        assertEquals("open maps", expand("m", "m" to "open maps"))
    }

    /** Five expansions at most: the sixth alias in a chain stays as it is. */
    @Test fun aChainExpandsFiveTimes() {
        val chain = (0..5).map { "a$it" to "a${it + 1}" }.toTypedArray()
        assertEquals("a5 x", expand("a0 x", *chain))
        assertEquals("a6 x", expand("a1 x", *chain))
    }

    @Test fun aCircleStops() {
        assertEquals("a x", expand("a x", "a" to "b", "b" to "a"))
        assertEquals("a", expand("a", "a" to "a"))
    }

    /** A built-in name wins over an alias of it (one could come from an old backup), in any case. */
    @Test fun aBuiltInIsNeverExpanded() {
        assertEquals("help", expand("help", "help" to "echo hijacked"))
        assertEquals("Help me", expand("Help me", "Help" to "echo hijacked"))
        // An alias may lead to a built-in, which then runs as it is.
        assertEquals("echo hi", expand("e hi", "e" to "echo", "echo" to "open x"))
    }

    @Test fun aPipeEndsTheAliasName() {
        assertEquals("echo a b | grep a", expand("hf|grep a", "hf" to "echo a b"))
        // An alias may hold a filter of its own.
        assertEquals("echo a b | head 1 | grep a", expand("hf | grep a", "hf" to "echo a b | head 1"))
    }

    @Test fun aliasLines() {
        assertEquals(Request.ShowAll, Aliases.parse("", reserved))
        assertEquals(Request.ShowAll, Aliases.parse("   ", reserved))
        assertEquals(Request.Show("m"), Aliases.parse("M", reserved))
        assertEquals(Request.Define("m", "open maps"), Aliases.parse("M = open maps", reserved))
        assertEquals(Request.Define("m", "open maps"), Aliases.parse("m='open maps'", reserved))
        // Quotes go only when they hold the whole expansion.
        assertEquals(Request.Define("m", "open \"maps\""), Aliases.parse("m = open \"maps\"", reserved))
        assertEquals(Request.Usage, Aliases.parse("m =", reserved))
        assertEquals(Request.Usage, Aliases.parse("= open maps", reserved))
        assertEquals(Request.BuiltIn("help"), Aliases.parse("Help = echo no", reserved))
        // Only the first word before "=" is the name.
        assertEquals(Request.Define("m", "open maps"), Aliases.parse("m extra = open maps", reserved))
    }
}
