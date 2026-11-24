package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.sourceFile
import com.mtwabpin.kolahal.terminal.HeaderPart
import com.mtwabpin.kolahal.terminal.HeaderParts
import com.mtwabpin.kolahal.terminal.HeaderTarget
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.KeyBarKey
import com.mtwabpin.kolahal.terminal.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font

/** Home's parts on the grid: the header, the pinned line, the prompt, the suggestions, the keys, the bands, the sheets. */
class HomeCellsTest {
    private fun part(kind: HeaderPart.Kind, text: String, tag: String, ink: Ink = Ink.FG, battery: Int? = null, charging: Boolean = false) =
        HeaderPart(kind, text, ink, tag, tag, target = HeaderTarget.CLOCK, battery = battery, charging = charging)

    private val time = part(HeaderPart.Kind.TIME, "14:02", "time")
    private val date = part(HeaderPart.Kind.DATE, "Tue 17 Nov", "date", Ink.DIM)
    private val alarm = part(HeaderPart.Kind.ALARM, "07:00", "alarm")
    private val todo = part(HeaderPart.Kind.TODO, "3", "todo")
    private val battery = part(HeaderPart.Kind.BATTERY, "82%", "battery", battery = 82)

    /** The battery's cells as text, as [HeaderCells] writes them: one per cell, drawn by the painter. */
    private fun batteryCells(percent: Int, charging: Boolean = false) =
        (0 until BoxShapes.BATTERY_CELLS).joinToString("") { BoxShapes.battery(it, percent, charging).toChar().toString() }

    /** Every spot of [cells] is on it. */
    private fun HomeCells.assertSpotsInside() {
        for (s in spots) {
            assertTrue("$s starts outside", s.rect.col >= 0 && s.rect.row >= 0)
            assertTrue("$s ends outside $cols × $rows", s.rect.col + s.rect.cols <= cols && s.rect.row + s.rect.rows <= rows)
        }
    }

    private fun HomeCells.style(col: Int, row: Int = 0) = grid.styleAt(col, row)

    @Test
    fun theHeaderPutsTheLeftSideLeftAndTheRightSideRight() {
        val cells = HeaderCells.row(HeaderParts(listOf(time, date), listOf(alarm, todo, battery)), 48)
        val row = cells.text()
        assertTrue(row, row.startsWith("14:02  Tue 17 Nov"))
        assertTrue(row, row.endsWith("◷ 07:00  ✓ 3  " + batteryCells(82) + " 82%"))
        assertEquals(48, cells.cols)
        // Time is bold; the date muted.
        assertTrue(cells.style(0).has(CellStyle.BOLD))
        assertEquals(Ink.DIM, cells.style(7).fg)
        // Each part is a spot, in order, with a cell of room on each side where the row allows.
        assertEquals(listOf(0, 1, 2, 3, 4), cells.spots.map { it.id })
        assertEquals(CellRect(0, 0, 6, 1), cells.spot(0)!!.rect)
        cells.assertSpotsInside()
    }

    @Test
    fun theHeaderDropsPartsFromTheMiddleOutward() {
        val cells = HeaderCells.row(HeaderParts(listOf(time, date), listOf(alarm, todo, battery)), 30)
        val row = cells.text()
        // The left side's first part and the right side's last part stay longest.
        assertTrue(row, row.startsWith("14:02"))
        assertTrue(row, row.endsWith("82%"))
        assertFalse(row, row.contains("07:00"))
        assertNull(cells.spot(2))
    }

    @Test
    fun aCutoutSplitsTheHeaderAndThePartsFlowAroundIt() {
        // Cells 10 px wide from x 0; a cutout over x 150–189 with 4 px clear on each side.
        val zones = HeaderCells.zones(40, 0, 10, listOf(150..189), 4)
        assertEquals(listOf(0..13, 20..39), zones)
        val cells = HeaderCells.row(HeaderParts(listOf(time, date), listOf(battery)), 40, zones)
        // Nothing is drawn under the cutout.
        for (c in 14..19) assertEquals(CellBuffer.EMPTY, cells.grid.glyphAt(c, 0))
    }

