package com.mtwabpin.kolahal.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.logEntry
import com.mtwabpin.kolahal.withValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How each kind of log line is drawn: its prefix, colours and weight, its details and body under it. */
class LogLineTextTest {
    private val indents = LineIndents(text = 10.sp, code = 12.sp, bullet = 8.sp)

    private fun render(entry: LogEntry, unread: Boolean = false, icon: Boolean = false, source: Boolean = true) =
        renderLine(entry, indents, unread, icon, source)

    /** The style over [part] of [text]: what the spans that cover all of it say, merged. */
    private fun AnnotatedString.at(part: String) = spanStyles.filter { r ->
        val i = text.indexOf(part)
        i >= 0 && r.start <= i && r.end >= i + part.length
    }.map { it.item }.reduce { a, b -> a.merge(b) }

    @Test fun aCommandIsItsPromptAndItsLineInBold() {
        val text = render(logEntry(LogKind.COMMAND, "torch on"))
        assertEquals("$ torch on", text.text)
        assertEquals(Term.prompt, text.at("$ ").color)
        assertEquals(Term.fg, text.at("torch on").color)
        assertEquals(FontWeight.Bold, text.at("torch on").fontWeight)
    }

    /** An error shows in the error colour, its styles taken off. */
    @Test fun anErrorIsPlainInItsColour() {
        val text = render(logEntry(LogKind.ERROR, "bad " + StyledText.wrap(StyledText.Style.BOLD, "thing")))
        assertEquals("bad thing", text.text)
        assertEquals(Term.error, text.at("bad thing").color)
        assertTrue(text.spanStyles.none { it.item.fontWeight == FontWeight.Bold })
    }

    /** An info line's first line in its colour, and the lines after it dimmed, as details under it. */
    @Test fun anInfoLinesDetailsAreDimmed() {
        val text = render(logEntry(LogKind.INFO, "Installed Clock\ncom.example.clock"))
        assertTrue(text.text, text.text.startsWith("Installed Clock") && text.text.endsWith("com.example.clock"))
        assertEquals(Term.info, text.at("Installed Clock").color)
        assertEquals(Term.dim, text.at("com.example.clock").color)
        // Two paragraphs: the details are indented as a block of their own.
        assertEquals(2, text.paragraphStyles.size)
    }

    /** A trailing profile tag takes its profile's colour, in bold. */
    @Test fun aProfileTagIsInItsColour() {
        val text = render(logEntry(LogKind.OUTPUT, "Chrome [W]"))
        assertEquals(Term.profileColor(ProfileKind.WORK), text.at("[W]").color)
        assertEquals(FontWeight.Bold, text.at("[W]").fontWeight)
        assertEquals(Term.fg, text.at("Chrome").color)
        // Or before a last word with no space in it, as an update's ↑.
        val updated = render(logEntry(LogKind.INFO, "Updated Maps [W] ↑"))
        assertEquals("Updated Maps [W] ↑", updated.text)
        assertEquals(Term.profileColor(ProfileKind.WORK), updated.at("[W]").color)
        assertEquals(FontWeight.Bold, updated.at("[W]").fontWeight)
        assertEquals(Term.info, updated.at("Updated Maps").color)
        assertEquals(Term.info, updated.at("↑").color)
        // Only at the end: a bracket in the middle is text.
        val middle = render(logEntry(LogKind.OUTPUT, "a [W] b c"))
        assertTrue(middle.spanStyles.none { it.item.color == Term.profileColor(ProfileKind.WORK) })
    }

