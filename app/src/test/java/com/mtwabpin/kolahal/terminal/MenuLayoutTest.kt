package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a line's sheet places its actions ([MenuLayout]): each in one place, nothing lost. */
class MenuLayoutTest {
    private fun action(
        label: String,
        place: ActionPlace?,
        destructive: Boolean = false,
        short: String = label
    ) = LogAction(label, destructive = destructive, short = short, place = place) {}

    private val script = ActionPlace.Group("script", "weather", own = true)
    private val app = ActionPlace.Group("app", "Maps", own = true)
    private val link = ActionPlace.Group("link", "Link · example.com/forecast", own = true)

    /** A script's line that names an app and has a link, in the order LogLineActions lists them. */
    private val scriptLine = listOf(
        action("Launch Maps [P]", app, short = "Open"),
        action("App info", app),
        action("Open link", link, short = "Open"),
        action("Run weather now", script, short = "Run now"),
        action("Edit weather.lua", script, short = "Edit"),
        action("Show only weather", ActionPlace.ONLY, short = "Only this"),
        action("Star line", ActionPlace.STAR, short = "Star"),
        action("Copy text", ActionPlace.COPY, short = "Copy"),
        action("Uninstall", app, destructive = true),
        action("Delete line", ActionPlace.DELETE, destructive = true, short = "Delete"),
    )

    /**
     * Every action is in exactly one place: nothing lost, nothing twice. "Fresh from here" may be
     * there or not (not on the first line shown), added as plain words.
     */
    @Test
    fun everyActionIsInOnePlace() {
        val fresh = LogAction(MenuLayout.FRESH) {}
        for (list in listOf(scriptLine, scriptLine + fresh)) {
            val placed = list.map(MenuLayout::placeKnown)
            val all = MenuLayout.of(placed).all
            assertEquals(list.size, all.size)
            placed.forEach { a -> assertEquals(a.label, 1, all.count { it === a }) }
            assertTrue(MenuLayout.of(placed).words.isEmpty())
        }
    }

    /** "Fresh from here", added as plain words, takes its place in the row with its short word and icon. */
    @Test
    fun freshFromHereFindsItsPlace() {
        val fresh = MenuLayout.placeKnown(LogAction(MenuLayout.FRESH) {})
        assertEquals(ActionPlace.FRESH, fresh.place)
        assertEquals("Fresh", fresh.short)
        assertTrue(fresh.icon != null)
        val row = MenuLayout.of((scriptLine + fresh).map(MenuLayout::placeKnown)).row!!
        assertEquals(listOf("Copy text", "Fresh from here", "Star line", "Show only weather", "Delete line"), row.map { it?.label })
        // Any other action without a place stays as it is.
        val other = LogAction("Settings") {}
        assertSame(other, MenuLayout.placeKnown(other))
    }