    /** A cell that a rounded screen corner would cut, with 4 px clear past it, is left empty at that end. */
    @Test
    fun theCornersLeaveTheirCellsEmpty() {
        // Cells 10 px wide from x 5 on a row 410 px wide; the corners hide 20 px at each end.
        // Cell 1 (x 15–24) touches the 4 px past the left corner; cell 38 (x 385–394) the 4 px before the right one.
        val zones = HeaderCells.zones(40, 5, 10, emptyList(), 4, cornerLeft = 20, cornerRight = 20, widthPx = 410)
        assertEquals(listOf(2..37), zones)
        // Each side on its own, and with a cutout in the middle.
        assertEquals(listOf(2..39), HeaderCells.zones(40, 5, 10, emptyList(), 4, cornerLeft = 20, widthPx = 410))
        assertEquals(listOf(0..37), HeaderCells.zones(40, 5, 10, emptyList(), 4, cornerRight = 20, widthPx = 410))
        assertEquals(
            listOf(2..13, 19..37),
            HeaderCells.zones(40, 5, 10, listOf(150..189), 4, cornerLeft = 20, cornerRight = 20, widthPx = 410)
        )
        // A corner within the grid's margin, 4 px clear included, leaves every cell; so do square corners.
        assertEquals(listOf(0..39), HeaderCells.zones(40, 5, 10, emptyList(), 4, cornerLeft = 1, cornerRight = 1, widthPx = 410))
        assertEquals(listOf(0..39), HeaderCells.zones(40, 5, 10, emptyList(), 4, widthPx = 410))
        val cells = HeaderCells.row(HeaderParts(listOf(time, date), listOf(battery)), 40, zones)
        assertTrue(cells.text(), cells.text().startsWith("  14:02  Tue 17 Nov"))
        assertTrue(cells.text(), cells.text().endsWith("82%"))
        for (c in listOf(0, 1, 38, 39)) assertEquals(CellBuffer.EMPTY, cells.grid.glyphAt(c, 0))
    }

    /**
     * The battery is cells the painter draws as one battery (outline, terminal, fill, bolt), never
     * font glyphs or blocks: at 100 % it is a full battery, not a solid block.
     */
    @Test
    fun theBatteryShowsItsLevelAndCharging() {
        for ((percent, charging) in listOf(0 to false, 50 to false, 100 to false, 0 to true, 50 to true, 100 to true)) {
            val ink = if (charging) Ink.PROMPT else Ink.FG
            val part = part(HeaderPart.Kind.BATTERY, "$percent%", "battery", ink, battery = percent, charging = charging)
            val cells = HeaderCells.row(HeaderParts(listOf(part), emptyList()), 20)
            val name = "$percent%${if (charging) ", charging" else ""}"
            // Its cells, a cell of room, and the level.
            assertEquals(name, batteryCells(percent, charging) + " $percent%", cells.text())
            for (i in 0 until BoxShapes.BATTERY_CELLS) {
                val g = cells.grid.glyphAt(i, 0)
                assertTrue(name, BoxShapes.isDrawn(g))
                assertEquals(name, i, BoxShapes.batteryCell(g))
                assertEquals(name, percent, BoxShapes.batteryPercent(g))
                assertEquals(name, charging, BoxShapes.batteryCharging(g))
                assertFalse(name, g in 0x2500..0x259F)
                assertEquals(name, ink, cells.style(i).fg)
            }
            assertEquals(name, CellBuffer.EMPTY, cells.grid.glyphAt(BoxShapes.BATTERY_CELLS, 0))
            assertEquals(name, HeaderCells.width(part), CellWidth.total(cells.text()))
        }
        val low = part(HeaderPart.Kind.BATTERY, "9%", "battery", Ink.ERROR, battery = 9)
        val cells = HeaderCells.row(HeaderParts(listOf(low), emptyList()), 20)
        assertEquals(batteryCells(9) + " 9%", cells.text())
        assertEquals(Ink.ERROR, cells.style(1).fg)
    }

    /** The todos' sign is a check mark that every font the app ships has, so no other font stands in for it. */
    @Test
    fun theTodoSignIsInEveryBundledFont() {
        assertEquals("✓ 3", HeaderCells.row(HeaderParts(listOf(todo), emptyList()), 20).text())
        val sign = HeaderCells.TODO_SIGN.trim().codePointAt(0)
        val fonts = sourceFile("src/main/res/font").listFiles().orEmpty().filter { it.extension == "ttf" }
        assertEquals(10, fonts.size)
        for (file in fonts) {
            val font = Font.createFont(Font.TRUETYPE_FONT, file)
            assertTrue("${file.name} has no ${HeaderCells.TODO_SIGN.trim()}", font.canDisplay(sign))
        }
    }

