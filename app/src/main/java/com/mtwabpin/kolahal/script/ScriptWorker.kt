package com.mtwabpin.kolahal.script

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs one script (by id) or an inline REPL snippet. Executes inside the `:script` process via
 * WorkManager's RemoteWorkerService, isolating native engine crashes from the launcher UI.
 */
class ScriptWorker(context: Context, params: WorkerParameters) : RemoteCoroutineWorker(context, params) {

    override suspend fun doRemoteWork(): Result = if (ScriptRun.run(applicationContext, inputData)) Result.success() else Result.failure()

    companion object {
        const val KEY_SCRIPT_ID = "script_id"
        const val KEY_LANG = "lang"
        const val KEY_CODE = "code"
        const val KEY_SCHEDULED = "scheduled"
        const val KEY_ARGS = "args"
        const val KEY_NAME = "name"
        const val KEY_INTERACTIVE = "interactive"
        /** Started by a command another script sent with launcher.run. */
        const val KEY_BY_SCRIPT = "by_script"
        /** JSON object for the script's `event` global (event and daily-time runs only). */
        const val KEY_EVENT = "event"
        /** The typed command's line that started this run ([LogContext.FROM]). */
        const val KEY_FROM = "from"
        const val TIMEOUT_MS = 60_000L
    }
}

/**
 * One script run, as [ScriptWorker] (the queue) or [ScriptRunService] (a daily time, in the
 * foreground) starts it, in the `:script` process.
 */
internal object ScriptRun {
    /**
     * One run of a script at a time, whichever way it started (its timer, a daily time, an event,
     * the prompt, a pinned line's refresh): two at once would share its store, and the later one to
     * end would write rss.fresh's seen items over the other's, so items came back as new. Every run
     * is in the :script process, so a lock here holds them all; the next one waits its turn.
     */
    private val running = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.sync.Mutex>()

    /** Runs what [inputData] names ([ScriptWorker]'s keys); false when it names nothing runnable. */
    suspend fun run(ctx: Context, inputData: Data): Boolean {
        val scriptId = inputData.getLong(ScriptWorker.KEY_SCRIPT_ID, -1L)
        if (scriptId < 0) return runOne(ctx, inputData, scriptId)
        return running.getOrPut(scriptId) { kotlinx.coroutines.sync.Mutex() }.withLock { runOne(ctx, inputData, scriptId) }
    }

    private suspend fun runOne(ctx: Context, inputData: Data, scriptId: Long): Boolean {
        val logs = LogRepository.get(ctx)
        val scripts = ScriptRepository.get(ctx)

        val script = if (scriptId >= 0) scripts.byId(scriptId) ?: return true else null
        if (script != null && !script.enabled && inputData.getBoolean(ScriptWorker.KEY_SCHEDULED, false)) {
            return true
        }
        val lang = script?.lang ?: ScriptLang.of(inputData.getString(ScriptWorker.KEY_LANG).orEmpty())
            ?: return false
        val code = script?.code ?: inputData.getString(ScriptWorker.KEY_CODE) ?: return false
        val name = script?.name ?: inputData.getString(ScriptWorker.KEY_NAME) ?: lang.ext
        val repl = script == null
        val args = inputData.getNullableStringArray(ScriptWorker.KEY_ARGS)?.filterNotNull().orEmpty()
        val event = inputData.getString(ScriptWorker.KEY_EVENT)?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() }
        val from = inputData.getLong(ScriptWorker.KEY_FROM, -1L).takeIf { it >= 0 }
        // Queued before a switch went off: the event (or, for a notification, reading notifications)
        // is checked again as the run starts.
        val on = event?.get("name") as? String
        // A sample event run from the editor is asked for: it runs.
        if (script != null && on != null && on in ScriptMeta.EVENTS && !inputData.getBoolean(ScriptWorker.KEY_INTERACTIVE, false) &&
            (!script.grants.allows(on) || (on == "notification" && !script.grants.allows("notifications")))
        ) return true
        // A pinned status line's refresh, every few seconds while home shows it: what stays the same isn't said
        // again. An error shows once until a run works, and a result a run returns isn't logged.
        val refresh = script != null && on == ScriptScheduler.REFRESH && !inputData.getBoolean(ScriptWorker.KEY_INTERACTIVE, false)
        val refreshes = KvStore(ctx, "worker")
        val refreshKey = "refresh-error:${script?.id}"
        val lastError = if (refresh) refreshes.get(refreshKey) else null
        var failed: String? = null
        fun fail(text: String, context: LogContext) {
            failed = text
            if (text != lastError) logs.add(LogKind.ERROR, text, name, context)
        }