    /**
     * The row has its five places in a fixed order, whatever the list's order; a place without its
     * action stays empty, so the others keep theirs.
     */
    @Test
    fun theRowKeepsItsPlaces() {
        val row = MenuLayout.of(scriptLine).row!!
        assertEquals(ActionPlace.SLOTS, row.size)
        assertEquals(listOf("Copy text", null, "Star line", "Show only weather", "Delete line"), row.map { it?.label })
        // A command's line: Copy output is in the command's group; the row keeps the same places.
        val commandGroup = ActionPlace.Group("command", "Command")
        val command = MenuLayout.of(
            listOf(
                action("Run again", commandGroup),
                action("Copy output", commandGroup),
                action("Show only commands", ActionPlace.ONLY),
                action("Star line", ActionPlace.STAR),
                action("Copy text", ActionPlace.COPY),
                action("Delete command and output", commandGroup, destructive = true),
                action("Delete line", ActionPlace.DELETE, destructive = true),
            )
        )
        assertEquals(listOf("Copy text", null, "Star line", "Show only commands", "Delete line"), command.row!!.map { it?.label })
        assertEquals(listOf("Run again", "Copy output", "Delete command and output"), command.groups.single().second.map { it.label })
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            listOf(ActionPlace.COPY, ActionPlace.FRESH, ActionPlace.STAR, ActionPlace.ONLY, ActionPlace.DELETE).map { it.slot },
        )
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            listOf(ActionPlace.OPEN, ActionPlace.APP_INFO, ActionPlace.FAVORITE, ActionPlace.HIDE, ActionPlace.UNINSTALL).map { it.slot },
        )
    }

    /**
     * The groups come in the order their first action comes; a group's destructive action is last
     * in it, even when the list puts it after every other group's.
     */
    @Test
    fun groupsFollowTheListAndEndWithTheirDestructiveOne() {
        val layout = MenuLayout.of(scriptLine)
        assertEquals(listOf("Maps", "Link · example.com/forecast", "weather"), layout.groups.map { it.first.heading })
        assertEquals(
            listOf("Launch Maps [P]", "App info", "Uninstall"),
            layout.groups.first().second.map { it.label })
        // A group of one destructive action (a note) comes where its action comes.
        val note = ActionPlace.Group("note", "Note")
        val withNote = MenuLayout.of(listOf(action("Copy text", ActionPlace.COPY), action("Delete note", note, destructive = true)))
        assertEquals(listOf("Note"), withNote.groups.map { it.first.heading })
        // Two links are two groups, each under its own address.
        val two = MenuLayout.of(
            listOf(
                action("Open “a”", ActionPlace.Group("link 0", "Link · a.example"), short = "Open"),
                action("Open “b”", ActionPlace.Group("link 1", "Link · b.example"), short = "Open"),
            )
        )
        assertEquals(listOf("Link · a.example", "Link · b.example"), two.groups.map { it.first.heading })
    }

    /** Actions without a place are a list of words, in their order; a second action for a taken place joins them. */
    @Test
    fun theRestAreWords() {
        val quick = listOf(action("Settings", null), action("Notifications", null))
        val layout = MenuLayout.of(quick)
        assertNull(layout.row)
        assertTrue(layout.groups.isEmpty())
        assertEquals(quick, layout.words)
        val twice = action("Copy again", ActionPlace.COPY)
        val clash = MenuLayout.of(listOf(action("Copy text", ActionPlace.COPY), twice))
        assertSame(twice, clash.words.single())
        assertEquals(2, clash.all.size)
    }

    /** A link's heading: the address without its scheme, `www.` and last slash, cut with "…". */
    @Test
    fun aLinksAddressIsShortened() {
        assertEquals("example.com/forecast", MenuLayout.shortAddress("https://www.example.com/forecast/"))
        assertEquals("example.com", MenuLayout.shortAddress("http://example.com"))
        assertEquals("ftp.example.com/a", MenuLayout.shortAddress("ftp://ftp.example.com/a"))
        val long = MenuLayout.shortAddress("https://example.com/" + "a".repeat(80))
        assertEquals(36, long.length)
        assertTrue(long.endsWith("…"))
    }

    /**
     * An app's shortcut is a chip under Shortcuts (or the app's group) whose icon is the shortcut's
     * own picture, keyed by package, shortcut and profile. The other actions keep their icons.
     */
    @Test
    fun aShortcutCarriesItsOwnPicture() {
        val s = MenuLayout.shortcut("Maps", "com.example.maps", 10L, "home", "Home") {}
        assertEquals("Maps › Home", s.label)
        assertEquals("Home", s.short)
        assertEquals(ActionIcon.Shortcut("com.example.maps", "home", 10L), s.icon)
        assertEquals("Shortcuts", (s.place as ActionPlace.Group).heading)
        assertSame(app, MenuLayout.shortcut("Maps", "com.example.maps", 0L, "home", "Home", app) {}.place)
        val fresh = MenuLayout.placeKnown(action(MenuLayout.FRESH, null))
        assertEquals(ActionIcon.Drawable(com.mtwabpin.kolahal.R.drawable.ic_clear_all), fresh.icon)
    }
}
