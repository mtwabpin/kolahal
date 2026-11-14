package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Shortcuts widget's list ([Shortcuts]): what is kept, and how it is stored. */
class ShortcutsTest {
    @Test fun theListRoundTrips() {
        val list = listOf(Shortcut("Torch", "torch", "🔦"), Shortcut("Maps", "open maps"), Shortcut("Say \"hi\"", "echo \"hi\" | head -1", "A"))
        assertEquals(list, Shortcuts.decode(Shortcuts.encode(list)))
        assertEquals(emptyList<Shortcut>(), Shortcuts.decode(Shortcuts.encode(emptyList())))
        assertEquals("[]", Shortcuts.encode(emptyList()))
    }

    /** An icon that is null is not written, and reads back as null. */
    @Test fun aMissingIconIsLeftOut() {
        val text = Shortcuts.encode(listOf(Shortcut("Todo", "todo")))
        assertFalse(text, text.contains("icon"))
        assertNull(Shortcuts.decode(text).single().icon)
        assertNull(Shortcuts.clean(listOf(Shortcut("Todo", "todo", "  "))).single().icon)
    }

    @Test fun aShortcutNeedsACommand() {
        assertEquals(listOf(Shortcut("Dnd", "dnd")), Shortcuts.clean(listOf(Shortcut("Empty", ""), Shortcut("Blank", " \n "), Shortcut("Dnd", " dnd "))))
    }

    /** Without a label, the button says its command. */
    @Test fun theCommandIsTheLabelWhenThereIsNone() {
        assertEquals(Shortcut("weather", "weather"), Shortcuts.clean(listOf(Shortcut("", "weather"))).single())
        assertEquals(Shortcut("weather", "weather"), Shortcuts.clean(listOf(Shortcut("  ", "weather"))).single())
        val long = Shortcuts.clean(listOf(Shortcut("", "remind tomorrow 9:00 call the dentist"))).single()
        assertEquals("remind tomorrow 9:00 ca…", long.label)
        assertEquals("remind tomorrow 9:00 call the dentist", long.command)
    }

    @Test fun textsAreCutToTheirLimits() {
        val s = Shortcuts.clean(listOf(Shortcut("L".repeat(40), "c".repeat(300), "abcdefg"))).single()
        assertEquals(Shortcuts.MAX_LABEL, s.label.length)
        assertTrue(s.label.endsWith("…"))
        // A cut command gets no "…": that would be part of what runs.
        assertEquals("c".repeat(Shortcuts.MAX_COMMAND), s.command)
        assertEquals("abcd", s.icon)
    }

    /** A cut never leaves half an emoji. */
    @Test fun aCutKeepsEmojiWhole() {
        val icon = Shortcuts.clean(listOf(Shortcut("x", "x", "a🔦🔦🔦"))).single().icon
        assertEquals("a🔦", icon)
        val label = Shortcuts.clean(listOf(Shortcut("L".repeat(22) + "🔦🔦", "x"))).single().label
        assertEquals("L".repeat(22) + "…", label)
    }

    /** A command is one line: what you would type before Enter. */
    @Test fun lineBreaksBecomeSpaces() {
        val s = Shortcuts.clean(listOf(Shortcut("Two\nlines", "todo buy\n  milk"))).single()
        assertEquals(Shortcut("Two lines", "todo buy milk"), s)
    }

    @Test fun onlyTheFirstEightAreKept() {
        val many = (1..12).map { Shortcut("S$it", "echo $it") }
        assertEquals(many.take(Shortcuts.MAX), Shortcuts.clean(many))
        assertEquals(many.take(Shortcuts.MAX), Shortcuts.decode(Shortcuts.encode(many)))
        // A shortcut that is left out does not take a place.
        assertEquals(8, Shortcuts.clean(listOf(Shortcut("none", "")) + many).size)
    }

    /** What is stored may be anything: an older version's text, or a backup written by hand. */
    @Test fun decodeNeverThrows() {
        assertEquals(emptyList<Shortcut>(), Shortcuts.decode(null))
        assertEquals(emptyList<Shortcut>(), Shortcuts.decode(""))
        assertEquals(emptyList<Shortcut>(), Shortcuts.decode("not json"))
        assertEquals(emptyList<Shortcut>(), Shortcuts.decode("{\"label\":\"a\",\"command\":\"b\"}"))
        assertEquals(emptyList<Shortcut>(), Shortcuts.decode("[".repeat(5000)))
        assertEquals(
            listOf(Shortcut("ok", "ok"), Shortcut("Named", "todo")),
            Shortcuts.decode("""[1, "x", null, {"label": "no command"}, {"command": 3}, {"command": "ok", "label": 7, "icon": 9}, {"label": "Named", "command": "todo", "more": true}]"""),
        )
    }
}
