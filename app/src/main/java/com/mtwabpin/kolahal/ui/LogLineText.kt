/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
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
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.LineModel
import com.mtwabpin.kolahal.terminal.Para
import com.mtwabpin.kolahal.terminal.Piece
import com.mtwabpin.kolahal.terminal.markRanges

/** Marks every place in [text] where one of [words] appears, ignoring case ([markRanges]). Used to show log search results. */
fun markMatches(text: AnnotatedString, words: List<String>): AnnotatedString {
    if (words.isEmpty()) return text
    val mark = SpanStyle(background = Term.prompt.copy(alpha = 0.35f))
    return AnnotatedString.Builder(text).apply {
        for (r in markRanges(text.text, words)) addStyle(mark, r.first, r.last + 1)
    }.toAnnotatedString()
}

/** The color of [ink] in the current theme. Null: no color of its own. */
internal fun inkColor(ink: Ink?): Color = when (ink) {
    null -> Color.Unspecified
    Ink.BG -> Term.bg
    Ink.SURFACE -> Term.surface
    Ink.FG -> Term.fg
    Ink.DIM -> Term.dim
    Ink.PROMPT -> Term.prompt
    Ink.ERROR -> Term.error
    Ink.INFO -> Term.info
    Ink.LINK -> Term.link
    Ink.SCRIPT -> Term.script
    Ink.PERSONAL -> Term.palette.personal
    Ink.WORK -> Term.palette.work
    Ink.PRIVATE -> Term.palette.private
    Ink.GOOD -> Term.palette.good
    Ink.WARN -> Term.palette.warn
}

private const val NOTIF_ICON = "notif-icon"

/** The status bar icon at the end of a notification line. It has the same muted tint as the name it replaces. */
internal fun notifIconContent(key: String) = mapOf(
    NOTIF_ICON to InlineTextContent(
        Placeholder(
            1.15.em,
            1.15.em,
            PlaceholderVerticalAlign.TextCenter
        )
    ) { NotifIcon(key) },
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
    if (on) copy(
        lineHeightStyle = LineHeightStyle(
            LineHeightStyle.Alignment.Center,
            LineHeightStyle.Trim.Both
        )
    ) else this

/** The terminal text style. A line that aligns columns ([LogContext.MONO]) always uses the terminal font. */
internal fun LogEntry.style() = lineTextStyle(ctx.values.containsKey(LogContext.MONO))

/** The terminal text style, or with [mono] ([LineModel.mono]) the terminal font always. */
internal fun lineTextStyle(mono: Boolean) = if (mono) Term.code else Term.text

/** Indents for wrapped and detail lines: the width of `$ ` in the log's font and in the code font, and the width of `• `. */
@androidx.compose.runtime.Immutable
data class LineIndents(val text: TextUnit, val code: TextUnit, val bullet: TextUnit)

/**
 * The [LineIndents] of the log's font, as the log measures them: wrapped and detail lines are
 * indented by the width of `$ ` (or `• `). Each is measured with a letter after it, because a
 * trailing space alone may not count towards the width.
 */
@androidx.compose.runtime.Composable
internal fun rememberLineIndents(): LineIndents {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    return androidx.compose.runtime.remember(Term.fontSize, Term.fontFamily, Term.sans, density) {
        fun width(prefix: String, style: TextStyle) = with(density) {
            (measurer.measure("${prefix}0", style).size.width - measurer.measure(
                "0",
                style
            ).size.width).toSp()
        }
        LineIndents(
            text = width("$ ", Term.text),
            code = width("$ ", Term.code),
            bullet = width("• ", Term.text)
        )
    }
}

/**
 * Styles a log line's [words]. Wrapped lines are indented past the prefix (`$ `, `• `), so each
 * item reads as one block. The widths are measured ([LineIndents]), so this works in any font.
 * - The title is one paragraph: an unread script line's bullet, the title, and the [source] at
 *   its end: `· app`, or a notification's icon.
 * - Details (an output's lines after the first) and a script line's body are muted paragraphs
 *   under it. Tables and bars, and what follows them, are drawn under the text ([RichBody]).
 */
internal fun renderLine(
    words: LineModel.Body.Words,
    indents: LineIndents,
    source: LineModel.Source? = null,
) = buildAnnotatedString {
    val indent = when (words.indent) {
        LineModel.Indent.NONE -> 0.sp
        LineModel.Indent.BULLET -> indents.bullet
        LineModel.Indent.PROMPT -> indents.text
        LineModel.Indent.CODE -> indents.code
    }
    // A heading is already bold, so its bold parts stand out in the text color instead.
    val base = SpanStyle(
        color = inkColor(words.ink),
        fontWeight = if (words.heading) FontWeight.Bold else null
    )
    withStyle(ParagraphStyle(textIndent = TextIndent(restLine = indent))) {
        if (words.bullet) withStyle(
            SpanStyle(
                color = Term.prompt,
                fontWeight = FontWeight.Bold
            )
        ) { append("• ") }
        appendLines(words.paras.filter { it.role == Para.Role.TITLE }, base)
        // The source goes at the end, muted, like other indicators (`… machine. · wikipedia`).
        // This way wrapped lines use the full width. A no-break space keeps the dot with the name.
        if (source != null) {
            val src = source
            withStyle(SpanStyle(color = Term.dim)) {
                when (src) {
                    // Screen readers read the notification icon as the app name. The no-break space keeps it with the last word.
                    is LineModel.Source.Icon -> {
                        append(" ")
                        appendInlineContent(NOTIF_ICON, src.name)
                    }

                    is LineModel.Source.Name -> append(" · ${src.name}")
                }
            }
        }
    }
    // Lines after the first (dimmed details) are separate paragraphs. Layout indents them to line up
    // with the text after the prefix. Padding with spaces would only line up in a monospace font.
    val details = words.paras.filter { it.role == Para.Role.DETAIL }
    if (details.isNotEmpty()) {
        withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = indent, restLine = indent))) {
            appendLines(details, SpanStyle(color = Term.dim))
        }
    }
    // A script line's body, below its title and aligned with it.
    val body = words.paras.filter { it.role == Para.Role.BODY }
    if (body.isNotEmpty()) appendBody(body, indent, indents.bullet)
}

