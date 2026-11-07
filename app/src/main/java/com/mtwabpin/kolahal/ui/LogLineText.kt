package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.StyledText

/** Marks every place in [text] where one of [words] appears, ignoring case. Used to show log search results. */
fun markMatches(text: AnnotatedString, words: List<String>): AnnotatedString {
    if (words.isEmpty()) return text
    val mark = SpanStyle(background = Term.prompt.copy(alpha = 0.35f))
    return AnnotatedString.Builder(text).apply {
        // Skip empty words: an empty word is found at the same place forever.
        for (w in words) {
            if (w.isEmpty()) continue
            var at = text.text.indexOf(w, ignoreCase = true)
            while (at >= 0) {
                addStyle(mark, at, at + w.length)
                at = text.text.indexOf(w, at + w.length, ignoreCase = true)
            }
        }
    }.toAnnotatedString()
}

/** A profile tag at the end of a line's first row, as in `Launched Maps [W]` or `Updated Maps [W] ↻`. */
private val profileTag = Regex(" \\[([PWSC])](?= \\S*$|$)")

/** Appends [text] in [color]. A profile tag at the end gets its profile colour. */
private fun AnnotatedString.Builder.appendTagged(text: String, color: Color) {
    val tag = profileTag.find(text)
    if (tag == null) {
        withStyle(SpanStyle(color = color)) { append(text) }
        return
    }
    val kind = ProfileKind.entries.first { it.tag == tag.groupValues[1] }
    withStyle(SpanStyle(color = color)) { append(text.substring(0, tag.range.first)) }
    withStyle(SpanStyle(color = Term.profileColor(kind), fontWeight = FontWeight.Bold)) { append(tag.value) }
    withStyle(SpanStyle(color = color)) { append(text.substring(tag.range.last + 1)) }
}

private const val NOTIF_ICON = "notif-icon"

/** The status bar icon at the end of a notification line. It has the same muted tint as the name it replaces. */
internal fun notifIconContent(key: String) = mapOf(
    NOTIF_ICON to InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) { NotifIcon(key) },
)

/**
 * Sizes of a log line:
 * - [TEXT_LINE]: text height in em (the line height of [Term.text]).
 * - [LINE_PADDING]: padding above and below the text, in dp.
 */
internal const val TEXT_LINE = 1.36f
internal const val LINE_PADDING = 3

/** For short lines (`log.line-height` below the text's own height): removes the space above the first line and below the last. */
internal fun TextStyle.dense(on: Boolean) =
    if (on) copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)) else this

/** The terminal text style. A line that aligns columns ([LogContext.MONO]) always uses the terminal font. */
internal fun LogEntry.style() = if (ctx.values.containsKey(LogContext.MONO)) Term.code else Term.text

/** Indents for wrapped and detail lines: the width of `$ ` in the log's font and in the code font, and the width of `• `. */
@androidx.compose.runtime.Immutable
data class LineIndents(val text: TextUnit, val code: TextUnit, val bullet: TextUnit)

/**
 * Styles one log line. Wrapped lines are indented past the prefix (`$ `, `• `), so each item
 * reads as one block. The widths are measured ([LineIndents]), so this works in any font.
 * - [icon]: end a script line with its notification icon instead of `· app`.
 * - [source]: show the source at all (true for the first line of a run).
 */
internal fun renderLine(entry: LogEntry, indents: LineIndents, unread: Boolean, icon: Boolean = false, source: Boolean = true) = buildAnnotatedString {
    val indent = when (entry.kind) {
        LogKind.SCRIPT -> if (unread) indents.bullet else 0.sp
        else -> if (entry.ctx.values.containsKey(LogContext.MONO)) indents.code else indents.text
    }
    withStyle(ParagraphStyle(textIndent = TextIndent(restLine = indent))) {
        when (entry.kind) {
            LogKind.COMMAND -> {
                withStyle(SpanStyle(color = Term.prompt)) { append("$ ") }
                withStyle(SpanStyle(color = Term.fg, fontWeight = FontWeight.Bold)) { append(entry.text) }
            }
            LogKind.ERROR -> withStyle(SpanStyle(color = Term.error)) { append(StyledText.plain(entry.text)) }
            LogKind.INFO -> {
                // Lines after the first are details, such as a package name under an app event. They are dimmed below.
                val first = entry.text.substringBefore('\n')
                appendTagged(first, Term.info)
            }
            LogKind.SCRIPT -> {
                if (unread) withStyle(SpanStyle(color = Term.prompt, fontWeight = FontWeight.Bold)) { append("• ") }
                // A title with a body is a heading: bold, with the body muted below it. A title on its own
                // (one HN story, one price) is in normal weight, so a list of them isn't a wall of bold.
                val title = entry.ctx.values.containsKey(LogContext.TITLE) && entry.ctx.values.containsKey(LogContext.BODY)
                // A heading is already bold, so its bold parts stand out in the text colour instead.
                appendStyled(
                    entry.text,
                    SpanStyle(color = if (entry.ctx.url != null) Term.link else Term.script, fontWeight = if (title) FontWeight.Bold else null),
                    strong = Term.fg,
                )
                // The source goes at the end, muted, like other indicators (`… machine. · wikipedia`).
                // This way wrapped lines use the full width. A no-break space keeps the dot with the name.
                entry.source?.takeIf { source }?.let { src ->
                    withStyle(SpanStyle(color = Term.dim)) {
                        // Screen readers read the notification icon as the app name. The no-break space keeps it with the last word.
                        if (icon) {
                            append("\u00A0")
                            appendInlineContent(NOTIF_ICON, src)
                        } else {
                            append(" ·\u00A0$src")
                        }
                    }
                }
            }
            LogKind.OUTPUT -> {
                val first = entry.text.substringBefore('\n')
                val color = if (entry.ctx.url != null) Term.link else Term.fg
                // A script's result can have styles ([StyledText]). Command output never has them.
                if (StyledText.has(first)) appendStyled(first, SpanStyle(color = color)) else appendTagged(first, color)
            }
        }
        Unit
    }
    // Lines after the first (dimmed details) are separate paragraphs. Layout indents them to line up
    // with the text after the prefix. Padding with spaces would only line up in a monospace font.
    if ((entry.kind == LogKind.INFO || entry.kind == LogKind.OUTPUT) && '\n' in entry.text) {
        withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = indent, restLine = indent))) {
            appendStyled(entry.text.substringAfter('\n'), SpanStyle(color = Term.dim), strong = Term.fg)
        }
    }
    // A script line's body, below its title and aligned with it. A table and what follows it are
    // drawn under the text ([TableBody]), so only the part above the table is text here.
    val body = entry.ctx.body?.let { StyledText.splitAtTable(it).first }
    if (entry.kind == LogKind.SCRIPT && !body.isNullOrEmpty()) appendBody(body, indent, indents.bullet)
}

