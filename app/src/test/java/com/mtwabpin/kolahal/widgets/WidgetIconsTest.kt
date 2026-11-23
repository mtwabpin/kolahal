package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.ui.widgets.widgetIconDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Icons by name: the list, its drawables, and how a widget's `icon` field reads a name. */
class WidgetIconsTest {
    private fun parse(raw: Map<String, Any?>, kind: WidgetKind = WidgetKind.STAT) =
        WidgetParser.parse(raw, kind, { it }, { it })

    private fun refused(raw: Map<String, Any?>): String? =
        runCatching { parse(raw) }.exceptionOrNull()?.message

    /** Every name has its drawable, in res/drawable/wi_<name>.xml, and the names are plain and unique (one group each). */
    @Test
    fun everyNameHasItsDrawable() {
        assertEquals(WidgetIcons.NAMES.size, WidgetIcons.NAMES.toSet().size)
        for (name in WidgetIcons.NAMES) {
            assertTrue(name, Regex("[a-z]+(-[a-z]+)*").matches(name))
            assertNotNull(name, widgetIconDrawable(name))
            assertTrue(name, File("src/main/res/drawable/wi_${name.replace('-', '_')}.xml").isFile)
        }
        // No drawable without its name: a drawable added by hand needs its name too.
        val drawables = File("src/main/res/drawable").list()!!.filter { it.startsWith("wi_") }
            .map { it.removePrefix("wi_").removeSuffix(".xml").replace('_', '-') }
        assertEquals(WidgetIcons.NAMES.sorted(), drawables.sorted())
        assertNull(widgetIconDrawable("☀"))
        assertNull(widgetIconDrawable("Sun"))
    }

    @Test
    fun anIconIsANameOrAShortText() {
        assertEquals("sun", parse(mapOf("value" to "1", "icon" to "sun")).base.icon)
        assertEquals("calendar", parse(mapOf("value" to "1", "icon" to "calendar")).base.icon)
        // An emoji or a letter is drawn as it is, cut to 4 characters with no "…".
        assertEquals("☀️", parse(mapOf("value" to "1", "icon" to "☀️")).base.icon)
        assertEquals("Abcd", parse(mapOf("value" to "1", "icon" to "Abcdef")).base.icon)
        // A short lower-case word that is no name is text: "ok" is drawn as "ok".
        assertEquals("ok", parse(mapOf("value" to "1", "icon" to "ok")).base.icon)
    }

    /** A lower-case word over 4 letters that is no name is most likely a typo. It is an error, not "sunn" cut to "sunn". */
    @Test
    fun aWordThatIsNoNameIsAnError() {
        assertEquals(
            "widget: icon \"sunny\" is no icon name: the scripting guide lists them, or give an emoji",
            refused(mapOf("value" to "1", "icon" to "sunny"))
        )
        assertTrue(WidgetIcons.looksLikeName("trophies"))
        assertFalse(WidgetIcons.looksLikeName("trophy"))
        assertFalse("4 letters or less is text", WidgetIcons.looksLikeName("abcd"))
    }

    /** Rows, buttons and switches read names too. */
    @Test
    fun rowsButtonsAndSwitchesTakeNames() {
        val go = mapOf("args" to "go")
        val rows = parse(
            mapOf("items" to listOf(mapOf("title" to "Bus", "icon" to "bus"))),
            WidgetKind.LIST
        ) as WidgetSpec.Rows
        assertEquals("bus", rows.items.single().icon)
        val buttons = parse(
            mapOf(
                "items" to listOf(
                    mapOf(
                        "label" to "Torch",
                        "icon" to "torch",
                        "tap" to go
                    )
                )
            ), WidgetKind.BUTTONS
        ) as WidgetSpec.Buttons
        assertEquals("torch", buttons.items.single().icon)
        val toggles = parse(
            mapOf(
                "items" to listOf(
                    mapOf(
                        "label" to "DND",
                        "icon" to "dnd",
                        "on" to true,
                        "tap" to go
                    )
                )
            ), WidgetKind.TOGGLES
        ) as WidgetSpec.Toggles
        assertEquals("dnd", toggles.items.single().icon)
    }

    /** A shortcut's icon can be a name longer than 4 letters. Other text is still cut. */
    @Test
    fun aShortcutKeepsAnIconName() {
        assertEquals(
            "calendar",
            Shortcuts.clean(listOf(Shortcut("Agenda", "agenda", "calendar"))).single().icon
        )
        assertEquals("abcd", Shortcuts.clean(listOf(Shortcut("X", "x", "abcdefg"))).single().icon)
    }
}
