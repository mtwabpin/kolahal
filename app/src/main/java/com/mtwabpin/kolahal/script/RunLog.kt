package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository

/**
 * The lines one run logs, on their way to the log ([HostApi] keeps one for the run).
 * - Script lines are batched: one transaction and one UI refresh per ~300 ms, instead of one per line.
 * - A run has limits: [HostApi.MAX_LINES_PER_RUN] lines and [HostApi.MAX_CHARS_PER_RUN] characters.
 *   Past them, the rest are dropped with one note.
 * - A script can change a line it logged (`line.set`, [set]), while it waits here and after it is written.
 *
 * [add], [set] and [flush] share one lock, so a line can't be written while it is being changed.
 * There is no Android here: [write] and [change] reach the log, and a JVM test passes its own.
 */
internal class RunLog(
    /** The script's name: where the run's lines come from. */
    private val source: String,
    /** The context every line of the run carries: its script, and the command that started it. */
    private val baseCtx: Map<String, String>,
    /** Whether the run has ended ([HostApi.over]), read each time. After that nothing is logged or changed. */
    private val over: () -> Boolean,
    /** Writes lines to the log ([LogRepository.addAll]). Returns their ids, or null when they aren't written yet. */
    private val write: (List<LogRepository.Pending>) -> List<Long>?,
    /** Changes written lines in place, by id ([LogRepository.updateAll]). */
    private val change: (Map<Long, Pair<String, LogContext>>) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Lines waiting to be written. It is also the lock that [add], [set] and [flush] share. */
    private val pending = ArrayList<LogRepository.Pending>()
    private var lastFlush = now()

    /**
     * The lines this run has logged, and their characters as stored. Past [HostApi.MAX_LINES_PER_RUN] or
     * [HostApi.MAX_CHARS_PER_RUN], the rest are dropped with one note. Error lines count too.
     */
    private var lines = 0
    private var chars = 0
    private var full = false

    /**
     * A line this run logged, which the script can change with `line.set` ([set]): where it
     * waits in [pending] ([slot], -1 once written), its id once written, its length as stored, and
     * its text and context as last logged or set.
     */
    private class RunLine(var slot: Int, var length: Int, var text: String, var ctx: LogContext, var id: Long = 0)

    /** The lines this run logged with `log` or `md.log`, in order. A script's line handle is its number here, from 1. */
    private val runLines = ArrayList<RunLine>()
    /** The line in [runLines] that each entry of [pending] is, if any. */
    private val pendingLines = ArrayList<RunLine?>()
    /** Changes to written lines, waiting for the next flush. */
    private val changes = LinkedHashMap<RunLine, Pair<String, LogContext>>()
    private var sets = 0

    /** Returns the line's number for `line.set` (from 1) when it is a [LogKind.SCRIPT] line that will be logged, or 0. */
    fun add(kind: LogKind, text: String, ctx: LogContext): Int {
        synchronized(pending) {
            if (full || over()) return 0
            lines++
            val size = stored(text, ctx)
            chars += size
            if (lines > HostApi.MAX_LINES_PER_RUN || chars > HostApi.MAX_CHARS_PER_RUN) {
                stopLogging(if (lines > HostApi.MAX_LINES_PER_RUN) "${HostApi.MAX_LINES_PER_RUN} lines" else "${HostApi.MAX_CHARS_PER_RUN / 1024}K characters")
                return 0
            }
            pending += LogRepository.Pending(kind, text, source, ctx, now())
            val line = if (kind == LogKind.SCRIPT) RunLine(pending.size - 1, size, text, ctx).also { runLines += it } else null
            pendingLines += line
            val number = if (line != null) runLines.size else 0
            if (pending.size >= 50 || now() - lastFlush > 300) flushLocked()
            return number
        }
    }

    /** Past the run's limit of [what]: one line says so, and nothing more is logged or changed. Under the lock. */
    private fun stopLogging(what: String) {
        full = true
        pending += LogRepository.Pending(LogKind.INFO, "More than $what in one run: the rest aren't logged", source, LogContext(baseCtx), now())
        pendingLines += null
    }

    /** The characters a line takes as stored, as the run's limit counts them. */
    private fun stored(text: String, ctx: LogContext) =
        minOf(text.length, LogRepository.MAX_FIELD) + minOf(ctx.values.entries.sumOf { (k, v) -> k.length + v.length }, LogRepository.MAX_FIELD)

    /** Writes buffered lines, and the changes to lines already written. The worker calls this when the run ends (success or failure). */
    fun flush() = synchronized(pending) { flushLocked() }

    private fun flushLocked() {
        // Null when the lines aren't written yet (a pipe's capture): those lines can't be changed later.
        val ids = write(pending.toList())
        pendingLines.forEachIndexed { i, line ->
            line ?: return@forEachIndexed
            line.slot = -1
            line.id = ids?.getOrNull(i) ?: 0
        }
        pending.clear()
        pendingLines.clear()
        // The changes of this flush in one write, and one notice to home.
        change(changes.entries.filter { it.key.id > 0 }.associate { (line, change) -> line.id to change })
        changes.clear()
        lastFlush = now()
    }

    /**
     * `line.set` on line [number] of this run (what [add] returned): the line changes in place,
     * where it is in the log.
     * - [content] gives the new text and context. It runs once the line is found, and before the lock
     *   is taken: it checks links and secrets, and may stop the run.
     * - A run can change its lines [HostApi.MAX_SETS_PER_RUN] times. Later changes are ignored, as are
     *   changes after the run's limits were reached, or after the run ended.
     * - Changes are written together with new lines, at most about every 300 ms.
     */
    fun set(number: Int, content: () -> Pair<String, LogContext>) {
        val line = runLines.getOrNull(number - 1) ?: throw IllegalArgumentException("line.set: not a line this run logged")
        val (text, lineCtx) = content()
        synchronized(pending) {
            // The same text again changes nothing, and doesn't count.
            if (text == line.text && lineCtx == line.ctx) return
            if (full || over() || ++sets > HostApi.MAX_SETS_PER_RUN) return
            // Only what the line grew by counts toward the run's characters, and the run's limit holds here too.
            val size = stored(text, lineCtx)
            val grown = maxOf(0, size - line.length)
            if (chars + grown > HostApi.MAX_CHARS_PER_RUN) return stopLogging("${HostApi.MAX_CHARS_PER_RUN / 1024}K characters")
            chars += grown
            line.text = text
            line.ctx = lineCtx
            line.length = size
            when {
                line.slot >= 0 -> pending[line.slot] = pending[line.slot].copy(text = text, ctx = lineCtx)
                line.id > 0 -> changes[line] = text to lineCtx
            }
            if (now() - lastFlush > 300) flushLocked()
        }
    }
}