        val host = HostApi(
            ctx, name, script?.id,
            // Its switch shows only when its code calls notify: one that hides the call can't post.
            notifyEnabled = script?.let { it.notify && ScriptMeta.postsNotifications(it.code) } ?: true,
            interactive = repl || inputData.getBoolean(ScriptWorker.KEY_INTERACTIVE, false),
            byScript = inputData.getBoolean(ScriptWorker.KEY_BY_SCRIPT, false),
            from = from,
            grants = script?.grants,
            secrets = script?.let { s -> ScriptMeta.secrets(s.code).let { d -> HostApi.Secrets(d, SecretStore(ctx).usable(s.name, d)) } },
            options = script?.let { s -> ScriptOptions(ctx).values(s.name, ScriptMeta.options(s.code)) }.orEmpty(),
        )
        val engine = ScriptEngine.forLang(lang)
        val started = System.currentTimeMillis()
        val errorCtx = LogContext(
            (if (script != null) mapOf(LogContext.SCRIPT to script.name) else emptyMap()) +
                (from?.let { mapOf(LogContext.FROM to it.toString()) } ?: emptyMap())
        )
        // Ended, however it ends: whatever is still in flight (a request, a timer) changes nothing after it,
        // also when Android stops the run and this leaves at once.
        val status = try {
            val result = withTimeout((ScriptWorker.TIMEOUT_MS + 2_000).milliseconds) {
                engine.run(code, name, host, ScriptWorker.TIMEOUT_MS, repl, args, event)
            }
            host.flush()
            if (result != null && !refresh) {
                val shown = host.redact(result)
                logs.add(LogKind.OUTPUT, shown, name, LogContext.of(LogContext.COPY to shown) + errorCtx.values)
            } else if (repl && host.effects == 0) {
                logs.add(LogKind.OUTPUT, "nil", name, errorCtx)
            }
            host.commit()
            "ok in ${System.currentTimeMillis() - started}ms"
        } catch (e: CancellationException) {
            host.flush()
            // Only our own timeout is a script failure; WorkManager stopping the worker must propagate.
            if (e !is TimeoutCancellationException) throw e
            fail("$name: timed out after ${ScriptWorker.TIMEOUT_MS / 1000}s", errorCtx)
            "timeout"
        } catch (e: Throwable) {
            host.flush()
            // Stopped by the switch itself, not an error the script caught and went on after.
            val off = host.denied?.takeIf { host.deniedMessage?.let { m -> e.message.orEmpty().contains(m) } == true }
            // QuickJS's interrupt, or Lua's own timeout error (raised by the hook or a host call that waits).
            val timedOut = e is com.dokar.quickjs.QuickJsInterruptedException || e.message.orEmpty().contains("script timed out")
            if (timedOut) {
                fail("$name: timed out after ${ScriptWorker.TIMEOUT_MS / 1000}s", errorCtx)
                "timeout"
            } else if (off != null) {
                // Stopped by a switch the user turned off: say which, with a tap to allow it.
                fail("$name: ${ScriptAccess.label(off).lowercase()} is off for this script — tap to allow", errorCtx + mapOf(LogContext.RUN to "script allow $name $off"))
                "stopped: ${ScriptAccess.label(off).lowercase()} off"
            } else {
                // An error can quote a URL or a header: masked like everything else a run shows.
                val message = e.message?.let(host::redact)
                // The first line, as the script made it: as long as it liked, so cut for the line and the status.
                val msg = (message?.lines()?.firstOrNull { it.isNotBlank() } ?: e.javaClass.simpleName).take(ScriptRepository.STATUS_MAX)
                val line = if (repl) null else errorLine(e, lang, name)
                val extra = buildMap {
                    put(LogContext.COPY, message ?: msg)
                    line?.let { put(LogContext.LINE, it.toString()) }
                }
                fail("$name: $msg" + (line?.let { " (tap to edit line $it)" } ?: ""), errorCtx + extra)
                "error: $msg" + (line?.let { " (line $it)" } ?: "")
            }
        } finally {
            host.finish()
        }
        // A run you started that asked for what nobody allowed yet got empty answers: offer to allow it.
        if (script != null && host.undecided.isNotEmpty() && inputData.getBoolean(ScriptWorker.KEY_INTERACTIVE, false)) {
            ScriptAccess.askToAllow(ctx, name, host.undecided.toList(), "needs permission for")
        }
        if (refresh && failed != lastError) refreshes.set(refreshKey, failed)
        script?.let {
            // A refresh that went as the last one did leaves the script's row be: one each few seconds
            // would have home read every script again.
            if (refresh && failed == lastError) return@let
            scripts.recordRun(it.id, status)
            // The run's lines went out before its result was stored; without this, home could show the previous run.
            logs.touch()
        }
        if (host.notifyBlocked) reportBlocked(ctx, logs, name, "notify-blocked", "notifications are blocked", "notifications")
        logs.trim()
        return true
    }

    /** Source line of a script error: Lua messages carry `name:LINE:`, QuickJS exceptions a line number. */
    private fun errorLine(e: Throwable, lang: ScriptLang, name: String): Int? = when (lang) {
        ScriptLang.LUA -> Regex("(?:^|\\s)${Regex.escape(name)}:(\\d+):").find(e.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        // Saved JS scripts are wrapped in one extra line (the async function header).
        ScriptLang.JS -> (e as? com.dokar.quickjs.QuickJsException)?.lineNumber?.minus(1)?.takeIf { it > 0 }
    }

    /** One "tap to allow" line per day ([key] tells kinds apart) instead of one per blocked call. */
    private fun reportBlocked(context: Context, logs: LogRepository, name: String, key: String, what: String, fix: String) {
        val store = KvStore(context, "worker")
        val today = java.time.LocalDate.now().toString()
        if (store.get(key) == today) return
        store.set(key, today)
        logs.add(LogKind.ERROR, "$name: $what — tap to allow", name, LogContext.of(LogContext.RUN to fix))
    }
}
