package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.CommandCatalog
import com.mtwabpin.kolahal.terminal.Suggestion

/** Matches a profile tag at the end of a completion's label (`Maps [W]`), the same tag app rows show. */
private val LABEL_TAG = Regex(" \\[([PWSC])]$")

/** Returns [label] with its trailing profile tag drawn small, bold and in the profile's colour, like app rows draw it. */
@Composable
private fun taggedLabel(label: String): androidx.compose.ui.text.AnnotatedString {
    val tag = LABEL_TAG.find(label) ?: return androidx.compose.ui.text.AnnotatedString(label)
    val kind = ProfileKind.entries.first { it.tag == tag.groupValues[1] }
    return androidx.compose.ui.text.buildAnnotatedString {
        append(label.substring(0, tag.range.first))
        withStyle(androidx.compose.ui.text.SpanStyle(color = Term.profileColor(kind), fontWeight = FontWeight.Bold, fontSize = (Term.fontSize - 2).sp)) {
            append(tag.value)
        }
    }
}

/** Short help shown next to a completion: the description of a command or of a config key. */
internal fun completionHelp(label: String, replacement: String): String? {
    // Top-level command completions replace the whole input with "name ".
    if (replacement.trim() == label) CommandCatalog.COMMANDS.firstOrNull { it.name == label }?.let { return it.help }
    return Keys.ALL.firstOrNull { it.key == label }?.help
}

/**
 * `suggestions.style list`: matches stacked above the prompt.
 * - The best match is at the bottom, nearest the thumb. TAB takes it. It is highlighted when Enter
 *   also acts on it.
 * - The list is at most ~40% of the screen tall, and no taller than the space above the prompt.
 *   Extra matches scroll.
 * - Tap picks a match. Long-press an app to open its actions.
 */
@Composable
fun SuggestionList(items: List<Suggestion>, onPick: (Suggestion) -> Unit, onAppLongPress: (AppEntry) -> Unit, icons: Boolean = false, wrapHelp: Boolean = false) {
    if (items.isEmpty()) return
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.4f).toDp() }
    // A lazy list keeps its first visible item in place when the data changes. But the best match
    // (what Enter acts on) must always be visible, so scroll back to it when it changes.
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(items.first().key) { state.scrollToItem(0) }
    LazyColumn(
        state = state,
        reverseLayout = true,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .testTag("suggestions"),
    ) {
        itemsIndexed(items, key = { _, s -> s.key }) { index, s ->
            // A row is at most 44 dp tall (the chip height, capped below). The icon is 8 dp smaller.
            val iconSize = minOf(Term.fontSize + 10, minOf(44, Term.chipHeight) - 8).dp
            // Highlight the first row only when Enter would act on it.
            val first = index == 0 && s.enter
            val color = when (s) {
                is Suggestion.App -> Term.profileColor(s.app.profile.kind)
                is Suggestion.Completion -> Term.prompt
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minOf(44, Term.chipHeight).dp)
                    .alpha(if (s is Suggestion.App && s.app.profile.quiet) 0.45f else 1f)
                    .background(if (first) color.copy(alpha = 0.16f) else Color.Transparent)
                    .combinedClickable(
                        onClick = { onPick(s) },
                        onLongClick = (s as? Suggestion.App)?.let { app -> { onAppLongPress(app.app) } },
                    ),
            ) {
                // A bar marks the row that Enter / TAB act on.
                Box(Modifier.width(3.dp).heightIn(min = minOf(44, Term.chipHeight).dp).background(if (first) color else Color.Transparent))
                Spacer(Modifier.width(9.dp))
                when (s) {
                    is Suggestion.App -> {
                        // suggestions.icons: the app's icon comes first. Its badge marks work / private apps.
                        if (icons) {
                            AppIcon(s.app, iconSize)
                            Spacer(Modifier.width(8.dp))
                        }
                        val kind = s.app.profile.kind
                        // The profile tag goes after the name, so all names start in the same column.
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                append(s.label)
                                if (kind != ProfileKind.PERSONAL) {
                                    withStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = FontWeight.Bold, fontSize = (Term.fontSize - 2).sp)) {
                                        append(" [${kind.tag}]")
                                    }
                                }
                            },
                            style = Term.text.copy(color = Term.fg, fontWeight = if (first) FontWeight.Bold else FontWeight.Normal),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    is Suggestion.Completion -> {
                        // With suggestions.wrap, the full description goes in its own column, on as many
                        // lines as it needs, and the name aligns with its first line. Without it, the
                        // description is one line, cut short.
                        val firstLine = if (wrapHelp) Modifier.alignBy(FirstBaseline) else Modifier
                        // With icons on, a terminal icon replaces the `$`, so labels line up with app labels.
                        if (icons) {
                            when {
                                s.app != null -> AppIcon(s.app, iconSize)
                                s.icon != null -> KindIcon(s.icon, iconSize)
                                else -> CommandIcon(iconSize)
                            }
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Text("$ ", style = Term.text.copy(color = Term.prompt, fontWeight = FontWeight.Bold), modifier = firstLine)
                        }
                        Text(
                            taggedLabel(s.label),
                            style = Term.text.copy(color = Term.fg, fontWeight = if (first) FontWeight.Bold else FontWeight.Normal),
                            maxLines = 1,
                            modifier = firstLine,
                        )
                        val help = s.help ?: completionHelp(s.label, s.replacement)
                        Text(
                            if (help == null) "" else if (wrapHelp) help else "  $help",
                            style = Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 2).sp),
                            maxLines = if (wrapHelp) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).then(firstLine).then(if (wrapHelp) Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp) else Modifier),
                        )
                    }
                }
                if (first) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(com.mtwabpin.kolahal.R.drawable.ic_keyboard_return),
                        contentDescription = "Enter",
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Term.dim),
                        modifier = Modifier.padding(horizontal = 12.dp).size((Term.fontSize + 3).dp),
                    )
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