/**
 * Appends [text] with its [StyledText] styles on top of [base].
 * - Bold text also uses the colour [strong], if given. So bold in a muted body or a bold title
 *   uses the text colour.
 * - Code shows on a shaded background.
 * - A list item shows its bullet or number in the accent colour.
 */
private fun AnnotatedString.Builder.appendStyled(text: String, base: SpanStyle, strong: Color? = null) {
    if (!StyledText.has(text)) {
        withStyle(base) { append(text) }
        return
    }
    StyledText.lines(text).forEachIndexed { i, line ->
        if (i > 0) append('\n')
        if (line.level > 0) withStyle(base.merge(SpanStyle(color = Term.prompt))) { append("  ".repeat(line.level - 1) + line.prefix + " ") }
        appendSpans(line.spans, base, strong)
    }
}

internal fun AnnotatedString.Builder.appendSpans(spans: List<StyledText.Span>, base: SpanStyle, strong: Color?) {
    for ((text, styles, colour) in spans) {
        if (styles.isEmpty() && colour == null) withStyle(base) { append(text) }
        else withStyle(base.merge(styleOf(styles, strong)).merge(SpanStyle(color = colourOf(colour)))) { append(text) }
    }
}

/** Maps a script colour role to a colour of the current theme (its dark or light version). */
internal fun colourOf(colour: StyledText.Colour?): Color = when (colour) {
    null -> Color.Unspecified
    StyledText.Colour.ACCENT -> Term.prompt
    StyledText.Colour.LINK -> Term.link
    StyledText.Colour.INFO -> Term.info
    StyledText.Colour.MUTED -> Term.dim
    StyledText.Colour.TEXT -> Term.fg
    StyledText.Colour.GOOD -> Term.palette.good
    StyledText.Colour.WARN -> Term.palette.warn
    StyledText.Colour.BAD -> Term.error
}

private fun styleOf(styles: Set<StyledText.Style>, strong: Color?): SpanStyle {
    val decorations = listOfNotNull(
        TextDecoration.Underline.takeIf { StyledText.Style.UNDERLINE in styles },
        TextDecoration.LineThrough.takeIf { StyledText.Style.STRIKE in styles },
    )
    val bold = StyledText.Style.BOLD in styles
    val code = StyledText.Style.CODE in styles
    return SpanStyle(
        color = if (bold && strong != null) strong else Color.Unspecified,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (StyledText.Style.ITALIC in styles) FontStyle.Italic else null,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        // The terminal font is already monospace. This keeps code monospace when `font.sans` is on.
        fontFamily = if (code) Term.code.fontFamily else null,
        background = if (code) Term.surface else Color.Unspecified,
    )
}

/**
 * Appends a script line's body, muted, aligned under its title at [indent]. Each list item is a
 * separate paragraph, and its wrapped lines start under the item's text. [bullet] is the width of "• ".
 */
internal fun AnnotatedString.Builder.appendBody(body: String, indent: TextUnit, bullet: TextUnit) {
    val base = SpanStyle(color = Term.dim)
    if (!StyledText.has(body)) {
        withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = indent, restLine = indent))) { withStyle(base) { append(body) } }
        return
    }
    val lines = StyledText.lines(body)
    val char = bullet.value / 2 // the log's text lines up in characters: "• " is two
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.level == 0) {
            // Plain lines that follow each other share one paragraph.
            val run = lines.drop(i).takeWhile { it.level == 0 }
            withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = indent, restLine = indent))) {
                run.forEachIndexed { k, l ->
                    if (k > 0) append('\n')
                    appendSpans(l.spans, base, Term.fg)
                }
            }
            i += run.size
            continue
        }
        val start = indent.value + char * 2 * (line.level - 1)
        val prefix = "${line.prefix} "
        withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = start.sp, restLine = (start + char * prefix.length).sp))) {
            withStyle(SpanStyle(color = Term.prompt)) { append(prefix) }
            appendSpans(line.spans, base, Term.fg)
        }
        i++
    }
}
