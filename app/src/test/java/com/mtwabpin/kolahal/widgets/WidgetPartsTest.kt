package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.script.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a card shows, chosen on its settings page ([WidgetParts]): the parts it hides are kept per
 * placed card after its shape and color, in the saved list and in backups, and only the parts the
 * card has to hide are offered.
 */
class WidgetPartsTest {
    private fun script(name: String, column: Int = 0, hidden: Set<String> = emptySet(), color: ColorRole? = null) =
        PlacedWidget(
            null, WidgetSource.script(name), 0L, 88, 0,
            span = 6, column = column, topDp = 0, color = color, hidden = hidden
        )

    /** The hidden parts are the line's last field, after empty ones for the shape and the color. None: the line as it was. */
    @Test
    fun theSavedListKeepsTheHiddenParts() {
        val all = listOf(
            script("a", hidden = setOf(WidgetParts.NAME, WidgetParts.ICON)),
            script("b", column = 6, hidden = setOf(WidgetParts.ICON), color = ColorRole.LINK),
            script("c", column = 0).copy(topDp = 96),
        )
        val text = WidgetCodec.encode(all)
        assertEquals(
            listOf(
                "-|script:a|0|88|0|panel|6|plain|0|0|||icon,name",
                "-|script:b|0|88|0|panel|6|plain|6|0||link|icon",
                "-|script:c|0|88|0|panel|6|plain|0|96",
            ),
            text.lines()
        )
        assertEquals(all, WidgetCodec.decode(text))
        assertFalse(WidgetCodec.start(text).changed)
    }

    /** Old lines hide nothing. A part this version doesn't know is kept; what is no id is left out. An app's widget hides nothing. */
    @Test
    fun oldOddAndAppLinesHideNothingTheyShouldNot() {
        assertEquals(emptySet<String>(), WidgetCodec.decode("-|script:a|0|88|0|panel|6|plain|0|0||warn").single().hidden)
        assertEquals(
            setOf("name", "humidity"),
            WidgetCodec.decode("-|script:a|0|88|0|panel|6|plain|0|0|||name,humidity,Not An Id,").single().hidden
        )
        assertEquals(emptySet<String>(), WidgetCodec.decode("5|com.a/.W|0|88|0|panel|6|plain|0|0|||name").single().hidden)
    }

    /** A backup carries the hidden parts as a list, only when there are some. */
    @Test
    fun backupsCarryTheHiddenParts() {
        val all = listOf(script("a", hidden = setOf(WidgetParts.ICON)), script("b", column = 6))
        val exported = WidgetCodec.export(all)
        assertEquals(listOf("icon"), exported[0]["hidden"])
        assertFalse(exported[1].containsKey("hidden"))
        assertEquals(all, WidgetCodec.fromExport(exported))
        @Suppress("UNCHECKED_CAST")
        val read = Json.parse(Json.stringify(exported)) as List<Map<String, Any?>>
        assertEquals(all, WidgetCodec.fromExport(read))
        // A string as a stored line has it reads too; anything else hides nothing.
        assertEquals(
            listOf(setOf("name"), emptySet(), emptySet()),
            WidgetCodec.fromExport(
                listOf(
                    mapOf("provider" to "script:c", "hidden" to "name"),
                    mapOf("provider" to "script:d", "hidden" to 3),
                    mapOf("provider" to "com.a/.W", "hidden" to listOf("name")),
                )
            ).map { it.hidden }
        )
    }

    /**
     * A card has its name to hide in every look but a built-in card's own; its icon only when it has
     * one and its look draws it.
     */
    @Test
    fun onlyThePartsACardHasAreOffered() {
        val withIcon = WidgetSpec.Stat(WidgetBase(icon = "sun"), value = "27°")
        val without = WidgetSpec.Stat(WidgetBase(), value = "27°")
        val both = listOf(WidgetParts.NAME, WidgetParts.ICON)
        val name = listOf(WidgetParts.NAME)
        listOf(WidgetLook.BOLD, WidgetLook.SPLIT, WidgetLook.SHAPE)
            .forEach { assertEquals(it.id, both, WidgetParts.offered(WidgetKind.STAT, withIcon, it)) }
        assertEquals(name, WidgetParts.offered(WidgetKind.STAT, withIcon, WidgetLook.PLAIN))
        assertEquals(name, WidgetParts.offered(WidgetKind.STAT, without, WidgetLook.BOLD))
        assertEquals(name, WidgetParts.offered(WidgetKind.STAT, null, WidgetLook.BOLD))
        val quote = WidgetSpec.Text(WidgetBase(icon = "quote"), "Carry an umbrella")
        assertEquals(both, WidgetParts.offered(WidgetKind.TEXT, quote, WidgetLook.SHAPE))
        assertEquals(name, WidgetParts.offered(WidgetKind.TEXT, quote, WidgetLook.SPLIT))
        val wait = WidgetSpec.Countdown(WidgetBase(icon = "plane"), to = 2_000_000_000L)
        assertEquals(both, WidgetParts.offered(WidgetKind.COUNTDOWN, wait, WidgetLook.SPLIT))
        // A built-in card's look of its own writes no name.
        listOf(WidgetLook.NEXT, WidgetLook.STACK, WidgetLook.NOW)
            .forEach { assertEquals(it.id, emptyList<String>(), WidgetParts.offered(WidgetKind.LIST, null, it)) }
    }

