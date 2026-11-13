package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.shownText
import com.mtwabpin.kolahal.commands.LogFilter
import com.mtwabpin.kolahal.terminal.LogRow
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import java.text.SimpleDateFormat

/**
 * A log search (`/words` at the prompt), shown in place of the log.
 * - It lists the matching lines, newest at the bottom, as in the log.
 * - The search words are marked, and each line shows its time.
 * - Tapping a line shows it in the log.
 * - Long press and swipes work as they do in the log.
 */
@Composable
fun LogSearchResults(
    search: TerminalViewModel.LogSearch,
    timeFormat: SimpleDateFormat,
    gutter: Boolean,
    indents: LineIndents,
    menuEntryId: Long?,
    apps: (LogEntry) -> List<AppEntry>,
    onOpen: (LogEntry) -> Unit,
    onLongPress: (LogEntry) -> Unit,
    onAppTap: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onSwipeCopy: (LogEntry) -> Unit,
    onSwipeDelete: (LogEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reversed = remember(search.rows) { search.rows.asReversed() }
    val today = rememberToday()
    val state = rememberLazyListState()
    // When the words change, scroll back to the newest match, next to the prompt.
    LaunchedEffect(search.words) { state.scrollToItem(0) }
    LazyColumn(
        state = state,
        reverseLayout = true,
        modifier = modifier.testTag("log-search"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        items(reversed, key = { it.key }, contentType = { if (it is LogRow.Entry) it.entry.kind else it.javaClass }) { row ->
            when (row) {
                is LogRow.Day -> DayDivider(row, today)
                is LogRow.Unread -> Unit
                is LogRow.Entry -> Column {
                    LogLine(
                        row = row,
                        timeMode = TimeMode.ALL,
                        timeFormat = timeFormat,
                        dim = false,
                        topGap = 0.dp,
                        gutter = gutter,
                        indents = indents,
                        highlighted = menuEntryId == row.entry.id,
                        apps = apps,
                        onTap = onOpen,
                        onLongPress = onLongPress,
                        onAppTap = onAppTap,
                        onAppLongPress = onAppLongPress,
                        onSwipeCopy = onSwipeCopy,
                        onSwipeDelete = onSwipeDelete,
                        marks = search.words,
                    )
                    FoundIn(
                        row.entry,
                        search.words,
                        Modifier
                            .fillMaxWidth()
                            .then(if (!gutter) Modifier else Modifier.drawBehind {
                                drawRect(gutterColor(row.gutter), topLeft = Offset.Zero, size = Size(2.dp.toPx(), size.height))
                            })
                            .padding(start = if (gutter) 10.dp else 0.dp, bottom = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * Shows where the search words that the line's text doesn't contain were found: its link or its
 * source. Without this, a line found only by its link would not show why it matched.
 */
@Composable
private fun FoundIn(entry: LogEntry, words: List<String>, modifier: Modifier) {
    val text = remember(entry, words) {
        val shown = listOfNotNull(entry.shownText, entry.source.takeIf { entry.kind == LogKind.SCRIPT }, entry.ctx.values[LogContext.DESC])
        val unseen = words.filter { w -> shown.none { it.contains(w, ignoreCase = true) } }
        if (unseen.isEmpty()) null
        else listOfNotNull(entry.ctx.url?.let { it to it }, entry.source?.let { it to "from $it" }, entry.ctx.script?.let { it to "from $it" })
            .filter { (place, _) -> unseen.any { place.contains(it, ignoreCase = true) } }
            .map { it.second }.distinct()
            .joinToString(" · ").ifEmpty { null }
    } ?: return
    Text(
        markMatches(AnnotatedString(text), words),
        style = Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 2).sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Shown under the log while searching: how many lines match, or how to search if nothing is typed yet. */
@Composable
fun LogSearchSummary(search: TerminalViewModel.LogSearch, modifier: Modifier = Modifier) {
    val shown = remember(search.rows) { search.rows.count { it is LogRow.Entry } }
    val from = if (search.sources.isEmpty()) "" else " from " + search.sources.joinToString(" or ") { it.label }
    val text = when {
        search.isEmpty -> "Search the log: type words after / to find lines, commands, sources and links · @ and a name searches one source's lines"
        search.total == 0 -> "No line$from" + (if (search.words.isEmpty()) "" else " has “${search.words.joinToString(" ")}”") + " · Esc or back to close"
        else -> "${search.total} of ${search.searched} lines$from" +
            (if (shown < search.total) " · showing the newest $shown" else "") +
            " · tap one to see it in the log"
    }
    Text(
        text,
        style = Term.small,
        modifier = modifier.fillMaxWidth().testTag("log-search-summary").padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Shown above the prompt while `log only …` is on: what the log shows, and ✕ to show every line again. */
@Composable
fun LogFilterBar(filter: LogFilter, count: Int, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().background(Term.surface).testTag("log-filter").padding(start = 12.dp),
    ) {
        Text(
            buildAnnotatedString {
                append("only ")
                withStyle(SpanStyle(color = Term.prompt, fontWeight = FontWeight.Bold)) { append(filter.label) }
                append(" · $count line" + if (count == 1) "" else "s")
            },
            style = Term.small,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "✕",
            style = Term.text.copy(color = Term.fg),
            modifier = Modifier
                .clickable(onClick = onClear)
                .semantics { contentDescription = "Show every line" }
                .heightIn(min = 40.dp)
                .wrapContentHeight()
                .padding(horizontal = 16.dp),
        )
    }
}