/**
 * Appends [paras], a line break between each, on top of [base]. A list item shows its bullet or
 * number in the accent color, further in at the second level.
 */
private fun AnnotatedString.Builder.appendLines(paras: List<Para>, base: SpanStyle) {
    paras.forEachIndexed { i, p ->
        if (i > 0) append('\n')
        if (p.level > 0) withStyle(base.merge(SpanStyle(color = Term.prompt))) {
            append("  ".repeat(p.level - 1) + p.prefix + " ")
        }
        appendPieces(p.pieces, base)
    }
}

/**
 * Appends [pieces] on top of [base], each with its styles and its ink ([Piece.ink]): a tag on its
 * chip, a link that opens.
 */
private fun AnnotatedString.Builder.appendPieces(pieces: List<Piece>, base: SpanStyle) {
    for (p in pieces) {
        val style =
            if (p.styles.isEmpty() && p.ink == null) base else base.merge(styleOf(p.styles, null))
                .merge(SpanStyle(color = inkColor(p.ink)))
        val tag = StyledText.Style.TAG in p.styles
        val shown = if (!tag) style else style.merge(
            SpanStyle(color = inkColor(p.ink), fontWeight = FontWeight.Bold, fontSize = TAG_SIZE.em)
        )
        appendShown(p.text, shown, p.url, tag)
    }
}

/**
 * Appends [text] in [shown]. A [url] makes it a link that opens; a [tag] is marked for its chip
 * ([TAG_CHIP]).
 */
