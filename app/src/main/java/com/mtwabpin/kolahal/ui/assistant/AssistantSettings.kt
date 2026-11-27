package com.mtwabpin.kolahal.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.assistant.AssistantKeys
import com.mtwabpin.kolahal.assistant.ConversationStore
import com.mtwabpin.kolahal.assistant.KeyRule
import com.mtwabpin.kolahal.assistant.KvShelf
import com.mtwabpin.kolahal.assistant.Service
import com.mtwabpin.kolahal.assistant.Services
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.SettingTitles
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.ActionGroup
import com.mtwabpin.kolahal.ui.Action
import com.mtwabpin.kolahal.ui.ConfirmDialog
import com.mtwabpin.kolahal.ui.ConnectedChoices
import com.mtwabpin.kolahal.ui.DialogBars
import com.mtwabpin.kolahal.ui.GroupHeader
import com.mtwabpin.kolahal.ui.PageHint
import com.mtwabpin.kolahal.ui.fieldColors
import com.mtwabpin.kolahal.ui.produceIo
import com.mtwabpin.kolahal.ui.settings.ChipChoices
import com.mtwabpin.kolahal.ui.settings.SettingItem
import com.mtwabpin.kolahal.ui.settings.UndoMessage
import com.mtwabpin.kolahal.ui.theme.segColors
import com.mtwabpin.kolahal.ui.theme.segShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Assistant: the page beside home; the service and its model; each service's key (saved or
 * not, never shown); rich answers and custom instructions; history, and deleting it. The service and
 * model rows are drawn here, as a new service starts on its own default model; the others are
 * ordinary setting rows. [say] shows a line in the page's snackbar.
 */
internal fun LazyListScope.assistantSettings(
    vm: TerminalViewModel,
    version: State<Int>,
    undo: UndoMessage,
    say: (String) -> Unit,
) {
    item(key = "assistant:top") { Spacer(Modifier.height(8.dp)) }
    item(key = Keys.ASSISTANT_PAGE.key) { SettingItem(vm, Keys.ASSISTANT_PAGE, version, 0, 1, undo) }
    item(key = "h:assistant:service") { GroupHeader("Service") }
    item(key = Keys.ASSISTANT_SERVICE.key) { ServiceRows(vm, version) }
    item(key = "h:assistant:keys") { GroupHeader("API keys") }
    item(key = "assistant:keys") { KeyRows(say) }
    item(key = "h:assistant:answers") { GroupHeader("Answers") }
    item(key = Keys.ASSISTANT_RICH.key) { SettingItem(vm, Keys.ASSISTANT_RICH, version, 0, 2, undo) }
    item(key = Keys.ASSISTANT_INSTRUCTIONS.key) { SettingItem(vm, Keys.ASSISTANT_INSTRUCTIONS, version, 1, 2, undo) }
    item(key = "h:assistant:history") { GroupHeader("History") }
    item(key = Keys.ASSISTANT_HISTORY.key) { SettingItem(vm, Keys.ASSISTANT_HISTORY, version, 0, 1, undo) }
    item(key = "assistant:delete") { DeleteAll(say) }
    item(key = "assistant:sent") {
        val service = remember(version.value) { Services.byId(vm.config[Keys.ASSISTANT_SERVICE]) }
        PageHint(sentLine(service), top = 16.dp)
    }
}