    @Test
    fun aProfileIsItsTagInverse() {
        val work = part(HeaderPart.Kind.PROFILE, "work", "profile-work", Ink.WORK)
        val cells = HeaderCells.row(HeaderParts(listOf(work), emptyList()), 20)
        assertEquals(" W", cells.text())
        assertTrue(cells.style(1).has(CellStyle.INVERSE))
        assertEquals(Ink.WORK, cells.style(1).fg)
    }

    @Test
    fun thePinnedLineIsItsTextAndItsProgressUnderIt() {
        val cells = PinnedCells.script("Build 4 of 7 running", Ink.SCRIPT, 0.5f, 20, 6)
        assertEquals(2, cells.rows)
        // Cut short of the dots' room.
        assertEquals("Build 4 of 7 …", cells.text(0))
        // A bar an eighth of a cell thin: filled in the line's ink, the rest muted.
        assertEquals("▔".repeat(20), cells.text(1))
        assertEquals(Ink.SCRIPT, cells.style(9, 1).fg)
        assertEquals(Ink.DIM, cells.style(10, 1).fg)
        assertEquals(Ink.SCRIPT, cells.style(0).fg)
        assertEquals(CellRect(0, 0, 20, 2), cells.spot(PinnedCells.ITEM)!!.rect)
        // No progress, no line.
        assertEquals("", PinnedCells.script("Idle", Ink.SCRIPT, null, 20, 0).text(1))
    }

    @Test
    fun whatsPlayingHasItsNextButton() {
        val cells = PinnedCells.media("Song — Band", playing = true, filled = 5, cols = 30, room = 0)
        assertTrue(cells.text(0), cells.text(0).startsWith("♪ Song — Band"))
        assertTrue(cells.text(0), cells.text(0).endsWith("⏭\uFE0E"))
        val next = cells.spot(PinnedCells.NEXT)!!.rect
        assertEquals(30, next.col + next.cols)
        assertEquals(5, (0 until 30).count { cells.style(it, 1).fg == Ink.PROMPT })
        assertTrue(PinnedCells.media("Song", playing = false, filled = 0, cols = 30, room = 0).text(0).startsWith("‖ Song"))
        assertEquals(" · ● ·", PinnedCells.dots(3, 1).text())
    }

    @Test
    fun thePromptShowsTheTextAndABlockCursor() {
        val cells = PromptCells.row("ls", 2, 2, cursor = true, first = 0, cols = 20, keyBarOn = false)
        assertTrue(cells.text(), cells.text().startsWith("$ ls"))
        // The cursor at the end is an inverse cell of its own.
        assertTrue(cells.style(4).has(CellStyle.INVERSE))
        assertFalse(cells.style(3).has(CellStyle.INVERSE))
        // ✕ and ⌨ at the end, ⌨ last.
        assertNotNull(cells.spot(PromptCells.CLEAR))
        assertEquals(CellRect(17, 0, 3, 1), cells.spot(PromptCells.KEYS)!!.rect)
        assertEquals(CellRect(0, 0, 2, 1), cells.spot(PromptCells.QUICK)!!.rect)
        // No text: no ✕.
        assertNull(PromptCells.row("", 0, 0, cursor = true, first = 0, cols = 20, keyBarOn = true).spot(PromptCells.CLEAR))
        // The cursor inside the text inverts the character under it; no cursor without the focus.
        assertTrue(PromptCells.row("ls", 0, 0, cursor = true, first = 0, cols = 20, keyBarOn = false).style(2).has(CellStyle.INVERSE))
        assertFalse(PromptCells.row("ls", 0, 0, cursor = false, first = 0, cols = 20, keyBarOn = false).style(2).has(CellStyle.INVERSE))
    }

