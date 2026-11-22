/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
 * The sheet for one shader on the Shaders screen: what it is, a small preview, and what can be done
 * with it. Its code is at the end.
 * - Use or Turn off, Check (does it compile, and which uniforms Kolahal sets), Copy to script folder, Share.
 * - Update, for one from Get more shaders with a newer version. An edited one asks first.
 * - Remove, for the user's own. Undo puts it back.
 *
 * One that was turned off has no preview: it may have crashed Kolahal.
 * [inUse] is the shader that is set, and [problem] why this one couldn't be used.
 */
@Composable
internal fun ShaderSheet(
    row: ShaderRow,
    inUse: String,
    problem: String?,
    actions: ShaderActions,
    onDismiss: () -> Unit
) {
    var confirmReplace by remember(row.name) { mutableStateOf(false) }
    // Checked again only when asked: a changed file has no result yet.
    var check by remember(row.name, row.code) { mutableStateOf<Shaders.Check?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val on = row.name == inUse && !row.turnedOff

    GuiSheet(onDismiss, scroll = true) {
        Text(row.title, style = MaterialTheme.typography.titleLarge)
        Text(
            ShaderPage.byLine(row),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.meta.description?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        if (!row.turnedOff && problem == null) {
            ShaderPreview(row.name, row.code, Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(12.dp))
        Fact("From", from(row))
        Fact("Moves", if (row.meta.animated) "yes" else "no")
        row.meta.license?.let { Fact("License", it) }
        if (row.turnedOff) SheetError(ShaderPage.turnedOff(row))
        problem?.let { SheetError("Can't be used: $it") }
        if (!Shaders.supported) SheetError(Shaders.NEEDS)

        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (on) OutlinedButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { actions.off() }) { Text("Turn off") }
            else Button(
                shapes = ButtonDefaults.shapes(),
                onClick = { actions.use(row.name) },
                enabled = Shaders.supported
            ) { IconLabel(R.drawable.ic_check, "Use") }
            if (row.origin?.newer == true) FilledTonalButton(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    if (row.origin.edited) confirmReplace = true
                    else row.latest?.let { actions.install(it) }
                }) { IconLabel(R.drawable.ic_download, ShaderPage.update(row)) }
            OutlinedButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { scope.launch { check = actions.check(row.name) } }) { Text("Check") }
            OutlinedButton(
                shapes = ButtonDefaults.shapes(),
                // Its message is on the page, behind the sheet.
                onClick = { onDismiss(); actions.copyToFolder(row.name) }) {
                IconLabel(R.drawable.ic_folder, "Copy to script folder")
            }
            OutlinedButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { share(context, row) }) { IconLabel(R.drawable.ic_share, "Share") }
            // An edited copy with nothing newer goes back to the shared version.
            if (row.origin?.edited == true && !row.origin.newer && row.latest != null) {
                ErrorTextButton(onClick = { confirmReplace = true }) { Text("Reset to shared version") }
            }
            if (row.yours) ErrorTextButton(onClick = { onDismiss(); actions.remove(row.name) }) {
                IconLabel(R.drawable.ic_delete, "Remove")
            }
        }
        check?.let { CheckResult(it) }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Code")
        Spacer(Modifier.height(8.dp))
        ReadOnlyCode(row.code, Syntax.AGSL)
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

/** Offers the shader's code, as text, to another app. Without one that takes it, nothing happens. */
private fun share(context: Context, row: ShaderRow) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "${row.name}.${ShaderCatalog.EXT}")
        .putExtra(Intent.EXTRA_TEXT, row.code)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}