/** The service (one tap), the line and button when it has no key, and its model: one offered, or typed. */
@Composable
private fun ServiceRows(vm: TerminalViewModel, version: State<Int>) {
    val config = vm.config
    val service = remember(version.value) { Services.byId(config[Keys.ASSISTANT_SERVICE]) }
    val stored = remember(version.value) { config[Keys.ASSISTANT_MODEL] }
    val model = service.modelFor(stored)
    val app = LocalContext.current.applicationContext
    val keys = remember { AssistantKeys(app) }
    var keyReads by remember { mutableIntStateOf(0) }
    val hasKey by produceIo<Boolean?>(null, service, keyReads, version.value, what = "Reading the API key") { keys.has(service) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope { Dispatchers.Main.immediate } // What follows an IO call changes state: on the main thread.
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
            colors = segColors(),
            shapes = segShapes(0, 2),
            supportingContent = {
                Column {
                    Text(Keys.ASSISTANT_SERVICE.help)
                    Spacer(Modifier.height(10.dp))
                    ConnectedChoices(Services.ALL.map { it.id }, service.id, tagPrefix = "service ", label = { Services.byId(it).name }) {
                        // Another service: its default model, as the one chosen was the other's.
                        vm.setConfig(Keys.ASSISTANT_SERVICE, it)
                        vm.setConfig(Keys.ASSISTANT_MODEL, "")
                    }
                    // The option needs something it hasn't got: the line, and the button that gets it (ui/AccessNeeded).
                    if (hasKey == false) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Needs ${article(service.company)} ${service.company} API key",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        FilledTonalButton(onClick = { adding = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("needs-key ${service.id}")) {
                            Text("Add ${article(service.company)} ${service.company} API key")
                        }
                    }
                }
            },
        ) { Text(SettingTitles[Keys.ASSISTANT_SERVICE.key] ?: "") }
        SegmentedListItem(
            colors = segColors(),
            shapes = segShapes(1, 2),
            supportingContent = {
                Column {
                    Text("${service.name}'s models. Another of its models works by its name, typed below")
                    Spacer(Modifier.height(10.dp))
                    ChipChoices(service.models.map { it.id }, model, label = service::modelName) { vm.setConfig(Keys.ASSISTANT_MODEL, it) }
                    Spacer(Modifier.height(8.dp))
                    TypedModel(service, stored) { vm.setConfig(Keys.ASSISTANT_MODEL, it) }
                }
            },
        ) { Text(SettingTitles[Keys.ASSISTANT_MODEL.key] ?: "") }
    }
    if (adding) KeyDialog(service, onDismiss = { adding = false }) { key ->
        adding = false
        scope.launch {
            withContext(Dispatchers.IO) { keys.set(service, key) }
            keyReads++
        }
    }
}

/** "an" before a vowel: an Anthropic key, a Google key. */
private fun article(word: String) = if (word.take(1).lowercase() in listOf("a", "e", "i", "o", "u")) "an" else "a"

/** The field for a model the service has but the list doesn't: applied with Enter or the check mark. */
@Composable
private fun TypedModel(service: Service, stored: String, onApply: (String) -> Unit) {
    val typed = stored.takeIf { s -> s.isNotBlank() && service.models.none { it.id == s } }.orEmpty()
    var text by remember(service, typed) { mutableStateOf(typed) }
    val valid = Keys.ASSISTANT_MODEL.parse(text) != null
    TextField(
        shape = TextFieldDefaults.roundedShape,
        colors = fieldColors(),
        value = text,
        onValueChange = { text = it.trim() },
        singleLine = true,
        isError = !valid,
        label = { Text("Another model") },
        placeholder = { Text(service.default) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (valid) onApply(text) }),
        trailingIcon = if (valid && text != typed) {
            { com.mtwabpin.kolahal.ui.LabeledIconButton(R.drawable.ic_check, "Apply", { onApply(text) }) }
        } else null,
        modifier = Modifier.fillMaxWidth().testTag("field assistant.model"),
    )
}

