package com.mtwabpin.kolahal.ui.assistant

import android.content.Context
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.compose.ui.toJsonSchemaString
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiCreateSurfaceMessage
import androidx.a2ui.model.protocol.A2uiDeleteSurfaceMessage
import androidx.a2ui.model.protocol.A2uiEventAction
import androidx.a2ui.model.protocol.A2uiServerToClientMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.assistant.AssistantException
import com.mtwabpin.kolahal.assistant.Asker
import com.mtwabpin.kolahal.assistant.Conversation
import com.mtwabpin.kolahal.assistant.ConversationStore
import com.mtwabpin.kolahal.assistant.Entry
import com.mtwabpin.kolahal.assistant.Kind
import com.mtwabpin.kolahal.assistant.Problem
import com.mtwabpin.kolahal.assistant.Prompts
import com.mtwabpin.kolahal.assistant.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What the next question goes with: the settings when it is asked. */
internal class AskWith(
    val service: Service,
    val model: String,
    val rich: Boolean,
    val instructions: String,
    val keep: Boolean,
)

/** One thing in the thread, in the order it came. */
internal sealed interface Item {
    data class Asked(val text: String) : Item

    /** A tap on an answer's button: [name] is its event's. */
    data class Tapped(val name: String) : Item
    data class Written(val text: String) : Item

    /** A surface, where it first appeared: it shows in its latest state. */
    data class Drawn(val surfaceId: String) : Item

    /** An answer that only changed surfaces shown above. */
    data object Changed : Item

    /** A question with no answer, or an answer that can't be drawn: [raw] is the answer as it came. */
    data class Failed(val message: String, val raw: String?, val problem: Problem?) : Item
}

/**
 * The page's state: the conversation, the thread the page shows, and the A2UI processor that holds
 * the surfaces. Nothing in an answer reaches the app: a button's event goes back to the model as the
 * next turn, local function calls are dropped, and pictures, video and sound are never loaded
 * ([answerCatalog]).
 */
