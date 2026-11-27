package com.mtwabpin.kolahal.ui.assistant

import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.assistant.Asker
import com.mtwabpin.kolahal.assistant.AssistantKeys
import com.mtwabpin.kolahal.assistant.ConversationStore
import com.mtwabpin.kolahal.assistant.KeyRule
import com.mtwabpin.kolahal.assistant.KvShelf
import com.mtwabpin.kolahal.assistant.Problem
import com.mtwabpin.kolahal.assistant.Service
import com.mtwabpin.kolahal.assistant.Services
import com.mtwabpin.kolahal.assistant.Summary
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.ui.ConnectedChoices
import com.mtwabpin.kolahal.ui.GuiSheet
import com.mtwabpin.kolahal.ui.LabeledIconButton
import com.mtwabpin.kolahal.ui.EmptyNote
import com.mtwabpin.kolahal.ui.fieldColors
import com.mtwabpin.kolahal.ui.produceIo
import com.mtwabpin.kolahal.ui.settings.ChipChoices
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.segColors
import com.mtwabpin.kolahal.ui.theme.segShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What the page says about what is sent, the first time and on its settings page. */
internal fun sentLine(service: Service) =
    "Your questions and the conversation go to ${service.name} with your own API key. Nothing else from the phone is sent."

/**
 * The Assistant's page (`assistant.page`, full build only): a thread of questions and answers with the
 * service and model of `assistant.service` and `assistant.model`, drawn as A2UI (`assistant.rich`) or
 * written as text. [asker] sends the questions: tests give a fake one. [onSettings] opens Settings →
 * Assistant.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator
@Composable
fun AssistantPage(config: Config, onSettings: () -> Unit, asker: Asker? = null) {
    val app = LocalContext.current.applicationContext
    // On the main thread: what a launch does after its IO call (an answer, a saved key, a conversation opened)
    // changes the thread. A test's effects run unconfined, and went on on the IO thread, where a change made
    // while the thread was measured broke it (IndexOutOfBounds) or wasn't shown.
    val scope = rememberCoroutineScope { Dispatchers.Main.immediate }
    val keys = remember { AssistantKeys(app) }
    val store = remember { ConversationStore(KvShelf(app)) }
    // Kept while the app runs: the conversation, and a question waiting, outlive the page.
    val session = remember(asker) { AssistantSessions.get(app, asker) }
    val version by config.version.collectAsStateWithLifecycle()
    val service = remember(version) { Services.byId(config[Keys.ASSISTANT_SERVICE]) }
    val model = remember(version, service) { service.modelFor(config[Keys.ASSISTANT_MODEL]) }
    val keep = remember(version) { config[Keys.ASSISTANT_HISTORY] }
    // Read when a question goes, not when the page was drawn: a tap's collector outlives a change of service.
    fun askWith(): AskWith {
        val s = Services.byId(config[Keys.ASSISTANT_SERVICE])
        return AskWith(s, s.modelFor(config[Keys.ASSISTANT_MODEL]), config[Keys.ASSISTANT_RICH], config[Keys.ASSISTANT_INSTRUCTIONS], config[Keys.ASSISTANT_HISTORY])
    }

    // Whether this service has a key, read again after one is saved.
    var keyReads by remember { mutableIntStateOf(0) }
    val hasKey by produceIo<Boolean?>(null, service, keyReads, what = "Reading the API key") { keys.has(service) }
    var enterKey by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }

    val processor = session.processor
    LaunchedEffect(processor) {
        try {
            withContext(Dispatchers.Default) { processor.collectMessages() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The renderer stopped on an answer: say so, and start it again without losing the page.
            session.restart(e.message ?: e.javaClass.simpleName)
        } catch (e: StackOverflowError) {
            session.restart("The components are nested too deep")
        }
    }
    LaunchedEffect(processor) {
        processor.outboundEvents.collect { event ->
            when (event) {
                is A2uiClientEventMessage -> session.tapped(askWith(), event)
                is A2uiClientErrorMessage -> session.rendererError(errorJson(event), event.message)
            }
        }
    }
    val surfaces by processor.activeSurfaces.collectAsStateWithLifecycle()

    GuiTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Assistant", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    // Not while a question waits: its answer belongs to the conversation shown.
                    if (keep) LabeledIconButton(R.drawable.ic_schedule, "Earlier conversations", { history = true }, enabled = !session.busy)
                    LabeledIconButton(R.drawable.ic_add, "New conversation", { session.newConversation() }, enabled = !session.busy)
                    LabeledIconButton(R.drawable.ic_settings, "Assistant settings", onSettings)
                }
                ConnectedChoices(Services.ALL.map { it.id }, service.id, tagPrefix = "service ", label = { Services.byId(it).name }) {
                    // Another service: its default model, as the one chosen was the other's.
                    config.set(Keys.ASSISTANT_SERVICE, it)
                    config.set(Keys.ASSISTANT_MODEL, "")
                }
                ModelChoice(service, model) { config.set(Keys.ASSISTANT_MODEL, it) }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // A surface never measures without a limit: four screens is more than an answer needs.
                    val most = maxHeight * 4
                    // A list of its own for each conversation: the last one's place in a longer list made it
                    // measure an item that no longer was (IndexOutOfBounds after New or an earlier one opened).
                    val list = remember(session.shown) { androidx.compose.foundation.lazy.LazyListState() }
                    val showKey = hasKey == false || enterKey
                    LaunchedEffect(session.items.size, session.busy, showKey) {
                        if (session.items.isNotEmpty()) list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                    }
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("assistant-thread"),
                        state = list,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // The thread's items only ever grow (a new conversation gets a list of its own): the line at
                        // the start and the key card are always there, empty when not shown. A list that lost an
                        // item while it was scrolled or focused measured one past its end (IndexOutOfBounds).
                        item(key = "start") {
                            if (session.items.isEmpty()) Text(
                                sentLine(service),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        itemsIndexed(session.items, key = { i, _ -> "item$i" }) { _, item ->
                            ThreadItem(item, surfaces, most, onKey = { enterKey = true }, onSettings = onSettings)
                        }
                        // Under the thread, just above the Ask field: where the next question needs it.
                        item(key = "key") {
                            if (showKey) KeyEntry(service) { key ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { keys.set(service, key) }
                                    enterKey = false
                                    keyReads++
                                }
                            }
                        }
                        item(key = "end") {
                            if (session.busy) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                LoadingIndicator(Modifier.testTag("assistant-busy").semantics { contentDescription = "Waiting for the answer" })
                            }
                        }
                    }
                }
                AskBar(enabled = !session.busy) { session.ask(askWith(), it) }
            }
        }
        if (history) HistorySheet(
            store,
            session.conversationId,
            onDismiss = { history = false },
            // The one open, deleted: the page starts a new one, so it isn't kept again.
            onDeleted = { session.deleted(it) },
        ) { c ->
            history = false
            if (session.open(c)) {
                config.set(Keys.ASSISTANT_SERVICE, c.service)
                config.set(Keys.ASSISTANT_MODEL, c.model)
            }
        }
    }
}

/** The service's models, one tap each. A model typed in Settings shows as one more choice. */
@Composable
private fun ModelChoice(service: Service, model: String, onModel: (String) -> Unit) {
    val ids = service.models.map { it.id }.let { if (model in it) it else it + model }
    ChipChoices(ids, model, label = service::modelName, onPick = onModel)
}