private fun AnnotatedString.Builder.appendShown(
    text: String,
    shown: SpanStyle,
    url: String?,
    tag: Boolean
) {
    val start = length
    if (url != null) {
        // A link in the text (fmt.link): a tap on it opens its address, through home's LocalUriHandler.
        // Its look is a plain style on the text, not the link's own styles: those cost each link an
        // effect, a coroutine and a second composition of the line.
        withLink(LinkAnnotation.Url(url)) {
            withStyle(
                shown.merge(
                    SpanStyle(
                        color = Term.link,
                        textDecoration = TextDecoration.Underline
                    )
                )
            ) { append(text) }
        }
    } else withStyle(shown) { append(text) }
    if (tag) addStringAnnotation(
        TAG_CHIP,
        (shown.color.takeIf { it.isSpecified } ?: Term.prompt).toArgb().toString(),
        start,
        length)
}

/**
 * Appends [text] with its [StyledText] styles on top of [base].
 * - Bold text also uses the color [strong], if given. So bold in a muted body or a bold title
 *   uses the text color.
 * - Code shows on a shaded background.
 * - A list item shows its bullet or number in the accent color.
 */
private fun AnnotatedString.Builder.appendStyled(
    text: String,
    base: SpanStyle,
    strong: Color? = null
) {
    if (!StyledText.has(text)) {
        withStyle(base) { append(text) }
        return
    }
    StyledText.lines(text).forEachIndexed { i, line ->
        if (i > 0) append('\n')
        if (line.level > 0) withStyle(base.merge(SpanStyle(color = Term.prompt))) {
            append(
                "  ".repeat(
                    line.level - 1
                ) + line.prefix + " "
            )
        }
        appendSpans(line.spans, base, strong)
    }
}

internal fun AnnotatedString.Builder.appendSpans(
    spans: List<StyledText.Span>,
    base: SpanStyle,
    strong: Color?
) {
    for ((text, styles, color, url) in spans) {
        val style =
            if (styles.isEmpty() && color == null) base else base.merge(styleOf(styles, strong))
                .merge(SpanStyle(color = colorOf(color)))
        // A tag: in its color (the accent color without one), bold and a little smaller, on a
        // rounded chip that RichText draws behind it.
        val tag = StyledText.Style.TAG in styles
        val shown = if (!tag) style else style.merge(
            SpanStyle(
                color = colorOf(
                    color ?: StyledText.ColorRole.ACCENT
                ), fontWeight = FontWeight.Bold, fontSize = TAG_SIZE.em
            )
        )
        appendShown(text, shown, url, tag)
    }
}