internal class AssistantSession(
    context: Context,
    private val asker: Asker,
    private val store: ConversationStore,
) {
    val catalog = answerCatalog(context)
    private val schema by lazy { catalog.toJsonSchemaString() }
    private val parser = A2uiMessageParser()

    /** Made again for each conversation, and after the renderer fails ([restart]). */
    var processor by mutableStateOf(newProcessor())
        private set

    val items = mutableStateListOf<Item>()
    private val entries = mutableListOf<Entry>()
    private var id: String? = null
    private var started = 0L

    /** The id of the conversation shown, or null for a new one not kept yet. */
    val conversationId: String? get() = id

    /** The renderer's complaints since the last turn: they go to the model with the next one. */
    private val pendingErrors = mutableListOf<String>()

    /** Counts the conversations shown: the thread starts at its top for each one. */
    var shown by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    var busy by mutableStateOf(false)
        private set

    // Only events go on (to the model); a local function call from an action is dropped.
    private fun newProcessor() =
        A2uiMessageProcessor(listOf(catalog), listOf(A2uiActionInterceptor { it as? A2uiEventAction }))

    /** After the processor stopped on an error of its own: a new one, with this conversation's answers drawn again. */
    fun restart(message: String) {
        items += Item.Failed("The answer couldn't be drawn\n$message", null, null)
        processor = newProcessor()
        entries.filter { it.kind == Kind.RICH }.forEach { draw(it.text, replay = true) }
    }

    fun newConversation() {
        if (busy) return
        id = null
        shown++
        entries.clear()
        items.clear()
        pendingErrors.clear()
        processor = newProcessor()
    }

    /** Shows [c] and goes on with it. */
    fun open(c: Conversation) {
        newConversation()
        id = c.id
        started = c.started
        entries += c.entries
        for (e in c.entries) when (e.kind) {
            Kind.QUESTION -> items += Item.Asked(e.text)
            Kind.ACTION -> items += Item.Tapped(eventName(e.text))
            Kind.PLAIN -> items += Item.Written(e.text)
            Kind.RICH -> draw(e.text, replay = true)
        }
    }

    /** What the renderer said about an answer: shown, and sent with the next turn. */
    fun rendererError(json: String, message: String) {
        pendingErrors += json
        items += Item.Failed("The answer couldn't be drawn\n$message", null, null)
    }

    /** Asks [text]. In debug builds, a hand-written answer typed in (starting with `[`) is drawn as a model's would be. */
    fun ask(scope: CoroutineScope, with: AskWith, text: String) {
        if (busy || text.isBlank()) return
        if (BuildConfig.DEBUG && text.startsWith("[")) {
            draw(text, replay = false)
            return
        }
        items += Item.Asked(text)
        send(scope, with, Entry(Kind.QUESTION, text, pendingNotes()))
    }

    /** A tap on an answer's button: its action goes to the model as the next turn. */
    fun tapped(scope: CoroutineScope, with: AskWith, event: A2uiClientEventMessage) {
        if (busy) return
        items += Item.Tapped(event.type)
        send(scope, with, Entry(Kind.ACTION, actionJson(event), pendingNotes()))
    }

    private fun pendingNotes(): String? = pendingErrors.joinToString("\n").ifEmpty { null }.also { pendingErrors.clear() }

    private fun send(scope: CoroutineScope, with: AskWith, entry: Entry) {
        busy = true
        entries += entry
        val system = Prompts.system(with.rich, catalogId, if (with.rich) schema else "", with.instructions)
        val turns = entries.map { it.turn }
        scope.launch {
            try {
                val answer = withContext(Dispatchers.IO) { asker.ask(with.service, with.model, system, turns, with.rich) }
                val kind = if (with.rich) Kind.RICH else Kind.PLAIN
                entries += Entry(kind, answer)
                if (with.rich) draw(answer, replay = false) else items += Item.Written(answer.trim())
                if (with.keep) keep(with)
            } catch (e: AssistantException) {
                entries.remove(entry)
                items += Item.Failed(e.message.orEmpty(), null, e.problem)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                entries.remove(entry)
                items += Item.Failed(e.message ?: e.javaClass.simpleName, null, Problem.OTHER)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun keep(with: AskWith) {
        val now = System.currentTimeMillis()
        val key = id ?: now.toString().also { id = it; started = now }
        val c = Conversation(key, started, now, with.service.id, with.model, entries.toList())
        withContext(Dispatchers.IO) { runCatching { store.save(c) } }
    }

    /**
     * Draws [answer]'s A2UI messages, all or none. A surface made again is made new in its place in the
     * thread; an answer that only changes surfaces says so. What can't be read is shown with the answer
     * as it came, and (for a new answer) goes to the model with the next turn.
     */
    private fun draw(answer: String, replay: Boolean) {
        val messages: List<A2uiServerToClientMessage> = try {
            Prompts.messages(answer).map { parser.parse(it) }
        } catch (e: Exception) {
            if (!replay) pendingErrors += JSONObject().put("error", JSONObject().put("message", e.message.orEmpty())).toString()
            items += Item.Failed("The answer isn't one the app can draw\n${e.message.orEmpty()}", answer, null)
            return
        }
        val shown = items.filterIsInstance<Item.Drawn>().map { it.surfaceId }.toSet()
        var added = false
        val active = processor.activeSurfaces.value.map { it.id }.toMutableSet()
        try {
            for (m in messages) {
                if (m is A2uiCreateSurfaceMessage) {
                    if (!active.add(m.surfaceId)) processor.processMessage(A2uiDeleteSurfaceMessage(m.surfaceId))
                    if (m.surfaceId !in shown) {
                        items += Item.Drawn(m.surfaceId)
                        added = true
                    }
                }
                processor.processMessage(m)
            }
        } catch (e: Exception) {
            items += Item.Failed("The answer isn't one the app can draw\n${e.message.orEmpty()}", answer, null)
            return
        }
        // A question answered by changing what is above says so; a tap's change shows where it is.
        if (!added && messages.isNotEmpty() && items.lastOrNull() is Item.Asked) items += Item.Changed
    }

    companion object {
        val catalogId: String get() = androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1.CatalogId

        /** The protocol's client-to-server `action` message, with the data model as its metadata. */
        fun actionJson(e: A2uiClientEventMessage): String {
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

        /** The event's name in an action's JSON. */
        fun eventName(json: String): String =
            runCatching { JSONObject(json).getJSONObject("action").getString("name") }.getOrDefault("")
    }
}
