package com.mtwabpin.kolahal.productivity

import android.content.ContentValues
import android.content.Context
import com.mtwabpin.kolahal.data.Db
import com.mtwabpin.kolahal.data.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Todo(val id: Long, val text: String, val done: Boolean)
data class Note(val id: Long, val text: String, val created: Long)
/**
 * - [repeat]: a [Repeat] rule, or null for a reminder that fires once.
 * - [time]: the wall-clock time the user asked for a repeating reminder (`02:30`). It is null for a
 *   reminder set before 0.4.12, until that reminder next fires.
 */
data class Reminder(val id: Long, val at: Long, val text: String, val repeat: String? = null, val time: String? = null)

/**
 * Picks an item by what the user typed:
 * - `#12` is a database id. Tap actions use it, because it stays the same after edits.
 * - A plain `2` is the position in the list that `todo` / `notes` / `reminders` show.
 */
fun <T> pickRef(ref: String?, list: List<T>, id: (T) -> Long): T? {
    val r = ref?.trim() ?: return null
    if (r.startsWith("#")) return r.drop(1).toLongOrNull()?.let { want -> list.firstOrNull { id(it) == want } }
    return r.toIntOrNull()?.let { list.getOrNull(it - 1) }
}

/**
 * Counts the writes of one store, so that whoever shows its items knows when to read them again.
 * - It counts in this process only. The main process makes every write but one: a script's
 *   `todos.setDone` writes in the `:script` process. That write tells the main process with a
 *   message ([TodoStore.ACTION_CHANGED]), and the count rises there too ([TodoStore.changedElsewhere]).
 * - A write that goes past the store (a backup that is restored) does not count.
 */
private class Changes {
    private val count = MutableStateFlow(0)
    val flow: StateFlow<Int> = count.asStateFlow()

    /** Counts one write, and hands [result] back: what the write returned. */
    fun <T> after(result: T): T = result.also { count.update { it + 1 } }
}

/** To-do items: open items oldest first, then done items, most recently finished first. */
class TodoStore private constructor(context: Context) {
    private val db = Db.get(context)
    private val written = Changes()

    /**
     * Rises on every write: one made in this process, and one a script made in the `:script`
     * process ([changedElsewhere]). The Todos widget reads the list again when it does.
     */
    val changes: StateFlow<Int> get() = written.flow

    /**
     * A script changed a todo in the `:script` process ([ACTION_CHANGED]). The count of writes rises
     * here too, so home reads the list again. LauncherApp calls this in the main process.
     */
    fun changedElsewhere() {
        written.after(Unit)
    }

    fun all(): List<Todo> {
        val out = ArrayList<Todo>()
        db.readableDatabase.rawQuery(
            "SELECT id, text, done FROM todos ORDER BY done, CASE WHEN done = 0 THEN created ELSE -done_at END, id",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += Todo(c.getLong(0), c.getString(1), c.getInt(2) != 0)
            }
        }
        return out
    }

    fun open(): List<Todo> = all().filter { !it.done }

    fun add(text: String): Long = written.after(db.writableDatabase.insert("todos", null, ContentValues().apply {
        put("text", text)
        put("created", System.currentTimeMillis())
    }))

    fun setDone(id: Long, done: Boolean) {
        written.after(db.writableDatabase.update("todos", ContentValues().apply {
            put("done", if (done) 1 else 0)
            if (done) put("done_at", System.currentTimeMillis()) else putNull("done_at")
        }, "id = ?", arrayOf(id.toString())))
    }

    /**
     * Marks todo [id] done, or open again, only when it is not so already. Whether it changed:
     * false when no todo has the id, and when the todo is so already. A script's `todos.setDone`
     * uses it.
     * - It is one statement. When two callers ask at once, one of them gets true.
     * - A todo that is done already keeps the time it was done at. [setDone] would write a new
     *   time, which moves the todo to the top of the done ones.
     * - A call that changes nothing is not counted as a write.
     */
    fun mark(id: Long, done: Boolean): Boolean {
        val rows = db.writableDatabase.update("todos", ContentValues().apply {
            put("done", if (done) 1 else 0)
            if (done) put("done_at", System.currentTimeMillis()) else putNull("done_at")
        }, "id = ? AND done != ?", arrayOf(id.toString(), if (done) "1" else "0"))
        if (rows > 0) written.after(Unit)
        return rows > 0
    }

    fun edit(id: Long, text: String) {
        written.after(db.writableDatabase.update("todos", ContentValues().apply { put("text", text) }, "id = ?", arrayOf(id.toString())))
    }

    fun remove(id: Long): Int = written.after(db.writableDatabase.delete("todos", "id = ?", arrayOf(id.toString())))

    /** Deletes finished items and returns how many it deleted. */
    fun clearDone(): Int = written.after(db.writableDatabase.delete("todos", "done = 1", null))

    companion object : Singleton<TodoStore>(::TodoStore) {
        /**
         * Sent inside the app when a script changed a todo in the `:script` process (`todos.setDone`),
         * as [com.mtwabpin.kolahal.widgets.WidgetStates.ACTION_CHANGED] is for a widget. LauncherApp
         * receives it and calls [changedElsewhere]. It carries nothing: home reads the list again.
         */
        const val ACTION_CHANGED = "com.mtwabpin.kolahal.TODOS_CHANGED"
    }
}

