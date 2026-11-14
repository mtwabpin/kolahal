package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.widgets.WidgetRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The words under the title of a widget's options sheet: whose options these are. Pure, for tests. */
internal object WidgetOptionWords {
    /** The first widget shows the script's own options. */
    const val SCRIPT = "These are the script's own options. Every run reads them, and so does each of its widgets that has no value of its own."
    /** A further widget follows the script until you set a value for it. */
    const val WIDGET = "This widget follows the script's options. A value you set here is for this widget only."
    const val NONE = "This script has no options. A script declares one with an @option line."
    const val GONE = "The script is gone, so there is nothing to set."

    /** What the sheet says for the widget [ref]. [found]: its script is there. [options]: how many options the script declares. */
    fun note(ref: WidgetRef, found: Boolean, options: Int): String = when {
        !found -> GONE
        options == 0 -> NONE
        ref.first -> SCRIPT
        else -> WIDGET
    }
}

/**
 * The options of one widget of a script ([ref]), on the Widgets screen.
 * - The script's first widget has the script's own options: the sheet edits those, as the script's
 *   sheet on the Scripts screen does.
 * - A further widget follows the script's options until you set one for that widget. A value set
 *   here is kept for that widget only, and shows in the primary colour. Reset makes the option
 *   follow the script again.
 * - A saved value asks for one run of this widget, so its card shows what the new value gives
 *   ([com.mtwabpin.kolahal.terminal.ScriptActions.setWidgetOption]).
 *
 * It reads the script from the view model's list, and its values off the main thread.
 */
@Composable
internal fun WidgetOptionsSheet(vm: TerminalViewModel, ref: WidgetRef, onDismiss: () -> Unit) {
    val scripts by vm.scriptList.collectAsStateWithLifecycle()
    val script = scripts.firstOrNull { it.name == ref.script }
    val options = remember(script?.code) { script?.let { ScriptMeta.options(it.code) }.orEmpty() }
    // The widget's name as the Widgets screen says it: its script's title, with the id of a further widget.
    val known by vm.widgetScripts.collectAsStateWithLifecycle()
    val name = ref.titled(known?.get(ref.script)?.title ?: ref.script)
    val version by vm.scriptActions.optionsVersion.collectAsStateWithLifecycle()
    val values by produceState(emptyMap<String, String>() to emptySet<String>(), script?.code, ref, version) {
        value = script?.let { s -> withContext(Dispatchers.IO) { vm.scriptActions.optionValues(s, ref.id) } } ?: (emptyMap<String, String>() to emptySet())
    }
    var editing by remember(ref) { mutableStateOf<ScriptMeta.Option?>(null) }

    GuiSheet(onDismiss, scroll = true) {
        Text("Options for $name", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("widget-options"))
        Text(
            WidgetOptionWords.note(ref, script != null, options.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        options.forEach { o ->
            ValueRow(
                o.name,
                o.desc,
                (values.first[o.name] ?: o.default).ifEmpty { "—" },
                if (o.name in values.second) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                // A value can be long (an address, a list): it gets one line, and the name keeps the rest.
                valueMaxWidth = 160.dp,
                onClick = { editing = o },
            )
        }
    }

    editing?.let { o ->
        // The dialog the script's own sheet uses. For a further widget, Reset takes its own value away.
        OptionDialog(ref.titled(ref.script), o, values.first[o.name] ?: o.default, changed = o.name in values.second, onDismiss = { editing = null }) { value ->
            editing = null
            vm.scriptActions.setWidgetOption(ref, o.name, value)
        }
    }
}