    /**
     * A script's line: unread gets a bullet, a title with a body is bold (a title alone isn't), a link
     * takes the link colour, and its source ends it.
     */
    @Test fun aScriptLine() {
        val plain = render(logEntry(LogKind.SCRIPT, "News", source = "hn"))
        assertEquals("News · hn", plain.text)
        assertEquals(Term.script, plain.at("News").color)
        assertEquals(Term.dim, plain.at("hn").color)
        assertEquals("unread", "• News · hn", render(logEntry(LogKind.SCRIPT, "News", source = "hn"), unread = true).text)
        assertEquals("without its source", "News", render(logEntry(LogKind.SCRIPT, "News", source = "hn"), source = false).text)
        val alone = render(logEntry(LogKind.SCRIPT, "News", ctx = LogContext.of(LogContext.TITLE to "1", LogContext.URL to "https://example.com/")))
        assertEquals(null, alone.at("News").fontWeight)
        assertEquals(Term.link, alone.at("News").color)
        val heading = render(logEntry(LogKind.SCRIPT, "News", ctx = LogContext.of(LogContext.TITLE to "1", LogContext.BODY to "Three new stories")))
        assertEquals(FontWeight.Bold, heading.at("News").fontWeight)
    }

    /** A notification's line ends with its app's icon, read out as the app's name. */
    @Test fun aNotificationEndsWithItsIcon() {
        val text = render(logEntry(LogKind.SCRIPT, "Message", source = "Chat"), icon = true)
        assertEquals("Message\u00A0Chat", text.text)
        assertTrue(text.getStringAnnotations(0, text.length).any { it.item == "notif-icon" })
    }

    /** A script's styles: bold in the text colour, italic, underline, code on a chip, and colours by role. */
    @Test fun stylesAndColours() {
        val styled = StyledText.wrap(StyledText.Style.BOLD, "big") + " " + StyledText.wrap(StyledText.Style.ITALIC, "aside") + " " +
            StyledText.wrap(StyledText.Style.UNDERLINE, "under") + " " + StyledText.wrap(StyledText.Style.CODE, "code") + " " +
            StyledText.colour(StyledText.Colour.BAD, "red")
        val text = render(logEntry(LogKind.SCRIPT, styled, ctx = LogContext.of(LogContext.TITLE to "1")))
        assertEquals("big aside under code red", text.text)
        assertEquals(Term.fg, text.at("big").color)
        assertEquals(FontStyle.Italic, text.at("aside").fontStyle)
        assertEquals(TextDecoration.Underline, text.at("under").textDecoration)
        assertEquals(Term.surface, text.at("code").background)
        assertEquals(Term.error, text.at("red").color)
    }

    /** A body is muted under its title; its list items get their bullet or number in the accent colour. */
    @Test fun aBodyWithAList() {
        val body = "Top stories\n" + StyledText.list(listOf("one", "two"), numbered = false) + "\n" + StyledText.list(listOf("first"), numbered = true)
        val text = render(logEntry(LogKind.SCRIPT, "News", ctx = LogContext.of(LogContext.TITLE to "1", LogContext.BODY to body)))
        assertTrue(text.text, "Top stories" in text.text && "• one" in text.text && "• two" in text.text && "1. first" in text.text)
        assertEquals(Term.dim, text.at("Top stories").color)
        assertEquals(Term.prompt, text.at("• ").color)
        assertEquals(Term.dim, text.at("one").color)
        // The title's paragraph, the plain line's, and one per item.
        assertTrue(text.paragraphStyles.size >= 4)
    }

    /**
     * Lines that line columns up use the terminal font, and wrap under their own indent. With the
     * phone's font for the terminal (`font.sans all`) the two fonts differ, so each line shows which.
     */
    @Test fun alignedLinesUseTheCodeFont() {
        withValue(Term::sans, Term.Sans.ALL) {
            assertNotEquals(Term.text, Term.code)
            val aligned = logEntry(LogKind.OUTPUT, "a  b", ctx = LogContext(LogContext.ALIGNED))
            val plain = logEntry(LogKind.OUTPUT, "a b")
            assertEquals(Term.code, aligned.style())
            assertEquals(Term.text, plain.style())
            assertEquals(indents.code, render(aligned).paragraphStyles[0].item.textIndent?.restLine)
            assertEquals(indents.text, render(plain).paragraphStyles[0].item.textIndent?.restLine)
        }
    }

