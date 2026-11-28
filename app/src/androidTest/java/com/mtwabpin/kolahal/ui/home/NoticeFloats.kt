package com.mtwabpin.kolahal.ui.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * On home in either style, with the three lines [ids] (oldest first) newest in the log: the notice over the log's
 * bottom ("Copied", "Line deleted" with Undo) moves no line when it comes and when it goes, and it is drawn over
 * the log's box, not under it. Deletes the newest of [ids]; the caller restores or deletes the rest.
 */
internal fun assertNoticeFloats(compose: ComposeContentTestRule, vm: TerminalViewModel, ids: List<Long>) {
    fun shows(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    fun says(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    fun top() = compose.onNodeWithTag("line-${ids[0]}").getUnclippedBoundsInRoot()

    compose.waitUntil(5_000) { shows("line-${ids[0]}") && shows("line-${ids[2]}") }
    compose.waitForIdle()
    val before = top()

    // A swipe right copies by default (the widgets pages are left of the log): "Copied" comes, no line goes.
    compose.onNodeWithTag("line-${ids[1]}").performTouchInput { swipeRight(startX = left + 10f, endX = right - 10f) }
    compose.waitUntil(5_000) { says("Copied") }
    compose.waitForIdle()
    assertEquals("the notice coming moved the log", before, top())
    val log = compose.onNodeWithTag("log").getUnclippedBoundsInRoot()
    val notice = compose.onNodeWithTag("undo-row").getUnclippedBoundsInRoot()
    assertTrue("the notice is drawn over the log's bottom: $notice in $log", notice.top < log.bottom && notice.bottom <= log.bottom)
    compose.waitUntil(COPIED_MS + 3_000) { !says("Copied") }
    compose.waitForIdle()
    assertEquals("the notice going moved the log", before, top())

    // A delete: the line goes at once, then nothing moves while Undo shows nor when the offer ends.
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val newest = LogRepository.get(app).recent(50).first { it.id == ids[2] }
    compose.runOnIdle { vm.swipeDelete(newest) }
    compose.waitUntil(5_000) { says("Line deleted") && !shows("line-${ids[2]}") }
    compose.waitForIdle()
    val during = top()
    compose.waitUntil(12_000) { vm.undo.value == null && !says("Line deleted") }
    compose.waitForIdle()
    assertEquals("the offer's end moved the log", during, top())
}

/**
 * On home in either style, with the three lines [ids] (oldest first) newest in the log: after the newest is deleted,
 * the next one is under the notice, and a scroll up brings it above the notice ([NoticeRoom]); once the notice goes,
 * the log is back down with that line at its bottom. Deletes the newest of [ids]; the caller deletes the rest.
 */
internal fun assertRoomUnderNotice(compose: ComposeContentTestRule, vm: TerminalViewModel, ids: List<Long>) {
    fun shows(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    fun says(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    fun newest() = compose.onNodeWithTag("line-${ids[1]}").getUnclippedBoundsInRoot()

    compose.waitUntil(5_000) { shows("line-${ids[1]}") && shows("line-${ids[2]}") }
    compose.waitForIdle()
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val deleted = LogRepository.get(app).recent(50).first { it.id == ids[2] }
    compose.runOnIdle { vm.swipeDelete(deleted) }
    // The deleted line fades out, and the one above it takes its place at the bottom, under the notice.
    compose.waitUntil(5_000) { says("Line deleted") && !shows("line-${ids[2]}") }
    compose.waitForIdle()
    val atBottom = newest()
    val notice = compose.onNodeWithTag("undo-row").getUnclippedBoundsInRoot()
    assertTrue("the newest line is under the notice: $atBottom, $notice", atBottom.bottom > notice.top)

    // Scrolled on past it, the log brings the line up above the notice.
    compose.onNodeWithTag("log").performTouchInput {
        swipeUp(startY = centerY, endY = centerY - 200.dp.toPx(), durationMillis = 300)
    }
    compose.waitForIdle()
    val lifted = newest()
    assertTrue("the newest line came above the notice: $lifted, $notice", lifted.bottom <= notice.top + 1.dp)

    // The notice goes, and the log settles back down to its bottom.
    compose.waitUntil(12_000) { vm.undo.value == null && !says("Line deleted") }
    compose.waitForIdle()
    assertEquals("the log is back at its bottom", atBottom, newest())
}
