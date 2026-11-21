/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.KeyBarKey

/**
 * A row of the key bar on the grid: the keys share the row in equal spans, each label in the middle
 * of its span, a muted `│` between two keys, all on the surface color. A pressed key is inverse.
 * When the keys would be narrower than [row]'s `minSpan`, each takes `minSpan` cells and the row
 * is wider than the screen (it scrolls). A key's spot id is its index.
 */
object KeyBarCells {
    /** A row's keys from its spec (`keybar.row1`): each a named key, or text that is typed as it is. */
    fun keys(spec: String): List<Pair<KeyBarKey?, String>> =
        spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { KeyBarKey.of(it) to it }

    /** What a key shows: its name's glyph (as text, never a color emoji), or its text. */
    fun label(key: KeyBarKey?, token: String): String = when (key) {
        null -> token
        KeyBarKey.KBD -> "⌨\uFE0E"
        else -> key.label
    }

    /**
     * The row for [labels] in [cols] columns; [pressed] is the index of the key held down, or -1.
     * Keys share [cols] when each gets at least [minSpan] cells, else the row is wider.
     */
    fun row(labels: List<String>, cols: Int, minSpan: Int, pressed: Int = -1): HomeCells {
        val n = labels.size
        val fits = n == 0 || cols / n >= minSpan
        val width = if (fits) cols else n * minSpan
        val w = HomeWriter(width.coerceAtLeast(1))
        val surface = CellStyle.of(Ink.FG, Ink.SURFACE)
        w.blank(0, 0, width, surface)
        for ((i, label) in labels.withIndex()) {
            val from = if (fits) i * cols / n else i * minSpan
            val to = if (fits) (i + 1) * cols / n else (i + 1) * minSpan
            val start = if (i > 0) from + 1 else from
            if (i > 0) w.put(from, 0, "│", CellStyle.of(Ink.DIM, Ink.SURFACE, CellStyle.FAINT))
            val inner = to - start
            val style = if (i == pressed) CellStyle.of(Ink.FG, Ink.SURFACE, CellStyle.INVERSE or CellStyle.BOLD)
            else CellStyle.of(Ink.FG, Ink.SURFACE, CellStyle.BOLD)
            if (i == pressed) w.blank(start, 0, inner, style)
            val shown = cut(label, inner)
            val pad = ((inner - CellWidth.total(shown)) / 2).coerceAtLeast(0)
            w.put(start + pad, 0, shown, style)
            w.spot(i, from, 0, to - from)
        }
        return w.build()
    }
}
