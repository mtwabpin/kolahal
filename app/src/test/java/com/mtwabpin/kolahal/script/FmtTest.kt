package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.StyledText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** What the engines share for `fmt.…`: the colour a role names, and rows read from a Lua table or a JavaScript array. */
class FmtTest {
    /** Text as Lua's engine gives it to [Fmt]: nothing is an empty cell. */
    private val fmt = Fmt { it?.toString() ?: "" }

    private val roles = "accent, link, info, muted, text, good, warn, bad"

    private fun refused(block: () -> Unit): String? = assertThrows(IllegalArgumentException::class.java) { block() }.message

    @Test fun aColourIsNamedByItsRole() {
        assertEquals(StyledText.Colour.GOOD, Fmt.colour("fmt.color", "good"))
        assertEquals(StyledText.Colour.WARN, Fmt.colour("fmt.color", " Warn "))
        assertEquals(StyledText.tag(StyledText.Colour.BAD, "down"), fmt.tag(listOf("bad", "down")))
    }

    /** No such role is an error for the script, the same in each call: it names the call and lists the roles. */
    @Test fun anUnknownColourListsTheRoles() {
        assertEquals("fmt.color: no colour 'pink'; use $roles", refused { Fmt.colour("fmt.color", "pink") })
        assertEquals("fmt.tag: no colour 'pink'; use $roles", refused { fmt.tag(listOf("pink", "text")) })
        assertEquals("fmt.bar: no colour 'pink'; use $roles", refused { fmt.bar(listOf(0.5, mapOf("color" to "pink"))) })
    }

    /** The error quotes what was passed. `fmt.color` quotes the engine's own word for nothing; the others show it as the script would. */
    @Test fun theErrorQuotesWhatWasPassed() {
        assertEquals("fmt.color: no colour 'null'; use $roles", refused { Fmt.colour("fmt.color", null) })
        assertEquals("fmt.tag: no colour ''; use $roles", refused { fmt.tag(listOf(null, "text")) })
        assertEquals("fmt.bars: no colour '7'; use $roles", refused { fmt.bars(listOf(emptyList<Any?>(), mapOf("color" to 7))) })
    }

    /** An empty Lua table has no keys to tell a list from a map: as rows, or as one row, it is an empty list. */
    @Test fun anEmptyTableIsNoRows() {
        assertEquals(StyledText.stats(emptyList()), fmt.stats(listOf(emptyMap<String, Any?>())))
        assertEquals(StyledText.stats(emptyList()), fmt.stats(listOf(emptyList<Any?>())))
        assertEquals(StyledText.stats(listOf("" to "")), fmt.stats(listOf(listOf(emptyMap<String, Any?>()))))
        assertEquals(StyledText.stats(listOf("3" to "open")), fmt.stats(listOf(listOf(listOf(3, "open")))))
    }

    @Test fun whatIsNotAListOfRowsIsAnError() {
        val bad = "fmt.stats expects a list of rows, each { value, label }"
        assertEquals(bad, refused { fmt.stats(listOf(mapOf("a" to 1))) })
        assertEquals(bad, refused { fmt.stats(listOf("text")) })
        assertEquals(bad, refused { fmt.stats(emptyList()) })
        assertEquals(bad, refused { fmt.stats(listOf(listOf(mapOf("a" to 1)))) })
        assertEquals(bad, refused { fmt.stats(listOf(listOf("not a row"))) })
    }
}
