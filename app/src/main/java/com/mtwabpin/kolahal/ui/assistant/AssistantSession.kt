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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.assistant.AssistantException
import com.mtwabpin.kolahal.assistant.Asker
import com.mtwabpin.kolahal.assistant.AssistantKeys
import com.mtwabpin.kolahal.assistant.Conversation
import com.mtwabpin.kolahal.assistant.ConversationStore
import com.mtwabpin.kolahal.assistant.Entry
import com.mtwabpin.kolahal.assistant.HttpAsker
import com.mtwabpin.kolahal.assistant.Kind
import com.mtwabpin.kolahal.assistant.KvShelf
import com.mtwabpin.kolahal.assistant.OpenConversation
import com.mtwabpin.kolahal.assistant.Problem
import com.mtwabpin.kolahal.assistant.Prompts
import com.mtwabpin.kolahal.assistant.Service
import com.mtwabpin.kolahal.assistant.SurfaceShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 *
 * It outlives the page ([AssistantSessions]): a question goes from the session's own scope, so its
 * answer still comes, and is kept, when the page is left while it waits. [close] stops it.
 */
internal class AssistantSession(
    context: Context,
    private val asker: Asker,
    private val store: ConversationStore,
) {
    val catalog = answerCatalog(context)
    private val schema by lazy { catalog.toJsonSchemaString() }
    private val parser = A2uiMessageParser()

    // On the main thread: an answer changes the thread, which the page reads there.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val talk = OpenConversation()
    private val shapes = SurfaceShapes()

    /** Made again for each conversation, after the renderer fails ([restart]), and when the page comes back ([reattach]). */
    var processor by mutableStateOf(newProcessor())
        private set

    val items = mutableStateListOf<Item>()

    /** The id of the conversation shown, or null for a new one not kept yet. */
    val conversationId: String? get() = talk.id

    /** Counts the conversations shown: the thread starts at its top for each one. */
    var shown by mutableIntStateOf(0)
        private set

    var busy by mutableStateOf(false)
        private set

    // Only events go on (to the model); a local function call from an action is dropped.
    private fun newProcessor() =
        A2uiMessageProcessor(listOf(catalog), listOf(A2uiActionInterceptor { it as? A2uiEventAction }))

    /** A new renderer, with no surfaces. */
    private fun freshRenderer() {
        processor = newProcessor()
        shapes.clear()
    }

    /**
     * After the processor stopped on an error of its own: a new one, with this conversation's answers
     * drawn again, but not the newest ones, which likely stopped it ([OpenConversation.redraw]).
     */
    fun restart(message: String) {
        items += Item.Failed("The answer couldn't be drawn\n$message", null, null)
        freshRenderer()
        talk.redraw().forEach { draw(it.text, replay = true, inThread = false) }
    }

    /** The page is shown again: a new renderer for it, with the answers drawn again where they are in the thread. */
    fun reattach() {
        freshRenderer()
        talk.entries.filter { it.kind == Kind.RICH }.forEach { draw(it.text, replay = true, inThread = false) }
    }

    /** Stops a waiting question for good: the session isn't used again. */
    fun close() {
        scope.cancel()
    }

    /** Starts a new conversation. Not while a question waits: its answer belongs to this one. */
    fun newConversation() {
        if (talk.clear()) cleared()
    }

    private fun cleared() {
        shown++
        items.clear()
        freshRenderer()
    }

    /** Shows [c] and goes on with it. False, and nothing changes, while a question waits. */
    fun open(c: Conversation): Boolean {
        if (!talk.open(c)) return false
        cleared()
        for (e in c.entries) when (e.kind) {
            Kind.QUESTION -> items += Item.Asked(e.text)
            Kind.ACTION -> items += Item.Tapped(eventName(e.text))
            Kind.PLAIN -> items += Item.Written(e.text)
            Kind.RICH -> draw(e.text, replay = true)
        }
        return true
    }

    /** Conversation [id] was deleted: when it is the one shown, a new one starts, and a waiting question is stopped, so it isn't kept again. */
    fun deleted(id: String) {
        if (talk.deleted(id)) stopped()
    }

    /** Every conversation was deleted (in Settings): the one shown, if it was kept, starts again as new. */
    fun deletedAll() {
        if (talk.deletedAll()) stopped()
    }

    private fun stopped() {
        job?.cancel()
        job = null
        busy = false
        cleared()
    }

    /** What the renderer said about an answer: shown, and sent with the next turn. */
    fun rendererError(json: String, message: String) {
        talk.note(json)
        items += Item.Failed("The answer couldn't be drawn\n$message", null, null)
    }

    /** Asks [text]. In debug builds, a hand-written answer typed in (starting with `[`) is drawn as a model's would be. */
    fun ask(with: AskWith, text: String) {
        if (busy || text.isBlank()) return
        if (BuildConfig.DEBUG && text.startsWith("[")) {
            draw(text, replay = false)
            return
        }
        items += Item.Asked(text)
        send(with, Entry(Kind.QUESTION, text, talk.takeNotes()))
    }

    /** A tap on an answer's button: its action goes to the model as the next turn. */
    fun tapped(with: AskWith, event: A2uiClientEventMessage) {
        if (busy) return
        items += Item.Tapped(event.type)
        send(with, Entry(Kind.ACTION, actionJson(event), talk.takeNotes()))
    }

    private fun send(with: AskWith, entry: Entry) {
        val ticket = talk.ask(entry) ?: return
        busy = true
        val system = Prompts.system(with.rich, catalogId, if (with.rich) schema else "", with.instructions)
        val turns = talk.turns()
        job = scope.launch {
            try {
                val answer = asker.send(with.service, with.model, system, turns, with.rich)
                // Deleted while it waited: the answer goes nowhere.
                if (!talk.answered(ticket, Entry(if (with.rich) Kind.RICH else Kind.PLAIN, answer))) return@launch
                if (with.rich) draw(answer, replay = false) else items += Item.Written(answer.trim())
                if (with.keep) keep(with, ticket)
            } catch (e: CancellationException) {
                talk.failed(ticket)
                throw e
            } catch (e: AssistantException) {
                if (talk.failed(ticket)) items += Item.Failed(e.message.orEmpty(), null, e.problem)
            } catch (e: Exception) {
                if (talk.failed(ticket)) items += Item.Failed(e.message ?: e.javaClass.simpleName, null, Problem.OTHER)
            } finally {
                talk.finished(ticket)
                busy = talk.busy
            }
        }
    }

    private suspend fun keep(with: AskWith, ticket: OpenConversation.Ticket) {
        val c = talk.kept(with.service.id, with.model, System.currentTimeMillis())
        val saved = withContext(Dispatchers.IO) { runCatching { store.save(c) } }
        // Deleted while it was being saved: deleted again, after the save.
        if (!talk.isCurrent(ticket)) {
            withContext(Dispatchers.IO) { runCatching { store.delete(c.id) } }
            return
        }
        // Back on the main thread: what couldn't be kept, or kept whole, is said here, where it still shows whole.
        saved.exceptionOrNull()?.let { items += Item.Failed("This conversation couldn't be kept\n${it.message ?: it.javaClass.simpleName}", null, null) }
        if ((saved.getOrNull() ?: 0) > 0) items += Item.Failed("Too large to keep in full\nThe history keeps a short note in its place", null, null)
    }

    /**
     * Draws [answer]'s A2UI messages, all or none. A surface made again is made new in its place in the
     * thread; an answer that only changes surfaces says so. What can't be read is shown with the answer
     * as it came, and (for a new answer) goes to the model with the next turn. Components that hold
     * each other in a circle, or nest too deep, are refused before the renderer sees them
     * ([SurfaceShapes]). [inThread] false: only the renderer gets it, for answers already in the thread.
     */
    private fun draw(answer: String, replay: Boolean, inThread: Boolean = true) {
        val messages: List<A2uiServerToClientMessage> = try {
            val texts = Prompts.messages(answer)
            texts.map { parser.parse(it) }.also { shapes.take(texts) }
        } catch (e: Exception) {
            refused(answer, replay, inThread, e.message.orEmpty())
            return
        } catch (e: StackOverflowError) {
            refused(answer, replay, inThread, "The components are nested too deep")
            return
        }
        val shown = items.filterIsInstance<Item.Drawn>().map { it.surfaceId }.toSet()
        var added = false
        val active = processor.activeSurfaces.value.map { it.id }.toMutableSet()
        try {
            for (m in messages) {
                if (m is A2uiCreateSurfaceMessage) {
                    if (!active.add(m.surfaceId)) processor.processMessage(A2uiDeleteSurfaceMessage(m.surfaceId))
                    if (inThread && m.surfaceId !in shown) {
                        items += Item.Drawn(m.surfaceId)
                        added = true
                    }
                }
                processor.processMessage(m)
            }
        } catch (e: Exception) {
            if (inThread) items += Item.Failed("The answer isn't one the app can draw\n${e.message.orEmpty()}", answer, null)
            return
        }
        // A question answered by changing what is above says so; a tap's change shows where it is.
        if (inThread && !added && messages.isNotEmpty() && items.lastOrNull() is Item.Asked) items += Item.Changed
    }

    private fun refused(answer: String, replay: Boolean, inThread: Boolean, message: String) {
        if (!replay) talk.note(JSONObject().put("error", JSONObject().put("message", message)).toString())
        if (inThread) items += Item.Failed("The answer isn't one the app can draw\n$message", answer, null)
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

/**
 * The page's session, kept while the app runs: leaving the page (for Settings, another screen, or home's
 * other page) and coming back finds the conversation as it was. One at a time, for the [Asker] it was
 * made with (null: the real one); another asker (a test's) gets a new one, and the old one is closed.
 * Main thread only.
 */
internal object AssistantSessions {
    private var kept: AssistantSession? = null
    private var keptFor: Asker? = null

    fun get(app: Context, asker: Asker?): AssistantSession {
        kept?.takeIf { keptFor === asker }?.let {
            it.reattach()
            return it
        }
        kept?.close()
        val session = AssistantSession(app, asker ?: HttpAsker(AssistantKeys(app)), ConversationStore(KvShelf(app)))
        kept = session
        keptFor = asker
        return session
    }

    /** Every conversation was deleted: the session's, if it was kept, isn't kept again. */
    fun deletedAll() {
        kept?.deletedAll()
    }
}
