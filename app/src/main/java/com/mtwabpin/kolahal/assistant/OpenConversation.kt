package com.mtwabpin.kolahal.assistant

/**
 * The conversation the Assistant's page shows, as it is kept: its id (null until it is first kept), its
 * entries, and the question waiting for its answer. No Compose here: the page's session holds one and
 * draws what it says.
 * - While a question waits, no other conversation is started or opened: its answer would land in the
 *   other one and be kept under the other's id.
 * - Deleting this conversation starts a new one at once, waiting or not: the waiting answer is dropped
 *   ([Ticket] no longer current), so nothing brings the deleted conversation back.
 */
class OpenConversation {
    /** The id it is kept under, or null for one not kept yet. */
    var id: String? = null
        private set
    private var started = 0L
    private val list = mutableListOf<Entry>()
    val entries: List<Entry> get() = list

    /** The question waiting for its answer, or null. */
    var waiting: Entry? = null
        private set
    val busy: Boolean get() = waiting != null

    /** Counts the conversations shown: a ticket from an earlier one is no longer current. */
    private var number = 0

    /** What the renderer said since the last turn: it goes to the model with the next one. */
    private val notes = mutableListOf<String>()

    /** How many times the renderer was started again since the last answer ([redraw]). */
    private var redraws = 0

    /** A question that went: its answer is taken only while it is current. */
    class Ticket internal constructor(internal val number: Int, internal val question: Entry)

    /** Starts a new conversation. False while a question waits: nothing changes. */
    fun clear(): Boolean {
        if (busy) return false
        reset()
        return true
    }

    /** Goes on with [c]. False while a question waits: nothing changes. */
    fun open(c: Conversation): Boolean {
        if (busy) return false
        reset()
        id = c.id
        started = c.started
        list += c.entries
        return true
    }

    /** [deleted] is gone from the store: when it is this one, a new one starts and a waiting answer is dropped. True when it was. */
    fun deleted(deleted: String): Boolean {
        if (deleted != id) return false
        reset()
        return true
    }

    /** Every conversation is gone from the store: one that was kept starts again as new. True when it was kept. */
    fun deletedAll(): Boolean {
        if (id == null) return false
        reset()
        return true
    }

    private fun reset() {
        id = null
        started = 0L
        list.clear()
        waiting = null
        notes.clear()
        number++
        redraws = 0
    }

    /** [question] goes: it is part of the conversation until it fails. Null while another waits. */
    fun ask(question: Entry): Ticket? {
        if (busy) return null
        waiting = question
        list += question
        return Ticket(number, question)
    }

    /** The turns that go with the question asked. */
    fun turns(): List<Turn> = list.map { it.turn }

    /** Whether [t] is still the question this conversation waits on. */
    fun isCurrent(t: Ticket): Boolean = t.number == number && waiting === t.question

    /** [answer] came for [t]: added. False when the conversation it was for is no longer shown. */
    fun answered(t: Ticket, answer: Entry): Boolean {
        if (!isCurrent(t)) return false
        list += answer
        redraws = 0
        return true
    }

    /** [t] got no answer: its question isn't part of the conversation. False when it is no longer current. */
    fun failed(t: Ticket): Boolean {
        if (!isCurrent(t)) return false
        list.remove(t.question)
        return true
    }

    /** [t] is done, answered or not: another question may go. */
    fun finished(t: Ticket) {
        if (isCurrent(t)) waiting = null
    }

    /** The conversation to save now, at [now]: the first time, it gets its id. */
    fun kept(service: String, model: String, now: Long): Conversation {
        val key = id ?: now.toString().also { id = it; started = now }
        return Conversation(key, started, now, service, model, list.toList())
    }

    /** Something the renderer said, for the next turn. */
    fun note(json: String) {
        notes += json
    }

    /** The notes for the next turn, taken: null when there are none. */
    fun takeNotes(): String? = notes.joinToString("\n").ifEmpty { null }.also { notes.clear() }

    /**
     * The rich answers to draw again after the renderer stopped on one. The newest is the likeliest to
     * have stopped it: the first time all but the newest, then all but the two newest, and so on, and
     * after [MAX_REDRAWS] times none, so it can't stop again and again. Counted again after each answer.
     */
    fun redraw(): List<Entry> {
        val rich = list.filter { it.kind == Kind.RICH }
        redraws++
        return if (redraws > MAX_REDRAWS) emptyList() else rich.dropLast(redraws)
    }

    companion object {
        const val MAX_REDRAWS = 3
    }
}
