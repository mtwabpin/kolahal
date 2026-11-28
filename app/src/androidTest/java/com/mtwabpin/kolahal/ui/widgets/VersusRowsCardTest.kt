package com.mtwabpin.kolahal.ui.widgets

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The versus card's rows ([VersusBody]): one row a side in shared columns. The marks, the names and
 * the scores of both rows share their center lines and their columns, at 4×1 and 2×2, in every
 * look the card offers; a right-to-left phone mirrors them. Sizes as on a phone 411 dp wide: 2×1 is
 * 196 by 94, 4×1 403 by 94, 2×2 196 by 196, 4×2 403 by 196. All names and scores are made up.
 */
@RunWith(AndroidJUnit4::class)
class VersusRowsCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private data class Shown(
        val spec: WidgetSpec,
        val width: Dp,
        val height: Dp,
        val look: WidgetLook,
        val direction: LayoutDirection,
    )

    private var shown by mutableStateOf<Shown?>(null)
    private var composed = false

    /** [spec]'s card, [width] by [height], in [look]; a later call draws another card in its place. */
    private fun show(
        spec: WidgetSpec,
        width: Dp,
        height: Dp,
        look: WidgetLook = WidgetLook.PLAIN,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        val card = Shown(spec, width, height, look, direction)
        if (composed) {
            compose.runOnIdle { shown = card }
            compose.waitForIdle()
            return
        }
        shown = card
        composed = true
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    shown?.let { card ->
                        CompositionLocalProvider(
                            LocalWidgetLook provides card.look,
                            LocalLayoutDirection provides card.direction,
                        ) {
                            Box(Modifier.width(card.width)) {
                                WidgetCard(
                                    title = card.spec.base.title ?: "card",
                                    height = card.height,
                                    spec = card.spec,
                                    note = null,
                                    description = "card",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** The bounds of the part tagged [part] of side [side] ([versusTag]), in the root. */
    private fun bounds(part: String, side: Int): Rect =
        compose.onNode(hasTestTag(versusTag(part, side)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun card(): Rect = compose.onNode(hasTestTag("widget-card")).fetchSemanticsNode().boundsInRoot

    private val hasWords = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

    /** Every text the card draws, each as its own. */
    private fun texts(): List<String> =
        compose.onAllNodes(hasWords, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    /** Within half a dp, in pixels. */
    private fun near(a: Float, b: Float) = abs(a - b) <= compose.density.density * 0.5f + 0.5f

    /**
     * Each row's mark, name and score share one center line; the two rows' marks, names and
     * scores share their columns: the marks' centers, the names' start, the scores' end.
     */
    private fun assertRowsAligned(what: String, rtl: Boolean = false) {
        for (side in 0..1) {
            val mark = bounds("mark", side).center.y
            val name = bounds("name", side).center.y
            val score = bounds("score", side).center.y
            assertTrue("$what: side ${side + 1}'s name ($name) is off its mark's line ($mark)", near(mark, name))
            assertTrue("$what: side ${side + 1}'s score ($score) is off its mark's line ($mark)", near(mark, score))
        }
        assertTrue("$what: the marks are in different columns", near(bounds("mark", 0).center.x, bounds("mark", 1).center.x))
        val start = { r: Rect -> if (rtl) r.right else r.left }
        val end = { r: Rect -> if (rtl) r.left else r.right }
        assertTrue("$what: the names start in different columns", near(start(bounds("name", 0)), start(bounds("name", 1))))
        assertTrue("$what: the scores end in different columns", near(end(bounds("score", 0)), end(bounds("score", 1))))
        // Both scores have one size: one line height.
        assertTrue("$what: the scores have different sizes", near(bounds("score", 0).height, bounds("score", 1).height))
        // The rows are one under the other, the first on top.
        assertTrue("$what: the rows overlap", bounds("mark", 0).bottom <= bounds("mark", 1).top)
        // In left-to-right, the mark is at the start and the score at the end; mirrored in right-to-left.
        val mark = bounds("mark", 0)
        val score = bounds("score", 0)
        if (rtl) assertTrue("$what: not mirrored", mark.left > score.right) else assertTrue("$what: the score is before the mark", mark.right < score.left)
        // Nothing is outside the card.
        val c = card()
        for (part in listOf("mark", "name", "score")) for (side in 0..1) {
            val b = bounds(part, side)
            assertTrue("$what: side ${side + 1}'s $part is outside the card", b.top >= c.top - 1 && b.bottom <= c.bottom + 1)
        }
    }

    /** A match with a long name, a long score on one side and one digit on the other, and everything optional. */
    private val cricket = WidgetSpec.Versus(
        WidgetBase(title = "Cup final"),
        listOf(
            WidgetSpec.Side("Harbour Town", score = "187/4", text = "18.2 ov", detail = "1st · 12 pts", lines = listOf("Ames 64* (38)")),
            WidgetSpec.Side("Valley", score = "9", text = "2 ov", detail = "4th · 7 pts", lines = listOf("Ruiz 3/28 (4)")),
        ),
        turn = 1,
        moving = true,
        line = "Need 15 off 10",
        badge = "LIVE",
    )

    /** The bare card: two names and a score. */
    private val bare = WidgetSpec.Versus(WidgetBase(title = "Match"), listOf(WidgetSpec.Side("North"), WidgetSpec.Side("South")), score = "2–1")

    @Test
    fun theRowsShareTheirLinesAt4x1InEveryLook() {
        for (look in WidgetLook.offeredFor(WidgetKind.VERSUS)) {
            show(cricket, 403.dp, 94.dp, look)
            assertRowsAligned("4×1 ${look.id}")
            show(bare, 403.dp, 94.dp, look)
            assertRowsAligned("4×1 bare ${look.id}")
        }
    }

    @Test
    fun theRowsShareTheirLinesAt2x2InEveryLook() {
        for (look in WidgetLook.offeredFor(WidgetKind.VERSUS)) {
            show(cricket, 196.dp, 196.dp, look)
            assertRowsAligned("2×2 ${look.id}")
            show(bare, 196.dp, 196.dp, look)
            assertRowsAligned("2×2 bare ${look.id}")
        }
    }

    @Test
    fun theRowsAreMirroredRightToLeft() {
        show(cricket, 403.dp, 94.dp, direction = LayoutDirection.Rtl)
        assertRowsAligned("4×1 right to left", rtl = true)
        show(cricket, 196.dp, 196.dp, direction = LayoutDirection.Rtl)
        assertRowsAligned("2×2 right to left", rtl = true)
    }

    /** "2–1" is split between the sides; each side's own score wins over its part. */
    @Test
    fun theScoreIsSplitBetweenTheSides() {
        show(bare, 403.dp, 94.dp)
        assertEquals(listOf("2"), textsIn(versusTag("score", 0)))
        assertEquals(listOf("1"), textsIn(versusTag("score", 1)))
        assertFalse(texts().toString(), "2–1" in texts())
        assertTrue(texts().toString(), texts().containsAll(listOf("North", "South", "2", "1")))
    }

    /** A score that doesn't split is drawn whole, in the status column at 4×1 and over the rows at 2×2. */
    @Test
    fun aScoreThatDoesntSplitIsWhole() {
        show(bare.copy(score = "W/O"), 403.dp, 94.dp)
        assertTrue(texts().toString(), "W/O" in texts())
        show(bare.copy(score = "W/O"), 196.dp, 196.dp)
        assertTrue(texts().toString(), "W/O" in texts())
    }

    /**
     * What each size shows: the badge and caption at 4×1 in the status column, the details at 2×2
     * and 4×2, the lines at 4×2 only; a 2×1 has the rows alone. Optional means absent.
     */
    @Test
    fun eachSizeShowsWhatItHasRoomFor() {
        show(cricket, 403.dp, 94.dp)
        assertTrue(texts().toString(), texts().containsAll(listOf("LIVE", "Need 15 off 10", "18.2 ov", "187/4", "9")))
        assertFalse(texts().toString(), "1st · 12 pts" in texts())
        // 4×2: each side's lines under its name, the small figures in a column. With the details too
        // the height is short, and the lines give way first.
        val lined = cricket.copy(sides = cricket.sides.map { it.copy(detail = null) })
        show(lined, 403.dp, 196.dp)
        assertTrue(texts().toString(), texts().containsAll(listOf("Cup final", "LIVE", "Ames 64* (38)", "18.2 ov", "Need 15 off 10")))
        show(cricket, 403.dp, 196.dp)
        assertTrue(texts().toString(), texts().containsAll(listOf("1st · 12 pts", "4th · 7 pts")))
        assertFalse(texts().toString(), "Ames 64* (38)" in texts())
        // 2×2: the small figure and the detail on one line under the name; no lines.
        show(cricket, 196.dp, 196.dp)
        assertTrue(texts().toString(), "18.2 ov · 1st · 12 pts" in texts())
        assertFalse(texts().toString(), texts().any { "Ames" in it })
        show(cricket, 196.dp, 94.dp)
        assertTrue(texts().toString(), texts().containsAll(listOf("187/4", "9")))
        assertFalse(texts().toString(), "LIVE" in texts())
        // The bare card at 4×1: no status column, nothing but the rows.
        show(bare, 403.dp, 94.dp)
        assertEquals(setOf("N", "North", "2", "S", "South", "1"), texts().toSet())
    }

    /** A chess game: no score, the clocks are the big figures, drawn 4:12. */
    @Test
    fun clocksAreTheScoresOfAGameWithoutOne() {
        val chess = WidgetSpec.Versus(
            WidgetBase(title = "Blitz"),
            listOf(
                WidgetSpec.Side("Iris Novak", mark = "IN", clock = 252),
                WidgetSpec.Side("Tomas Brandt", mark = "TB", clock = 3852),
            ),
            turn = 2,
        )
        show(chess, 403.dp, 94.dp)
        assertTrue(texts().toString(), texts().containsAll(listOf("4:12", "1:04:12")))
        assertRowsAligned("4×1 chess")
    }

    /** How side [side]'s name is laid out: its lines, and whether one is cut with "…". */
    private fun nameLayout(side: Int): TextLayoutResult {
        val node = compose.onNode(hasWords and hasAnyAncestor(hasTestTag(versusTag("name", side))), useUnmergedTree = true)
            .fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** A 2×2 match whose first name is too long for its line, as the library's cricket script has it. */
    private val longName = WidgetSpec.Versus(
        WidgetBase(title = "Cup final"),
        listOf(
            WidgetSpec.Side("Harbour Town", score = "187/4", text = "18.2 ov"),
            WidgetSpec.Side("Valley", score = "201/6", text = "20 ov"),
        ),
        turn = 1,
        line = "Need 15 off 10",
        badge = "LIVE",
    )

    /**
     * At 2×2 a name too long for its line takes a second line where the card has spare height:
     * both its words show, none cut, and the mark and the score are centered on its two lines.
     * Where there is no spare height (Split's tiles), it is cut with "…" as before, and the caption
     * and the small figures stay: a name's second line never pushes another part out.
     */
    @Test
    fun aLongNameWrapsAt2x2WhereThereIsSpareHeight() {
        show(longName, 196.dp, 196.dp)
        val wrapped = nameLayout(0)
        assertEquals("the long name's lines", 2, wrapped.lineCount)
        assertFalse("the long name is cut", wrapped.isLineEllipsized(0) || wrapped.isLineEllipsized(1))
        assertEquals("the short name's lines", 1, nameLayout(1).lineCount)
        assertTrue(
            "the score (${bounds("score", 0).center.y}) is off the two-line name's center (${bounds("name", 0).center.y})",
            near(bounds("score", 0).center.y, bounds("name", 0).center.y)
        )
        assertTrue("the name is not on two lines", bounds("name", 0).height > bounds("score", 0).height)
        assertRowsAligned("2×2 with a name on two lines")
        assertTrue(texts().toString(), texts().containsAll(listOf("Need 15 off 10", "18.2 ov", "20 ov")))

        show(longName, 196.dp, 196.dp, WidgetLook.SPLIT)
        val cut = nameLayout(0)
        assertEquals("the cut name's lines", 1, cut.lineCount)
        assertTrue("the name isn't cut with …", cut.isLineEllipsized(0))
        assertTrue(texts().toString(), texts().containsAll(listOf("Need 15 off 10", "18.2 ov", "20 ov")))
        assertRowsAligned("2×2 Split with a cut name")
    }

    /** The texts drawn inside the part tagged [tag]. */
    private fun textsIn(tag: String): List<String> =
        compose.onAllNodes(hasWords and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
}
