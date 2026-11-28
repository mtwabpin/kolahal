package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.ui.widgets.VersusWords.Face
import com.mtwabpin.kolahal.ui.widgets.VersusWords.Figures
import com.mtwabpin.kolahal.ui.widgets.VersusWords.Part
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * A versus card's words and numbers ([VersusWords]): marks, colors, the bar, the score split
 * between the sides, the clocks, what gives way at each size, and what a screen reader hears.
 * All teams, players and scores are made up.
 */
class VersusWordsTest {
    private val football = WidgetSpec.Versus(
        WidgetBase(title = "League"),
        listOf(
            WidgetSpec.Side("North", value = 58f, text = "58%", lines = listOf("Moss 23', 61'")),
            WidgetSpec.Side("South", value = 42f, text = "42%", lines = listOf("Reed 54' (pen)", "Pike 59' red card")),
        ),
        score = "2–1",
        line = "Second half",
        badge = "67'",
    )

    private val cricket = WidgetSpec.Versus(
        WidgetBase(title = "Cup final"),
        listOf(
            WidgetSpec.Side("Harbour", score = "187/4", text = "18.2 ov", lines = listOf("Ames 64* (38)")),
            WidgetSpec.Side("Valley", score = "201/6", text = "20 ov"),
        ),
        turn = 1,
        moving = true,
        line = "Need 15 off 10",
        badge = "LIVE",
    )

    private val chess = WidgetSpec.Versus(
        WidgetBase(title = "Blitz"),
        listOf(
            WidgetSpec.Side("Iris Novak", mark = "IN", detail = "2214 · White", clock = 252),
            WidgetSpec.Side("Tomas Brandt", mark = "TB", detail = "2187 · Black", clock = 238),
        ),
        turn = 2,
        moving = true,
        line = "31. Nf3",
        badge = "3+2",
    )

    private val bare = WidgetSpec.Versus(WidgetBase(), listOf(WidgetSpec.Side("North"), WidgetSpec.Side("South")), score = "2–1")

    @Test
    fun aSideHasItsMarkAndItsColor() {
        assertEquals("N", VersusWords.mark(football.sides[0]))
        assertEquals("IN", VersusWords.mark(chess.sides[0]))
        assertEquals("", VersusWords.mark(WidgetSpec.Side("…")))
        assertEquals(StyledText.ColorRole.ACCENT, VersusWords.role(football.sides[0], 0))
        assertEquals(StyledText.ColorRole.LINK, VersusWords.role(football.sides[1], 1))
        assertEquals(StyledText.ColorRole.GOOD, VersusWords.role(WidgetSpec.Side("B", color = StyledText.ColorRole.GOOD), 1))
    }

    /** The bar's two parts are each side's share of the two values; none without both, or with two 0s. */
    @Test
    fun theBarSharesTheTwoValues() {
        val (a, b) = VersusWords.shares(football)!!
        assertEquals(0.58f, a, 0.0001f)
        assertEquals(0.42f, b, 0.0001f)
        assertNull(VersusWords.shares(bare))
        assertNull(VersusWords.shares(bare.copy(sides = listOf(WidgetSpec.Side("A", value = 0f), WidgetSpec.Side("B", value = 0f)))))
        assertEquals(1f to 0f, VersusWords.shares(bare.copy(sides = listOf(WidgetSpec.Side("A", value = 3f), WidgetSpec.Side("B", value = 0f)))))
    }

    @Test
    fun theTurnIsASidesPlace() {
        assertEquals(0, VersusWords.turnIndex(cricket))
        assertEquals(1, VersusWords.turnIndex(chess))
        assertNull(VersusWords.turnIndex(bare))
    }

    /** A score of two parts around "–", "-", ":" or " · " is split between the sides; any other is whole. */
    @Test
    fun aScoreOfTwoPartsIsSplit() {
        assertEquals("2" to "1", VersusWords.split("2–1"))
        assertEquals("2" to "1", VersusWords.split("2 – 1"))
        assertEquals("2" to "1", VersusWords.split("2-1"))
        assertEquals("2" to "1", VersusWords.split("2 - 1"))
        assertEquals("2" to "1", VersusWords.split("2:1"))
        assertEquals("1½" to "½", VersusWords.split("1½–½"))
        assertEquals("187/4" to "201/6", VersusWords.split("187/4 · 201/6"))
        // Not two parts, or no score at all: drawn whole.
        assertNull(VersusWords.split("W/O"))
        assertNull(VersusWords.split("Abandoned"))
        assertNull(VersusWords.split("Abandoned: rain"))
        assertNull(VersusWords.split("Rain-delayed"))
        assertNull(VersusWords.split("2–1 (4–3)"))
        assertNull(VersusWords.split("–1"))
        assertNull(VersusWords.split(null))
    }

