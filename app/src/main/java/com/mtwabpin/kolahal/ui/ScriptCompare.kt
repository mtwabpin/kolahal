package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.script.LineDiff
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compare: your copy of a library script against the library version. Its changed lines with three
 * unchanged ones around each, those only in the library version in red and those only in yours in
 * the theme's colour, the rest counted; Reset (or Update) from there.
 */
@Composable
internal fun CompareDialog(vm: TerminalViewModel, s: Script, lib: LibRow, onDismiss: () -> Unit, onReset: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Read here, outside the dialog's own composition, and handed in: its counts and its lines then
    // change together (read inside it, the lines could show while the counts stayed at 0).
    val hunks = produceState<List<LineDiff.Hunk>?>(null, s.code, lib.entry) {
        value = withContext(Dispatchers.IO) { LineDiff.hunks(LineDiff.lines(vm.scriptActions.library.code(lib.entry), s.code)) }
    }.value
    val removed = hunks.orEmpty().sumOf { h -> h.lines.count { it.kind == LineDiff.Kind.REMOVED } }
    val added = hunks.orEmpty().sumOf { h -> h.lines.count { it.kind == LineDiff.Kind.ADDED } }
    fun lines(n: Int) = if (n == 1) "1 line" else "$n lines"
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = colors.surface, modifier = Modifier.fillMaxSize().testTag("compare")) {
            Column(Modifier.safeDrawingPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp)) {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDismiss) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close") }
                    Text(
                        "${s.name}.${s.lang.ext}",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // What the colours mean, with how many lines each.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    DiffKey("- ${lines(removed)} only in the library version", colors.errorContainer)
                    DiffKey("+ ${lines(added)} only in yours", colors.primaryContainer)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    if (hunks != null && hunks.isEmpty()) item {
                        Text("No differences", color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                    hunks.orEmpty().forEach { hunk ->
                        if (hunk.skipped > 0) item {
                            Text(
                                "⋯ ${lines(hunk.skipped)} unchanged",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.outline,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(hunk.lines) { DiffLine(it) }
                    }
                }
                Row(Modifier.padding(16.dp)) { LibraryCopyButton(lib.state, ButtonDefaults.MinHeight, onReset) }
            }
        }
    }
}

/** What a colour in Compare means. */
@Composable
private fun DiffKey(label: String, color: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = color) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** One line in Compare: - and red only in the library version, + and the theme's colour only in yours; a long one wraps under its text. */
@Composable
private fun DiffLine(line: LineDiff.Line) {
    val colors = MaterialTheme.colorScheme
    val (sign, background) = when (line.kind) {
        LineDiff.Kind.REMOVED -> "-" to colors.errorContainer
        LineDiff.Kind.ADDED -> "+" to colors.primaryContainer
        LineDiff.Kind.SAME -> " " to Color.Transparent
    }
    val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val color = if (line.kind == LineDiff.Kind.SAME) colors.onSurfaceVariant else colors.onSurface
    Row(Modifier.fillMaxWidth().background(background).padding(horizontal = 16.dp, vertical = 1.dp)) {
        Text("$sign ", style = style, color = color)
        Text(line.text.replace("\t", "    "), style = style, color = color, modifier = Modifier.weight(1f))
    }
}
