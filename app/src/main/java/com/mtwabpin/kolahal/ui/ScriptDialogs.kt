package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptNames
import com.mtwabpin.kolahal.script.SecretStore
import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/**
 * Asks before an edited copy gets its source's version, because the edits are lost. Only the code
 * changes: Reset (nothing newer) or Update (a new version).
 * - Compare first (library copies) and Export your copy first open over it; the dialog stays.
 * - Cancel, Back and a tap outside keep the copy.
 */
@Composable
internal fun RestoreDialog(vm: TerminalViewModel, s: Script, copy: ScriptCopy, onExport: () -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val update = copy.state == CopyState.NEW_VERSION
    val source = if (copy.lib != null) "library" else "shared"
    var comparing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_refresh), null) },
        title = { Text("${if (update) "Update" else "Reset"} ${s.name}.${s.lang.ext}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (update) "Your edits will be lost. It gets the new $source version."
                    else "Your edits will be lost. It gets the $source version back.",
                )
                Text(
                    "Only the code changes. Its timers, options, access and pinning are kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                if (copy.lib != null) {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = { comparing = true }) { IconLabel(R.drawable.ic_visibility, "Compare first") }
                }
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onExport) { IconLabel(R.drawable.ic_download, "Export your copy first") }
            }
        },
        confirmButton = { ErrorTextButton(onClick = onConfirm) { Text(if (update) "Update" else "Reset") } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    )
    // Compare's own Update or Reset answers this dialog too.
    if (comparing && copy.lib != null) CompareDialog(vm, s, copy.lib, onDismiss = { comparing = false }) { comparing = false; onConfirm() }
}

/**
 * Update all, from the banner: one row to tick per copy that differs. A new version starts ticked;
 * an edited copy with nothing newer doesn't, so its edits stay unless the user ticks it. A ticked
 * edited copy says its edits will be lost. Update N brings the ticked ones the library or GitHub
 * version, code only.
 */
