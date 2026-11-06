package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository.Pending
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shell-style filters over a command's output: `help | grep app`, `history | tail 5`,
 * `feed read | grep -v sponsored | head 3`, `contacts | count`.
 *
 * Only a `|` followed by a known filter starts a stage, so `lua a or b`, `js x || y` and `=` maths
 * are never split. One with no space before it belongs to a stage that keeps its words as typed
 * ([keepsItsWords]): `grep head|tail` is one pattern. Error lines always pass through; output a
 * command prints later (network replies, scripts) isn't captured.
 */
object Pipes {
    val FILTERS = listOf("grep", "head", "tail", "count", "wc", "sort", "uniq")

    private val SPLIT = Regex("""(?<!\|)(\s*)\|\s*(?=(?:${FILTERS.joinToString("|")})\b)""")

    /** A regex, code, or words kept or sent as they are. */
    private val KEEP_WORDS = setOf("grep", "lua", "js", "alias", "config", "echo", "todo", "note", "remind", "timer", "alarm", "sms", "url", "rename")

    private fun keepsItsWords(stage: String): Boolean {
        val t = stage.trimStart()
        return t.startsWith("=") || InstantRun.canonical(t.substringBefore(' ')) in KEEP_WORDS || t.count { it == '"' } % 2 == 1 || t.count { it == '\'' } % 2 == 1
    }

    fun split(line: String): List<String> {
        val stages = ArrayList<String>()
        var start = 0
        for (pipe in SPLIT.findAll(line)) {
            val stage = line.substring(start, pipe.range.first)
            if (pipe.groupValues[1].isEmpty() && keepsItsWords(stage)) continue
            stages += stage
            start = pipe.range.last + 1
        }
        stages += line.substring(start)
        return stages.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** [line] with a space around each filter's `|`, so its first word is the command: checks and aliases read it that way. */
    fun spaced(line: String): String = split(line).joinToString(" | ")

    fun apply(lines: List<Pending>, stages: List<String>): Result<List<Pending>> = runCatching {
        var out = lines
        for (stage in stages) out = applyStage(out, stage)
        out
    }

    private fun applyStage(lines: List<Pending>, stage: String): List<Pending> {
        val parts = stage.split(Regex("\\s+"))
        val name = parts[0].lowercase()
        val args = parts.drop(1)
        // Errors and the command echo are not data: they pass every stage untouched.
        val (keep, data) = lines.partition { it.kind == LogKind.ERROR || it.kind == LogKind.COMMAND }
        val result = when (name) {
            "grep" -> {
                val invert = "-v" in args
                val pattern = args.filterNot { it == "-v" || it == "-i" }.joinToString(" ")
                require(pattern.isNotEmpty()) { "Usage: … | grep [-v] <pattern>" }
                val regex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }
                    .getOrElse { Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE) }
                GREP_LIMIT.run { data.filter { regex.containsMatchIn(searchable(it)) != invert } }
            }
            "head" -> data.take(count(args))
            "tail" -> data.takeLast(count(args))
            "sort" -> if ("-r" in args) data.sortedByDescending { it.text.lowercase() } else data.sortedBy { it.text.lowercase() }
            "uniq" -> data.distinctBy { it.text }
            "count", "wc" -> listOf(data.firstOrNull()?.copy(kind = LogKind.OUTPUT, text = "${data.size}", ctx = LogContext.EMPTY)
                ?: Pending(LogKind.OUTPUT, "0", null, LogContext.EMPTY, System.currentTimeMillis()))
            else -> throw IllegalArgumentException("Unknown filter '$name' (${FILTERS.joinToString()})")
        }
        return keep + result
    }

    /** Text plus the right-hand description of two-column rows (help, config, script ls). */
    private fun searchable(p: Pending): String = p.ctx.values[LogContext.DESC]?.let { "${p.text} $it" } ?: p.text

    /** `head 5`, `head -5`, `head -n5` and `head -n 5`. */
    private fun count(args: List<String>): Int {
        val raw = if (args.firstOrNull() == "-n") args.getOrNull(1) else args.firstOrNull()?.removePrefix("-")?.removePrefix("n")
        return raw?.toIntOrNull()?.coerceIn(0, 10_000) ?: 10
    }
}

private val GREP_LIMIT = MatchLimit(ms = 1_000, maxThreads = 2)

/**
 * Runs a match on a thread of its own and waits [ms] for it, so a pattern that backtracks without
 * end (`(.*.*)*x`) can't hold up the commands after it. Android's regex engine is native and can't
 * be stopped: such a match runs on, at the lowest priority, until it ends or Kolahal restarts, and
 * at most [maxThreads] matches run at once.
 */
internal class MatchLimit(private val ms: Long, private val maxThreads: Int) {
    private val running = AtomicInteger()

    fun <T> run(match: () -> T): T {
        if (running.incrementAndGet() > maxThreads) {
            running.decrementAndGet()
            throw IllegalArgumentException("grep: earlier patterns are still running; restart stops them")
        }
        // Given back inside the task, before its answer is: a caller that has its answer can start
        // the next match at once. Given back after, the place could still look taken by then.
        val task = FutureTask {
            try {
                match()
            } finally {
                running.decrementAndGet()
            }
        }
        val thread = Thread(task).apply { isDaemon = true; start() }
        return try {
            task.get(ms, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            thread.priority = Thread.MIN_PRIORITY
            throw IllegalArgumentException("grep: pattern too slow, try a simpler one; restart stops it")
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}
