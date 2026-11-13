package com.mtwabpin.kolahal

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogMarks
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Db
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.notifications.KolahalNotificationListener
import org.junit.rules.ExternalResource
import java.io.File

/*
 * Rules that put back what a test changed, so a later class starts from the same phone. Each acts
 * after the class's own `@After`, also when the test or that fails half way.
 */

private val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

/** Every setting back to its default after the test. */
class ConfigReset : ExternalResource() {
    override fun after() {
        val config = Config.get(target)
        Keys.ALL.forEach(config::reset)
    }
}

/**
 * The log as it was before the test: the lines written from its start on are deleted, after the
 * class's `@After` (and so also when that fails half way), and a clear, a fresh screen or an
 * emptied ↑ recall the test did is undone. Lines already there stay, even those the test hid; a
 * test that deletes them too (a trim, a purge, an import) needs [LogKept] instead.
 */
class LogLinesSince : ExternalResource() {
    private val logs = LogRepository.get(target)
    // The marks that say what the screen and ↑ recall leave out.
    private val pointers = listOf(LogMarks.CLEARED_UP_TO, LogMarks.HIDDEN_UP_TO, LogMarks.PURGE_UP_TO, LogMarks.HISTORY_FROM)
    private var saved = emptyMap<String, String?>()

    /** The newest line before the test: every line after it is the test's. */
    var start = 0L
        private set

    override fun before() {
        start = logs.maxId()
        saved = pointers.associateWith { logs.marks.string(it) }
    }

    override fun after() {
        logs.marks.set(*saved.toList().toTypedArray())
        logs.recent(100_000, afterId = start).forEach { logs.delete(it.id) }
    }
}

/** What these preferences hold now, to [restore] after. */
private fun SharedPreferences.copy(): Map<String, Any?> = all.toMap()

/** These preferences as [saved] had them: what was added since goes. */
private fun SharedPreferences.restore(saved: Map<String, Any?>) = edit(commit = true) {
    clear()
    saved.forEach { (k, v) ->
        when (v) {
            is Boolean -> putBoolean(k, v)
            is Int -> putInt(k, v)
            is Long -> putLong(k, v)
            is Float -> putFloat(k, v)
            is String -> putString(k, v)
            is Set<*> -> putStringSet(k, v.map { it.toString() }.toSet())
        }
    }
}

/**
 * The phone's crash reports set aside for the test, which sees only its own, and put back after
 * it, with which were announced and how far Android's record of exits was read. The test's own
 * reports (each says [TEST_REPORT]) are deleted; a real one written meanwhile stays. Reports a run
 * that died left aside come back first.
 */
class CrashReportsAside : ExternalResource() {
    private val dir = File(target.filesDir, "crash")
    private val aside = File(target.filesDir, "crash-set-aside")
    private val prefs = target.getSharedPreferences("crash", Context.MODE_PRIVATE)
    private var saved = emptyMap<String, Any?>()

    override fun before() {
        putBack()
        saved = prefs.copy()
        dir.listFiles { f -> f.isFile }?.forEach { f ->
            aside.mkdirs()
            // Said in logcat, so a run can tell what wrote it.
            Log.w("CrashReportsAside", "set aside ${f.name}: ${f.readLines().take(8).joinToString(" | ")}")
            f.renameTo(File(aside, f.name))
        }
    }

    override fun after() {
        dir.listFiles { f -> f.isFile }?.filter { TEST_REPORT in it.readText() }?.forEach { it.delete() }
        putBack()
        prefs.restore(saved)
    }

    private fun putBack() {
        val files = aside.listFiles() ?: return
        dir.mkdirs()
        files.forEach { it.renameTo(File(dir, it.name)) }
        aside.delete()
    }

    companion object {
        /** In every report a test writes, and in none Kolahal does: what the test's own are known by. */
        const val TEST_REPORT = "What: crash in test"
    }
}

/**
 * Notification-listener access as each test needs it ([set]), and after the test as it was before,
 * whatever the test did or wherever it stopped.
 */
class NotificationAccess : ExternalResource() {
    private val component = ComponentName(target, KolahalNotificationListener::class.java)
    private var had = false

    /**
     * Whether access is on, as the notification service has it: the setting the access screen reads
     * is written after, so just after a change it can still say the old.
     */
    val on: Boolean
        get() = target.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)

    override fun before() {
        had = on
    }

    override fun after() = restore()

    /** Access as it was before the test: by itself after it, or sooner, where what comes after needs that. */
    fun restore() = set(had)

    /**
     * Access [on] or off, once Android says so; on a change, then a few seconds at most for the
     * listener to be bound as access says. It can stay bound a while after access goes, so one still
     * there isn't a failure: a test that needs it gone or bound waits for that itself.
     */
    fun set(on: Boolean) {
        if (this.on == on) return
        shell("cmd notification ${if (on) "allow" else "disallow"}_listener ${component.flattenToString()}")
        waitUntil("notification access ${if (on) "on" else "off"}", 15_000) { this.on == on }
        eventually(5_000) { (KolahalNotificationListener.connected != null) == on }
    }
}

/**
 * The whole log as it was before the test, put back after it: every line as stored, the id the
 * next one gets, and the log's marks (how far a clear, a fresh screen, ↑ recall and what was seen
 * reach). For tests that empty the log or replace it, as a backup's import does. With [eachTest]
 * false only a test that calls [keep] has it put back.
 */
class LogKept(private val eachTest: Boolean = true) : ExternalResource() {
    private val db get() = Db.get(target).writableDatabase
    private val marks get() = LogRepository.get(target).marks
    private var lines: TableRows? = null
    private var savedMarks = emptyMap<String, String>()

    /** Takes the copy [after] puts back. */
    fun keep() {
        lines = TableRows.take(db, "logs")
        savedMarks = marks.all()
    }

    override fun before() {
        if (eachTest) keep()
    }

    override fun after() {
        val kept = lines ?: return
        val db = db
        db.beginTransaction()
        try {
            // Ids are never reused: lines to come are numbered on from the newest there has been.
            val newest = maxOf(sequence(db), db.compileStatement("SELECT COALESCE(MAX(id), 0) FROM logs").simpleQueryForLong(), kept.maxOf("id"))
            db.delete("logs", null, null)
            kept.putBack()
            if (db.compileStatement("UPDATE sqlite_sequence SET seq = $newest WHERE name = 'logs'").executeUpdateDelete() == 0) {
                db.execSQL("INSERT INTO sqlite_sequence(name, seq) VALUES('logs', $newest)")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        marks.replaceAll(savedMarks)
        LogRepository.get(target).onExternalRestructure()
    }

    private fun sequence(db: SQLiteDatabase): Long =
        db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name = 'logs'", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
}
