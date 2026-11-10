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
 * Rules:
 * - A new stage starts only at a `|` followed by a known filter. So `lua a or b`, `js x || y`
 *   and `=` maths are never split.
 * - A `|` with no space before it stays in the stage if that stage keeps its words as typed
 *   ([keepsItsWords]). So `grep head|tail` is one pattern.
 * - Error lines always pass through.
 * - Output that a command prints later (network replies, scripts) is not captured.
 */
object Pipes {
    val FILTERS = listOf("grep", "head", "tail", "count", "wc", "sort", "uniq")

    /** The filters that `help` names. `wc` is another name for `count`. */
    val SHOWN = listOf("grep", "head", "tail", "sort", "uniq", "count")

    private val SPLIT = Regex("""(?<!\|)(\s*)\|\s*(?=(?:${FILTERS.joinToString("|")})\b)""")

    /** Commands whose arguments are a regex, code, or words that are kept or sent exactly as typed. */
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

    /**
     * Returns [line] with a space around each filter's `|`. Then the first word is always the
     * command, which is how checks and aliases read the line.
     */
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
        // Errors and the echoed command are not data. They pass through every stage unchanged.
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

    /**
     * The text to search: the line's text, plus the right-hand description in two-column rows
     * (help, config, script ls), and the arguments under it in `help`.
     */
    private fun searchable(p: Pending): String =
        listOfNotNull(p.text, p.ctx.values[LogContext.DESC], p.ctx.values[LogContext.ARGS]).joinToString(" ")

    /** Reads the count from `head 5`, `head -5`, `head -n5` or `head -n 5`. The default is 10. */
    private fun count(args: List<String>): Int {
        val raw = if (args.firstOrNull() == "-n") args.getOrNull(1) else args.firstOrNull()?.removePrefix("-")?.removePrefix("n")
        return raw?.toIntOrNull()?.coerceIn(0, 10_000) ?: 10
    }
}

private val GREP_LIMIT = MatchLimit(ms = 1_000, maxThreads = 2)

/**
 * Runs a match on its own thread and waits up to [ms] for it. This way a pattern that backtracks
 * forever (`(.*.*)*x`) can't block the commands after it.
 *
 * Android's regex engine is native and can't be stopped. So a slow match keeps running, at the
 * lowest priority, until it ends or Kolahal restarts. At most [maxThreads] matches run at once.
 */
internal class MatchLimit(private val ms: Long, private val maxThreads: Int) {
    private val running = AtomicInteger()

    fun <T> run(match: () -> T): T {
        if (running.incrementAndGet() > maxThreads) {
            running.decrementAndGet()
            throw IllegalArgumentException("grep: earlier patterns are still running; restart stops them")
        }
        // Free the slot inside the task, before the result is returned. Then a caller that has its
        // result can start the next match at once. If we freed it later, the slot could still look taken.
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
