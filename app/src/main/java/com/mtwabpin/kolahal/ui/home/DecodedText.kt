/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.BidiText
import com.mtwabpin.kolahal.terminal.tty.BoxShapes
import com.mtwabpin.kolahal.terminal.tty.Decode
import com.mtwabpin.kolahal.terminal.tty.screens.DecodeWave
import com.mtwabpin.kolahal.terminal.tty.screens.LogDecode
import com.mtwabpin.kolahal.ui.theme.Term

/** The decode effect home's log is in ([LogDecode]), or null: then its texts are plain ones. */
internal val LocalLogDecode = compositionLocalOf<LogDecode?> { null }

/** A [Text] of home's log, which `decode` scrambles. */
@Composable
internal fun LogText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val decoded = rememberDecodedText()
    Text(
        text,
        modifier.then(decoded?.modifier ?: Modifier),
        style = style,
        inlineContent = inlineContent,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
        onTextLayout = { decoded?.layout = it },
    )
}

@Composable
internal fun LogText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val decoded = rememberDecodedText()
    Text(
        text,
        modifier.then(decoded?.modifier ?: Modifier),
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = decoded?.let { d -> { d.layout = it } },
    )
}

/** What draws a text of the log while `decode` is open, or null. Its modifier goes last, its layout comes from the text. */
@Composable
internal fun rememberDecodedText(): DecodedText? {
    val decode = LocalLogDecode.current ?: return null
    val text = remember(decode) { DecodedText(decode) }
    DisposableEffect(text) {
        decode.add(text)
        onDispose { decode.remove(text) }
    }
    return text
}

/**
 * Draws a text with the decode effect over it: the characters whose turn it is are left out, and
 * others are drawn in their places, in their size, weight and color. The layout is the text's own,
 * so nothing moves, and a screen reader reads the real text.
 *
 * Blanks, box drawing and blocks, emoji and other joined characters, pictures in the text, and the
 * scripts whose letters join (Arabic, the Indic scripts, Thai…) stay as they are.
 */
internal class DecodedText(private val decode: LogDecode) : LogDecode.Part {
    var layout: TextLayoutResult? = null

    private var at: LayoutCoordinates? = null

    /** The characters of [madeFrom] that scramble. */
    private var chars: Array<Char> = emptyArray()
    private var madeFrom: TextLayoutResult? = null

    /** Where the text was in the window when the wave [seen] reached it: a wave goes on if the log moves. */
    private var seen: DecodeWave? = null
    private var left = 0f
    private var top = 0f

    /** Tells its characters from those of the other texts. */
    private var own = 0

    /** When it came into the log, or -1 if it was there before the first wave. Then it decodes in. */
    private var born = UNSEEN

    private val hidden = Path()

    val modifier: Modifier = Modifier
        .onPlaced { at = it }
        .drawWithContent { draw() }

    override fun place(): LogDecode.Place? {
        val at = at?.takeIf { it.isAttached } ?: return null
        val p = at.positionInWindow()
        return LogDecode.Place(p.x, p.y, p.x + at.size.width, p.y + at.size.height)
    }

    /** One character: where it is, and how it is drawn. */
    private class Char(
        val box: Rect,
        val baseline: Float,
        val wide: Boolean,
        val color: Int,
        val background: Color,
        val size: Float,
        val face: Typeface?,
        val line: Int,
    )

    private fun ContentDrawScope.draw() {
        val now = decode.now
        val wave = decode.wave
        val layout = layout
        if (born == UNSEEN) born = if (wave == null) BEFORE else now
        if (wave == null || layout == null) return drawContent()
        if (seen !== wave) {
            val p = place() ?: return drawContent()
            seen = wave
            left = p.left
            top = p.top
            own = DecodeWave.mix(p.top.toInt(), p.left.toInt(), wave.seed) and 0xFFFF
        }
        if (madeFrom !== layout) {
            chars = read(layout)
            madeFrom = layout
        }
        val t = (now - decode.waveAt).toInt()
        val age = if (born == BEFORE) Int.MAX_VALUE else (now - born).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        var any = false
        hidden.rewind()
        for (i in chars.indices) if (scrambles(wave, i, t, age)) {
            hidden.addRect(chars[i].box)
            any = true
        }
        if (!any) return drawContent()
        clipPath(hidden, ClipOp.Difference) { this@draw.drawContent() }
        for (i in chars.indices) if (scrambles(wave, i, t, age)) {
            val c = chars[i]
            if (c.background.isSpecified) drawRect(c.background, Offset(c.box.left, c.box.top), Size(c.box.width, c.box.height))
            glyph(wave, i, c, t)
        }
    }

