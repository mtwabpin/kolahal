/**
 * Manual Review - Pending
 */
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
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.widgets.WidgetRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs one script (by id) or an inline REPL snippet. It runs inside the `:script` process through
 * WorkManager's RemoteWorkerService, so a native engine crash can't take down the launcher UI.
 */
class ScriptWorker(context: Context, params: WorkerParameters) :
    RemoteCoroutineWorker(context, params) {

    override suspend fun doRemoteWork(): Result =
        if (ScriptRun.runForWorker(applicationContext, inputData)) Result.success() else Result.failure()

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
    private val running =
        java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.sync.Mutex>()

    /** The runs with a screen of their own, which go on outside their worker. */
    private val screenRuns =
        ScreenRuns(CoroutineScope(SupervisorJob() + Dispatchers.Default)) { e ->
            android.util.Log.w("Kolahal", "a run with a screen failed", e)
        }

    /**
     * [run], for a worker. A run that may open a screen of its own (`tty.screen`) leaves the worker once
     * the screen shows: Android stops a worker after about ten minutes, and the screen may stay open for
     * hours. Until then it is the worker's, like any other run. Every other run is only [run].
     */
    suspend fun runForWorker(ctx: Context, inputData: Data): Boolean =
        if (mayOpenScreen(ctx, inputData)) screenRuns.run { run(ctx, inputData, it) } else run(ctx, inputData)

    /** Whether this run is of a script that declares `@uses screen`, or of typed code that names `tty.screen`. */
    private fun mayOpenScreen(ctx: Context, inputData: Data): Boolean {
        if (inputData.getBoolean(ScriptWorker.KEY_SCHEDULED, false)) return false
        val id = inputData.getLong(ScriptWorker.KEY_SCRIPT_ID, -1L)
        return if (id >= 0) ScriptRepository.get(ctx).byId(id)?.let { ScriptMeta.isScreen(it.code) } == true
        else ScriptMeta.opensScreen(inputData.getString(ScriptWorker.KEY_CODE).orEmpty())
    }

    /**
     * Runs what [inputData] names ([ScriptWorker]'s keys). Returns false when it names nothing that can run.
     * Only a run with a [handover] may open a screen.
     */
    suspend fun run(ctx: Context, inputData: Data, handover: ScreenHandover? = null): Boolean {
        val scriptId = inputData.getLong(ScriptWorker.KEY_SCRIPT_ID, -1L)
        if (scriptId < 0) return runOne(ctx, inputData, scriptId, handover)
        return running.getOrPut(scriptId) { kotlinx.coroutines.sync.Mutex() }
            .withLock { runOne(ctx, inputData, scriptId, handover) }
    }

    private suspend fun runOne(ctx: Context, inputData: Data, scriptId: Long, handover: ScreenHandover?): Boolean {
        val logs = LogRepository.get(ctx)
        val scripts = ScriptRepository.get(ctx)

        val script = if (scriptId >= 0) scripts.byId(scriptId) ?: return true else null
        if (script != null && !script.enabled && inputData.getBoolean(
                ScriptWorker.KEY_SCHEDULED,
                false
            )
        ) {
            return true
        }
        val lang =
            script?.lang ?: ScriptLang.of(inputData.getString(ScriptWorker.KEY_LANG).orEmpty())
            ?: return false
        val code = script?.code ?: inputData.getString(ScriptWorker.KEY_CODE) ?: return false
        val name = script?.name ?: inputData.getString(ScriptWorker.KEY_NAME) ?: lang.ext
        val repl = script == null
        val args =
            inputData.getNullableStringArray(ScriptWorker.KEY_ARGS)?.filterNotNull().orEmpty()
        val event = inputData.getString(ScriptWorker.KEY_EVENT)
            ?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() }
        val from = inputData.getLong(ScriptWorker.KEY_FROM, -1L).takeIf { it >= 0 }
        // The run may have been queued before a switch went off or the code changed. So the event (or, for
        // a notification, reading notifications and the apps its `@on` line names) is checked again when the run starts.
        val on = event?.get("name") as? String
        // A sample event run from the editor was asked for, so it always runs.
        if (script != null && on != null && on in ScriptMeta.EVENTS && !inputData.getBoolean(
                ScriptWorker.KEY_INTERACTIVE,
                false
            ) &&
            (!script.grants.allows(on) || (on == "notification" && (!script.grants.allows("notifications") ||
                    !ScriptMeta.runsFor(ScriptMeta.parse(script.code).notificationApps, event?.get("pkg") as? String))))
        ) return true
        // A pinned status line's refresh runs every few seconds while home shows it, and a widget's run
        // whenever its card is old. So what stays the same isn't reported again: an error shows once until
        // a run works, and a returned result isn't logged.
        val widgetRun = on == ScriptScheduler.WIDGET
        // Which of the script's widgets this run is for, when it is for one ([widgetOf]): the page
        // asked for it, or a tap on that widget's card started it. A tap's run is a run like any other
        // that you start, and its end is noted for the card too. Null: the run is for no widget.
        val forWidget = widgetOf(event)
        // The widget whose options the run reads and which `widget.show` fills. Every run that is
        // not for a further widget fills the first one, with the script's own options.
        val widgetId = forWidget.orEmpty()
        val refresh =
            script != null && (on == ScriptScheduler.REFRESH || widgetRun) && !inputData.getBoolean(
                ScriptWorker.KEY_INTERACTIVE,
                false
            )
        val refreshes = KvStore(ctx, Stores.WORKER)
        val refreshKey = errorKey(script?.id, widgetId)
        val lastError = if (refresh) refreshes.get(refreshKey) else null
        var failed: String? = null
        // A switch that is off for the script stopped the run ([RunOutcome.switchOff]).
        var switchOff = false
        fun fail(text: String, context: LogContext) {
            failed = text
            if (text != lastError) logs.add(LogKind.ERROR, text, name, context)
        }
        // A script with a screen of its own is a command only. Something still queued by itself (a timer set
        // before it declared the screen) doesn't run.
        if (script != null && ScriptMeta.isScreen(code) && inputData.getBoolean(
                ScriptWorker.KEY_SCHEDULED,
                false
            )
        ) {
            fail("$name ${ScriptMeta.SCREEN_ONLY}", LogContext.of(LogContext.SCRIPT to name))
            if (refresh) refreshes.set(refreshKey, failed)
            return true
        }

        val host = HostApi(
            ctx, name, script?.id,
            // Its switch shows only when its code calls notify. So a script that hides the call can't post.
            notifyEnabled = script?.let { it.notify && ScriptMeta.postsNotifications(it.code) }
                ?: true,
            interactive = repl || inputData.getBoolean(ScriptWorker.KEY_INTERACTIVE, false),
            byScript = inputData.getBoolean(ScriptWorker.KEY_BY_SCRIPT, false),
            from = from,
            grants = script?.grants,
            secrets = script?.let { s ->
                ScriptMeta.secrets(s.code)
                    .let { d -> HostApi.Secrets(d, SecretStore(ctx).usable(s.name, d)) }
            },
            options = script?.let { s ->
                ScriptOptions(ctx).values(
                    s.name,
                    ScriptMeta.options(s.code),
                    widgetId
                )
            }.orEmpty(),
            widget = script?.let { ScriptMeta.widget(it.code) },
            widgetId = widgetId,
            gives = script?.let { ScriptMeta.gives(it.code) }.orEmpty(),
            widgetIssue = script?.let { ScriptCheck.widgetKindIssue(it.code) },
        )
        host.handover = handover
        host.screens = handover != null
        val engine = ScriptEngine.forLang(lang)
        val started = System.currentTimeMillis()
        val errorCtx = LogContext(
            (if (script != null) mapOf(LogContext.SCRIPT to script.name) else emptyMap()) +
                    (from?.let { mapOf(LogContext.FROM to it.toString()) } ?: emptyMap())
        )
        // Android stopped the run (the worker was canceled). It did not end by itself, so the card notes no end.
        var stopped = false
        // However the run ends, anything still in flight (a request, a timer) changes nothing after it.
        // This is also true when Android stops the run and this returns at once.
        val status = try {
            val most = ScriptWorker.TIMEOUT_MS + 2_000
            val result = if (handover == null) withTimeout(most.milliseconds) {
                engine.run(code, name, host, ScriptWorker.TIMEOUT_MS, repl, args, event)
            } else ScreenRuns.limited(most, handover) {
                // Only its code has the limit of a run. Its screen goes on for as long as it shows.
                engine.run(code, name, host, ScriptWorker.TIMEOUT_MS, repl, args, event)
            }
            host.flush()
            if (result != null && !refresh) {
                val shown = host.redact(result)
                logs.add(
                    LogKind.OUTPUT,
                    shown,
                    name,
                    LogContext.of(LogContext.COPY to shown) + errorCtx.values
                )
            } else if (repl && host.effects == 0) {
                logs.add(LogKind.OUTPUT, "nil", name, errorCtx)
            } else if (script != null && host.effects == 0 && on != "action" && inputData.getBoolean(
                    ScriptWorker.KEY_INTERACTIVE,
                    false
                )
            ) {
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
            // What stopped the run is recorded where it happened: Lua's hook or a host call that waits
            // ([HostApi.stoppedBy]), or QuickJS's interrupt. The error's text is the script's own.
            // A host call's timeout counts only when the error is still that one: a script that caught it
            // and then failed otherwise ends in its own error.
            val stop = if (e is com.dokar.quickjs.QuickJsInterruptedException) RunStop.Timeout
            else host.stoppedBy?.takeUnless {
                it == RunStop.Timeout && !e.message.orEmpty().contains("script timed out")
            }
            val outcome = runOutcome(
                name,
                e,
                stop,
                repl,
                host::redact,
                errorLine = { errorLine(it, lang, name) })
            fail(outcome.line, if (outcome.extra.isEmpty()) errorCtx else errorCtx + outcome.extra)
            switchOff = outcome.switchOff
            outcome.status
        } finally {
            host.finish()
            // Here, so it happens however the run ends: with a result, an error, a timeout, or stopped by Android.
            if (script != null) widgetEnd(
                ctx,
                host,
                noteEnd = forWidget != null && !stopped,
                failed,
                switchOff
            )
        }
        // A run you started asked for things nobody has allowed yet, and got empty answers. Offer to allow them.
        if (script != null && host.undecided.isNotEmpty() && inputData.getBoolean(
                ScriptWorker.KEY_INTERACTIVE,
                false
            )
        ) {
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
        if (host.notifyBlocked) reportNotifyBlocked(ctx, logs, name)
        logs.trimIfWritten()
        return true
    }

    /**
     * The end of a script's run, for the widget the run filled ([HostApi.widgetKey]). The cards hear
     * of a run once, here, whatever it changed.
     * - [noteEnd], after a run for the widget that ended by itself: one the widgets page asked for,
     *   or one a tap on the widget started. The card says when its last such run failed ("Failed
     *   10:42"), and forgets it after one that works. So the time and [failed] are noted, but only
     *   while the widget is still placed: one removed during the run keeps nothing. With
     *   [switchOff], a switch that is off for the script stopped the run, and that is noted too.
     *   A switch that waits for its script stops waiting then too, whether or not the run showed anything.
     * - Then one message goes to that widget's card, if the run stored something: this note, or what
     *   `widget.show` and `widget.clear` stored ([HostApi.widgetChanged]). Those calls send none
     *   themselves, so a run that calls them twenty times and notes its end still sends one.
     * - A run on its timer or for an event that calls `widget.show` is announced in the same way.
     * Nothing here throws: it runs while the run's own result or error is on its way out.
     */
    private fun widgetEnd(
        ctx: Context,
        host: HostApi,
        noteEnd: Boolean,
        failed: String?,
        switchOff: Boolean
    ) {
        val states = com.mtwabpin.kolahal.widgets.WidgetStates
        val noted = noteEnd && runCatching {
            val placed = host.widgetPlacedNow()
            if (placed) states.ran(ctx, host.widgetKey, failed, switchOff)
            placed
        }.getOrDefault(false)
        if (noted || host.widgetChanged) states.announce(ctx, host.widgetKey)
    }

    /**
     * Which of a script's widgets a run with this [event] is for: "" for its first widget, or a
     * further widget's id. Null when the run is for no widget.
     * - A run is for a widget when the widgets page asked for it (`event.name` is "widget"), or when
     *   a tap on a card started it (the card puts `widget = true` in the tap's event).
     * - Only such a run reads [ScriptScheduler.INSTANCE], and only an id of the allowed form counts.
     *   The main process builds these events. A script can't set the field for its own run.
     */
    internal fun widgetOf(event: Map<*, *>?): String? {
        val name = event?.get("name")
        if (name != ScriptScheduler.WIDGET && !(name == "action" && event["widget"] == true)) return null
        return (event[ScriptScheduler.INSTANCE] as? String)?.takeIf(WidgetRef.ID::matches)
            .orEmpty()
    }

    /**
     * The key of the last error of a script's quiet runs, in [Stores.WORKER]: such an error is
     * logged once, until a run works. Each further widget has its own (`refresh-error:12#2`), so
     * one widget's failed run does not hide another's.
     */
    internal fun errorKey(scriptId: Long?, widgetId: String): String =
        "refresh-error:" + WidgetRef(scriptId.toString(), widgetId).key

    /** The source line of a script error. Lua messages carry `name:LINE:`; QuickJS exceptions carry a line number. */
    private fun errorLine(e: Throwable, lang: ScriptLang, name: String): Int? = when (lang) {
        ScriptLang.LUA -> Regex("(?:^|\\s)${Regex.escape(name)}:(\\d+):").find(e.message.orEmpty())?.groupValues?.get(
            1
        )?.toIntOrNull()
        // Saved JS scripts are wrapped in one extra line (the async function header).
        ScriptLang.JS -> (e as? com.dokar.quickjs.QuickJsException)?.lineNumber?.minus(1)
            ?.takeIf { it > 0 }
    }

    /** Logs one "tap to allow" line per day when notifications are blocked, instead of one per blocked call. */
    private fun reportNotifyBlocked(context: Context, logs: LogRepository, name: String) {
        val store = KvStore(context, Stores.WORKER)
        val today = java.time.LocalDate.now().toString()
        if (store.get("notify-blocked") == today) return
        store.set("notify-blocked", today)
        logs.add(
            LogKind.ERROR,
            "$name: notifications are blocked\nTap to allow them",
            name,
            LogContext.of(LogContext.RUN to "allow send-notifications")
        )
    }
}