    /**
     * Last updated is off until turned on: a card keeps it among the parts it shows, the line's last
     * field after the scene's, and a backup's "shown". Hidden parts stay as they were. An app's widget has none.
     */
    @Test
    fun lastUpdatedIsKeptAmongTheShownParts() {
        val on = script("a").copy(shown = setOf(WidgetParts.UPDATED))
        val both = script("b", column = 6, hidden = setOf(WidgetParts.NAME)).copy(shown = setOf(WidgetParts.UPDATED))
        val all = listOf(on, both, script("c").copy(topDp = 96))
        val text = WidgetCodec.encode(all)
        assertEquals(
            listOf(
                "-|script:a|0|88|0|panel|6|plain|0|0||||||updated",
                "-|script:b|0|88|0|panel|6|plain|6|0|||name|||updated",
                "-|script:c|0|88|0|panel|6|plain|0|96",
            ),
            text.lines()
        )
        assertEquals(all, WidgetCodec.decode(text))
        assertEquals(emptySet<String>(), WidgetCodec.decode("5|com.a/.W|0|88|0|panel|6|plain|0|0||||||updated").single().shown)
        val exported = WidgetCodec.export(all)
        assertEquals(listOf("updated"), exported[0]["shown"])
        assertFalse(exported[2].containsKey("shown"))
        @Suppress("UNCHECKED_CAST")
        val read = Json.parse(Json.stringify(exported)) as List<Map<String, Any?>>
        assertEquals(all, WidgetCodec.fromExport(read))
        // Off by default; on once shown. The name shows until it is hidden.
        assertFalse(WidgetParts.shows(script("d"), WidgetParts.UPDATED))
        assertTrue(WidgetParts.shows(on, WidgetParts.UPDATED))
        assertTrue(WidgetParts.shows(on, WidgetParts.NAME))
        assertFalse(WidgetParts.shows(both, WidgetParts.NAME))
        assertFalse("Last updated is never hidden", WidgetParts.shows(script("e", hidden = setOf(WidgetParts.UPDATED)), WidgetParts.UPDATED))
    }

    /** Last updated is offered on a script's card in every look, and never on one of Kolahal's own. */
    @Test
    fun lastUpdatedIsOfferedOnAScriptsCard() {
        val stat = WidgetSpec.Stat(WidgetBase(icon = "sun"), value = "27°")
        WidgetLook.entries.forEach {
            assertTrue(it.id, WidgetParts.UPDATED in WidgetParts.offered(WidgetKind.STAT, stat, it, script = true))
            assertFalse(it.id, WidgetParts.UPDATED in WidgetParts.offered(WidgetKind.STAT, stat, it))
        }
        assertEquals(listOf(WidgetParts.NAME, WidgetParts.UPDATED), WidgetParts.offered(WidgetKind.VERSUS, null, WidgetLook.PLAIN, script = true))
        assertEquals("Last updated", WidgetParts.label(WidgetParts.UPDATED))
        assertEquals("When the card's content was last refreshed", WidgetParts.detail(WidgetParts.UPDATED))
    }

    /** A hidden icon leaves the description; nothing else changes, and a card with nothing to hide is the same. */
    @Test
    fun hidingTheIconTakesItOutOfTheDescription() {
        val stat = WidgetSpec.Stat(WidgetBase(title = "Air", icon = "sun", color = ColorRole.WARN), value = "118")
        val hidden = WidgetParts.applied(stat, setOf(WidgetParts.ICON)) as WidgetSpec.Stat
        assertNull(hidden.base.icon)
        assertEquals(stat.copy(base = stat.base.copy(icon = null)), hidden)
        assertSame(stat, WidgetParts.applied(stat, setOf(WidgetParts.NAME)))
        assertSame(stat, WidgetParts.applied(stat, emptySet()))
        assertEquals("icon,name", WidgetParts.write(setOf("name", "icon")))
        assertEquals("", WidgetParts.write(emptySet()))
    }
}
