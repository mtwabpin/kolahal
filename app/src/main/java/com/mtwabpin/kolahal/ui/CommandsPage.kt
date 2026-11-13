package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.CommandCatalog
import com.mtwabpin.kolahal.terminal.CommandSpec
import com.mtwabpin.kolahal.terminal.CommandsOff
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/** The Commands page in Settings. Its switches set `commands.off`. */
internal const val COMMANDS_CATEGORY = "commands"

/**
 * The commands that can be turned off ([CommandsOff.CHOICES]), under the section titles `help`
 * uses. A command in no section comes last, under "Other".
 */
internal fun commandSections(): List<Pair<String, List<CommandSpec>>> {
    val specs = CommandCatalog.OFFERED.filter { it.name in CommandsOff.CHOICES }.associateBy { it.name }
    val placed = HashSet<String>()
    val sections = CommandCatalog.HELP_SECTIONS.mapNotNull { (section, names) ->
        val shown = names.mapNotNull(specs::get).filter { placed.add(it.name) }
        if (shown.isEmpty()) null else section.replaceFirstChar(Char::uppercase) to shown
    }
    val rest = specs.values.filter { it.name !in placed }
    return if (rest.isEmpty()) sections else sections + ("Other" to rest)
}

/**
 * Settings → Commands: a switch for each built-in command, in `help`'s sections. A command
 * switched off isn't suggested or listed in help, and its name opens the app it matches.
 */
internal fun LazyListScope.commandsPage(vm: TerminalViewModel, version: State<Int>) {
    item(key = "commands:hint") {
        PageHint(
            "Turn off the commands you don't use. A command that is off isn't suggested or listed in help, " +
                "and typing its name opens the app it matches: with maps off, maps opens Maps. " +
                "help, config and settings stay on.",
        )
    }
    for ((section, specs) in commandSections()) {
        item(key = "commands:h:$section") { GroupHeader(section) }
        specs.forEachIndexed { index, spec ->
            item(key = "command:${spec.name}") { CommandSwitch(vm, spec, version, index, specs.size) }
        }
    }
}

/** One command's row: its name, what it does and its other names, and a switch (on: the command works). */
@Composable
private fun CommandSwitch(vm: TerminalViewModel, spec: CommandSpec, version: State<Int>, index: Int, count: Int) {
    val on by remember(spec.name) { derivedStateOf { version.value; spec.name !in CommandsOff.names(vm.config[Keys.COMMANDS_OFF]) } }
    val also = spec.aka
    Box(Modifier.padding(bottom = ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
            colors = segColors(),
            checked = on,
            onCheckedChange = { vm.setConfig(Keys.COMMANDS_OFF, CommandsOff.toggled(vm.config[Keys.COMMANDS_OFF], spec.name, off = !it)) },
            shapes = segShapes(index, count),
            supportingContent = { Text(if (also.isEmpty()) spec.short else spec.short + " · also " + also.joinToString(", ")) },
            trailingContent = { Switch(checked = on, onCheckedChange = null) },
            modifier = Modifier.testTag("command ${spec.name}"),
        ) { Text(spec.name, fontFamily = FontFamily.Monospace) }
    }
}
