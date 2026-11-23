/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.commands.LogFilter
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.LivePreview
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.commands.UpdateCommands
import com.mtwabpin.kolahal.terminal.tty.BandCells
import com.mtwabpin.kolahal.ui.home.LocalScramble
import com.mtwabpin.kolahal.ui.home.scrambledFilter
import com.mtwabpin.kolahal.ui.home.searchSummary

/** The notice's and the filter's height, as the GUI's bands have it. */
private val BAND_HEIGHT = 44.dp

/**
 * What was just done, on the surface color, with Undo at the end after a delete or a clear, or
 * "Copied" after a swipe copy ([BandCells.notice]). TalkBack reads it when it appears, after what
 * it is saying. Tag `undo-row`, as the GUI's.
 */
@Composable
internal fun TtyNotice(text: String, undo: Boolean, onUndo: () -> Unit) {
    val frame = LocalTtyFrame.current ?: return
    val cells = remember(text, undo, frame) { BandCells.notice(text, undo, frame.cols) }
    TtyBand(
        cells,
        with(LocalDensity.current) { BAND_HEIGHT.roundToPx() },
        Modifier
            .testTag("undo-row")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                this.text = AnnotatedString(text)
            },
        fill = Ink.SURFACE,
    ) { SpotAction(onUndo, text = "Undo", toEnd = true) }
}

/** The download `update install` is running, if there is one: its words, and its progress line under them. */
@Composable
internal fun TtyUpdateProgress() {
    val progress by UpdateCommands.progress.collectAsStateWithLifecycle()
    val p = progress ?: return
    val frame = LocalTtyFrame.current ?: return
    val cells = remember(p, frame) { BandCells.download(p.downloaded, p.total, p.percent, frame.cols) }
    TtyBand(
        cells,
        cells.rows * frame.cellH + with(LocalDensity.current) { 8.dp.roundToPx() },
        Modifier
            .testTag("update-progress")
            .semantics { text = AnnotatedString("Downloading update ${p.percent}%") },
    )
}

/** The search's summary under the log: how many lines match, or how to search. Tag `log-search-summary`. */
@Composable
internal fun TtySearchSummary(search: TerminalViewModel.LogSearch) {
    val frame = LocalTtyFrame.current ?: return
    val text = remember(search) { searchSummary(search) }
    val cells = remember(text, frame) { BandCells.summary(text, frame.cols) }
    TtyBand(
        cells,
        cells.rows * frame.cellH + with(LocalDensity.current) { 8.dp.roundToPx() },
        Modifier
            .testTag("log-search-summary")
            .semantics { this.text = AnnotatedString(text) },
    )
}

/** What the log shows while `log only …` is on, and `✕` that shows every line again. Tag `log-filter`. */
@Composable
internal fun TtyFilterBar(filter: LogFilter, count: Int, onClear: () -> Unit) {
    val frame = LocalTtyFrame.current ?: return
    // While home is scrambled, so is the name of what the log shows only.
    val label = scrambledFilter(filter.label, LocalScramble.current)
    val cells = remember(label, count, frame) { BandCells.filter(label, count, frame.cols) }
    val words = "only $label · $count line" + if (count == 1) "" else "s"
    TtyBand(
        cells,
        with(LocalDensity.current) { 40.dp.roundToPx() },
        Modifier
            .testTag("log-filter")
            .semantics { text = AnnotatedString(words) },
        fill = Ink.SURFACE,
    ) { SpotAction(onClear, description = "Show every line", toEnd = true) }
}

/**
 * Tells the user that Kolahal is not the phone's home app: a band in the accent color right above
 * the prompt; a tap asks Android to make it home. Tag `default-home`.
 */
@Composable
internal fun TtyDefaultHomeBand(visible: Boolean, onClick: () -> Unit) =
    AccentBand(visible, "Kolahal isn't your home app  ·  Tap to make it", "default-home", onClick, "make Kolahal the home app")

/** Tells the user that home previews the typed line, not the settings in use. Tag `live-preview`. */
@Composable
internal fun TtyLivePreviewBand(live: LivePreview?) {
    // The last words stay while the band slides away.
    val shown = remember { arrayOfNulls<LivePreview>(1) }
    if (live != null) shown[0] = live
    AccentBand(live != null, "Preview: ${shown[0]?.label}  ·  Enter to apply", "live-preview")
}

/** A band of [BandCells.accent] that slides in from the prompt and back. TalkBack reads it when it appears. */
@Composable
private fun AccentBand(visible: Boolean, text: String, tag: String, onClick: (() -> Unit)? = null, clickLabel: String? = null) {
    val frame = LocalTtyFrame.current ?: return
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        val cells = remember(text, frame, onClick != null) { BandCells.accent(text, frame.cols, onClick != null) }
        TtyBand(
            cells,
            frame.cellH + with(LocalDensity.current) { 12.dp.roundToPx() },
            Modifier
                .testTag(tag)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    this.text = AnnotatedString(text)
                },
            fill = Ink.PROMPT,
        ) { if (onClick != null) SpotAction(onClick, clickLabel = clickLabel, toStart = true, toEnd = true, role = null) else null }
    }
}

/**
 * The chip over the log's bottom that scrolls back to the newest line, when the log is scrolled
 * away from it ([BandCells.jump]): its cells in the middle of the grid, on whole columns, and a
 * touch 48 dp tall at least. Tag `jump-latest`, as the GUI's.
 */
@Composable
internal fun TtyJumpLatest(onClick: () -> Unit) {
    val frame = LocalTtyFrame.current ?: return
    val cells = remember { BandCells.jump() }
    val m = frame.grid.metrics
    val height = maxOf(with(LocalDensity.current) { 48.dp.roundToPx() }, m.cellH)
    val x = frame.originX + ((frame.cols - cells.cols) / 2).coerceAtLeast(0) * m.cellW
    val top = (height - m.cellH) / 2
    // As wide as the grid, so the chip is inside it wherever its columns put it; only the chip takes taps.
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .offset { IntOffset(x, 0) }
                .exactSize(cells.cols * m.cellW, height)
                .testTag("jump-latest")
                .clickable(onClickLabel = "scroll to the bottom", onClick = onClick)
                .drawBehind { paintCells(frame.grid, (if (layoutDirection == LayoutDirection.Rtl) cells.mirrored else cells).grid, 0f, top.toFloat()) },
        )
    }
}
