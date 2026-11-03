package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.script.LineDiff.Kind.ADDED
import com.mtwabpin.kolahal.script.LineDiff.Kind.REMOVED
import com.mtwabpin.kolahal.script.LineDiff.Kind.SAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineDiffTest {
    private fun kinds(old: String, new: String) = LineDiff.lines(old, new).map { "${it.kind.name[0]} ${it.text}" }

    @Test fun theSameTextHasNoChanges() {
        val lines = LineDiff.lines("a\nb\nc", "a\nb\nc")
        assertTrue(lines.all { it.kind == SAME })
        assertEquals(emptyList<LineDiff.Hunk>(), LineDiff.hunks(lines))
    }

    @Test fun anAddedLine() = assertEquals(listOf("S a", "A x", "S b"), kinds("a\nb", "a\nx\nb"))

    @Test fun aRemovedLine() = assertEquals(listOf("S a", "R b", "S c"), kinds("a\nb\nc", "a\nc"))

    @Test fun aChangedLineIsRemovedThenAdded() = assertEquals(listOf("S a", "R b", "A B", "S c"), kinds("a\nb\nc", "a\nB\nc"))

    @Test fun changesApartKeepWhatLiesBetween() =
        assertEquals(listOf("R a", "A A", "S b", "S c", "R d", "A D", "S e"), kinds("a\nb\nc\nd\ne", "A\nb\nc\nD\ne"))

    @Test fun aLineAddedAtTheEnd() = assertEquals(listOf("S a", "A -- mine"), kinds("a\n", "a\n-- mine"))

    @Test fun aLastNewlineIsNoLine() = assertEquals(listOf("S a", "S b"), kinds("a\nb\n", "a\nb"))

    @Test fun anEmptyLineAtTheEndStillCounts() = assertEquals(listOf("S a", "A "), kinds("a\n", "a\n\n"))

    @Test fun fromNothing() = assertEquals(listOf("A a", "A b"), kinds("", "a\nb"))

    @Test fun hunksKeepThreeLinesAroundAChangeAndCountTheRest() {
        val old = (1..20).joinToString("\n")
        val new = old.replace("\n10\n", "\nten\n")
        val hunks = LineDiff.hunks(LineDiff.lines(old, new))
        assertEquals(listOf(6, 7), hunks.map { it.skipped })
        assertEquals(listOf("7", "8", "9", "10", "ten", "11", "12", "13"), hunks[0].lines.map { it.text })
        assertEquals(listOf(SAME, SAME, SAME, REMOVED, ADDED, SAME, SAME, SAME), hunks[0].lines.map { it.kind })
        assertTrue("the count of the lines after it", hunks[1].lines.isEmpty())
    }

    @Test fun closeChangesShareAHunk() {
        val old = (1..20).joinToString("\n")
        val new = old.replace("\n5\n", "\nfive\n").replace("\n10\n", "\nten\n")
        val hunks = LineDiff.hunks(LineDiff.lines(old, new))
        assertEquals(listOf(1, 7), hunks.map { it.skipped })
        assertEquals("2", hunks[0].lines.first().text)
        assertEquals("13", hunks[0].lines.last().text)
    }

    @Test fun aChangeAtTheStartSkipsNothingBeforeIt() {
        val hunks = LineDiff.hunks(LineDiff.lines("a\nb\nc\nd\ne\nf\ng\nh", "A\nb\nc\nd\ne\nf\ng\nh"))
        assertEquals(listOf(0, 4), hunks.map { it.skipped })
    }

    @Test fun aMiddleTooBigForTheTableIsAllRemovedThenAllAdded() {
        val old = (1..1500).joinToString("\n") { "old $it" }
        val new = (1..1500).joinToString("\n") { "new $it" }
        val lines = LineDiff.lines("top\n$old\nend", "top\n$new\nend")
        assertEquals(SAME, lines.first().kind)
        assertEquals(SAME, lines.last().kind)
        assertEquals(List(1500) { REMOVED } + List(1500) { ADDED }, lines.drop(1).dropLast(1).map { it.kind })
    }

    /** A copy saved with Windows line endings shows no lines changed, nor an empty one added. */
    @Test fun windowsLineEndingsAreLineEndings() = assertEquals(listOf("S a", "S b"), kinds("a\r\nb\r\n", "a\nb"))

    @Test fun anEmptyTextHasNoLines() {
        assertEquals(listOf("R a"), kinds("a", ""))
        assertEquals(emptyList<String>(), kinds("", ""))
    }
}