    /** A whole score is the card's score that doesn't split, where a side has no score of its own. */
    @Test
    fun aScoreThatDoesntSplitIsWhole() {
        assertNull(VersusWords.whole(football))
        assertNull(VersusWords.whole(cricket))
        assertEquals("W/O", VersusWords.whole(bare.copy(score = "W/O")))
        // Each side has its own: the card's score adds nothing.
        assertNull(VersusWords.whole(cricket.copy(score = "Abandoned")))
    }

    @Test
    fun aClockIsMinutesAndSecondsOrHours() {
        assertEquals("0:00", VersusWords.clockText(0))
        assertEquals("0:05", VersusWords.clockText(5))
        assertEquals("4:12", VersusWords.clockText(252))
        assertEquals("59:59", VersusWords.clockText(3599))
        assertEquals("1:04:12", VersusWords.clockText(3852))
        assertEquals("99:59:59", VersusWords.clockText(359_999))
        assertEquals("0:00", VersusWords.clockText(-3))
    }

    /** The side to move's clock counts down from when the content was set, and stops at 0. */
    @Test
    fun theTurnsClockCountsDownFromWhenItWasSet() {
        val set = 1_800_000_000_000L
        assertEquals(252L, VersusWords.clockLeft(252, true, set, set))
        assertEquals(242L, VersusWords.clockLeft(252, true, set, set + 10_500))
        assertEquals(0L, VersusWords.clockLeft(252, true, set, set + 600_000))
        // Not running, or no time for the content: as given.
        assertEquals(252L, VersusWords.clockLeft(252, false, set, set + 10_000))
        assertEquals(252L, VersusWords.clockLeft(252, true, 0, set))
        assertTrue(VersusWords.clockRuns(chess, 1))
        assertFalse(VersusWords.clockRuns(chess, 0))
        assertFalse(VersusWords.clockRuns(chess.copy(moving = false), 1))
        assertFalse(VersusWords.clockRuns(cricket, 0))
    }

    /** Each side's big figure (its score, else its clock) and its small one (its clock, else its text). */
    @Test
    fun eachSideHasItsFigures() {
        assertEquals(Figures("2", "58%"), VersusWords.figures(football, 0))
        assertEquals(Figures("1", "42%"), VersusWords.figures(football, 1))
        assertEquals(Figures("187/4", "18.2 ov"), VersusWords.figures(cricket, 0))
        // Chess: no score, the clocks are the big figures.
        assertEquals(Figures("4:12", null, bigClock = true), VersusWords.figures(chess, 0))
        // A score and a clock: the clock takes the text's place.
        val match = chess.copy(score = "1½–½")
        assertEquals(Figures("1½", "4:12", smallClock = true), VersusWords.figures(match, 0))
        // A whole score: no big figure in the rows.
        assertEquals(Figures(null, null), VersusWords.figures(bare.copy(score = "W/O"), 0))
    }

    /** Each size has its face, and each face its places for the parts that may give way. */
    @Test
    fun eachSizeHasItsFace() {
        assertEquals(Face.SMALL, VersusWords.face(WidgetSizes.Box.SMALL, 94f))
        assertEquals(Face.NARROW, VersusWords.face(WidgetSizes.Box.WIDE, 193f))
        assertEquals(Face.ROW, VersusWords.face(WidgetSizes.Box.WIDE, 394f))
        assertEquals(Face.SQUARE, VersusWords.face(WidgetSizes.Box.LARGE, 193f))
        assertEquals(Face.LARGE, VersusWords.face(WidgetSizes.Box.LARGE, 394f))
        assertEquals(emptySet<Part>(), VersusWords.present(football, Face.NARROW))
        assertEquals(setOf(Part.CAPTION, Part.TEXT, Part.BADGE), VersusWords.present(football, Face.ROW))
        assertEquals(setOf(Part.CAPTION, Part.TEXT, Part.BADGE), VersusWords.present(football, Face.SQUARE))
        assertEquals(setOf(Part.LINES, Part.CAPTION, Part.TEXT, Part.BADGE), VersusWords.present(football, Face.LARGE))
        // Optional means absent: the bare card has nothing to give way.
        assertEquals(emptySet<Part>(), VersusWords.present(bare, Face.LARGE))
        assertEquals(setOf(Part.DETAIL, Part.CAPTION, Part.BADGE), VersusWords.present(chess, Face.LARGE))
    }

