package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.StyledText.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The Markdown md.log reads. */
class MarkdownTest {
    private fun styles(md: String) = StyledText.lines(Markdown.inline(md)).flatMap { it.spans }.filter { it.styles.isNotEmpty() }.associate { it.text to it.styles }

    @Test fun eachStyle() {
        assertEquals(
            mapOf(
                "b" to setOf(Style.BOLD), "B" to setOf(Style.BOLD), "i" to setOf(Style.ITALIC), "j" to setOf(Style.ITALIC),
                "s" to setOf(Style.STRIKE), "u" to setOf(Style.UNDERLINE), "c" to setOf(Style.CODE),
            ),
            styles("**b** __B__ *i* _j_ ~~s~~ ++u++ `c`"),
        )
        assertEquals("b B i j s u c", StyledText.plain(Markdown.inline("**b** __B__ *i* _j_ ~~s~~ ++u++ `c`")))
    }

    @Test fun stylesNest() = assertEquals(setOf(Style.BOLD, Style.ITALIC), styles("**a *b* c**")["b"])

    @Test fun whatIsntAStyleStaysAsWritten() {
        for (text in listOf("my_var_name", "a * b", "**open", "2 * 3 * 4", "c++ is old", "~ tilde ~")) {
            assertEquals(text, text, StyledText.plain(Markdown.inline(text)))
            assertFalse(text, StyledText.has(Markdown.inline(text)))
        }
        // Code shows its marks; a backslash shows the next mark.
        assertEquals(mapOf("**x**" to setOf(Style.CODE)), styles("`**x**`"))
        assertEquals("*not italic*", StyledText.plain(Markdown.inline("\\*not italic\\*")))
    }

    @Test fun listItemsInABody() {
        val lines = StyledText.lines(Markdown.block("- milk\n  - skim\n* bread\n1. eggs\n  2) tea\nplain text"))
        assertEquals(listOf(1, 2, 1, 1, 2, 0), lines.map { it.level })
        assertEquals(listOf("•", "◦", "•", "1.", "2.", ""), lines.map { it.prefix })
        assertEquals("• milk\n  ◦ skim\n• bread\n1. eggs\n  2. tea\nplain text", StyledText.plain(Markdown.block("- milk\n  - skim\n* bread\n1. eggs\n  2) tea\nplain text")))
        // A title has no list items.
        assertEquals("- milk", StyledText.plain(Markdown.inline("- milk")))
    }

    /** Web text put inside md.log through md.escape shows exactly as it was. */
    @Test fun escapedTextComesOutAsWritten() {
        for (text in listOf(
            "Show HN: *nix tools in __50__ lines", "- not a list", "  1. nor this", "12) or this",
            "C++ ~~ `tick` \\ back", "a_b_c **x**", "-5 degrees", "1.5 GB", "[not](https://a.link)",
        )) {
            val shown = Markdown.block(Markdown.escape(text))
            assertFalse(text, StyledText.has(shown))
            assertEquals(text, text, shown)
        }
    }

    /** [text](address): a link, with styles in its text. Brackets in the address pair up; a space ends it. */
    @Test fun links() {
        val spans = StyledText.lines(Markdown.inline("see [**Lua**](https://en.wikipedia.org/wiki/Lua_(programming_language)) now")).single().spans
        assertEquals(listOf("see ", "Lua", " now"), spans.map { it.text })
        assertEquals(listOf(null, "https://en.wikipedia.org/wiki/Lua_(programming_language)", null), spans.map { it.url })
        assertEquals(setOf(Style.BOLD), spans[1].styles)
        for (text in listOf("[a](no space)", "[](https://x)", "[a] (https://x)", "[a](https://x")) {
            assertEquals(text, emptyList<String>(), StyledText.links(Markdown.inline(text)))
        }
        // In "[a [b](x)" the inner one is the link.
        assertEquals(listOf("https://x"), StyledText.links(Markdown.inline("[a [b](https://x)")))
    }
}
