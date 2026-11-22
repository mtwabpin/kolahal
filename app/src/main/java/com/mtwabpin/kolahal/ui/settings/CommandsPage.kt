/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.commands.CommandSpec
import com.mtwabpin.kolahal.commands.CommandsOff
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.SearchWords
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.GroupHeader
import com.mtwabpin.kolahal.ui.NoMatchNote
import com.mtwabpin.kolahal.ui.PageHint
import com.mtwabpin.kolahal.ui.SearchField
import com.mtwabpin.kolahal.ui.theme.segColors
import com.mtwabpin.kolahal.ui.theme.segShapes

/** The Commands page in Settings. Its switches set `commands.off`. */
internal const val COMMANDS_CATEGORY = "commands"

/**
 * The commands that can be turned off ([CommandsOff.CHOICES]), under the section titles `help`
 * uses. A command in no section comes last, under "Other".
 */
internal fun commandSections(): List<Pair<String, List<CommandSpec>>> {
    val specs =
        CommandCatalog.OFFERED.filter { it.name in CommandsOff.CHOICES }.associateBy { it.name }
    val placed = HashSet<String>()
    val sections = CommandCatalog.HELP_SECTIONS.mapNotNull { (section, names) ->
        val shown = names.mapNotNull(specs::get).filter { placed.add(it.name) }
        if (shown.isEmpty()) null else section.replaceFirstChar(Char::uppercase) to shown
    }
    val rest = specs.values.filter { it.name !in placed }
    return if (rest.isEmpty()) sections else sections + ("Other" to rest)
}

/**
 * [sections] with only the commands that [query] finds, for the page's search field.
 * - A command is found when its row shows every word of [query] ([shown], [SearchWords]).
 * - A section with no command found is left out. The others keep their order.
 * - A blank [query] finds every command.
 */
internal fun commandsFound(
    sections: List<Pair<String, List<CommandSpec>>>,
    query: String
): List<Pair<String, List<CommandSpec>>> {
    val words = SearchWords.of(query)
    if (words.isEmpty()) return sections
    return sections.mapNotNull { (section, specs) ->
        val found = specs.filter { SearchWords.allIn(words, it.shown()) }
        if (found.isEmpty()) null else section to found
    }
}

/** The texts the command's row shows, which the search looks in: its name, what it does ([CommandSpec.short]) and its other names. */
private fun CommandSpec.shown(): List<String> = listOf(name, short) + aka

/**
 * Settings → Commands: a switch for each built-in command, in `help`'s sections. A command
 * switched off isn't suggested or listed in help, and its name opens the app it matches.
 *
 * The search field at the top keeps only the commands that [query] finds ([commandsFound]).
 * A row's shape comes from its place among the rows shown, so the first and the last row of a
 * section keep their round corners.
 */
internal fun LazyListScope.commandsPage(
    vm: TerminalViewModel,
    version: State<Int>,
    query: String,
    onQuery: (String) -> Unit
) {
    item(key = "commands:search") {
        SearchField(
            query,
            onQuery,
            "Search commands",
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            clear = "Clear",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
    }
    val sections = commandsFound(commandSections(), query)
    // During a search, the commands found come right under the field: the keyboard leaves little room.
    if (query.isBlank()) {
        item(key = "commands:hint") {
            PageHint(
                "Turn off the commands you don't use. A command that is off isn't suggested or listed in help, " +
                        "and typing its name opens the app it matches: with maps off, maps opens Maps. " +
                        "help, config and settings stay on.",
            )
        }
    }
    if (sections.isEmpty()) item(key = "commands:none") { NoMatchNote("No command matches “${query.trim()}”") }
    for ((section, specs) in sections) {
        item(key = "commands:h:$section") { GroupHeader(section) }
        specs.forEachIndexed { index, spec ->
            item(key = "command:${spec.name}") {
                CommandSwitch(
                    vm,
                    spec,
                    version,
                    index,
                    specs.size
                )
            }
        }
    }
}

/** One command's row: its name, what it does and its other names, and a switch (on: the command works). */
@Composable
private fun CommandSwitch(
    vm: TerminalViewModel,
    spec: CommandSpec,
    version: State<Int>,
    index: Int,
    count: Int
) {
    val on by remember(spec.name) {
        derivedStateOf {
            version.value; spec.name !in CommandsOff.names(
            vm.config[Keys.COMMANDS_OFF]
        )
        }
    }
    val also = spec.aka
    Box(Modifier.padding(bottom = ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
            colors = segColors(),
            checked = on,
            onCheckedChange = {
                vm.setConfig(
                    Keys.COMMANDS_OFF,
                    CommandsOff.toggled(vm.config[Keys.COMMANDS_OFF], spec.name, off = !it)
                )
            },
            shapes = segShapes(index, count),
            supportingContent = {
                Text(
                    if (also.isEmpty()) spec.short else spec.short + " · also " + also.joinToString(
                        ", "
                    )
                )
            },
            trailingContent = { Switch(checked = on, onCheckedChange = null) },
            modifier = Modifier.testTag("command ${spec.name}"),
        ) { Text(spec.name, fontFamily = FontFamily.Monospace) }
    }
}
