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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How each kind of log line is drawn: its prefix, colours and weight, its details and body under it. */
class LogLineTextTest {
    private val indents = LineIndents(text = 10.sp, code = 12.sp, bullet = 8.sp)

    private fun line(kind: LogKind, text: String, source: String? = null, ctx: LogContext = LogContext.EMPTY) =
        LogEntry(1, 0, kind, source, text, ctx)

    private fun render(entry: LogEntry, unread: Boolean = false, icon: Boolean = false, source: Boolean = true) =
        renderLine(entry, indents, unread, icon, source)

    /** The style over [part] of [text]: what the spans that cover all of it say, merged. */
    private fun AnnotatedString.at(part: String) = spanStyles.filter { r ->
        val i = text.indexOf(part)
        i >= 0 && r.start <= i && r.end >= i + part.length
    }.map { it.item }.reduce { a, b -> a.merge(b) }

    @Test fun aCommandIsItsPromptAndItsLineInBold() {
        val text = render(line(LogKind.COMMAND, "torch on"))
        assertEquals("$ torch on", text.text)
        assertEquals(Term.prompt, text.at("$ ").color)
        assertEquals(Term.fg, text.at("torch on").color)
        assertEquals(FontWeight.Bold, text.at("torch on").fontWeight)
    }

    /** An error shows in the error colour, its styles taken off. */
    @Test fun anErrorIsPlainInItsColour() {
        val text = render(line(LogKind.ERROR, "bad " + StyledText.wrap(StyledText.Style.BOLD, "thing")))
        assertEquals("bad thing", text.text)
        assertEquals(Term.error, text.at("bad thing").color)
        assertTrue(text.spanStyles.none { it.item.fontWeight == FontWeight.Bold })
    }

    /** An info line's first line in its colour, and the lines after it dimmed, as details under it. */
    @Test fun anInfoLinesDetailsAreDimmed() {
        val text = render(line(LogKind.INFO, "Installed Clock\ncom.example.clock"))
        assertTrue(text.text, text.text.startsWith("Installed Clock") && text.text.endsWith("com.example.clock"))
        assertEquals(Term.info, text.at("Installed Clock").color)
        assertEquals(Term.dim, text.at("com.example.clock").color)
        // Two paragraphs: the details are indented as a block of their own.
        assertEquals(2, text.paragraphStyles.size)
    }

    /** A trailing profile tag takes its profile's colour, in bold. */
    @Test fun aProfileTagIsInItsColour() {
        val text = render(line(LogKind.OUTPUT, "Chrome [W]"))
        assertEquals(Term.profileColor(ProfileKind.WORK), text.at("[W]").color)
        assertEquals(FontWeight.Bold, text.at("[W]").fontWeight)
        assertEquals(Term.fg, text.at("Chrome").color)
        // Only at the end: a bracket in the middle is text.
        val middle = render(line(LogKind.OUTPUT, "a [W] b c"))
        assertTrue(middle.spanStyles.none { it.item.color == Term.profileColor(ProfileKind.WORK) })
    }

    /** A script's line: unread gets a bullet, a title is bold, a link takes the link colour, and its source ends it. */
    @Test fun aScriptLine() {
        val plain = render(line(LogKind.SCRIPT, "News", source = "hn"))
        assertEquals("News · hn", plain.text)
        assertEquals(Term.script, plain.at("News").color)
        assertEquals(Term.dim, plain.at("hn").color)
        assertEquals("unread", "• News · hn", render(line(LogKind.SCRIPT, "News", source = "hn"), unread = true).text)
        assertEquals("without its source", "News", render(line(LogKind.SCRIPT, "News", source = "hn"), source = false).text)
        val titled = render(line(LogKind.SCRIPT, "News", ctx = LogContext.of(LogContext.TITLE to "1", LogContext.URL to "https://example.com/")))
        assertEquals(FontWeight.Bold, titled.at("News").fontWeight)
        assertEquals(Term.link, titled.at("News").color)
    }

    /** A notification's line ends with its app's icon, read out as the app's name. */
    @Test fun aNotificationEndsWithItsIcon() {
        val text = render(line(LogKind.SCRIPT, "Message", source = "Chat"), icon = true)
        assertEquals("Message\u00A0Chat", text.text)
        assertTrue(text.getStringAnnotations(0, text.length).any { it.item == "notif-icon" })
    }

    /** A script's styles: bold in the text colour, italic, underline, code on a chip, and colours by role. */
    @Test fun stylesAndColours() {
        val styled = StyledText.wrap(StyledText.Style.BOLD, "big") + " " + StyledText.wrap(StyledText.Style.ITALIC, "aside") + " " +
            StyledText.wrap(StyledText.Style.UNDERLINE, "under") + " " + StyledText.wrap(StyledText.Style.CODE, "code") + " " +
            StyledText.colour(StyledText.Colour.BAD, "red")
        val text = render(line(LogKind.SCRIPT, styled, ctx = LogContext.of(LogContext.TITLE to "1")))
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
        val text = render(line(LogKind.SCRIPT, "News", ctx = LogContext.of(LogContext.TITLE to "1", LogContext.BODY to body)))
        assertTrue(text.text, "Top stories" in text.text && "• one" in text.text && "• two" in text.text && "1. first" in text.text)
        assertEquals(Term.dim, text.at("Top stories").color)
        assertEquals(Term.prompt, text.at("• ").color)
        assertEquals(Term.dim, text.at("one").color)
        // The title's paragraph, the plain line's, and one per item.
        assertTrue(text.paragraphStyles.size >= 4)
    }

    /** Lines that line columns up use the terminal font, and wrap under their own indent. */
    @Test fun alignedLinesUseTheCodeFont() {
        val entry = line(LogKind.OUTPUT, "a  b", ctx = LogContext(LogContext.ALIGNED))
        assertEquals(Term.code, entry.style())
        assertEquals(Term.text, line(LogKind.OUTPUT, "a b").style())
    }
}