    /**
     * The prompt at the bottom of a phone 1080 px wide, 46 cells of 22 px from x 34, where the rounded
     * corners hide 36 px at each end of its cells' rows. Cell 0 (x 34–55) is within 11 px (4 dp) of the
     * left corner, so the `$` moves a cell in. The last cell is only the key bar button's room, so the
     * buttons stay.
     */
    @Test
    fun theCornersMoveThePromptsEndsIn() {
        val free = HeaderCells.zones(46, 34, 22, emptyList(), 11, cornerLeft = 36, cornerRight = 36, widthPx = 1080)
        assertEquals(listOf(1..44), free)
        assertEquals(1 to 0, PromptCells.ends(46, free, rtl = false))
        // Uneven: the `$` takes its side's cells, the buttons one fewer. In a right-to-left layout the `$` is on the right.
        assertEquals(2 to 2, PromptCells.ends(46, listOf(2..42), rtl = false))
        assertEquals(3 to 1, PromptCells.ends(46, listOf(2..42), rtl = true))
        // Square corners, or a row above them: nothing moves.
        assertEquals(0 to 0, PromptCells.ends(46, listOf(0..45), rtl = false))
        assertEquals(0 to 0, PromptCells.ends(46, emptyList(), rtl = false))

        val cells = PromptCells.row("ls", 2, 2, cursor = true, first = 0, cols = 20, keyBarOn = false, lead = 1, trail = 1)
        assertTrue(cells.text(), cells.text().startsWith(" $ ls"))
        assertEquals(CellBuffer.EMPTY, cells.grid.glyphAt(0, 0))
        assertEquals(CellBuffer.EMPTY, cells.grid.glyphAt(19, 0))
        // The cursor after the text, the targets moved with what they show.
        assertTrue(cells.style(5).has(CellStyle.INVERSE))
        assertEquals(CellRect(1, 0, 2, 1), cells.spot(PromptCells.QUICK)!!.rect)
        assertEquals(CellRect(13, 0, 3, 1), cells.spot(PromptCells.CLEAR)!!.rect)
        assertEquals(CellRect(16, 0, 3, 1), cells.spot(PromptCells.KEYS)!!.rect)
        // Turned around for a right-to-left layout, the `$` stands a cell in from the right.
        assertEquals('$'.code, cells.mirrored.grid.glyphAt(18, 0))
    }

    @Test
    fun thePromptMarksTheSelectionAndTheComposition() {
        val cells = PromptCells.row("hello world", 0, 5, cursor = true, first = 0, cols = 30, keyBarOn = false, compStart = 6, compEnd = 11)
        for (c in 2..6) assertTrue(cells.style(c).has(CellStyle.INVERSE))
        assertFalse(cells.style(7).has(CellStyle.INVERSE))
        for (c in 8..12) assertTrue(cells.style(c).has(CellStyle.UNDERLINE))
    }

    @Test
    fun longTextScrollsToKeepTheCursorInView() {
        val text = "abcdefghijklmnopqrstuvwxyz"
        // 10 cells of text; the cursor at the end needs a cell of its own.
        assertEquals(17, PromptCells.scroll(text, text.length, 10, 0))
        // The cursor back in view: no scroll.
        assertEquals(17, PromptCells.scroll(text, 20, 10, 17))
        // Left of the view: scrolls back just to it.
        assertEquals(3, PromptCells.scroll(text, 3, 10, 17))
        val width = PromptCells.textCols(20, clear = true)
        val first = PromptCells.scroll(text, text.length, width, 0)
        val cells = PromptCells.row(text, text.length, text.length, cursor = true, first = first, cols = 20, keyBarOn = false)
        assertTrue(cells.text(), cells.text().startsWith("$ " + text.substring(first)))
        // A tap's column back to the text.
        assertEquals(first + 2, PromptCells.offsetAt(text, 2, first))
        assertEquals(text.length, PromptCells.offsetAt(text, 40, first))
    }

    @Test
    fun aSecretIsMasked() {
        val cells = PromptCells.row("hunter2", 7, 7, cursor = false, first = 0, cols = 20, keyBarOn = false, mask = true, buttons = false)
        assertEquals("$ •••••••", cells.text())
        assertTrue(cells.spots.isEmpty())
    }