/** Quick notes, newest first. */
class NoteStore private constructor(context: Context) {
    private val db = Db.get(context)
    private val written = Changes()

    /** Rises on every write, in this process. The Notes widget reads the notes again when it does. */
    val changes: StateFlow<Int> get() = written.flow

    fun all(): List<Note> {
        val out = ArrayList<Note>()
        db.readableDatabase.rawQuery("SELECT id, text, created FROM notes ORDER BY created DESC, id DESC", null).use { c ->
            while (c.moveToNext()) out += Note(c.getLong(0), c.getString(1), c.getLong(2))
        }
        return out
    }

    fun add(text: String): Long = written.after(db.writableDatabase.insert("notes", null, ContentValues().apply {
        put("text", text)
        put("created", System.currentTimeMillis())
    }))

    fun remove(id: Long): Int = written.after(db.writableDatabase.delete("notes", "id = ?", arrayOf(id.toString())))

    companion object : Singleton<NoteStore>(::NoteStore)
}

/**
 * Pending reminders, soonest first. A reminder is deleted when it is cancelled, or when it fires,
 * unless it repeats.
 */
class ReminderStore private constructor(context: Context) {
    private val db = Db.get(context)
    private val written = Changes()

    /**
     * Rises on every write, in this process: a reminder that is set, cancelled or snoozed, and one
     * that fires. The Reminders widget reads the list again when it does.
     */
    val changes: StateFlow<Int> get() = written.flow

    fun all(): List<Reminder> {
        val out = ArrayList<Reminder>()
        db.readableDatabase.rawQuery("SELECT id, at, text, repeat_rule, repeat_time FROM reminders ORDER BY at, id", null).use { c ->
            while (c.moveToNext()) out += Reminder(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4))
        }
        return out
    }

    fun byId(id: Long): Reminder? = all().firstOrNull { it.id == id }

    fun add(at: Long, text: String, repeat: String? = null, time: String? = null): Reminder {
        val id = written.after(db.writableDatabase.insertOrThrow("reminders", null, ContentValues().apply {
            put("at", at)
            put("text", text)
            put("created", System.currentTimeMillis())
            put("repeat_rule", repeat)
            put("repeat_time", time)
        }))
        return Reminder(id, at, text, repeat, time)
    }

    /** Sets a repeating reminder's next time. When [time] is given, also sets the time of day it repeats at. */
    fun setAt(id: Long, at: Long, time: String? = null) {
        val values = ContentValues().apply {
            put("at", at)
            if (time != null) put("repeat_time", time)
        }
        written.after(db.writableDatabase.update("reminders", values, "id = ?", arrayOf(id.toString())))
    }

    fun remove(id: Long): Int = written.after(db.writableDatabase.delete("reminders", "id = ?", arrayOf(id.toString())))

    companion object : Singleton<ReminderStore>(::ReminderStore)
}