/** Maps a script color role to a color of the current theme (its dark or light version). */
internal fun colorOf(color: StyledText.ColorRole?): Color = when (color) {
    null -> Color.Unspecified
    StyledText.ColorRole.ACCENT -> Term.prompt
    StyledText.ColorRole.LINK -> Term.link
    StyledText.ColorRole.INFO -> Term.info
    StyledText.ColorRole.MUTED -> Term.dim
    StyledText.ColorRole.TEXT -> Term.fg
    StyledText.ColorRole.GOOD -> Term.palette.good
    StyledText.ColorRole.WARN -> Term.palette.warn
    StyledText.ColorRole.BAD -> Term.error
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
 * Appends a script line's body ([Para.Role.BODY]), muted, aligned under its title at [indent].
 * Each list item is a separate paragraph, and its wrapped lines start under the item's text.
 * [bullet] is the width of "• ".
 */
internal fun AnnotatedString.Builder.appendBody(body: List<Para>, indent: TextUnit, bullet: TextUnit) {
    val base = SpanStyle(color = Term.dim)
    val char = bullet.value / 2 // the log's text lines up in characters: "• " is two
    var i = 0
    while (i < body.size) {
        val line = body[i]
        if (line.level == 0) {
            // Plain lines that follow each other share one paragraph. So does a body with no styles.
            val run = body.drop(i).takeWhile { it.level == 0 }
            withStyle(
                ParagraphStyle(
                    textIndent = TextIndent(
                        firstLine = indent,
                        restLine = indent
                    )
                )
            ) {
                run.forEachIndexed { k, l ->
                    if (k > 0) append('\n')
                    appendPieces(l.pieces, base)
                }
            }
            i += run.size
            continue
        }
        val start = indent.value + char * 2 * (line.level - 1)
        val prefix = "${line.prefix} "
        withStyle(
            ParagraphStyle(
                textIndent = TextIndent(
                    firstLine = start.sp,
                    restLine = (start + char * prefix.length).sp
                )
            )
        ) {
            withStyle(SpanStyle(color = Term.prompt)) { append(prefix) }
            appendPieces(line.pieces, base)
        }
        i++
    }
}

/** A tag's text size, against the line's ([StyledText.Style.TAG]). */
internal const val TAG_SIZE = 0.82f

/** Marks a tag's text with its color (ARGB), so [RichText] can draw its chip. */
internal const val TAG_CHIP = "tag-chip"

/** [text] with its [StyledText] styles (tags, colors, links), in the style its `Text` gives; plain text as it is. */
internal fun styledText(text: String): AnnotatedString =
    if (!StyledText.has(text)) AnnotatedString(text) else buildAnnotatedString {
        appendStyled(
            text,
            SpanStyle(),
            Term.fg
        )
    }

/**
 * A widget's text, with the styles a card draws: weight (bold), slant (italic), lines (underline,
 * strike) and color. Plain text comes back as it is.
 * - It is never a link. A tap on a card's text is the card's own tap, and a card opens a link only
 *   through its `tap`. So this does not use the log's span code, which makes links that open.
 * - Anything else the log draws (a tag, a code chip, a table, a bar, a list) shows as its plain
 *   words ([StyledText.inline]). A stored text is in that form already. This runs it again, so a
 *   text from anywhere else is safe too.
 * - A color is a role of the theme, as in the log ([colorOf]). A piece without one keeps the
 *   color its `Text` gives.
 */
internal fun widgetText(text: String): AnnotatedString {
    if (!StyledText.has(text)) return AnnotatedString(text)
    return buildAnnotatedString {
        StyledText.lines(StyledText.inline(text)).forEachIndexed { i, line ->
            if (i > 0) append('\n')
            for ((spanText, styles, color) in line.spans) {
                val style = widgetStyle(styles, color)
                if (style == null) append(spanText) else withStyle(style) { append(spanText) }
            }
        }
    }
}

/** The style of one piece of a widget's text. Null for a piece with nothing to draw differently. */
private fun widgetStyle(styles: Set<StyledText.Style>, color: StyledText.ColorRole?): SpanStyle? {
    val bold = StyledText.Style.BOLD in styles
    val italic = StyledText.Style.ITALIC in styles
    val lines = listOfNotNull(
        TextDecoration.Underline.takeIf { StyledText.Style.UNDERLINE in styles },
        TextDecoration.LineThrough.takeIf { StyledText.Style.STRIKE in styles },
    )
    if (!bold && !italic && lines.isEmpty() && color == null) return null
    return SpanStyle(
        color = colorOf(color),
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (lines.isEmpty()) null else TextDecoration.combine(lines),
    )
}

/**
 * [text] that wraps only at a space or after "|", never inside a word, for `help`'s rows.
 * - After each "|" goes a zero-width space, so `<5m|1h30m>` can wrap there.
 * - After each "-", "–" and "/" goes a word joiner, so `--settings`, `0–100` and `a/b` stay whole.
 * Both are invisible. Text with [StyledText] marks is left as it is.
 */
internal fun breaksAtSpaces(text: String): String {
    if (StyledText.has(text) || text.none { it in BREAK_CHARS }) return text
    val out = StringBuilder(text.length + 8)
    text.forEachIndexed { i, c ->
        out.append(c)
        val next = text.getOrNull(i + 1)
        if (next == null || next == ' ') return@forEachIndexed
        when (c) {
            '|' -> out.append(ZERO_WIDTH_SPACE)
            '-', '–', '/' -> out.append(WORD_JOINER)
        }
    }
    return out.toString()
}

private const val BREAK_CHARS = "|-–/"
private const val ZERO_WIDTH_SPACE = '​'
private const val WORD_JOINER = '⁠'
