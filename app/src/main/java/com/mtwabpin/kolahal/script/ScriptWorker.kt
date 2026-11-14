package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.Stores
import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs one script (by id) or an inline REPL snippet. It runs inside the `:script` process through
 * WorkManager's RemoteWorkerService, so a native engine crash can't take down the launcher UI.
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
        /** Set when another script started this run, by sending a command with launcher.run. */
        const val KEY_BY_SCRIPT = "by_script"
        /** A JSON object for the script's `event` global (only for event and daily-time runs). */
        const val KEY_EVENT = "event"
        /** The line of the typed command that started this run ([LogContext.FROM]). */
        const val KEY_FROM = "from"
        const val TIMEOUT_MS = 60_000L
    }
}

/**
 * One script run in the `:script` process. [ScriptWorker] (the queue) or [ScriptRunService] (a daily
 * time, in the foreground) starts it.
 */
internal object ScriptRun {
    /**
     * Only one run of a script at a time, however it started (its timer, a daily time, an event, the
     * prompt, a pinned line's refresh). Two runs at once would share its store. The one that ended
     * later would overwrite rss.fresh's seen items from the other, so items would come back as new.
     * Every run is in the :script process, so a lock here covers them all. The next run waits its turn.
     */
    private val running = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.sync.Mutex>()

    /** Runs what [inputData] names ([ScriptWorker]'s keys). Returns false when it names nothing that can run. */
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
        // The run may have been queued before a switch went off. So the event (or, for a notification,
        // reading notifications) is checked again when the run starts.
        val on = event?.get("name") as? String
        // A sample event run from the editor was asked for, so it always runs.
        if (script != null && on != null && on in ScriptMeta.EVENTS && !inputData.getBoolean(ScriptWorker.KEY_INTERACTIVE, false) &&
            (!script.grants.allows(on) || (on == "notification" && !script.grants.allows("notifications")))
        ) return true
        // A pinned status line's refresh runs every few seconds while home shows it, and a widget's run
        // whenever its card is old. So what stays the same isn't reported again: an error shows once until
        // a run works, and a returned result isn't logged.
        val widgetRun = on == ScriptScheduler.WIDGET
        val refresh = script != null && (on == ScriptScheduler.REFRESH || widgetRun) && !inputData.getBoolean(ScriptWorker.KEY_INTERACTIVE, false)
        val refreshes = KvStore(ctx, Stores.WORKER)
        val refreshKey = "refresh-error:${script?.id}"
        val lastError = if (refresh) refreshes.get(refreshKey) else null
        var failed: String? = null
        fun fail(text: String, context: LogContext) {
            failed = text
            if (text != lastError) logs.add(LogKind.ERROR, text, name, context)
        }

