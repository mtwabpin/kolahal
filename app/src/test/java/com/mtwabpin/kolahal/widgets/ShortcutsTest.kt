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

    private val three = listOf(Shortcut("Torch", "torch"), Shortcut("Todo", "todo"), Shortcut("Maps", "open maps"))

    /** What the editor does: add at the end, change one in its place, remove one, move one. */
    @Test fun theEditorsChanges() {
        val dnd = Shortcut("Quiet", "dnd", "🌙")
        assertEquals(three + dnd, Shortcuts.with(three, 3, dnd))
        assertEquals(listOf(three[0], dnd, three[2]), Shortcuts.with(three, 1, dnd))
        assertEquals(listOf(dnd), Shortcuts.with(emptyList(), 0, dnd))
        // A place that is not there changes nothing.
        assertEquals(three, Shortcuts.with(three, 7, dnd))
        assertEquals(three, Shortcuts.with(three, -1, dnd))
        assertEquals(listOf(three[0], three[2]), Shortcuts.without(three, 1))
        assertEquals(three, Shortcuts.without(three, 9))
        assertEquals(listOf(three[1], three[0], three[2]), Shortcuts.moved(three, 1, -1))
        assertEquals(listOf(three[0], three[2], three[1]), Shortcuts.moved(three, 1, 1))
        // At an end of the list there is nowhere to go.
        assertEquals(three, Shortcuts.moved(three, 0, -1))
        assertEquals(three, Shortcuts.moved(three, 2, 1))
        assertEquals(three, Shortcuts.moved(three, 5, 1))
    }

    /** A change gives a clean list: what was typed is cut, and a ninth shortcut is not added. */
    @Test fun aChangedListIsClean() {
        assertEquals(Shortcut("weather", "weather"), Shortcuts.with(three, 3, Shortcut(" ", " weather\n")).last())
        // Changing one to have no command takes it out.
        assertEquals(listOf(three[0], three[2]), Shortcuts.with(three, 1, Shortcut("Empty", "")))
        val full = (1..8).map { Shortcut("S$it", "echo $it") }
        assertEquals(full, Shortcuts.with(full, 8, Shortcut("Ninth", "echo 9")))
    }

    /** After a backup is imported, every shortcut that the file brought is listed for a look, whatever it runs. */
    @Test fun anImportedBackupsShortcutsAreWorthALook() {
        val planted = listOf(Shortcut("Torch", "uninstall bank"), Shortcut("Weather", "weather"), Shortcut("todo", "todo"))
        assertEquals(
            listOf("shortcut Torch = uninstall bank", "shortcut Weather = weather", "shortcut todo"),
            Shortcuts.review(before = emptyList(), after = planted),
        )
        // Onto a phone that has shortcuts: only what the file changed is listed.
        assertEquals(listOf("shortcut Weather = weather"), Shortcuts.review(before = three, after = three + Shortcut("Weather", "weather")))
        // A command that changed under a label you know is the one to see.
        assertEquals(
            listOf("shortcut Torch = uninstall bank"),
            Shortcuts.review(before = three, after = listOf(Shortcut("Torch", "uninstall bank"), three[1], three[2])),
        )
        // So is a label that moved to another of your commands: the button would not do what it says.
        assertEquals(
            listOf("shortcut Torch = todo", "shortcut Todo = torch"),
            Shortcuts.review(before = three, after = listOf(Shortcut("Torch", "todo"), Shortcut("Todo", "torch"), three[2])),
        )
    }

    /** A file that brings no list, the same list, or only another order or other icons, plants nothing. */
    @Test fun yourOwnShortcutsAreNotListed() {
        assertEquals(emptyList<String>(), Shortcuts.review(before = three, after = three))
        assertEquals(emptyList<String>(), Shortcuts.review(before = three, after = three.reversed()))
        assertEquals(emptyList<String>(), Shortcuts.review(before = three, after = three.map { it.copy(icon = "★") }))
        assertEquals(emptyList<String>(), Shortcuts.review(before = three, after = emptyList()))
        assertEquals(emptyList<String>(), Shortcuts.review(before = emptyList(), after = emptyList()))
    }

    /** A long command is cut for the line, as an alias is. The "…" says that there is more to read in the editor. */
    @Test fun aLongCommandIsCutInTheReview() {
        val long = "echo " + "a".repeat(100)
        val line = Shortcuts.review(emptyList(), listOf(Shortcut("Say", long))).single()
        assertEquals("shortcut Say = " + long.take(59) + "…", line)
        val exact = "c".repeat(60)
        assertEquals("shortcut Sixty = $exact", Shortcuts.review(emptyList(), listOf(Shortcut("Sixty", exact))).single())
    }

    /** The backup's import reads the list before and after, and adds what [Shortcuts.review] says to its own review. */
    @Test fun theBackupImportListsTheShortcuts() {
        val backup = com.mtwabpin.kolahal.sourceFile("src/main/java/com/mtwabpin/kolahal/data/Backup.kt").readText()
        val before = backup.indexOf("Shortcuts.read(context)")
        val restore = backup.indexOf("FullBackup.importRaw(context")
        val review = backup.indexOf("Shortcuts.review(shortcutsBefore")
        assertTrue("the list is read before the file's data is restored", before in 0 until restore)
        assertTrue("and compared after it", review > restore)
        assertTrue("the result's review has the shortcuts", backup.contains("+ sensitiveAliases + shortcuts"))
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
