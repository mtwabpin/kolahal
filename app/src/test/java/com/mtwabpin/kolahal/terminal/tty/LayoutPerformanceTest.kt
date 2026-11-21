package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.terminal.LineModel
import com.mtwabpin.kolahal.terminal.LineOptions
import com.mtwabpin.kolahal.terminal.LogCorpus
import com.mtwabpin.kolahal.terminal.TimeMode
import com.mtwabpin.kolahal.terminal.lineModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory

/**
 * A long log on the grid stays cheap (design §2.8): 5,000 rows of the corpus, with right-to-left
 * and joining-script lines among them, lay out within a budget on the JVM; the log's cache of
 * laid-out lines stays within its bounds however many rows go by; and reading a laid-out block
 * the way the painter does on every frame allocates nothing.
 *
 * The budgets are loose for a JVM on a busy machine; they catch a layout that becomes many times
 * slower, not small changes. The device's frame times are measured on the phone.
 */
class LayoutPerformanceTest {
    private val options = LineOptions(TimeMode.ALL, foldLines = 8)

    /** 5,000 rows: the corpus over and over, each with its own id, and a Hebrew, an Arabic and a mixed line now and then. */
    private val rows: List<LineModel> by lazy {
        val extra = listOf("שלום עולם, מה שלומך היום?", "مرحبا بالعالم، كيف حالك اليوم؟", "Open קובץ.txt in אפליקציה (now)")
        (0 until ROWS).map { i ->
            val e = if (i % 10 == 9) {
                LogEntry(1_000_000L + i, LogCorpus.AT + i, LogKind.OUTPUT, null, extra[i % 3], LogContext(emptyMap()))
            } else {
                val base = LogCorpus.all[i % LogCorpus.all.size].entry
                base.copy(id = 1_000_000L + i)
            }
            lineModel(LogCorpus.row(LogCorpus.Line("row $i", e)), options)
        }
    }

    private fun appsOf(m: LineModel) =
        (m.body as? LineModel.Body.AppGrid)?.apps?.map { GridApp(it.pkg.substringAfterLast('.')) }.orEmpty()

    private fun layoutAll(cols: Int, rtl: Boolean): List<CellBlock> =
        rows.map { m -> LineLayout.layout(m, LogLook(cols, rtl = rtl), "14:02", apps = appsOf(m), initials = m.person?.let { "AL" }) }

    /** 5,000 rows lay out well within the budget, left to right and right to left. */
    @Test
    fun aLongLogLaysOutWithinItsBudget() {
        // Warm up, so the measure is of the layout and not of the JIT.
        repeat(2) { layoutAll(40, rtl = false) }
        for (rtl in listOf(false, true)) {
            val start = System.nanoTime()
            val blocks = layoutAll(40, rtl)
            val ms = (System.nanoTime() - start) / 1_000_000
            assertEquals(ROWS, blocks.size)
            assertTrue("$ROWS rows took $ms ms${if (rtl) " right to left" else ""} (budget $BUDGET_MS ms)", ms < BUDGET_MS)
        }
    }

    /** However many lines go by, the cache keeps at most its bound, and what it keeps stays under 2 MB, even at 80 columns. */
    @Test
    fun theCacheStaysBounded() {
        val cache = BlockCache<Long, CellBlock>(KEPT)
        for (m in rows) cache.get(m.id) { LineLayout.layout(m, LogLook(80), "14:02", apps = appsOf(m)) }
        assertEquals(KEPT, cache.size)
        val bytes = cache.values().sumOf { it.grid.bytes() }
        assertTrue("$bytes bytes kept", bytes < 2L * 1024 * 1024)
        // The most recently used are the ones kept.
        val newest = rows.last().id
        var made = false
        cache.get(newest) { made = true; LineLayout.layout(rows.last(), LogLook(80)) }
        assertTrue("the newest line is still kept", !made)
        cache.clear()
        assertEquals(0, cache.size)
    }

    /**
     * Reading every cell of a screenful of blocks as the painter does on every frame (glyph,
     * style, width, a cluster's text, the shaped words of each row) allocates nothing: the
     * painter's own draw calls reuse their paints and arrays, so a frame is allocation-free.
     */
    @Test
    fun aFramesReadsAllocateNothing() {
        val blocks = layoutAll(40, rtl = false).take(60) + layoutAll(40, rtl = true).takeLast(60)
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        if (!bean.isThreadAllocatedMemorySupported) return
        bean.isThreadAllocatedMemoryEnabled = true
        var sink = 0L
        repeat(50) { sink += walk(blocks) }
        val before = bean.currentThreadAllocatedBytes
        repeat(100) { sink += walk(blocks) }
        val allocated = bean.currentThreadAllocatedBytes - before
        assertTrue("$allocated bytes allocated over 100 frames ($sink)", allocated < 4096)
    }

    /** One frame's reads of [blocks], as [com.mtwabpin.kolahal.ui.tty.GridPainter] makes them. */
    private fun walk(blocks: List<CellBlock>): Long {
        var sum = 0L
        for (b in 0 until blocks.size) {
            val grid = blocks[b].grid
            val shaped = grid.shaped
            for (row in 0 until grid.rows) {
                var s = 0
                while (s < shaped.size && shaped[s].row < row) s++
                var col = 0
                while (col < grid.cols) {
                    if (s < shaped.size && shaped[s].row == row && shaped[s].col == col) {
                        val run = shaped[s++]
                        sum += run.text.length + if (run.rtl) 1 else 0
                        col += run.cells.coerceAtLeast(1)
                        continue
                    }
                    val g = grid.glyphAt(col, row)
                    val style = grid.styleAt(col, row)
                    sum += style.bits + if (style.has(CellStyle.MIRROR)) 1 else 0
                    if (g < CellBuffer.WIDE_TAIL) sum += grid.clusterOf(g).length
                    if (g > 0 && BoxShapes.isDrawn(g)) sum += BoxShapes.arms(g)
                    sum += grid.cellsOf(g)
                    col++
                }
            }
        }
        return sum
    }

    private companion object {
        const val ROWS = 5_000
        const val BUDGET_MS = 4_000L
        const val KEPT = 400
    }
}
