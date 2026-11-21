package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** What the search field of Get more scripts finds ([ScriptCatalog.found]). */
class ScriptCatalogSearchTest {
    private fun shared(
        name: String,
        title: String,
        desc: String,
        author: String,
        vararg tags: String
    ) = ScriptCatalog.Entry(
        name,
        ScriptLang.LUA,
        "scripts/$name/$name.lua",
        title,
        desc,
        author,
        version = "1.0",
        tags = tags.toList(),
        uses = emptyList(),
        on = emptyList(),
        command = false,
        every = null,
        at = emptyList(),
        requires = null,
        sha256 = "0".repeat(64),
    )

    private val tide =
        shared("gstide", "Tide times", "The next high and low tide", "ann", "sea", "weather")
    private val joke = shared("gsjoke", "Joke of the day", "A joke each morning", "bo", "fun")
    private val list = listOf(tide, joke)

    private fun found(query: String) = ScriptCatalog.found(list, query).map { it.name }

    @Test
    fun aBlankSearchFindsEveryScript() {
        assertEquals(list, ScriptCatalog.found(list, ""))
        assertEquals(list, ScriptCatalog.found(list, "  "))
    }

    @Test
    fun aScriptIsFoundByItsNameTitleDescriptionAuthorOrTag() {
        assertEquals(listOf("gstide"), found("gstide"))
        assertEquals(listOf("gsjoke"), found("Joke"))
        assertEquals(listOf("gsjoke"), found("morning"))
        assertEquals(listOf("gstide"), found("ann"))
        assertEquals(listOf("gstide"), found("sea"))
        assertEquals(listOf("gstide", "gsjoke"), found("gs"))
    }

    /** Each word can be in another part, in any case; two words that each find another script find none. */
    @Test
    fun everyWordMustBeFound() {
        assertEquals(listOf("gsjoke"), found("BO  fun"))
        assertEquals(listOf("gstide"), found("gstide weather"))
        assertEquals(emptyList<String>(), found("sea fun"))
        assertEquals(emptyList<String>(), found("zzqx"))
    }

    /** The scripts found are the list's own entries, in its order. */
    @Test
    fun theListKeepsItsEntriesAndItsOrder() {
        assertSame(joke, ScriptCatalog.found(list, "fun").single())
        assertEquals(listOf("gstide", "gsjoke"), found("o"))
    }
}
