package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A label and its value on a script's sheet. Used for the user's scripts and for shared ones. */
@Composable
internal fun Fact(label: String, value: String) {
    Text("$label: $value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The sheet for one script: run, edit, export, on/off, notifications, timers, library and delete.
 * It reads nothing from the script's code but its options: the page already read the rest ([info]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScriptSheet(
    vm: TerminalViewModel,
    /** The script, and what the page read from its code. */
    info: ScriptInfo,
    /** Its declared secrets that have a value, as the page read them. */
    secretsSet: List<String>,
    /** How a library or shared copy compares with its source. Null for a script the user wrote. */
    copy: ScriptCopy?,
    onEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
) {
    val s = info.script
    var confirmDelete by remember(s.id) { mutableStateOf(false) }
    // The dialog of its copy that shows: the question before Update or Reset, or Compare.
    var copyDialog by remember(s.id) { mutableStateOf<CopyDialog?>(null) }
    var times by remember(s.id, s.atTimes) { mutableStateOf(s.atTimes.joinToString(", ")) }
    var pickingTime by remember(s.id) { mutableStateOf(false) }
    var timesError by remember(s.id) { mutableStateOf(false) }
    val isCommand = info.isCommand
    val desc = info.desc
    val title = info.title
    // `@version` and `@author`, if the script has them: v1.2 · by Someone
    val byLine = remember(info) {
        listOfNotNull(info.version?.let { "v$it" }, info.author?.let { "by $it" }).joinToString(" · ").ifEmpty { null }
    }
    val access = info.accessItems
    val notifies = info.notifies
    val secrets = info.secrets
    var editingSecret by remember(s.id) { mutableStateOf<ScriptMeta.Secret?>(null) }
    val options = remember(s.code) { ScriptMeta.options(s.code) }
    val optionsVersion by vm.scriptActions.optionsVersion.collectAsStateWithLifecycle()
    val optionValues by produceState(emptyMap<String, String>() to emptySet<String>(), s.code, s.name, optionsVersion) {
        value = withContext(Dispatchers.IO) { vm.scriptActions.optionValues(s) }
    }
    var editingOption by remember(s.id) { mutableStateOf<ScriptMeta.Option?>(null) }
    val grants = info.grants
    var renaming by remember(s.id) { mutableStateOf(false) }

    GuiSheet(onDismiss, scroll = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${s.name}.${s.lang.ext}",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                color = scriptColor(s.name),
                modifier = Modifier.weight(1f, fill = false),
            )
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { renaming = true }) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        Text(
            listOfNotNull(if (s.lang == ScriptLang.LUA) "Lua" else "JavaScript", byLine, if (isCommand) "command" else null, desc)
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            s.lastRun?.let { "Last run ${relative(it)} · ${s.lastStatus.orEmpty()}" } ?: "Never run",
            style = MaterialTheme.typography.bodySmall,
            color = if (s.failed()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shapes = ButtonDefaults.shapes(), onClick = { vm.scriptActions.runScriptNow(s) }) {
                IconLabel(R.drawable.ic_play, "Run")
            }
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onEdit) {
                IconLabel(R.drawable.ic_edit, "Edit")
            }
            OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onExport) {
                IconLabel(R.drawable.ic_download, "Export")
            }
        }

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            "Enabled",
            if (isCommand) "Runs on its timers and events, and from the prompt" else "Runs on its timers and events",
            s.enabled,
        ) { onEnabled(it) }

        // What the script may use, in this order:
        // - internet and the pinned header;
        // - notifications, if its code posts them;
        // - what it declares with @uses.
        // The events of its @on lines follow in a separate group.
        val (events, uses) = access.partition { it in ScriptMeta.EVENTS }
        if (uses.isNotEmpty() || notifies) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Access")
            Spacer(Modifier.height(4.dp))
            val (base, declared) = uses.partition { it == ScriptAccess.INTERNET || it == ScriptAccess.HEADER }
            base.forEach { item -> SwitchRow(ScriptAccess.label(item), ScriptAccess.help(item), grants.allows(item)) { vm.scriptActions.setScriptAccess(s, item, it) } }
            if (notifies) SwitchRow("Post notifications", "Lets the script notify you", s.notify) { vm.scriptActions.setScriptNotify(s, it) }
            declared.forEach { item -> SwitchRow(ScriptAccess.label(item), ScriptAccess.help(item), grants.allows(item)) { vm.scriptActions.setScriptAccess(s, item, it) } }
        }
        if (events.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Events")
            Spacer(Modifier.height(4.dp))
            events.forEach { item -> SwitchRow(ScriptAccess.label(item), ScriptAccess.help(item), grants.allows(item)) { vm.scriptActions.setScriptAccess(s, item, it) } }
        }

        // Keys and tokens it declares with @secret. The user sets them here, never in the code.
        if (secrets.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Secrets")
            Text(
                "Keys and tokens it needs. Kept encrypted on this phone, never in its code, backups or the log.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
            secrets.forEach { d -> SecretRow(d, isSet = d.name in secretsSet) { editingSecret = d } }
        }

        // Settings it declares with @option. The user changes them here, not in the code, so
        // updates to the script keep them.
        if (options.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("Options")
            Text(
                "Settings it reads as it runs. Values set here are kept when the script updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
            options.forEach { o ->
                OptionRow(o, optionValues.first[o.name] ?: o.default, changed = o.name in optionValues.second) { editingOption = o }
            }
        }

        Spacer(Modifier.height(16.dp))
        IntervalSelector("Runs every", s.intervalMin, { vm.scriptActions.setScriptInterval(s, it) }, INTERVALS)

        Spacer(Modifier.height(12.dp))
        TextField(
            shape = TextFieldDefaults.roundedShape,
            colors = fieldColors(),
            value = times,
            onValueChange = { times = it; timesError = false },
            label = { Text("Daily at") },
            placeholder = { Text("08:00, 18:30") },
            // Or pick a time on a clock. It is added to the times and saved at once.
            leadingIcon = {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { pickingTime = true }) { Icon(painterResource(R.drawable.ic_alarm), "Add a time") }
            },
            singleLine = true,
            isError = timesError,
            supportingText = if (timesError) {
                { Text("Use HH:mm, separated by commas") }
            } else null,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { timesError = !vm.scriptActions.setScriptTimes(s, times) }),
            trailingIcon = if (times != s.atTimes.joinToString(", ")) {
                {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { timesError = !vm.scriptActions.setScriptTimes(s, times) }) {
                        Icon(painterResource(R.drawable.ic_check), "Set times")
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )

        if (pickingTime) {
            val clock = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = vm.config[Keys.CLOCK_24H])
            TimePickerDialog(
                onDismissRequest = { pickingTime = false },
                title = { Text("Daily at") },
                confirmButton = {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                        val picked = "%02d:%02d".format(clock.hour, clock.minute)
                        // Add it to the current text in the box, saved or not.
                        times = (times.split(',', ' ').filter { it.isNotBlank() } + picked).distinct().sorted().joinToString(", ")
                        timesError = !vm.scriptActions.setScriptTimes(s, times)
                        pickingTime = false
                    }) { Text("Add") }
                },
                dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { pickingTime = false }) { Text("Cancel") } },
            ) { TimePicker(clock) }
        }

        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (copy != null && copy.canRestore) {
                if (copy.lib != null) CompareButton(ButtonDefaults.MinHeight) { copyDialog = CopyDialog.COMPARE }
                // Update or Reset to the source's version. An edited copy asks first; an unedited one changes at once.
                CopyButton(copy, ButtonDefaults.MinHeight) { restoreCopy(vm, s, copy) { copyDialog = CopyDialog.CONFIRM } }
            }
            ErrorTextButton(onClick = { confirmDelete = true }) {
                IconLabel(R.drawable.ic_delete, "Delete")
            }
        }
    }

    editingSecret?.let { d ->
        SecretDialog(s.name, d, d.name in secretsSet, onDismiss = { editingSecret = null }) { value ->
            editingSecret = null
            vm.scriptActions.setSecret(s.name, d, value)
        }
    }

    editingOption?.let { o ->
        OptionDialog(s.name, o, optionValues.first[o.name] ?: o.default, changed = o.name in optionValues.second, onDismiss = { editingOption = null }) { value ->
            editingOption = null
            vm.scriptActions.setOption(s.name, o.name, value)
        }
    }

    if (renaming) {
        val all by vm.scriptList.collectAsStateWithLifecycle()
        RenameDialog(s, all.mapTo(HashSet()) { it.name }, onDismiss = { renaming = false }) { to ->
            renaming = false
            vm.scriptActions.renameScript(s, to)
        }
    }

    CopyDialogs(vm, s, copy, copyDialog, onShow = { copyDialog = it }, onExport = onExport)

    if (confirmDelete) {
        ConfirmDialog(
            "Delete ${s.name}.${s.lang.ext}?",
            "Its timers, stored data and status line are removed too. Export it first to keep a copy.",
            "Delete",
            icon = R.drawable.ic_delete,
            onDismiss = { confirmDelete = false },
        ) { confirmDelete = false; onDismiss(); vm.scriptActions.deleteScript(s) }
    }
}

/** One option on a script's sheet: its name, what it is for, and its value (muted while it is the default). */
@Composable
private fun OptionRow(option: ScriptMeta.Option, value: String, changed: Boolean, onClick: () -> Unit) {
    ValueRow(
        option.name,
        option.desc,
        value.ifEmpty { "—" },
        if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        // A value can be long (an address, a list): it gets one line, and the name keeps the rest.
        valueMaxWidth = 160.dp,
        onClick = onClick,
    )
}

/** One secret on a script's sheet: its name, what it is for, where it is sent, and whether it is set. */
@Composable
private fun SecretRow(secret: ScriptMeta.Secret, isSet: Boolean, onClick: () -> Unit) {
    ValueRow(
        secret.name,
        listOf(secret.desc, "sent only to ${secret.hosts.joinToString()}").filter { it.isNotBlank() }.joinToString(" · "),
        if (isSet) "Set" else "Not set",
        if (isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        onClick = onClick,
    )
}
