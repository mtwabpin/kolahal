/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.documents

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** How a file ends its lines. The editor's own text always uses `\n`. */
enum class LineEnding(val chars: String) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r"),
}

/**
 * How a text file is written. It is kept from when the file was read, so that saving changes the
 * text and nothing else.
 * - [lineEnding]: what ends each line.
 * - [finalNewline]: the file ended with a line break (or was empty). Saving keeps one at the end.
 * - [bom]: the file started with a UTF-8 byte-order mark.
 * - [mixed]: the file had more than one kind of line ending. Saving writes them all as [lineEnding],
 *   the most common one.
 */
data class TextFormat(
    val lineEnding: LineEnding = LineEnding.LF,
    val finalNewline: Boolean = true,
    val bom: Boolean = false,
    val mixed: Boolean = false,
) {
    /**
     * [text] as it is saved, still in the editor's form: with a line break at the end if the file
     * had one. An empty text stays empty.
     */
    fun complete(text: String): String =
        if (finalNewline && text.isNotEmpty() && !text.endsWith("\n")) text + "\n" else text

    /**
     * The bytes of [text] in this format: UTF-8, with the file's line endings and byte-order mark.
     * A `\r` in the text (pasted from elsewhere) counts as a line break, alone or before `\n`.
     */
    fun encode(text: String): ByteArray {
        val lines = if ('\r' in text) text.replace("\r\n", "\n").replace('\r', '\n') else text
        val body = (if (lineEnding == LineEnding.LF) lines else lines.replace("\n", lineEnding.chars)).toByteArray(Charsets.UTF_8)
        return if (bom) BOM + body else body
    }

    private companion object {
        val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}

/**
 * Reads a file's bytes as text for the editor, or says why it can't be edited.
 *
 * What the editor takes:
 * - UTF-8 text only. A file with a NUL byte, or bytes that are not UTF-8, is refused: saving it
 *   would damage it.
 * - At most [MAX_CHARS] characters and [MAX_LINES] lines.
 *
 * Why these limits. The editor is one Compose text field, and it lays out the whole text again at
 * every key.
 * - [MAX_CHARS]: on the emulator, a key in 100 KB of text took about 70 ms without colours, and
 *   about 100 ms with colours around the view (measured for 0.4.12). The time grows with the
 *   length. So typing is smooth up to about [SLOW_FROM] characters, and at half a million a key
 *   takes a third to half of a second: slow, but the editor still follows the keyboard, and you
 *   can read, search and fix the file. Much past that it doesn't. These numbers are worked out
 *   from that one measurement. They were not measured for each size, or on a phone.
 * - [MAX_LINES]: Android can't lay out anything taller than 16,777,215 pixels. 50,000 lines stay
 *   under that up to a line height of 335 pixels, which is more than the biggest font setting
 *   gives on any screen.
 */
object TextFiles {
    const val MAX_CHARS = 500_000
    const val MAX_LINES = 50_000

    /** From this length on, each key takes longer than about a tenth of a second, and the editor says so. */
    const val SLOW_FROM = 150_000

    /** A file with more bytes than this has more than [MAX_CHARS] characters: UTF-8 takes at most 3 bytes for each. */
    const val MAX_BYTES = MAX_CHARS * 3 + 3

    sealed interface Decoded {
        /** The text, with `\n` for every line break, and how the file was written. */
        data class Text(val text: String, val format: TextFormat) : Decoded

        /** The file can't be edited. [why] is a sentence for the user. */
        data class Refused(val why: String) : Decoded
    }

    private val TOO_LONG = "It is too long to edit here: the editor takes up to ${"%,d".format(java.util.Locale.ROOT, MAX_CHARS)} characters."
    private val TOO_MANY_LINES = "It has too many lines to edit here: the editor takes up to ${"%,d".format(java.util.Locale.ROOT, MAX_LINES)} lines."

    fun decode(bytes: ByteArray): Decoded {
        if (bytes.size > MAX_BYTES) return Decoded.Refused(TOO_LONG)
        if (bytes.size >= 2 && ((bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) || (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()))) {
            return Decoded.Refused("It is UTF-16 text. The editor reads and writes UTF-8 only.")
        }
        if (bytes.any { it == 0.toByte() }) return Decoded.Refused("It is not a text file: it holds binary data.")
        val bom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val start = if (bom) 3 else 0
        val raw = try {
            // A plain decode would put a replacement character where bytes are not UTF-8, and saving would write that.
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, bytes.size - start))
                .toString()
        } catch (_: CharacterCodingException) {
            return Decoded.Refused("It is not UTF-8 text. It may be a binary file, or text in an older encoding.")
        }
        if (raw.length > MAX_CHARS) return Decoded.Refused(TOO_LONG)

        var crlf = 0
        var lf = 0
        var cr = 0
        var i = 0
        while (i < raw.length) {
            when (raw[i]) {
                '\r' -> if (i + 1 < raw.length && raw[i + 1] == '\n') { crlf++; i++ } else cr++
                '\n' -> lf++
            }
            i++
        }
        if (crlf + lf + cr >= MAX_LINES) return Decoded.Refused(TOO_MANY_LINES)
        // The most common ending wins. With equal counts, the simpler one does.
        val ending = when {
            crlf > lf && crlf >= cr -> LineEnding.CRLF
            cr > lf && cr > crlf -> LineEnding.CR
            else -> LineEnding.LF
        }
        val kinds = listOf(crlf, lf, cr).count { it > 0 }
        val text = if (crlf + cr == 0) raw else raw.replace("\r\n", "\n").replace('\r', '\n')
        return Decoded.Text(text, TextFormat(ending, finalNewline = text.isEmpty() || text.endsWith("\n"), bom = bom, mixed = kinds > 1))
    }
}