@Composable
private fun ThreadItem(
    item: Item,
    surfaces: List<androidx.a2ui.model.processor.A2uiSurfaceModel>,
    most: androidx.compose.ui.unit.Dp,
    onKey: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    when (item) {
        is Item.Asked -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 300.dp)) {
                SelectionContainer {
                    Text(item.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is Item.Tapped -> Text(
            "You tapped ${item.name.ifEmpty { "a button" }}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
        )

        is Item.Written -> SelectionContainer {
            Text(
                remember(item.text, colors.primary) { answerText(item.text, block = true, code = colors.primary) },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().testTag("assistant-text"),
            )
        }

        is Item.Drawn -> surfaces.firstOrNull { it.id == item.surfaceId }?.let { surface ->
            A2uiSurface(
                surface,
                Modifier.fillMaxWidth().heightIn(max = most).testTag("surface ${item.surfaceId}"),
                errorContent = { e -> Failure("The answer couldn't be drawn\n${e.message.orEmpty()}", null, null, onKey, onSettings) },
            )
        }

        Item.Changed -> Text(
            "Changed the answer above",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )

        is Item.Failed -> Failure(item.message, item.raw, item.problem, onKey, onSettings)
    }
}

/** A question with no answer, or an answer that can't be drawn: what happened, and the answer as it came. */
@Composable
private fun Failure(message: String, raw: String?, problem: Problem?, onKey: () -> Unit, onSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("assistant-error")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val lines = message.lines()
            SelectionContainer { Text(lines.first(), fontWeight = FontWeight.Medium) }
            if (lines.size > 1) SelectionContainer { Text(lines.drop(1).joinToString("\n"), style = MaterialTheme.typography.bodySmall) }
            if (raw != null) SelectionContainer {
                Text(raw, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 30, overflow = TextOverflow.Ellipsis)
            }
            when (problem) {
                Problem.BAD_KEY, Problem.NO_KEY ->
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = onKey) { Text("Enter another key") }
                Problem.NO_MODEL, Problem.NO_CREDIT ->
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = onSettings) { Text("Open settings") }
                else -> Unit
            }
        }
    }
}

