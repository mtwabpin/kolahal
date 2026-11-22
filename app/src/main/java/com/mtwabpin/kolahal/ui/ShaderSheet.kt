/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.script.ShaderCatalog
import com.mtwabpin.kolahal.script.Shaders
import com.mtwabpin.kolahal.terminal.ShaderActions
import com.mtwabpin.kolahal.terminal.ShaderCommands
import com.mtwabpin.kolahal.terminal.ShaderRow
import kotlinx.coroutines.launch

/**
 * The sheet for one shader on the Shaders screen ([name] of [rows]). Left and right swipe to the others,
 * in the list's order, as the preview is what one looks for.
 * - A preview, then Use or Turn off, Check (does it compile, and which uniforms Kolahal sets), Share, and
 *   Copy to the script folder. Holding the eye shows home with the shader, in use or not ([onPeek]).
 * - Its parameters, under the preview, which follows them ([ShaderParamRows]).
 * - Update, for one from Get more shaders with a newer version. An edited one asks first.
 * - Remove, for the user's own. Undo puts it back.
 * - Its code is at the end.
 *
 * One that was turned off has no preview: it may have crashed Kolahal.
 * [inUse] is the shader that is set, and [problems] why one couldn't be used. [shown] clears the sheet
 * while home is looked at through it.
 */
@Composable
internal fun ShaderSheet(
    rows: List<ShaderRow>,
    name: String,
    inUse: String,
    problems: Map<String, String>,
    actions: ShaderActions,
    shown: () -> Float,
    onShow: (String) -> Unit,
    onPeek: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    GuiSheet(onDismiss, alpha = shown, sides = 0.dp) {
        val pager = rememberPagerState(initialPage = rows.indexOfFirst { it.name == name }.coerceAtLeast(0)) { rows.size }
        val current by rememberUpdatedState(rows)
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { page -> current.getOrNull(page)?.let { onShow(it.name) } }
        }
        HorizontalPager(
            pager,
            key = { rows[it].name },
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxHeight()
                .testTag("shader-pager"),
        ) { page ->
            ShaderDetails(
                rows[page], "${page + 1} of ${rows.size}", inUse, problems[rows[page].name], actions,
                front = page == pager.settledPage, shown, onPeek, onDismiss,
            )
        }
    }
}

/** One shader's page of the sheet. Only the one in [front] moves, and its preview is the only one that may. */
@Composable
private fun ShaderDetails(
    row: ShaderRow,
    place: String,
    inUse: String,
    problem: String?,
    actions: ShaderActions,
    front: Boolean,
    shown: () -> Float,
    onPeek: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var confirmReplace by remember(row.name) { mutableStateOf(false) }
    // Checked again only when asked: a changed file has no result yet.
    var check by remember(row.name, row.code) { mutableStateOf<Shaders.Check?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val on = row.name == inUse && !row.turnedOff
    val drawable = Shaders.supported && !row.turnedOff && problem == null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .graphicsLayer { alpha = shown() }
            .padding(horizontal = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    place,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A message of the page would be behind the sheet.
            if (drawable) PeekButton(
                onHold = { holding -> onPeek(row.name.takeIf { holding }) },
                onTap = { Toast.makeText(context, "Tap and hold the eye to see the home screen", Toast.LENGTH_SHORT).show() },
            )
        }
        if (drawable) ShaderPreview(row.name, row.code, Modifier.padding(top = 12.dp), moves = front)
        row.meta.description?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        if (row.meta.params.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ShaderParamRows(row.name, row.meta.params, actions)
        }
        if (row.turnedOff) SheetError(ShaderPage.turnedOff(row))
        problem?.let { SheetError("Can't be used: $it") }
        if (!Shaders.supported) SheetError(Shaders.NEEDS)

        Spacer(Modifier.height(16.dp))
        val size = ButtonDefaults.MediumContainerHeight
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val wide = Modifier
                .weight(1f)
                .heightIn(size)
            if (on) FilledTonalButton(
                onClick = { actions.off() },
                shapes = ButtonDefaults.shapesFor(size),
                contentPadding = ButtonDefaults.contentPaddingFor(size),
                modifier = wide,
            ) { Text("Turn off", style = ButtonDefaults.textStyleFor(size)) }
            else Button(
                onClick = { actions.use(row.name) },
                shapes = ButtonDefaults.shapesFor(size),
                enabled = Shaders.supported,
                contentPadding = ButtonDefaults.contentPaddingFor(size),
                modifier = wide,
            ) { Text("Use", style = ButtonDefaults.textStyleFor(size)) }
            if (row.origin?.newer == true) FilledTonalButton(
                onClick = {
                    if (row.origin.edited) confirmReplace = true
                    else row.latest?.let { actions.install(it) }
                },
                shapes = ButtonDefaults.shapesFor(size),
                contentPadding = ButtonDefaults.contentPaddingFor(size),
                modifier = wide,
            ) { Text(ShaderPage.update(row), style = ButtonDefaults.textStyleFor(size)) }
        }
        Spacer(Modifier.height(8.dp))
        SheetActions(
            buildList {
                add(SheetAction("Check", R.drawable.ic_info) { scope.launch { check = actions.check(row.name) } })
                add(SheetAction("Share", R.drawable.ic_share) { share(context, row) })
                // Its message is on the page, behind the sheet.
                add(SheetAction("Copy", R.drawable.ic_folder, "Copy to script folder") { onDismiss(); actions.copyToFolder(row.name) })
                if (row.yours) add(SheetAction("Remove", R.drawable.ic_delete, error = true) { onDismiss(); actions.remove(row.name) })
            }
        )
        // An edited copy with nothing newer goes back to the shared version.
        if (row.origin?.edited == true && !row.origin.newer && row.latest != null) {
            ErrorTextButton(onClick = { confirmReplace = true }) { Text("Reset to shared version") }
        }
        check?.let { CheckResult(it) }

        Spacer(Modifier.height(16.dp))
        Text(
            ShaderPage.byLine(row),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Fact("From", from(row))
        Fact("Moves", if (row.meta.animated) "yes" else "no")
        if (row.meta.tilt) Fact("Uses tilt", "yes")
        row.meta.license?.let { Fact("License", it) }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Code")
        Spacer(Modifier.height(8.dp))
        ReadOnlyCode(row.code, Syntax.AGSL)
        Spacer(Modifier.height(16.dp))
    }

    val latest = row.latest
    if (confirmReplace && latest != null) {
        ConfirmDialog(
            "Replace ${row.name}.${ShaderCatalog.EXT}?",
            "Your copy differs from the shared one. Its code is replaced and your changes are lost.",
            "Replace",
            onDismiss = { confirmReplace = false },
        ) { confirmReplace = false; actions.install(latest, replace = true) }
    }
}