        val host = HostApi(
            ctx, name, script?.id,
            // Its switch shows only when its code calls notify. So a script that hides the call can't post.
            notifyEnabled = script?.let { it.notify && ScriptMeta.postsNotifications(it.code) } ?: true,
            interactive = repl || inputData.getBoolean(ScriptWorker.KEY_INTERACTIVE, false),
            byScript = inputData.getBoolean(ScriptWorker.KEY_BY_SCRIPT, false),
            from = from,
            grants = script?.grants,
            secrets = script?.let { s -> ScriptMeta.secrets(s.code).let { d -> HostApi.Secrets(d, SecretStore(ctx).usable(s.name, d)) } },
            options = script?.let { s -> ScriptOptions(ctx).values(s.name, ScriptMeta.options(s.code)) }.orEmpty(),
            widget = script?.let { ScriptMeta.widget(it.code) },
        )
        val engine = ScriptEngine.forLang(lang)
        val started = System.currentTimeMillis()
        val errorCtx = LogContext(
            (if (script != null) mapOf(LogContext.SCRIPT to script.name) else emptyMap()) +
                (from?.let { mapOf(LogContext.FROM to it.toString()) } ?: emptyMap())
        )
        // Android stopped the run (the worker was cancelled). It did not end by itself, so the card notes no end.
        var stopped = false
        // However the run ends, anything still in flight (a request, a timer) changes nothing after it.
        // This is also true when Android stops the run and this returns at once.
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
            } else if (script != null && host.effects == 0 && on != "action" && inputData.getBoolean(ScriptWorker.KEY_INTERACTIVE, false)) {
                // A run you started showed nothing (hn with no new stories, say). Say it finished, so
                // "Running hn…" isn't left without an answer. A button's run is left out: its line shows it done.
                logs.add(LogKind.INFO, "$name ran: nothing to show", name, errorCtx)
            }
            host.commit()
            "ok in ${System.currentTimeMillis() - started}ms"
        } catch (e: CancellationException) {
            host.flush()
            // Only our own timeout is a script failure. WorkManager stopping the worker must propagate.
            if (e !is TimeoutCancellationException) {
                stopped = true
                throw e
            }
            fail("$name: timed out after ${ScriptWorker.TIMEOUT_MS / 1000}s", errorCtx)
            "timeout"
        } catch (e: Throwable) {
            host.flush()
            // The switch itself stopped the run. This is not an error that the script caught and then went on.
            val off = host.denied?.takeIf { host.deniedMessage?.let { m -> e.message.orEmpty().contains(m) } == true }
            // QuickJS's interrupt, or Lua's own timeout error (raised by the hook or a host call that waits).
            val timedOut = e is com.dokar.quickjs.QuickJsInterruptedException || e.message.orEmpty().contains("script timed out")
            if (timedOut) {
                fail("$name: timed out after ${ScriptWorker.TIMEOUT_MS / 1000}s", errorCtx)
                "timeout"
            } else if (off != null) {
                // A switch the user turned off stopped the run. Say which one, with a tap to allow it.
                fail("$name: ${ScriptAccess.label(off).lowercase()} is off for this script — tap to allow", errorCtx + mapOf(LogContext.RUN to "script allow $name $off"))
                "stopped: ${ScriptAccess.label(off).lowercase()} off"
            } else {
                // An error can quote a URL or a header, so mask it like everything else a run shows.
                val message = e.message?.let(host::redact)
                // The first line as the script made it. It can be any length, so cut it for the line and the status.
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
            // Here, so it happens however the run ends: with a result, an error, a timeout, or stopped by Android.
            if (script != null) widgetEnd(ctx, script, host, noteEnd = widgetRun && !stopped, failed)
        }
        // A run you started asked for things nobody has allowed yet, and got empty answers. Offer to allow them.
        if (script != null && host.undecided.isNotEmpty() && inputData.getBoolean(ScriptWorker.KEY_INTERACTIVE, false)) {
            ScriptAccess.askToAllow(ctx, name, host.undecided.toList(), "needs permission for")
        }
        if (refresh && failed != lastError) refreshes.set(refreshKey, failed)
        script?.let {
            // If a refresh ended the same way as the last one, leave the script's row as it is. Writing it
            // every few seconds would make home read every script again.
            if (refresh && failed == lastError) return@let
            scripts.recordRun(it.id, status)
            // The run's lines went out before its result was stored. Without this, home could show the previous run.
            logs.touch()
        }
        if (host.notifyBlocked) reportBlocked(ctx, logs, name, "notify-blocked", "notifications are blocked", "notifications")
        logs.trimIfWritten()
        return true
    }

    /**
     * The end of a run of [script], for its widget. The cards hear of a run once, here, whatever it changed.
     * - [noteEnd], after a widget's run that ended by itself: the card says when its last run failed
     *   ("Failed 10:42"), and forgets it after one that works. So the time and [failed] are noted, but
     *   only while the widget is still placed: one removed during the run keeps nothing.
     * - Then one message goes to the cards, if the run stored something: this note, or what
     *   `widget.show` and `widget.clear` stored ([HostApi.widgetChanged]). Those calls send none
     *   themselves, so a run that calls them twenty times and notes its end still sends one.
     * - A run on its timer or for an event that calls `widget.show` is announced in the same way.
     * Nothing here throws: it runs while the run's own result or error is on its way out.
     */
    private fun widgetEnd(ctx: Context, script: Script, host: HostApi, noteEnd: Boolean, failed: String?) {
        val states = com.mtwabpin.kolahal.widgets.WidgetStates
        val noted = noteEnd && runCatching {
            val placed = ScriptRepository.get(ctx).byId(script.id)?.grants?.allows(ScriptAccess.WIDGET) == true
            if (placed) states.ran(ctx, script.name, failed, quietly = true)
            placed
        }.getOrDefault(false)
        if (noted || host.widgetChanged) states.announce(ctx, script.name)
    }

    /** The source line of a script error. Lua messages carry `name:LINE:`; QuickJS exceptions carry a line number. */
    private fun errorLine(e: Throwable, lang: ScriptLang, name: String): Int? = when (lang) {
        ScriptLang.LUA -> Regex("(?:^|\\s)${Regex.escape(name)}:(\\d+):").find(e.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        // Saved JS scripts are wrapped in one extra line (the async function header).
        ScriptLang.JS -> (e as? com.dokar.quickjs.QuickJsException)?.lineNumber?.minus(1)?.takeIf { it > 0 }
    }

    /** Logs one "tap to allow" line per day ([key] tells the kinds apart), instead of one per blocked call. */
    private fun reportBlocked(context: Context, logs: LogRepository, name: String, key: String, what: String, fix: String) {
        val store = KvStore(context, Stores.WORKER)
        val today = java.time.LocalDate.now().toString()
        if (store.get(key) == today) return
        store.set(key, today)
        logs.add(LogKind.ERROR, "$name: $what — tap to allow", name, LogContext.of(LogContext.RUN to fix))
    }
}