/** One row per service: whether its key is saved; a tap adds or replaces it, the bin removes it. The key is never shown. */
@Composable
private fun KeyRows(say: (String) -> Unit) {
    val app = LocalContext.current.applicationContext
    val keys = remember { AssistantKeys(app) }
    val scope = rememberCoroutineScope { Dispatchers.Main.immediate } // What follows an IO call changes state: on the main thread.
    var reads by remember { mutableIntStateOf(0) }
    val saved by produceIo<Map<String, Boolean>?>(null, reads, what = "Reading the API keys") {
        Services.ALL.associate { it.id to keys.has(it) }
    }
    var editing by remember { mutableStateOf<Service?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        Services.ALL.forEachIndexed { i, service ->
            val has = saved?.get(service.id) == true
            SegmentedListItem(
                colors = segColors(),
                shapes = segShapes(i, Services.ALL.size),
                onClick = { editing = service },
                supportingContent = {
                    Text(if (has) "Saved, sent only to ${service.host}. Tap to replace it" else "Not saved. Tap to add it, for ${service.name}")
                },
                trailingContent = if (has) {
                    {
                        com.mtwabpin.kolahal.ui.LabeledIconButton(R.drawable.ic_delete, "Remove the ${service.company} key", {
                            scope.launch {
                                withContext(Dispatchers.IO) { keys.set(service, null) }
                                reads++
                                say("Removed the ${service.company} API key")
                            }
                        }, modifier = Modifier.testTag("remove-key ${service.id}"))
                    }
                } else null,
                modifier = Modifier.testTag("key ${service.id}"),
            ) { Text("${service.company} API key") }
        }
    }
    editing?.let { service ->
        KeyDialog(service, onDismiss = { editing = null }) { key ->
            editing = null
            scope.launch {
                withContext(Dispatchers.IO) { keys.set(service, key) }
                reads++
                say("Saved the ${service.company} API key")
            }
        }
    }
}

/** Asks for [service]'s key, hidden as it is typed. */
@Composable
private fun KeyDialog(service: Service, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    val fits = key.isEmpty() || KeyRule.fits(key)
    val ok = key.length >= AssistantKeys.MIN_LENGTH && fits
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogBars(); Text("${service.company} API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("It's kept sealed on this phone, never in a backup, and sent only to ${service.host}.")
                TextField(
                    shape = TextFieldDefaults.roundedShape,
                    colors = fieldColors(),
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = { Text("API key") },
                    singleLine = true,
                    isError = !fits,
                    supportingText = if (!fits) { { Text(KeyRule.UNFIT) } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ok) onSave(key) }),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("key-field"),
                )
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), enabled = ok, onClick = { onSave(key) }) { Text("Save") } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Deletes every conversation kept, after asking. */
@Composable
private fun DeleteAll(say: (String) -> Unit) {
    val app = LocalContext.current.applicationContext
    val store = remember { ConversationStore(KvShelf(app)) }
    val scope = rememberCoroutineScope { Dispatchers.Main.immediate } // What follows an IO call changes state: on the main thread.
    var asking by remember { mutableStateOf(false) }
    var reads by remember { mutableIntStateOf(0) }
    val count by produceIo<Int?>(null, reads, what = "Reading the conversations") { store.list().size }
    Box(Modifier.padding(top = 8.dp)) {
        ActionGroup(
            listOf(
                Action(
                    R.drawable.ic_delete,
                    "Delete all conversations",
                    when (count) {
                        null -> "Kept on this phone"
                        0 -> "None kept"
                        1 -> "1 kept on this phone"
                        else -> "$count kept on this phone"
                    },
                ) { if ((count ?: 0) > 0) asking = true else say("No conversations to delete") },
            )
        )
    }
    if (asking) ConfirmDialog(
        title = "Delete all conversations?",
        text = "The ${count ?: 0} conversations kept on this phone are deleted. This can't be undone.",
        confirm = "Delete",
        onDismiss = { asking = false },
    ) {
        asking = false
        scope.launch {
            val n = withContext(Dispatchers.IO) { store.deleteAll() }
            // The conversation on the Assistant's page, kept while it is away, isn't kept again.
            AssistantSessions.deletedAll()
            reads++
            say(if (n == 1) "Deleted 1 conversation" else "Deleted $n conversations")
        }
    }
}