    @Test
    fun aSuggestionIsItsNameAndDescription() {
        val s = Suggestion.Completion("help", "help ")
        val cells = SuggestionCells.row(s, "List every command", 40, enter = false, wrap = false)
        assertEquals("$ help  List every command", cells.text())
        assertEquals(Ink.PROMPT, cells.style(0).fg)
        assertEquals(Ink.DIM, cells.style(8).fg)
        assertEquals(CellRect(0, 0, 40, 1), cells.spot(0)!!.rect)
        // Cut short with "…" when it doesn't fit.
        val short = SuggestionCells.row(s, "List every command there is, and more", 24, enter = false, wrap = false)
        assertTrue(short.text(), short.text().endsWith("…"))
        assertEquals(1, short.rows)
    }

    @Test
    fun theRowEnterActsOnIsInverseWithItsMark() {
        val s = Suggestion.Completion("help", "help ", enter = true)
        val cells = SuggestionCells.row(s, null, 30, enter = true, wrap = false)
        assertTrue(cells.style(0).has(CellStyle.INVERSE))
        assertTrue(cells.style(2).has(CellStyle.INVERSE or CellStyle.BOLD))
        assertTrue(cells.text(), cells.text().endsWith(SuggestionCells.ENTER))
    }

    @Test
    fun aWrappedDescriptionTakesMoreRowsInItsOwnColumn() {
        val s = Suggestion.Completion("help", "help ")
        val help = "List every command with what it does, in groups, and how to read more"
        val cells = SuggestionCells.row(s, help, 40, enter = false, wrap = true)
        assertTrue(cells.rows > 1)
        // Every row of the description starts in the same column, past the name.
        for (r in 1 until cells.rows) assertEquals(8, cells.grid.rowText(r).indexOfFirst { it != ' ' })
        assertEquals(CellRect(0, 0, 40, cells.rows), cells.spot(0)!!.rect)
    }

    @Test
    fun aProfileTagIsTextInItsColor() {
        val cells = SuggestionCells.row(Suggestion.Completion("Maps [W]", "open maps "), null, 30, enter = false, wrap = false)
        assertEquals("$ Maps [W]", cells.text())
        assertTrue(cells.style(7).has(CellStyle.INVERSE))
        assertEquals(Ink.WORK, cells.style(7).fg)
    }

    @Test
    fun theCalculatorsAnswerIsBold() {
        val s = Suggestion.Completion("6*7", "6*7", big = "42" to "6*7")
        val cells = SuggestionCells.row(s, null, 30, enter = false, wrap = false)
        assertEquals("$ 42  6*7", cells.text())
        assertTrue(cells.style(2).has(CellStyle.BOLD))
    }

    @Test
    fun chipsPackIntoTheShorterRow() {
        assertEquals(listOf(listOf(0, 1, 2)), SuggestionCells.pack(listOf(5, 5, 5), rows = 2))
        assertEquals(listOf(listOf(0, 2, 4), listOf(1, 3)), SuggestionCells.pack(listOf(5, 5, 5, 5, 5), rows = 2))
        val items = listOf(Suggestion.Completion("ls", "ls ", enter = true), Suggestion.Completion("log", "log "))
        val rows = SuggestionCells.strip(items, rows = 1)
        assertEquals(1, rows.size)
        assertEquals(" $ ls   $ log", rows[0].text())
        // The first chip, which Enter takes, is inverse; the others are on the surface color.
        assertTrue(rows[0].style(0).has(CellStyle.INVERSE))
        assertEquals(Ink.SURFACE, rows[0].style(7).bg)
        assertEquals(listOf(0, 1), rows[0].spots.map { it.id })
    }

    @Test
    fun keysShareTheRow() {
        val labels = KeyBarCells.keys("ESC,TAB,UP,~").map { (k, t) -> KeyBarCells.label(k, t) }
        assertEquals(listOf("ESC", "TAB", KeyBarKey.UP.label, "~"), labels)
        val cells = KeyBarCells.row(labels, 40, minSpan = 4)
        assertEquals(40, cells.cols)
        assertEquals(listOf(0, 10, 20, 30), cells.spots.map { it.rect.col })
        assertTrue(cells.spots.all { it.rect.cols == 10 })
        assertEquals("│", cells.grid.rowText(0).substring(10, 11))
        assertEquals(Ink.SURFACE, cells.style(5).bg)
        // A held key is inverse.
        val held = KeyBarCells.row(labels, 40, minSpan = 4, pressed = 1)
        assertTrue(held.style(15).has(CellStyle.INVERSE))
        assertFalse(held.style(5).has(CellStyle.INVERSE))
        // Too many keys: each takes the least span, and the row is wider than the screen.
        assertEquals(60, KeyBarCells.row(List(10) { "k" }, 40, minSpan = 6).cols)
    }

