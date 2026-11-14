package com.mtwabpin.kolahal.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetJson
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetParser
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/**
 * Styled text on a card: the few fields that keep their styles are drawn with them, as plain
 * words for a screen reader, and never as a link.
 */
@RunWith(AndroidJUnit4::class)
class StyledWidgetTextTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun bold(s: String) = StyledText.wrap(StyledText.Style.BOLD, s)
    private fun italic(s: String) = StyledText.wrap(StyledText.Style.ITALIC, s)
    private fun good(s: String) = StyledText.colour(StyledText.Colour.GOOD, s)

    /** [spec]'s card, [width] by [height], with the description a script's card has. */
    private fun show(spec: WidgetSpec, width: Dp = 320.dp, height: Dp = 200.dp) {
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    Box(Modifier.width(width)) {
                        WidgetCard(
                            title = spec.base.title ?: "card",
                            height = height,
                            spec = spec,
                            note = null,
                            description = WidgetWords.description(spec.base.title, spec, 0L, ZoneId.systemDefault()),
                        )
                    }
                }
            }
        }
    }

    private val hasWords = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
    private val hasDescription = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    /** Every text the card draws, with its styles. */
    private fun drawn(): List<AnnotatedString> = compose.onAllNodes(hasWords, useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.Text] }

    /** Waits for the text that reads [words], and returns it with its styles. */
    private fun text(words: String): AnnotatedString {
        compose.waitFor("the card doesn't draw “$words”, it draws ${drawn().map { it.text }}") { drawn().any { it.text == words } }
        return drawn().first { it.text == words }
    }

    private fun said(): List<String> = compose.onAllNodes(hasDescription, useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.ContentDescription] }

    /** The description as `widget.show` checks it and as the card reads it back from what is stored. */
    private fun stored(kind: WidgetKind, vararg fields: Pair<String, Any?>): WidgetSpec =
        WidgetJson.decode(WidgetJson.encode(WidgetParser.parse(mapOf(*fields), kind, { it }, { it })))!!

    /** A text widget: bold and a colour in its lines, a slant in its caption. The words are plain for a screen reader. */
    @Test fun aTextCardDrawsItsStyles() {
        show(stored(WidgetKind.TEXT, "title" to "Quote", "text" to "Be " + bold("kind") + " to " + good("all") + ".", "caption" to italic("Anon")))
        val lines = text("Be kind to all.")
        assertEquals(listOf(3 to 7, 11 to 14), lines.spanStyles.map { it.start to it.end })
        assertEquals(FontWeight.Bold, lines.spanStyles[0].item.fontWeight)
        assertEquals(colourOf(StyledText.Colour.GOOD), lines.spanStyles[1].item.color)
        assertEquals(FontStyle.Italic, text("Anon").spanStyles.single().item.fontStyle)
        // The title is plain, and so is what a screen reader hears.
        assertEquals(0, text("Quote").spanStyles.size)
        assertEquals(listOf("Quote: Be kind to all., Anon"), said())
    }

    /** A list's row: its title, its second line and the text at its end keep their styles. */
    @Test fun aRowDrawsItsStyles() {
        show(
            stored(
                WidgetKind.LIST, "title" to "Prices",
                "items" to listOf(mapOf("title" to bold("BTC"), "detail" to good("up 1.2%"), "trailing" to StyledText.wrap(StyledText.Style.STRIKE, "67,000") + " 67,120")),
            ),
        )
        assertEquals(FontWeight.Bold, text("BTC").spanStyles.single().item.fontWeight)
        assertEquals(colourOf(StyledText.Colour.GOOD), text("up 1.2%").spanStyles.single().item.color)
        val end = text("67,000 67,120")
        assertEquals(TextDecoration.LineThrough, end.spanStyles.single().item.textDecoration)
        assertEquals(0 to 6, end.spanStyles.single().let { it.start to it.end })
        assertEquals(listOf("Prices: BTC, up 1.2%, 67,000 67,120"), said())
    }

    /** A stat's label keeps its styles. Its value, like every other text, is plain. */
    @Test fun aStatsLabelDrawsItsStyles() {
        show(stored(WidgetKind.STAT, "title" to "Weather", "value" to bold("24"), "unit" to "°C", "label" to good("Sunny") + " today"), height = 220.dp)
        assertEquals(colourOf(StyledText.Colour.GOOD), text("Sunny today").spanStyles.single().item.color)
        assertEquals(0, text("24").spanStyles.size)
        assertEquals(listOf("Weather: 24°C, Sunny today"), said())
    }

    /** A link, a tag and a code chip in a styled field show as their words. Nothing on the card opens a link. */
    @Test fun aCardsTextIsNeverALink() {
        val link = StyledText.link("https://example.com/x", bold("the page"))
        show(stored(WidgetKind.TEXT, "title" to "Note", "text" to "see $link " + StyledText.tag(StyledText.Colour.BAD, "LIVE"), "caption" to StyledText.wrap(StyledText.Style.CODE, "v1.2")))
        val lines = text("see the page LIVE")
        assertEquals(0, lines.getLinkAnnotations(0, lines.length).size)
        assertEquals(0, lines.getStringAnnotations(0, lines.length).size)
        assertEquals(FontWeight.Bold, lines.spanStyles.first().item.fontWeight)
        assertEquals(0, text("v1.2").spanStyles.size)
        assertTrue(drawn().none { it.getLinkAnnotations(0, it.length).isNotEmpty() })
        assertEquals(listOf("Note: see the page LIVE, v1.2"), said())
    }
}
