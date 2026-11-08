package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.mtwabpin.kolahal.data.Script
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

/** The sheet for one script: run, edit, export, on/off, notifications, timers, library and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScriptSheet(
    vm: TerminalViewModel,
    s: Script,
    /** How a library or shared copy compares with its source. Null for a script the user wrote. */
    copy: ScriptCopy?,
    onEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
) {
    val state = rememberExpandedSheetState()
    var confirmDelete by remember(s.id) { mutableStateOf(false) }
    var confirmReplace by remember(s.id) { mutableStateOf(false) }
    var comparing by remember(s.id) { mutableStateOf(false) }
    // Update or Reset to the source's version. For an edited copy, this loses the edits, so ask first.
    // An unedited copy has no edits to lose: it updates at once, and the notice offers Undo.
    fun restore(c: ScriptCopy) {
        if (c.edited) confirmReplace = true else vm.scriptActions.restore(listOf(c.restore(s)))
    }
    var times by remember(s.id, s.atTimes) { mutableStateOf(s.atTimes.joinToString(", ")) }
    var pickingTime by remember(s.id) { mutableStateOf(false) }
    var timesError by remember(s.id) { mutableStateOf(false) }
    val isCommand = remember(s.code) { ScriptMeta.isCommand(s.code) }
    val desc = remember(s.code) { ScriptMeta.description(s.code) }
    val title = remember(s.code) { ScriptMeta.title(s.code) }
    // `@version` and `@author`, if the script has them: v1.2 · by Someone
    val byLine = remember(s.code) {
        listOfNotNull(ScriptMeta.version(s.code)?.let { "v$it" }, ScriptMeta.author(s.code)?.let { "by $it" }).joinToString(" · ").ifEmpty { null }
    }
    val access = remember(s.code) { ScriptAccess.items(s.code) }
    val notifies = remember(s.code) { ScriptMeta.postsNotifications(s.code) }
    val secrets = remember(s.code) { ScriptMeta.secrets(s.code) }
    val secretsVersion by vm.scriptActions.secretsVersion.collectAsStateWithLifecycle()
    val secretsSet by produceState(emptySet(), s.code, s.name, secretsVersion) { value = withContext(Dispatchers.IO) { vm.scriptActions.secretsSet(s) } }
    var editingSecret by remember(s.id) { mutableStateOf<ScriptMeta.Secret?>(null) }
    val options = remember(s.code) { ScriptMeta.options(s.code) }
    val optionsVersion by vm.scriptActions.optionsVersion.collectAsStateWithLifecycle()
    val optionValues by produceState(emptyMap<String, String>() to emptySet<String>(), s.code, s.name, optionsVersion) {
        value = withContext(Dispatchers.IO) { vm.scriptActions.optionValues(s) }
    }
    var editingOption by remember(s.id) { mutableStateOf<ScriptMeta.Option?>(null) }
    val grants = remember(s.code, s.access) { s.grants }
    var renaming by remember(s.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
        ) {
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
                Text("Access", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                val (base, declared) = uses.partition { it == ScriptAccess.INTERNET || it == ScriptAccess.HEADER }
                base.forEach { item -> SwitchRow(ScriptAccess.label(item), ScriptAccess.help(item), grants.allows(item)) { vm.scriptActions.setScriptAccess(s, item, it) } }
                if (notifies) SwitchRow("Post notifications", "Lets the script notify you", s.notify) { vm.scriptActions.setScriptNotify(s, it) }
                declared.forEach { item -> SwitchRow(ScriptAccess.label(item), ScriptAccess.help(item), grants.allows(item)) { vm.scriptActions.setScriptAccess(s, item, it) } }
            }
            if (events.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Events", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                events.forEach { item -> SwitchRow(ScriptAccess.label(item), ScriptAccess.help(item), grants.allows(item)) { vm.scriptActions.setScriptAccess(s, item, it) } }
            }

            // Keys and tokens it declares with @secret. The user sets them here, never in the code.
            if (secrets.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Secrets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                Text("Options", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                    if (copy.lib != null) CompareButton(ButtonDefaults.MinHeight) { comparing = true }
                    CopyButton(copy, ButtonDefaults.MinHeight) { restore(copy) }
                }
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    IconLabel(R.drawable.ic_delete, "Delete")
                }
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

    if (confirmReplace && copy != null && copy.canRestore) {
        RestoreDialog(vm, s, copy, onExport = onExport, onDismiss = { confirmReplace = false }) {
            confirmReplace = false
            vm.scriptActions.restore(listOf(copy.restore(s)))
        }
    }

    if (comparing && copy?.lib != null && copy.canRestore) {
        CompareDialog(vm, s, copy.lib, onDismiss = { comparing = false }) { comparing = false; restore(copy) }
    }

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
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(option.name, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
                if (option.desc.isNotBlank()) {
                    Text(option.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                value.ifEmpty { "—" },
                style = MaterialTheme.typography.labelLarge,
                color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}

/** One secret on a script's sheet: its name, what it is for, where it is sent, and whether it is set. */
@Composable
private fun SecretRow(secret: ScriptMeta.Secret, isSet: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(secret.name, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
                Text(
                    listOf(secret.desc, "sent only to ${secret.hosts.joinToString()}").filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                if (isSet) "Set" else "Not set",
                style = MaterialTheme.typography.labelLarge,
                color = if (isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
