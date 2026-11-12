package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `commands.off`: which built-in commands can be turned off, and how the setting reads what is typed. */
class CommandsOffTest {
    @Test fun namesAreCommandsInTheCatalogsOrder() {
        val ytFirst = CommandsOff.CHOICES.indexOf("yt") < CommandsOff.CHOICES.indexOf("maps")
        assertEquals(if (ytFirst) "yt,maps" else "maps,yt", CommandsOff.parse("maps, yt"))
        // Commas or spaces, any case, and a name twice counts once.
        assertEquals(CommandsOff.parse("maps,yt"), CommandsOff.parse("YT maps yt"))
        // Another name for a command is that command.
        assertEquals("yt", CommandsOff.parse("youtube"))
        assertEquals("", CommandsOff.parse("none"))
        assertEquals("", CommandsOff.parse(""))
    }

    @Test fun onlyCommandsThatCanBeTurnedOff() {
        assertNull("not a command", CommandsOff.parse("yt,nope"))
        for (name in CommandsOff.ALWAYS_ON) assertNull(name, CommandsOff.parse(name))
        assertNull("another name for config", CommandsOff.parse("set"))
        assertNull("the calculator", CommandsOff.parse("="))
        assertTrue(CommandsOff.CHOICES.none { it in CommandsOff.ALWAYS_ON })
        // The setting reads the same way.
        assertNull(Keys.COMMANDS_OFF.parse("help"))
        assertEquals("yt", Keys.COMMANDS_OFF.parse("youtube"))
    }

    @Test fun aCommandIsOffByAnyOfItsNames() {
        val off = CommandsOff.names("yt,maps")
        assertTrue(CommandsOff.isOff("yt", off))
        assertTrue(CommandsOff.isOff("YouTube", off))
        assertTrue(CommandsOff.isOff("map", off))
        assertFalse(CommandsOff.isOff("wiki", off))
        assertFalse(CommandsOff.isOff("yt", emptySet()))
    }

    @Test fun aSwitchTurnsOneCommandOffOrOn() {
        val value = CommandsOff.toggled("", "yt", off = true)
        assertEquals("yt", value)
        assertEquals(CommandsOff.parse("yt,wiki"), CommandsOff.toggled(value, "wiki", off = true))
        assertEquals("", CommandsOff.toggled(value, "yt", off = false))
        // Turning on one that is on changes nothing.
        assertEquals("yt", CommandsOff.toggled(value, "wiki", off = false))
    }

    @Test fun aTipForACommandThatIsOffIsSkipped() {
        val first = Tips.next(emptyList(), emptySet())!!
        val head = InstantRun.canonical(first.example.substringBefore(' '))
        if (head !in CommandsOff.CHOICES) return
        assertTrue(Tips.next(emptyList(), emptySet(), setOf(head)) != first)
    }
}