    /** What gives way, in order: lines, details, the caption, the small figures, the badge. */
    @Test
    fun thePartsGiveWayInOrder() {
        val all = Part.entries.toSet()
        assertEquals(all, VersusWords.keep(all) { true })
        assertEquals(all - Part.LINES, VersusWords.keep(all) { it.size <= 4 })
        assertEquals(setOf(Part.TEXT, Part.BADGE), VersusWords.keep(all) { it.size <= 2 })
        assertEquals(emptySet<Part>(), VersusWords.keep(all) { false })
        // A part that isn't there doesn't count: the caption goes first when there are no lines or details.
        assertEquals(setOf(Part.TEXT, Part.BADGE), VersusWords.keep(setOf(Part.CAPTION, Part.TEXT, Part.BADGE)) { Part.CAPTION !in it })
    }

    private val heights = VersusWords.Heights(header = 20, line = 28, sub = 16, rowGap = 8, caption = 16)

    /**
     * A 2×2 Plain card is 164 dp inside, 144 over the card's own age line: the header (20), two rows
     * of 28 with a line under each name (16) and 8 between them, the caption (16), 6 between zones.
     */
    @Test
    fun aSquareCardNeedsItsHeight() {
        val h = heights.copy(zoneGap = 6)
        val withDetails = VersusWords.largeHeight(chess, Face.SQUARE, setOf(Part.DETAIL, Part.CAPTION, Part.BADGE), h, title = true)
        assertEquals(20 + 6 + 28 + 16 + 8 + 28 + 16 + 6 + 16, withDetails)
        assertEquals(144, withDetails)
        // Without the details, the rows are 32 shorter; without the caption, the foot goes.
        assertEquals(112, VersusWords.largeHeight(chess, Face.SQUARE, setOf(Part.CAPTION, Part.BADGE), h, title = true))
        assertEquals(90, VersusWords.largeHeight(chess, Face.SQUARE, setOf(Part.BADGE), h, title = true))
        // No title and no badge: no header.
        assertEquals(64, VersusWords.largeHeight(bare, Face.SQUARE, emptySet(), h, title = false))
        // A whole score is over the rows at 2×2.
        val wholeH = h.copy(whole = 24)
        assertEquals(88, VersusWords.largeHeight(bare.copy(score = "W/O"), Face.SQUARE, emptySet(), wholeH, title = false))
    }

    /** A 4×2 has the detail and the lines on lines of their own; Split's tiles add their room. */
    @Test
    fun aLargeCardHasLinesUnderTheDetail() {
        val sides = football.copy(sides = football.sides.map { it.copy(detail = "3rd") })
        val all = setOf(Part.LINES, Part.DETAIL, Part.CAPTION, Part.TEXT, Part.BADGE)
        assertEquals(20 + (28 + 32) * 2 + 8 + 16, VersusWords.largeHeight(sides, Face.LARGE, all, heights, title = true))
        assertEquals(20 + (28 + 16) * 2 + 8 + 16, VersusWords.largeHeight(sides, Face.LARGE, all - Part.LINES, heights, title = true))
        // Split: three tiles, 16 dp inside each, 4 between them.
        val split = heights.copy(zonePad = 16, zoneGap = 4)
        assertEquals(
            VersusWords.largeHeight(sides, Face.LARGE, all, heights, title = true) + 3 * 16 + 2 * 4,
            VersusWords.largeHeight(sides, Face.LARGE, all, split, title = true)
        )
        // A Bold or Split card's own age: beside the caption at 4×2, under it at 2×2.
        val aged = heights.copy(age = 16)
        assertEquals(VersusWords.largeHeight(sides, Face.LARGE, all, heights, true), VersusWords.largeHeight(sides, Face.LARGE, all, aged, true))
        assertEquals(
            VersusWords.largeHeight(chess, Face.SQUARE, setOf(Part.CAPTION), heights, true) + 16,
            VersusWords.largeHeight(chess, Face.SQUARE, setOf(Part.CAPTION), aged, true)
        )
    }

