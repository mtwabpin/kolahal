package com.mtwabpin.kolahal.ui.assistant

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.Markdown

/**
 * An answer's simple Markdown as styled text, read the way `md.log` reads it ([Markdown]): bold, italic,
 * code, strike, list items, and `#` headings as bold lines. A link shows its text only: nothing in an
 * answer opens anything. The app's own style markers in [text] are dropped first.
 * [block]: list items and headings too (an answer); without, styles within lines only (a label).
 */
internal fun answerText(text: String, block: Boolean, code: Color): AnnotatedString {
    val clean = StyledText.strip(text)
    val marked = if (!block) Markdown.inline(clean) else Markdown.block(
        clean.lines().joinToString("\n") { line ->
            HEADING.find(line)?.let { "**${it.groupValues[1].trim()}**" } ?: line
        }
    )
    val lines = StyledText.lines(marked)
    return buildAnnotatedString {
        lines.forEachIndexed { i, line ->
            if (i > 0) append('\n')
            if (line.level > 0) append("  ".repeat(line.level - 1) + line.prefix + " ")
            for (span in line.spans) {
                withStyle(spanStyle(span.styles, code)) { append(span.text) }
            }
        }
    }
}

private val HEADING = Regex("^#{1,6}\\s+(.+)$")

private fun spanStyle(styles: Set<StyledText.Style>, code: Color): SpanStyle {
    var s = SpanStyle()
    if (StyledText.Style.BOLD in styles) s = s.merge(SpanStyle(fontWeight = FontWeight.SemiBold))
    if (StyledText.Style.ITALIC in styles) s = s.merge(SpanStyle(fontStyle = FontStyle.Italic))
    val lines = listOfNotNull(
        TextDecoration.LineThrough.takeIf { StyledText.Style.STRIKE in styles },
        TextDecoration.Underline.takeIf { StyledText.Style.UNDERLINE in styles },
    )
    if (lines.isNotEmpty()) s = s.merge(SpanStyle(textDecoration = TextDecoration.combine(lines)))
    if (StyledText.Style.CODE in styles) s = s.merge(SpanStyle(fontFamily = FontFamily.Monospace, color = code))
    return s
}