    private fun scrambles(wave: DecodeWave, i: Int, t: Int, age: Int): Boolean {
        val c = chars[i]
        val x = left + c.box.center.x
        if (age < Decode.LINE_MS) return age < DecodeWave.settles(wave.across(x), DecodeWave.mix(own + i, 2, 0))
        val y = top + c.box.center.y
        return t >= wave.start(own + i, x, y) && t < wave.settle(own + i, x, y)
    }

    private fun ContentDrawScope.glyph(wave: DecodeWave, i: Int, c: Char, t: Int) {
        // Each character changes at its own moment, so the log never blinks as one.
        val step = (t + DecodeWave.mix(own + i, c.line, wave.seed) % Decode.STEP_MS) / Decode.STEP_MS
        val pick = DecodeWave.mix(own + i, step, wave.seed + c.line)
        val glyphs = if (c.wide) WIDE else NARROW
        // It flickers: the tint comes and goes from one character to the next.
        val tint = wave.tint(wave.row(top + c.box.center.y)).takeIf { (pick ushr 8) % 4 != 0 }
        PAINT.color = when (tint) {
            Ink.WARN -> Term.palette.warn.toArgb()
            Ink.PROMPT -> Term.prompt.toArgb()
            else -> c.color
        }
        PAINT.typeface = c.face
        PAINT.textSize = c.size
        PAINT.textScaleX = 1f
        val glyph = glyphs[pick % glyphs.size]
        // Not wider than the character it stands for, so it never runs into its neighbors.
        val width = PAINT.measureText(glyph)
        if (width > c.box.width) PAINT.textScaleX = c.box.width / width
        drawIntoCanvas { it.nativeCanvas.drawText(glyph, c.box.center.x, c.baseline, PAINT) }
    }

    /** The characters of [layout] that scramble, with their looks: the text's style and the spans over it. */
    private fun read(layout: TextLayoutResult): Array<Char> {
        val input = layout.layoutInput
        val text = input.text.text
        val spans = input.text.spanStyles
        val base = input.style.toSpanStyle()
        val faces = HashMap<SpanStyle, Typeface?>()
        val out = ArrayList<Char>()
        for (line in 0 until layout.lineCount) {
            val baseline = layout.getLineBaseline(line)
            val lineTop = layout.getLineTop(line)
            val lineBottom = layout.getLineBottom(line)
            for (o in layout.getLineStart(line) until layout.getLineEnd(line, visibleEnd = true)) {
                if (!scrambles(text, o)) continue
                val box = layout.getBoundingBox(o)
                if (box.width < 1f) continue
                var style = base
                for (s in spans) if (o >= s.start && o < s.end) style = style.merge(s.item.copy(fontSize = sized(s.item.fontSize, style.fontSize)))
                val size = with(input.density) { if (style.fontSize.isSp) style.fontSize.toPx() else Term.fontSize.sp.toPx() }
                val face = faces.getOrPut(SpanStyle(fontFamily = style.fontFamily, fontWeight = style.fontWeight, fontStyle = style.fontStyle)) {
                    input.fontFamilyResolver.resolve(
                        style.fontFamily, style.fontWeight ?: FontWeight.Normal, style.fontStyle ?: FontStyle.Normal,
                    ).value as? Typeface
                }
                out += Char(
                    // The whole row's height: a tall letter or one under the baseline goes too.
                    Rect(box.left, lineTop, box.right, lineBottom), baseline,
                    wide = box.width > size * 0.85f && Character.isIdeographic(text[o].code) || text[o] in '぀'..'ヿ',
                    color = (if (style.color.isSpecified) style.color else Term.fg).toArgb(),
                    background = style.background,
                    size = size,
                    face = face,
                    line = line,
                )
            }
        }
        return out.toTypedArray()
    }

    /** A span's size: its own, its part of the size around it, or that size. */
    private fun sized(own: TextUnit, around: TextUnit): TextUnit = when {
        own.isEm && around.isSp -> around * own.value
        own.isSp -> own
        else -> around
    }

    private fun scrambles(text: String, o: Int): Boolean {
        val c = text[o]
        if (c.isWhitespace() || c == ' ' || c == '�' || c.isSurrogate() || Character.isISOControl(c)) return false
        if (mark(c) || o + 1 < text.length && mark(text[o + 1])) return false
        return !BoxShapes.isDrawn(c.code) && !BidiText.joins(c.code)
    }

    /** A character that goes with the one before it: an accent, a variation selector, a joiner. */
    private fun mark(c: kotlin.Char): Boolean = when (Character.getType(c).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK, Character.FORMAT -> true
        else -> false
    }

    private companion object {
        const val UNSEEN = -2L
        const val BEFORE = -1L

        /** Texts draw one after another, on one thread. */
        val PAINT = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { textAlign = Paint.Align.CENTER }

        val NARROW = Decode.NARROW_GLYPHS.map { String(Character.toChars(it)) }.toTypedArray()
        val WIDE = Decode.WIDE_GLYPHS.map { String(Character.toChars(it)) }.toTypedArray()
    }
}