    /** A 4×1's status column gives the caption two lines, one, or none. */
    @Test
    fun theStatusColumnGivesTheCaptionItsLines() {
        // 68 dp: the badge (18) and two caption lines (32), with 3 between.
        assertEquals(2, VersusWords.captionLines(68, 16, 3, 0, 18, 0))
        // With the age (16) too, one line.
        assertEquals(1, VersusWords.captionLines(68, 16, 3, 0, 18, 16))
        // With a whole score (22) as well, none: the caption gives way.
        assertEquals(0, VersusWords.captionLines(68, 16, 3, 22, 18, 16))
        assertEquals(2, VersusWords.captionLines(68, 16, 3, 0, 0, 0))
    }

    /** The status column is as wide as its widest part, a caption's up to 110 dp, from 84 to 124 dp. */
    @Test
    fun theStatusColumnIsAsWideAsWhatItHolds() {
        assertEquals(84f, VersusWords.statusDp(30f, 0f, 0f, 0f))
        assertEquals(100f, VersusWords.statusDp(30f, 87f, 0f, 0f))
        assertEquals(123f, VersusWords.statusDp(30f, 87f, 300f, 0f))
        assertEquals(124f, VersusWords.statusDp(200f, 0f, 0f, 0f))
    }

    /** The small figures' column gives way where a name would have less than it needs, up to 72 dp. */
    @Test
    fun theFigureColumnLeavesTheNamesTheirRoom() {
        // A 4×1 with a status column: 250 dp of row, 32 for the mark, 52 for "18.2 ov", 70 for "187/4".
        assertTrue(VersusWords.figureFits(250f, 32f, 60f, 52f, 70f))
        assertFalse(VersusWords.figureFits(200f, 32f, 90f, 52f, 70f))
        // A short name needs only its own width.
        assertTrue(VersusWords.figureFits(200f, 32f, 40f, 52f, 70f))
    }

    /** Both scores take one size: the largest that fits, then smaller in steps down to 12 sp. */
    @Test
    fun bothScoresTakeOneSize() {
        assertEquals(22, VersusWords.scoreSize(listOf(22, 20, 18)) { true })
        assertEquals(20, VersusWords.scoreSize(listOf(22, 20, 18)) { it <= 20 })
        assertEquals(14, VersusWords.scoreSize(listOf(22, 20, 18)) { it <= 15 })
        assertEquals(12, VersusWords.scoreSize(listOf(22, 20, 18)) { false })
    }

    /**
     * A screen reader hears the badge, each side as "name score, figure, detail" with whose turn it
     * is after the side to move, each side's lines after it, then the caption.
     */
    @Test
    fun aScreenReaderHearsTheWholeMatch() {
        assertEquals(
            listOf("67'", "North 2, 58%", "Moss 23', 61'", "South 1, 42%", "Reed 54' (pen)", "Pike 59' red card", "Second half"),
            VersusWords.said(football)
        )
        assertEquals(
            listOf("3+2", "Iris Novak 4:12, 2214 · White", "Tomas Brandt 3:58, 2187 · Black, Tomas Brandt's turn", "31. Nf3"),
            VersusWords.said(chess)
        )
        assertEquals(
            listOf("LIVE", "Harbour 187/4, 18.2 ov, Harbour's turn", "Ames 64* (38)", "Valley 201/6, 20 ov", "Need 15 off 10"),
            VersusWords.said(cricket)
        )
        // A whole score: the sides around it, and a side not said again with nothing more.
        assertEquals(listOf("North W/O South"), VersusWords.said(bare.copy(score = "W/O")))
        assertEquals(
            "League: 67', North 2, 58%, Moss 23', 61', South 1, 42%, Reed 54' (pen), Pike 59' red card, Second half",
            WidgetWords.description("League", football, 0, ZoneId.of("UTC"))
        )
    }

    /** Versus has no Shape and no Poster: a card with either stored draws as Plain. */
    @Test
    fun aLookVersusLacksDrawsPlain() {
        assertEquals(WidgetLook.PLAIN, WidgetLook.SHAPE.drawnFor(WidgetKind.VERSUS))
        assertEquals(WidgetLook.PLAIN, WidgetLook.POSTER.drawnFor(WidgetKind.VERSUS))
        assertEquals(WidgetLook.BOLD, WidgetLook.BOLD.drawnFor(WidgetKind.VERSUS))
        assertEquals(WidgetLook.SPLIT, WidgetLook.SPLIT.drawnFor(WidgetKind.VERSUS))
    }
}
