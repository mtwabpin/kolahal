package com.mtwabpin.kolahal.ui.a2ui

import android.content.Context
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.compose.ui.toJsonSchemaString
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiCreateSurfaceMessage
import androidx.a2ui.model.protocol.A2uiDeleteSurfaceMessage
import androidx.a2ui.model.protocol.A2uiEventAction
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.protocol.A2uiExecutionContext
import androidx.a2ui.model.protocol.A2uiServerToClientMessage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.script.SecretStore
import com.mtwabpin.kolahal.ui.fieldColors
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/*
 * The A2UI trial (`a2ui.page`, full build only): Gemini answers with A2UI v0.9 messages, and Google's
 * renderer draws them with its Material 3 basic catalog. Nothing in an answer reaches the app: a
 * button's event goes back to Gemini as the next turn, local function calls (openUrl) are dropped,
 * and pictures, video and sound are never loaded. The conversation lives as long as home does.
 */

/** Where the Gemini key is kept: in SecretStore, under an owner name no script can have. */
private const val SECRET_OWNER = "/a2ui"
private const val SECRET_NAME = "gemini"

/** Debug builds only: asking this draws [SAMPLE] without Gemini, and answers its button locally ([A2uiSession.send]). */
private const val SAMPLE_WORD = "/sample"

/** Asking this forgets the saved Gemini key and shows the key's field again, to enter another. */
private const val KEY_WORD = "/key"

@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator
@Composable
internal fun A2uiPage(model: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { A2uiSession(context.applicationContext) }
    LaunchedEffect(session) { withContext(Dispatchers.Default) { session.processor.collectMessages() } }
    LaunchedEffect(session) {
        session.processor.outboundEvents.collect { event ->
            when (event) {
                is A2uiClientEventMessage -> session.send(scope, model, actionJson(event))
                is A2uiClientErrorMessage -> session.rendererError(errorJson(event), event.message)
            }
        }
    }
    val surfaces by session.processor.activeSurfaces.collectAsStateWithLifecycle()
    GuiTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!session.hasKey) KeyEntry { session.saveKey(scope, it) }
                    session.error?.let { ErrorState(it, session.raw) { session.forgetKey(scope) } }
                    surfaces.forEach { surface ->
                        A2uiSurface(
                            surface,
                            Modifier.fillMaxWidth(),
                            errorContent = { e -> ErrorState("The renderer rejected the answer\n${e.message}", session.raw) {} },
                        )
                    }
                    if (session.busy) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                AskBar(enabled = !session.busy) { session.send(scope, model, it) }
            }
        }
    }
}

@Composable
private fun KeyEntry(onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Text("A Gemini API key from Google AI Studio is needed.", style = MaterialTheme.typography.bodyLarge)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            shape = TextFieldDefaults.roundedShape,
            colors = fieldColors(),
            value = key,
            onValueChange = { key = it.trim() },
            label = { Text("API key") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (key.length >= SecretStore.MIN_LENGTH) onSave(key) }),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.weight(1f),
        )
        Button(shapes = ButtonDefaults.shapes(), enabled = key.length >= SecretStore.MIN_LENGTH, onClick = { onSave(key) }) {
            Text("Save")
        }
    }
}

