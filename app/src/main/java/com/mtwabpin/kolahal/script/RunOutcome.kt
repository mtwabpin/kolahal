/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.ScriptRepository

/** What stopped a run before it ended by itself ([HostApi.stoppedBy]). It is recorded where it happens, never read from a message. */
sealed interface RunStop {
    /** The run's time was up: Lua's hook, a host call that waits, or QuickJS's interrupt. */
    data object Timeout : RunStop

    /**
     * [item] is switched off for the script, so a host call stopped the run with [message]. The
     * message tells this apart from a denial that the script caught before it went on.
     */
    data class Denied(val item: String, val message: String) : RunStop
}

/**
 * How a run that ended in an error is reported:
 * - [line]: the error line in the log;
 * - [extra]: what goes into that line's context, besides the script's own;
 * - [status]: the result the script's card keeps.
 * - [switchOff]: a switch that is off for the script stopped the run ([RunStop.Denied]).
 */
internal data class RunOutcome(
    val line: String,
    val status: String,
    val extra: Map<String, String> = emptyMap(),
    val switchOff: Boolean = false,
)

/**
 * Works out how the run of [name] that threw [error] ended.
 * - [stop] is what stopped it, if anything did ([HostApi.stoppedBy]). A script that calls
 *   `error("script timed out")` stopped nothing, so its run is an error like any other.
 * - A denial counts only when [error] is the one it threw. A script can catch the denial and go on,
 *   then fail for another reason.
 * - [redact] masks what the message may quote (a URL, a header). [errorLine] finds the script's
 *   line in [error]. A REPL run has no line to edit.
 * - A Lua error starts with where it happened (`hn:12: bad value`). That start is left out: the
 *   line says the name once, and the line number at its end (`hn: bad value (tap to edit line 12)`).
 */
internal fun runOutcome(
    name: String,
    error: Throwable,
    stop: RunStop?,
    repl: Boolean,
    redact: (String) -> String,
    errorLine: (Throwable) -> Int?,
    timeoutS: Long = ScriptWorker.TIMEOUT_MS / 1000,
): RunOutcome {
    if (stop == RunStop.Timeout) return RunOutcome("$name: timed out after ${timeoutS}s", "timeout")
    if (stop is RunStop.Denied && error.message.orEmpty().contains(stop.message)) {
        // A switch the user turned off stopped the run. Say which one, with a tap to allow it.
        val off = ScriptAccess.label(stop.item).lowercase()
        return RunOutcome(
            "$name: $off is off for this script — tap to allow",
            "stopped: $off off",
            mapOf(LogContext.RUN to "script allow $name ${stop.item}"),
            switchOff = true,
        )
    }
    // An error can quote a URL or a header, so mask it like everything else a run shows.
    val message = error.message?.let(redact)
    // The first line as the script made it, without Lua's `hn:12: ` before it. It can be any
    // length, so cut it for the line and the status.
    val first = message?.lines()?.firstOrNull { it.isNotBlank() }
    val msg = (first?.let { it.replace(position(name), "").ifBlank { it } }
        ?: error.javaClass.simpleName).take(ScriptRepository.STATUS_MAX)
    val line = if (repl) null else errorLine(error)
    val extra = buildMap {
        put(LogContext.COPY, message ?: msg)
        line?.let { put(LogContext.LINE, it.toString()) }
    }
    return RunOutcome(
        "$name: $msg" + (line?.let { " (tap to edit line $it)" } ?: ""),
        "error: $msg" + (line?.let { " (line $it)" } ?: ""),
        extra,
    )
}

/**
 * Where a Lua error of the script [name] happened, at the start of its message: `hn:12: `. A
 * script that throws an error again can have it twice (`hn:40: hn:12: `), so each is matched.
 */
private fun position(name: String) = Regex("""^\s*(?:${Regex.escape(name)}:\d+:\s*)+""")