/** The field for [service]'s key, where it has none. The key is kept sealed on this phone. */
@Composable
private fun KeyEntry(service: Service, onSave: (String) -> Unit) {
    var key by remember(service) { mutableStateOf("") }
    val fits = key.isEmpty() || KeyRule.fits(key)
    val ok = key.length >= AssistantKeys.MIN_LENGTH && fits
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${service.company} API key", style = MaterialTheme.typography.titleMedium)
            Text(
                "${service.name} needs your own API key from ${service.company}. It's kept sealed on this phone and sent only to ${service.host}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.weight(1f).testTag("assistant-key"),
                )
                Button(shapes = ButtonDefaults.shapes(), enabled = ok, onClick = { onSave(key) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun AskBar(enabled: Boolean, onAsk: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun ask() {
        if (enabled && text.isNotBlank()) {
            onAsk(text.trim())
            text = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            shape = TextFieldDefaults.roundedShape,
            colors = fieldColors(),
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Ask") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { ask() }),
            maxLines = 4,
            modifier = Modifier.weight(1f).testTag("assistant-ask"),
        )
        Button(shapes = ButtonDefaults.shapes(), enabled = enabled, onClick = { ask() }) { Text("Ask") }
    }
}

/** The conversations kept: each one's first question and date; a tap opens it, the bin deletes it. */
@Composable
private fun HistorySheet(
    store: ConversationStore,
    current: String?,
    onDismiss: () -> Unit,
    onDeleted: (String) -> Unit,
    onOpen: (com.mtwabpin.kolahal.assistant.Conversation) -> Unit,
) {
    val scope = rememberCoroutineScope { Dispatchers.Main.immediate } // As the page's: on the main thread.
    var reads by remember { mutableIntStateOf(0) }
    val list by produceIo<List<Summary>?>(null, reads, what = "Reading the conversations") { store.list() }
    GuiSheet(onDismiss = onDismiss, scroll = true) {
        Text("Earlier conversations", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
        val rows = list ?: return@GuiSheet
        if (rows.isEmpty()) EmptyNote("No conversations kept yet")
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { i, s ->
                val title = s.title.ifEmpty { "(no question)" }
                SegmentedListItem(
                    colors = segColors(),
                    shapes = segShapes(i, rows.size),
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { store.load(s.id) }?.let(onOpen)
                        }
                    },
                    supportingContent = {
                        Text("${dateOf(s.updated)} · ${Services.byId(s.service).name}${if (s.id == current) " · open now" else ""}")
                    },
                    trailingContent = {
                        LabeledIconButton(R.drawable.ic_delete, "Delete $title", {
                            scope.launch {
                                withContext(Dispatchers.IO) { store.delete(s.id) }
                                onDeleted(s.id)
                                reads++
                            }
                        }, modifier = Modifier.testTag("delete ${s.id}"))
                    },
                    modifier = Modifier.testTag("conversation ${s.id}"),
                ) { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

private fun dateOf(millis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(millis))

private fun errorJson(e: A2uiClientErrorMessage): String = JSONObject().put(
    "error", JSONObject(e.context).put("code", e.code).put("surfaceId", e.surfaceId).put("message", e.message),
).toString()