@Composable
internal fun UpdateAllDialog(copies: List<Pair<Script, ScriptCopy>>, onDismiss: () -> Unit, onUpdate: (List<Pair<Script, ScriptCopy>>) -> Unit) {
    // The user's ticks, by script. The list is rebuilt when a script runs, so a row not touched yet uses the default.
    val ticked = remember { mutableStateMapOf<Long, Boolean>() }
    fun isOn(s: Script, c: ScriptCopy) = ticked[s.id] ?: (c.state == CopyState.NEW_VERSION)
    val picked = copies.filter { (s, c) -> isOn(s, c) }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_refresh), null) },
        title = { Text("Update from the library") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Ticked scripts get the library or GitHub version. Only the code changes.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                for ((s, c) in copies) {
                    val on = isOn(s, c)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .toggleable(value = on, role = Role.Checkbox) { ticked[s.id] = it }
                            .padding(vertical = 4.dp)
                            .testTag("update:${s.name}"),
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${s.name}.${s.lang.ext}", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
                            Text(
                                buildAnnotatedString {
                                    append(
                                        when {
                                            c.state == CopyState.EDITED -> "Edited, nothing newer"
                                            c.lib != null -> "New version"
                                            else -> "New version on GitHub"
                                        },
                                    )
                                    if (on && c.edited) withStyle(SpanStyle(color = colors.error)) { append(" · your edits will be lost") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), enabled = picked.isNotEmpty(), onClick = { onUpdate(picked) }) { Text("Update ${picked.size}") }
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Asks before a script may use what it declares. It shows when the user:
 * - switches on a script that declares access nobody allowed yet, or
 * - installs a shared script.
 * Each item ([items], [ScriptAccess] names) has a switch, and all start on. The user can switch off
 * what they don't want before [confirm]. The script's Access switches can change them later.
 */
@Composable
internal fun AccessDialog(title: String, items: List<String>, confirm: String, onDismiss: () -> Unit, onConfirm: (Map<String, Boolean>) -> Unit) {
    val choices = remember(items) { mutableStateMapOf<String, Boolean>().apply { items.forEach { put(it, true) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("It requests:", style = MaterialTheme.typography.bodyMedium)
                items.forEach { item ->
                    SwitchRow(ScriptAccess.label(item), ScriptAccess.help(item), choices[item] == true, color = Color.Transparent) { choices[item] = it }
                }
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { onConfirm(choices.toMap()) }) { Text(confirm) } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Sets one of a script's secrets.
 * - The box is masked; the eye button shows the text. The keyboard does not learn from it.
 * - The box is never filled with the stored value.
 * - Clear deletes the stored secret.
 */
@Composable
internal fun SecretDialog(script: String, secret: ScriptMeta.Secret, isSet: Boolean, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${secret.name} for $script") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (secret.desc.isNotBlank()) Text(secret.desc, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Sent only to ${secret.hosts.joinToString()}. Kept encrypted on this phone: never in the script, its backups or the log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextField(
                    shape = TextFieldDefaults.roundedShape,
                    colors = fieldColors(),
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { Text(if (isSet) "Set · type a new one to replace it" else "Paste the value") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { visible = !visible }) {
                            Icon(
                                painterResource(R.drawable.ic_visibility),
                                contentDescription = if (visible) "Hide it" else "Show it",
                                tint = if (visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("secret-value"),
                )
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { onSave(value.trim()) }, enabled = value.trim().length >= SecretStore.MIN_LENGTH) { Text("Save") } },
        dismissButton = {
            Row {
                if (isSet) TextButton(shapes = ButtonDefaults.shapes(), onClick = { onSave(null) }) { Text("Clear") }
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * Changes one of a script's options: the user picks one of its choices, or edits the value in a box.
 * Default restores the default value.
 */
@Composable
internal fun OptionDialog(script: String, option: ScriptMeta.Option, current: String, changed: Boolean, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(current.length))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${option.name} for $script") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (option.desc.isNotBlank()) Text(option.desc, style = MaterialTheme.typography.bodyMedium)
                if (option.choices.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        option.choices.forEach { c ->
                            FilterChip(selected = value.text == c, onClick = { value = TextFieldValue(c) }, label = { Text(c) })
                        }
                    }
                } else {
                    TextField(
                        shape = TextFieldDefaults.roundedShape,
                        colors = fieldColors(),
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        placeholder = { if (option.default.isNotEmpty()) Text(option.default) },
                        modifier = Modifier.fillMaxWidth().testTag("option-value"),
                    )
                }
                Text(
                    "Default: " + option.default.ifEmpty { "empty" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { onSave(value.text.trim()) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (changed) TextButton(shapes = ButtonDefaults.shapes(), onClick = { onSave(null) }) { Text("Reset") }
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Renames [s]. Opened from its sheet. */
@Composable
internal fun RenameDialog(s: Script, existing: Set<String>, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember(s.id) { mutableStateOf(TextFieldValue(s.name, TextRange(s.name.length))) }
    val clean = name.text.trim().lowercase()
    // The same checks as `script mv`, shown while the user types.
    val problem = if (clean.isEmpty() || clean == s.name) null else ScriptNames.invalid(clean) { it in existing }
    val valid = clean.isNotEmpty() && clean != s.name && problem == null
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_edit), null) },
        title = { Text("Rename ${s.name}.${s.lang.ext}") },
        text = {
            TextField(
                shape = TextFieldDefaults.roundedShape,
                colors = fieldColors(),
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                suffix = { Text(".${s.lang.ext}") },
                singleLine = true,
                isError = problem != null,
                supportingText = {
                    Text(problem ?: "Its keys, timers and status line are kept. A copy from the library or Get more scripts becomes yours and no longer gets updates.")
                },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onRename(clean) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), enabled = valid, onClick = { onRename(clean) }) { Text("Rename") } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun NewScriptDialog(existing: Set<String>, onDismiss: () -> Unit, onCreate: (String, ScriptLang, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf(ScriptLang.LUA) }
    var command by remember { mutableStateOf(false) }
    val builtIns = CommandCatalog.RESERVED_NAMES
    val clean = name.trim().lowercase()
    // The same checks as `script new`, shown while the user types.
    val problem = when {
        clean.isEmpty() -> null
        !ScriptNames.NAME.matches(clean) -> "Lowercase letters, digits, - and _"
        clean in builtIns -> "'$clean' is a built-in command"
        clean in existing -> "You already have a script named $clean"
        else -> null
    }
    val valid = clean.isNotEmpty() && problem == null
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_code), null) },
        title = { Text("New script") },
        text = {
            Column {
                TextField(
                    shape = TextFieldDefaults.roundedShape,
                    colors = fieldColors(),
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("hn, weather, feeds…") },
                    singleLine = true,
                    isError = problem != null,
                    supportingText = { Text(problem ?: "Lowercase letters, digits, - and _") },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                ConnectedChoices(listOf("Lua", "JavaScript"), if (lang == ScriptLang.LUA) "Lua" else "JavaScript") {
                    lang = if (it == "Lua") ScriptLang.LUA else ScriptLang.JS
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(checked = command, onCheckedChange = { command = it })
                    Column {
                        Text("Command template", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Runs from the prompt by name, with arguments",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), enabled = valid, onClick = { onCreate(name, lang, command) }) { Text("Create") } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    )
}