    @Test
    fun aSheetIsABoxWithItsActions() {
        val rows = SheetCells.sheet(
            title = "countdown",
            detail = "today 6:55:12",
            url = null,
            actions = listOf("Copy text" to false, "Run again" to false, "Delete" to true),
            cols = 24,
            focused = 1,
        )
        val text = rows.joinToString("\n") { it.cells.text() }
        assertEquals(
            """
            ┌─ countdown ──────────┐
            │ today 6:55:12        │
            ├──────────────────────┤
            │ Copy text            │
            │ Run again            │
            ├──────────────────────┤
            │ Delete               │
            └──────────────────────┘
            """.trimIndent(),
            text,
        )
        val actions = rows.filter { it.kind == SheetCells.Kind.ACTION }
        assertEquals(listOf(0, 1, 2), actions.map { it.action })
        // The focused action is inverse inside the box, the box's sides are not.
        assertTrue(actions[1].cells.style(5).has(CellStyle.INVERSE))
        assertFalse(actions[1].cells.style(0).has(CellStyle.INVERSE))
        assertEquals(Ink.ERROR, actions[2].cells.style(2).fg)
        assertEquals(CellRect(0, 0, 24, 1), actions[0].cells.spot(0)!!.rect)
    }

    @Test
    fun aSheetsButtonsGoRightAndOnlyUsableOnesTakeTaps() {
        val cells = SheetCells.buttons(listOf("Cancel" to true, "Save" to false), 30)
        assertEquals("│       [ Cancel ]  [ Save ] │", cells.text())
        assertEquals(listOf(0), cells.spots.map { it.id })
        assertTrue(cells.style(22).has(CellStyle.FAINT))
    }

    @Test
    fun bands() {
        val notice = BandCells.notice("Deleted 3 lines", undo = true, cols = 30)
        assertTrue(notice.text().startsWith("Deleted 3 lines"))
        assertTrue(notice.text().endsWith("Undo"))
        assertEquals(30, notice.spot(BandCells.UNDO)!!.rect.let { it.col + it.cols })
        assertNull(BandCells.notice("Copied", undo = false, cols = 30).spot(BandCells.UNDO))
        val filter = BandCells.filter("errors", 1, 30)
        assertTrue(filter.text(), filter.text().startsWith("only errors · 1 line"))
        assertNotNull(filter.spot(BandCells.CLEAR))
        val accent = BandCells.accent("Preview: theme nord", 30, tappable = false)
        assertEquals("     Preview: theme nord", accent.text())
        assertTrue(accent.style(0).has(CellStyle.INVERSE))
        val download = BandCells.download(4L * 1024 * 1024, 10L * 1024 * 1024, 40, 40)
        assertTrue(download.text(0).endsWith("4 MB / 10 MB  40%"))
        assertEquals(16, download.text(1).count { it == '━' })
        assertEquals(BandCells.JUMP.trimEnd(), BandCells.jump().text())
    }

    @Test
    fun aSelectorRowIsItsLetterAndName() {
        val row = SelectorCells.row("Maps", 'M', null, Ink.FG, star = false, favorite = false, focused = false, far = false, quiet = false, mirrored = false, cols = 20)
        assertEquals("M  Maps", row.text())
        val tagged = SelectorCells.row("Maps", null, "W", Ink.WORK, star = true, favorite = false, focused = true, far = true, quiet = false, mirrored = false, cols = 30)
        assertEquals("   [W] ★ Maps  ⋯ Actions", tagged.text())
        // The app under the thumb is inverse in the accent color.
        assertTrue(tagged.style(10).has(CellStyle.INVERSE))
        assertEquals(Ink.PROMPT, tagged.style(10).fg)
        // On the left edge, the name goes toward the strip, and the letter at the edge.
        val left = SelectorCells.row("Maps", 'M', null, Ink.FG, star = false, favorite = false, focused = false, far = false, quiet = false, mirrored = true, cols = 20)
        assertEquals("             Maps  M", left.text())
        assertEquals(" A ", SelectorCells.bubble('A').grid.rowText(0))
    }
}