@Composable
private fun ErrorState(message: String, raw: String?, onForgetKey: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer { Text(message) }
            if (raw != null) SelectionContainer {
                Text(raw, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            if ("HTTP 400" in message || "HTTP 403" in message) {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onForgetKey) { Text("Enter another key") }
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
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(shapes = ButtonDefaults.shapes(), enabled = enabled, onClick = { ask() }) { Text("Ask") }
    }
}

/** The page's state: the processor, the conversation, and what the page shows besides the surfaces. */
private class A2uiSession(private val context: Context) {
    private val secrets = SecretStore(context)

    // openUrl is the one function that reaches outside: it does nothing here.
    private val functions = object : A2uiBasicCatalogV1.Functions by A2uiBasicCatalogV1.Functions(context) {
        override fun openUrl(url: String, executionContext: A2uiExecutionContext) {}
    }

    val catalog: A2uiCatalog = materialA2uiBasicCatalogV1(
        image = MaterialA2uiBasicCatalogV1Defaults.image { url, description, _, modifier, _ ->
            Text("[picture: ${description ?: url}]", modifier, style = MaterialTheme.typography.bodySmall)
        },
        video = MaterialA2uiBasicCatalogV1Defaults.video { url, modifier, _ ->
            Text("[video: $url]", modifier, style = MaterialTheme.typography.bodySmall)
        },
        audioPlayer = MaterialA2uiBasicCatalogV1Defaults.audioPlayer { url, description, modifier, _ ->
            Text("[sound: ${description ?: url}]", modifier, style = MaterialTheme.typography.bodySmall)
        },
        functions = functions,
    )

    // Only events go on (to Gemini); a local function call from an action is dropped.
    val processor = A2uiMessageProcessor(listOf(catalog), listOf(A2uiActionInterceptor { it as? A2uiEventAction }))
    private val parser = A2uiMessageParser()
    private val system by lazy { systemInstruction(catalog) }
    private val turns = mutableListOf<Gemini.Turn>()

    /** The renderer's complaints since the last turn: they go to Gemini with the next one. */
    private val pendingErrors = mutableListOf<String>()
    private var sample = false

    var hasKey by mutableStateOf(secrets.get(SECRET_OWNER, SECRET_NAME) != null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var raw by mutableStateOf<String?>(null)

    fun saveKey(scope: CoroutineScope, key: String) = scope.launch {
        withContext(Dispatchers.IO) { secrets.set(SECRET_OWNER, SECRET_NAME, key, listOf(Gemini.HOST)) }
        hasKey = true
        error = null
    }

    fun forgetKey(scope: CoroutineScope) = scope.launch {
        withContext(Dispatchers.IO) { secrets.set(SECRET_OWNER, SECRET_NAME, null, emptyList()) }
        hasKey = false
        error = null
    }

    fun rendererError(json: String, message: String) {
        pendingErrors += json
        error = "The renderer rejected the answer\n$message"
    }

    /** Sends [text] as the user's next turn (a question, or an action's JSON) and draws the answer. */
    fun send(scope: CoroutineScope, model: String, text: String) {
        if (busy) return
        if (text.trim() == KEY_WORD) {
            forgetKey(scope)
            return
        }
        if (BuildConfig.DEBUG && (text == SAMPLE_WORD || sample && text.startsWith("{\"action\""))) {
            sample = true
            apply(if (text == SAMPLE_WORD) SAMPLE else sampleReply(text))
            return
        }
        // Debug builds: a hand-written answer typed in (starting with "[") is drawn as Gemini's would be.
        if (BuildConfig.DEBUG && text.startsWith("[")) {
            apply(text)
            return
        }
        sample = false
        busy = true
        error = null
        scope.launch {
            try {
                val key = withContext(Dispatchers.IO) { secrets.get(SECRET_OWNER, SECRET_NAME)?.value }
                if (key == null) {
                    hasKey = false
                    return@launch
                }
                val turn = (pendingErrors + text).joinToString("\n")
                pendingErrors.clear()
                turns += Gemini.Turn("user", turn)
                val answer = try {
                    withContext(Dispatchers.IO) { Gemini.generate(key, model, system, turns) }
                } catch (e: Exception) {
                    turns.removeAt(turns.lastIndex)
                    throw e
                }
                turns += Gemini.Turn("model", answer)
                apply(answer)
            } catch (e: Exception) {
                raw = null
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    /** Parses [answer] into messages and hands them to the processor, all or none. */
    private fun apply(answer: String) {
        raw = answer
        error = null
        val messages: List<A2uiServerToClientMessage> = try {
            val value = JSONTokener(answer.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")).nextValue()
            val list = when (value) {
                is JSONArray -> value
                is JSONObject -> value.optJSONArray("messages") ?: JSONArray().put(value)
                else -> throw IllegalArgumentException("Not a JSON array of messages")
            }
            (0 until list.length()).map { parser.parse(list.get(it).toString()) }
        } catch (e: A2uiException) {
            pendingErrors += JSONObject().put("error", JSONObject().put("code", e.code).put("message", e.message)).toString()
            error = "The renderer rejected the answer\n${e.message}"
            return
        } catch (e: Exception) {
            error = "The answer isn't A2UI JSON\n${e.message}"
            return
        }
        // A surface made again replaces the one there: the answer to a new question is a new screen.
        val shown = processor.activeSurfaces.value.map { it.id }.toMutableSet()
        for (m in messages) {
            if (m is A2uiCreateSurfaceMessage && !shown.add(m.surfaceId)) processor.processMessage(A2uiDeleteSurfaceMessage(m.surfaceId))
            processor.processMessage(m)
        }
    }
}

/** The protocol's client-to-server `action` message, with the data model as its metadata. */
private fun actionJson(e: A2uiClientEventMessage): String {
    val time = java.time.Instant.ofEpochMilli(e.timestamp).toString()
    val json = JSONObject().put(
        "action", JSONObject()
            .put("name", e.type)
            .put("surfaceId", e.surfaceId)
            .put("sourceComponentId", e.componentId)
            .put("timestamp", time)
            .put("context", JSONObject(e.context)),
    )
    e.clientDataModel?.let { json.put("metadata", JSONObject(it.toPayloadMap())) }
    return json.toString()
}

private fun errorJson(e: A2uiClientErrorMessage): String = JSONObject().put(
    "error", JSONObject(e.context).put("code", e.code).put("surfaceId", e.surfaceId).put("message", e.message),
).toString()

private fun systemInstruction(catalog: A2uiCatalog): String = """
You draw the screen of a phone app with the A2UI protocol, version 0.9.1. Answer only with a JSON array of A2UI server-to-client messages: no prose, no code fences.

Messages: each is an object with "version": "v0.9" and exactly one of these keys:
- "createSurface": {"surfaceId", "catalogId": "${A2uiBasicCatalogV1.CatalogId}", "sendDataModel": true}
- "updateComponents": {"surfaceId", "components": [...]}: a flat list; each component has a unique "id", "component" (its type from the catalog) and its properties. Children are named by id ("child": "id", "children": ["id", ...]). Exactly one component has the id "root".
- "updateDataModel": {"surfaceId", "path": "/a/b", "value": ...}: sets the data model at a JSON Pointer.
- "deleteSurface": {"surfaceId"}

Rules:
- Use only the components and properties of the catalog below. Use surfaceId "main".
- Answer a new question with createSurface "main" followed by updateComponents (and updateDataModel for initial values): the app replaces the screen that was there.
- A dynamic value is a literal, or {"path": "/json/pointer"} bound to the data model. Input components (TextField, CheckBox, ChoicePicker, Slider, DateTimeInput) bind their "value" to a path.
- A Button's "action" is {"event": {"name": "...", "context": {"key": {"path": "/..."}}}}. Never use "functionCall" actions or openUrl: the app ignores them. Images, video and audio are not loaded: avoid them.
- When the user acts, you get {"action": {"name", "surfaceId", "sourceComponentId", "timestamp", "context"}, "metadata": {"a2uiClientDataModel": ...}}. Answer with messages that update surface "main" (updateComponents, updateDataModel), or a new screen.
- When you get {"error": ...}, your last answer was rejected: send a corrected one.
- Keep the screen phone-sized. Text "variant" can be h1 to h5, caption or body.

Example answer:
$SAMPLE

The catalog (JSON Schema):
${catalog.toJsonSchemaString()}
""".trimIndent()

/** A card with a title, a text, a text field and a button: the spike's hand-written answer. */
private val SAMPLE = """
[
{"version":"v0.9","createSurface":{"surfaceId":"main","catalogId":"${A2uiBasicCatalogV1.CatalogId}","sendDataModel":true}},
{"version":"v0.9","updateComponents":{"surfaceId":"main","components":[
{"id":"root","component":"Card","child":"body"},
{"id":"body","component":"Column","children":["title","text","name","send"]},
{"id":"title","component":"Text","text":"Hello from A2UI","variant":"h2"},
{"id":"text","component":"Text","text":"Type your name and tap Send."},
{"id":"name","component":"TextField","label":"Your name","value":{"path":"/form/name"}},
{"id":"send_label","component":"Text","text":"Send"},
{"id":"send","component":"Button","child":"send_label","variant":"primary","action":{"event":{"name":"send","context":{"name":{"path":"/form/name"}}}}}
]}},
{"version":"v0.9","updateDataModel":{"surfaceId":"main","path":"/form","value":{"name":""}}}
]
""".trimIndent()

/** Debug builds: the sample's answer to its button, shown in its text, so a tap's whole path can be seen without a key. */
private fun sampleReply(action: String): String = JSONArray().put(
    JSONObject().put("version", "v0.9").put(
        "updateComponents", JSONObject().put("surfaceId", "main").put(
            "components", JSONArray().put(
                JSONObject().put("id", "text").put("component", "Text").put("text", "The app sent: $action")
            )
        )
    )
).toString()