private fun from(row: ShaderRow): String = when {
    row.origin != null -> row.origin.source
    row.yours && row.bundled -> "you, used instead of the one that comes with Kolahal"
    row.yours -> "you"
    else -> "Kolahal"
}

@Composable
private fun SheetError(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.padding(top = 4.dp)
)

/** What Check found: the problem, or that it compiles and its uniforms. */
@Composable
private fun CheckResult(check: Shaders.Check) {
    Column(
        Modifier
            .padding(top = 12.dp)
            .testTag("shader-check")
    ) {
        val problem = check.problem
        if (problem != null) SheetError(problem.replaceFirstChar { it.uppercase() })
        else ShaderCommands.checkLines(check).forEach { line ->
            Text(
                line.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One of the smaller things to do with a shader. [says] is what a screen reader hears, when it is more than [label]. */
private class SheetAction(val label: String, val icon: Int, val says: String = label, val error: Boolean = false, val run: () -> Unit)

/** [actions] in an Expressive button group: the pressed one widens, and its neighbors give way. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SheetActions(actions: List<SheetAction>) {
    val colors = MaterialTheme.colorScheme
    ButtonGroup(
        overflowIndicator = { menu -> ButtonGroupDefaults.OverflowIndicator(menu) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        for (a in actions) customItem(
            buttonGroupContent = {
                val source = remember { MutableInteractionSource() }
                FilledTonalButton(
                    onClick = a.run,
                    shapes = ButtonDefaults.shapes(),
                    colors = if (a.error) ButtonDefaults.filledTonalButtonColors(containerColor = colors.errorContainer, contentColor = colors.onErrorContainer)
                    else ButtonDefaults.filledTonalButtonColors(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    interactionSource = source,
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(source)
                        .semantics { if (a.says != a.label) contentDescription = a.says },
                ) {
                    Icon(painterResource(a.icon), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(6.dp))
                    Text(a.label, maxLines = 1, softWrap = false)
                }
            },
            menuContent = { state ->
                DropdownMenuItem(text = { Text(a.says) }, onClick = { a.run(); state.dismiss() })
            },
        )
    }
}

/** Offers the shader's code, as text, to another app. Without one that takes it, nothing happens. */
private fun share(context: Context, row: ShaderRow) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "${row.name}.${ShaderCatalog.EXT}")
        .putExtra(Intent.EXTRA_TEXT, row.code)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}
