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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.data.takeWhole
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.widgets.Shortcut
import com.mtwabpin.kolahal.widgets.Shortcuts

/** The editor's pure part: which commands it offers while you type one. */
internal object ShortcutPicks {
    /** The most commands offered under the box. */
    const val MAX = 6

    /**
     * The commands to offer for what is [typed] in the command box: those of [commands] that start
     * with it, at most [MAX]. None once a whole command is typed, or a second word is started.
     */
    fun matching(typed: String, commands: List<String>): List<String> {
        val start = typed.trimStart().lowercase()
        if (start.isEmpty() || start.any { it.isWhitespace() }) return emptyList()
        return commands.filter { it.startsWith(start) && it != start }.take(MAX)
    }
}

/**
 * The Shortcuts widget's editor: a sheet over the Widgets screen that lists the widget's buttons.
 * - Add a shortcut, change one (tap it), move it earlier or later, or remove it. Each change is
 *   stored at once ([TerminalViewModel.saveShortcuts]), and the widget's card follows.
 * - This is the only place that writes the list. A button runs its command as if you had typed
 *   it, so you choose each command here yourself.
 */
@Composable
internal fun ShortcutsEditor(vm: TerminalViewModel, onDismiss: () -> Unit) {
    val stored by vm.shortcuts.collectAsStateWithLifecycle()
    val scriptCommands by vm.commandNames.collectAsStateWithLifecycle()
    // The built-in commands and your command scripts, to pick from while typing.
    val commands = remember(scriptCommands) { (CommandCatalog.COMMAND_USAGES.keys + scriptCommands).distinct().sorted() }
    val list = stored.orEmpty()
    // The place in the list that is being changed. The list's size means a new shortcut.
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    GuiSheet(onDismiss = onDismiss, scroll = true) {
        Text("Shortcuts", style = MaterialTheme.typography.titleLarge)
        Text(
            "Each button runs a command, as if you typed it and pressed Enter.",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            list.forEachIndexed { i, s ->
                ShortcutRow(
                    shortcut = s,
                    index = i,
                    count = list.size,
                    onEdit = { editing = i },
                    onMove = { direction -> vm.saveShortcuts(Shortcuts.moved(list, i, direction)) },
                    onRemove = { vm.saveShortcuts(Shortcuts.without(list, i)) },
                )
            }
        }
        // Until the list is read, nothing can be added: a list saved now would replace the stored one.
        if (stored != null && list.isEmpty()) EmptyNote("No shortcuts yet. Add a command such as torch, todo or open maps.", top = 8.dp, bottom = 16.dp)
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            shapes = ButtonDefaults.shapes(),
            enabled = stored != null && list.size < Shortcuts.MAX,
            onClick = { editing = list.size },
            modifier = Modifier.testTag("shortcut-add"),
        ) {
            Icon(painterResource(R.drawable.ic_add), null)
            Spacer(Modifier.width(8.dp))
            Text("Add shortcut")
        }
        if (list.size >= Shortcuts.MAX) {
            Text("The widget has ${Shortcuts.MAX} buttons at most.", style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 8.dp))
        }
    }

    editing?.let { index ->
        ShortcutDialog(
            initial = list.getOrNull(index),
            commands = commands,
            onDismiss = { editing = null },
            onSave = { s ->
                editing = null
                vm.saveShortcuts(Shortcuts.with(list, index, s))
            },
        )
    }
}

/** One shortcut in the editor: its icon, its label and its command, with buttons to move and remove it. A tap changes it. */
@Composable
private fun ShortcutRow(shortcut: Shortcut, index: Int, count: Int, onEdit: () -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    SegmentedListItem(
        colors = segColors(),
        onClick = onEdit,
        shapes = segShapes(index, count),
        leadingContent = shortcut.icon?.let { icon -> { Text(icon, style = MaterialTheme.typography.titleMedium, maxLines = 1) } },
        supportingContent = {
            Text(shortcut.command, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(painterResource(R.drawable.ic_arrow_up), "Move ${shortcut.label} earlier")
                }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onMove(1) }, enabled = index < count - 1) {
                    Icon(painterResource(R.drawable.ic_arrow_down), "Move ${shortcut.label} later")
                }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onRemove) {
                    Icon(painterResource(R.drawable.ic_delete), "Remove ${shortcut.label}", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) { Text(shortcut.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/**
 * Adds a shortcut, or changes [initial].
 * - The command is needed. While you type its first word, the commands that start so are offered.
 * - The label and the icon may stay empty: the button then says the command.
 * - Each box takes no more than the widget keeps ([Shortcuts]).
 */
@Composable
private fun ShortcutDialog(initial: Shortcut?, commands: List<String>, onDismiss: () -> Unit, onSave: (Shortcut) -> Unit) {
    var command by rememberSaveable { mutableStateOf(initial?.command.orEmpty()) }
    // A label that is only the command was not typed: the box starts empty for it.
    var label by rememberSaveable { mutableStateOf(initial?.label?.takeIf { it != initial?.command }.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(initial?.icon.orEmpty()) }
    val picks = remember(command, commands) { ShortcutPicks.matching(command, commands) }
    val plain = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Next)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add shortcut" else "Change shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    shape = TextFieldDefaults.roundedShape,
                    colors = fieldColors(),
                    value = command,
                    // A command is one line.
                    onValueChange = { command = it.replace('\n', ' ').takeWhole(Shortcuts.MAX_COMMAND) },
                    label = { Text("Command") },
                    placeholder = { Text("torch") },
                    supportingText = { Text("What you would type in the terminal") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = plain,
                    modifier = Modifier.fillMaxWidth().testTag("shortcut-command"),
                )
                if (picks.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        picks.forEach { name ->
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = { command = name }) { Text(name, fontFamily = FontFamily.Monospace) }
                        }
                    }
                }
                TextField(
                    shape = TextFieldDefaults.roundedShape,
                    colors = fieldColors(),
                    value = label,
                    onValueChange = { label = it.replace('\n', ' ').takeWhole(Shortcuts.MAX_LABEL) },
                    label = { Text("Label") },
                    placeholder = { Text("The command, if empty") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("shortcut-label"),
                )
                TextField(
                    shape = TextFieldDefaults.roundedShape,
                    colors = fieldColors(),
                    value = icon,
                    onValueChange = { icon = it.replace('\n', ' ').takeWhole(Shortcuts.MAX_ICON) },
                    label = { Text("Icon") },
                    placeholder = { Text("An emoji, if you like") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("shortcut-icon"),
                )
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                enabled = command.isNotBlank(),
                onClick = { onSave(Shortcut(label = label, command = command, icon = icon.ifBlank { null })) },
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    )
}
