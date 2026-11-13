package com.mtwabpin.kolahal

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.terminal.CommandProcessor
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalUi
import org.junit.Assert.assertTrue

/*
 * What many device tests need, written once: waiting for something to happen, the shell, a
 * terminal with no screen, and command lines run as typed. The rules are in TestRules.kt.
 */

/**
 * Looks at [check] every 50 ms until it holds or [ms] have passed, then once more: a slow moment
 * at the deadline doesn't fail what holds a moment later. Whether it held.
 */
fun eventually(ms: Long = 10_000, check: () -> Boolean): Boolean {
    val until = SystemClock.uptimeMillis() + ms
    while (SystemClock.uptimeMillis() < until) {
        if (check()) return true
        Thread.sleep(50)
    }
    return check()
}

/** Waits up to [ms] for [check] to hold ([eventually]); fails with [what] if it doesn't. */
fun waitUntil(what: String, ms: Long = 10_000, check: () -> Boolean) = assertTrue(what, eventually(ms, check))

/** Waits up to [ms] for [find] to find something, and returns it; fails with [what] if it doesn't. */
fun <T : Any> waitForValue(what: String, ms: Long = 10_000, find: () -> T?): T {
    var found: T? = null
    waitUntil(what, ms) { find().also { found = it } != null }
    return found!!
}

/**
 * Compose's [ComposeTestRule.waitUntil], which gives up at the deadline without looking again:
 * here [check] is looked at once more first, so one slow frame doesn't fail it. Fails with [what].
 */
fun ComposeTestRule.waitFor(what: String, ms: Long = 5_000, check: () -> Boolean) {
    try {
        waitUntil(ms) { check() }
    } catch (e: ComposeTimeoutException) {
        if (!check()) throw AssertionError(what, e)
    }
}

/** Waits up to [ms] for a node [matcher] matches ([waitFor]). */
fun ComposeTestRule.waitForNode(matcher: SemanticsMatcher, ms: Long = 5_000) =
    waitFor("nothing shown is ${matcher.description}", ms) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

/** Waits up to [ms] for [text] on screen, or text holding it with [substring] ([waitFor]). */
fun ComposeTestRule.waitForText(text: String, substring: Boolean = false, ms: Long = 5_000) =
    waitFor("“$text” isn't shown", ms) { onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }

/**
 * Runs [command] as the shell user and returns what it printed; with [errors], what it printed to
 * stderr after. No shell runs it: arguments are split at spaces, and quotes are kept as they are.
 */
fun shell(command: String, errors: Boolean = false): String {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    if (!errors) return ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().decodeToString() }
    val (out, input, err) = automation.executeShellCommandRwe(command)
    input.close()
    val text = ParcelFileDescriptor.AutoCloseInputStream(out).use { it.readBytes().decodeToString() }
    return text + ParcelFileDescriptor.AutoCloseInputStream(err).use { it.readBytes().decodeToString() }
}

/** A terminal with no screen: what commands ask of it is ignored. Override what a test wants to see. */
open class TerminalUiStub : TerminalUi {
    override fun requestContactsPermission() = Unit
    override fun openScreen(screen: Screen) = Unit
    override fun pickScriptFolder() = Unit
    override fun requestFontImport() = Unit
    override fun openEditor(script: Script) = Unit
    override fun askSecret(script: Script, name: String) = Unit
    override fun requestNotificationPermission() = Unit
    override fun clearScreen() = Unit
    override fun pickContact(then: (com.mtwabpin.kolahal.contacts.Contact) -> Unit) = Unit
    override fun freshScreen() = Unit
    override fun requestCalendarPermission() = Unit
    override fun requestCallLogPermission() = Unit
    override fun askNotificationAccess() = Unit
    override fun filterLog(filter: com.mtwabpin.kolahal.commands.LogFilter?) = Unit
    override fun logWidthPx(): Int? = null
    override fun askBackupFolder(thenBackUp: Boolean) = Unit
}

/** Runs command lines as typed at the prompt, through a [CommandProcessor] on [ui], and reads back what each printed. */
class CommandRunner(context: Context, ui: TerminalUi = TerminalUiStub()) {
    val processor = CommandProcessor(context, ui)
    private val logs = LogRepository.get(context)

    /** The lines [line] printed, its own command line left out. */
    fun lines(line: String): List<LogEntry> {
        val from = logs.maxId()
        processor.run(line)
        return logs.recent(400, afterId = from).filter { it.kind != LogKind.COMMAND }
    }

    /** The lines [line] printed, as text. */
    fun run(line: String): List<String> = lines(line).map { it.text }

    /** Only the lines of [line]'s own run (its command line's id in [LogContext.FROM]): not one another run printed meanwhile. */
    fun ownLines(line: String): List<LogEntry> {
        val from = logs.maxId()
        processor.run(line)
        val command = commandId(line)
        return logs.recent(400, afterId = from).filter { it.kind != LogKind.COMMAND && it.ctx.values[LogContext.FROM] == command }
    }

    /** The id of the newest command line [line]. */
    fun commandId(line: String) = logs.recent(400).last { it.kind == LogKind.COMMAND && it.text == line }.id.toString()
}

/** Rows of a table as stored, every column, to [putBack] as they were, ids and all. */
class TableRows private constructor(private val db: SQLiteDatabase, private val table: String, val rows: List<ContentValues>) {
    /** Puts the rows back; one whose id is taken again meanwhile stays as it is now. */
    fun putBack() = rows.forEach { db.insertWithOnConflict(table, null, it, SQLiteDatabase.CONFLICT_IGNORE) }

    /** The largest value of [column] among the rows, 0 for none. */
    fun maxOf(column: String): Long = rows.maxOfOrNull { it.getAsLong(column) ?: 0L } ?: 0L

    companion object {
        /** The rows of [table] that [where] (with [args]) takes, all of them without one. */
        fun take(db: SQLiteDatabase, table: String, where: String? = null, vararg args: String): TableRows {
            val rows = db.query(table, null, where, args.ifEmpty { null }, null, null, null).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(ContentValues().apply {
                            for (i in 0 until c.columnCount) {
                                val name = c.getColumnName(i)
                                when (c.getType(i)) {
                                    Cursor.FIELD_TYPE_INTEGER -> put(name, c.getLong(i))
                                    Cursor.FIELD_TYPE_FLOAT -> put(name, c.getDouble(i))
                                    Cursor.FIELD_TYPE_BLOB -> put(name, c.getBlob(i))
                                    Cursor.FIELD_TYPE_NULL -> putNull(name)
                                    else -> put(name, c.getString(i))
                                }
                            }
                        })
                    }
                }
            }
            return TableRows(db, table, rows)
        }
    }
}