    // ---- a widget's text --------------------------------------------------------------------

    /** A card draws weight, slant, lines and colour, and leaves plain pieces to the Text's own style. */
    @Test fun aWidgetsTextHasWeightSlantLinesAndColour() {
        val text = widgetText(
            "a " + StyledText.wrap(StyledText.Style.BOLD, "bold") + " " + StyledText.wrap(StyledText.Style.ITALIC, "slant") + " " +
                StyledText.wrap(StyledText.Style.UNDERLINE, "under") + " " + StyledText.wrap(StyledText.Style.STRIKE, "struck") + " " +
                StyledText.colour(StyledText.Colour.GOOD, "green") + " " +
                StyledText.colour(StyledText.Colour.BAD, StyledText.wrap(StyledText.Style.BOLD, StyledText.wrap(StyledText.Style.UNDERLINE, StyledText.wrap(StyledText.Style.STRIKE, "all")))),
        )
        assertEquals("a bold slant under struck green all", text.text)
        assertEquals(FontWeight.Bold, text.at("bold").fontWeight)
        assertEquals(FontStyle.Italic, text.at("slant").fontStyle)
        assertEquals(TextDecoration.Underline, text.at("under").textDecoration)
        assertEquals(TextDecoration.LineThrough, text.at("struck").textDecoration)
        assertEquals(Term.palette.good, text.at("green").color)
        assertEquals(Term.error, text.at("all").color)
        assertEquals(FontWeight.Bold, text.at("all").fontWeight)
        assertEquals(TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)), text.at("all").textDecoration)
        // Six styled pieces, and nothing over the plain ones: they keep the colour of their Text.
        assertEquals(6, text.spanStyles.size)
        assertTrue(text.spanStyles.none { it.start == 0 })
        // Plain text comes back plain, and lines stay lines.
        assertEquals(AnnotatedString("no styles"), widgetText("no styles"))
        val lines = widgetText(StyledText.wrap(StyledText.Style.BOLD, "one\ntwo"))
        assertEquals("one\ntwo", lines.text)
        assertEquals(2, lines.spanStyles.count { it.item.fontWeight == FontWeight.Bold })
    }

    /** A card's text is never a link, and what only the log draws shows as its words. */
    @Test fun aWidgetsTextIsNeverALink() {
        val text = widgetText(
            "see " + StyledText.link("https://example.com/x", StyledText.wrap(StyledText.Style.BOLD, "the page")) + " " +
                StyledText.tag(StyledText.Colour.BAD, "LIVE") + " " + StyledText.wrap(StyledText.Style.CODE, "v1.2"),
        )
        assertEquals("see the page LIVE v1.2", text.text)
        assertEquals(0, text.getLinkAnnotations(0, text.length).size)
        assertEquals(0, text.getStringAnnotations(0, text.length).size)
        assertEquals(FontWeight.Bold, text.at("the page").fontWeight)
        // The link's own look is not there either: no underline, and no link colour.
        assertEquals(null, text.at("the page").textDecoration)
        assertNotEquals(Term.link, text.at("the page").color)
        // A tag keeps its colour, and has no chip. Code has no shade and no font of its own.
        assertEquals(Term.error, text.at("LIVE").color)
        assertEquals(null, text.at("LIVE").fontWeight)
        assertTrue(text.spanStyles.none { it.item.background != androidx.compose.ui.graphics.Color.Unspecified || it.item.fontFamily != null })
        // A table, a bar and a list show as the words that plain text has.
        val blocks = StyledText.table(listOf(listOf("a", "b")), header = false) + "\n" + StyledText.meter(0.0, 0.5, null, "Overs", "", "40/50") + "\n" + StyledText.list(listOf("one"), numbered = false)
        assertEquals("a\tb\nOvers\t40/50\n• one", widgetText(blocks).text)
        assertEquals(0, widgetText(blocks).spanStyles.size)
    }
}
