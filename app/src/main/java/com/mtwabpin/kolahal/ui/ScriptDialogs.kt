package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import com.mtwabpin.kolahal.terminal.CommandCatalog

/** Resetting an edited library script to the library version: the edits go, so it asks first. */
@Composable
internal fun ResetDialog(lib: LibRow, onDismiss: () -> Unit, onReset: () -> Unit) {
    ConfirmDialog(
        "Reset ${lib.entry.file}?",
        "Your copy differs from the library version. Its code is replaced by the library version and your changes are lost; its timers and switches are kept. Export it first to keep a copy.",
        "Reset",
        onDismiss = onDismiss,
        onConfirm = onReset,
    )
}

/**
 * Before a script may use what it declares: switching on one that declares what nobody allowed yet,
 * or installing a shared one. Each item ([items], [ScriptAccess] names) with a switch, all on, to
 * switch off what you don't want before [confirm]. The script's Access switches change them later.
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
 * Sets one of a script's secrets: a masked box (the eye shows it) that the keyboard doesn't learn
 * from, never filled with the stored value. Clear forgets it.
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
 * Changes one of a script's options: one of its choices, or a box with the value in it. Default
 * puts its default back.
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

/** A new name for [s], from its sheet. */
@Composable
internal fun RenameDialog(s: Script, existing: Set<String>, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember(s.id) { mutableStateOf(TextFieldValue(s.name, TextRange(s.name.length))) }
    val clean = name.text.trim().lowercase()
    // The same checks as `script mv`, shown while typing.
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
    // The same checks as `script new`, shown while typing.
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