@Composable
internal fun SuggestionStrip(items: List<Suggestion>, rows: Int, onPick: (Suggestion) -> Unit, icons: Boolean = false) {
    if (items.isEmpty()) return
    val key = { _: Int, s: Suggestion -> s.key }
    // Keep the first chip (the one TAB takes) visible when the matches change.
    val first = items.first().key
    // suggestions.rows 2: two rows show twice as many chips before the user must scroll sideways.
    if (rows >= 2 && items.size > 3) {
        val gridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
        LaunchedEffect(first) { gridState.scrollToItem(0) }
        androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid(
            state = gridState,
            rows = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .height((Term.chipHeight * 2 + 6).dp)
                .testTag("suggestions")
                .padding(vertical = 2.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalItemSpacing = 8.dp,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items.size, key = { key(it, items[it]) }) { index -> SuggestionChip(items[index], onPick, icons) }
        }
        return
    }
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(first) { rowState.scrollToItem(0) }
    LazyRow(
        state = rowState,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("suggestions")
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items, key = key) { _, s -> SuggestionChip(s, onPick, icons) }
    }
}

@Composable
private fun SuggestionChip(s: Suggestion, onPick: (Suggestion) -> Unit, icons: Boolean = false) {
        run {
            val (color, tag) = when (s) {
                is Suggestion.App -> Term.profileColor(s.app.profile.kind) to s.app.profile.kind.tag
                is Suggestion.Completion -> Term.dim to null
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = Term.chipHeight.dp)
                    .alpha(if (s is Suggestion.App && s.app.profile.quiet) 0.45f else 1f)
                    // Fill the chip that Enter acts on (the first one, when Enter acts on it), so the user can see it.
                    .background(if (s.enter) color.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(4.dp))
                    .border(1.dp, color.copy(alpha = if (s.enter) 0.9f else 0.6f), RoundedCornerShape(4.dp))
                    .clickable { onPick(s) }
                    .padding(horizontal = 12.dp, vertical = (if (Term.chipHeight < 40) 2 else 8).dp),
            ) {
                // suggestions.icons: the app's icon comes first. Its badge marks work / private apps.
                val iconSize = minOf(Term.fontSize + 6, Term.chipHeight - 4).dp
                if (icons) {
                    val app = (s as? Suggestion.App)?.app ?: (s as? Suggestion.Completion)?.app
                    val kind = (s as? Suggestion.Completion)?.icon
                    when {
                        app != null -> AppIcon(app, iconSize)
                        kind != null -> KindIcon(kind, iconSize)
                        else -> CommandIcon(iconSize)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                // Commands show the prompt's `$`, so "maps" (command) and "Maps" (app) look different.
                // With icons on, the terminal icon above replaces it.
                if (s is Suggestion.Completion && !icons) {
                    Text("$ ", style = Term.text.copy(color = Term.prompt, fontWeight = FontWeight.Bold))
                }
                Text(taggedLabel(s.label), style = Term.text.copy(color = if (s is Suggestion.App) Term.fg else Term.dim))
                // The profile tag goes after the name, as in the log.
                if (tag != null && tag != ProfileKind.PERSONAL.tag) {
                    Spacer(Modifier.width(6.dp))
                    Text(tag, style = Term.text.copy(color = color, fontWeight = FontWeight.Bold, fontSize = (Term.fontSize - 2).sp))
                }
            }
        }
}
