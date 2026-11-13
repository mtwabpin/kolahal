package com.mtwabpin.kolahal.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParsingTest {
    @Test fun tokenizeHonoursQuotes() {
        assertEquals(listOf("open", "Google Maps", "-w"), CommandParsing.tokenize("open \"Google Maps\" -w"))
        assertEquals(listOf("a", "b c"), CommandParsing.tokenize("  a   'b c' "))
    }

    @Test fun anApostropheInsideAWordIsALetter() {
        assertEquals(listOf("sms", "mom", "I'm", "late"), CommandParsing.tokenize("sms mom I'm late"))
        assertEquals(listOf("open", "Domino's", "-w", "--resume"), CommandParsing.tokenize("open Domino's -w --resume"))
        assertEquals(listOf("say", "it's ok"), CommandParsing.tokenize("say \"it's ok\""))
    }

    @Test fun everyKindOfSpaceIsAPlainOne() {
        assertEquals("script secret x K v", CommandParsing.spaces("script\tsecret\u00A0x K\nv"))
        val plain = "already plain"
        assertTrue(plain === CommandParsing.spaces(plain))
    }

    @Test fun parseInterval() {
        assertEquals(30, CommandParsing.parseInterval("30m"))
        assertEquals(30, CommandParsing.parseInterval("30"))
        assertEquals(120, CommandParsing.parseInterval("2h"))
        assertEquals(1440, CommandParsing.parseInterval("1d"))
        assertEquals(0, CommandParsing.parseInterval("off"))
        assertNull(CommandParsing.parseInterval("soon"))
    }

    @Test fun formatInterval() {
        assertEquals("30m", CommandParsing.formatInterval(30))
        assertEquals("2h", CommandParsing.formatInterval(120))
        assertEquals("1d", CommandParsing.formatInterval(1440))
        assertEquals("not whole hours", "90m", CommandParsing.formatInterval(90))
    }

    /** A typo is one letter off for a short name (four letters or fewer), two for a longer one. */
    @Test fun aTypoIsNearItsName() {
        assertTrue(CommandParsing.isNear("tod", "todo"))
        assertFalse(CommandParsing.isNear("td", "todo"))
        assertTrue(CommandParsing.isNear("wether", "weather"))
        assertTrue(CommandParsing.isNear("weathr", "weather"))
        assertFalse(CommandParsing.isNear("wthr", "weather"))
    }

    /** "Did you mean": the closest near name, the first one on a tie; one-letter names never. */
    @Test fun theNearestNameIsSuggested() {
        assertEquals("help", CommandParsing.nearest("hlp", listOf("todo", "help")))
        assertEquals("notes", CommandParsing.nearest("notez", listOf("notes", "note")))
        assertEquals("calc", CommandParsing.nearest("calx", listOf("calc", "cal")))
        assertEquals("cal", CommandParsing.nearest("calx", listOf("cal", "calc")))
        assertNull(CommandParsing.nearest("zzzz", listOf("help", "todo")))
        assertNull(CommandParsing.nearest("q", listOf("x")))
        // Swapped letters count as two, so a four-letter name isn't offered for them.
        assertNull(CommandParsing.nearest("hepl", listOf("help")))
    }

    /** A week at most; a number too big to multiply is refused, not wrapped round to some other time. */
    @Test fun anIntervalPastAWeekIsRefused() {
        assertEquals(10_080, CommandParsing.parseInterval("7d"))
        assertEquals(10_080, CommandParsing.parseInterval("10080"))
        assertNull(CommandParsing.parseInterval("8d"))
        assertNull(CommandParsing.parseInterval("10081"))
        assertNull(CommandParsing.parseInterval("1500000d"))
        // These used to wrap: to 352 minutes, and to a negative number read as "off".
        assertNull(CommandParsing.parseInterval("6405119470038039d"))
        assertNull(CommandParsing.parseInterval("10000000000000000d"))
        assertNull(CommandParsing.parseInterval("99999999999999999999"))
    }
}
