package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.Stores
import android.content.Context
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.multiprocess.RemoteListenableWorker
import androidx.work.multiprocess.RemoteWorkerService
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.widgets.WidgetRef
import java.time.Duration
import java.time.ZonedDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ScriptScheduler {
    /** The minimum interval WorkManager allows for periodic work. */
    const val MIN_INTERVAL_MIN = 15
    private const val MAX_INLINE_CODE = 8_000
    private const val RECHECK_MS = 6 * 3_600_000L
    private const val KEY_SYNCED_AT = "synced_at"

    private fun uniqueName(id: Long) = "script-$id"

    /** Schedules or cancels the interval and daily runs for [script], based on its settings. */
    fun sync(context: Context, script: Script) {
        val wm = WorkManager.getInstance(context)
        syncDaily(context, wm, script)
        syncInterval(context, wm, script)
    }

    private fun syncInterval(context: Context, wm: WorkManager, script: Script) {
        if (!script.enabled || script.intervalMin <= 0) {
            wm.cancelUniqueWork(uniqueName(script.id))
            return
        }
        val minutes = script.intervalMin.coerceAtLeast(MIN_INTERVAL_MIN).toLong()
        val request = PeriodicWorkRequestBuilder<ScriptWorker>(minutes, TimeUnit.MINUTES)
            // Only a script that goes online waits for a network. A battery log runs offline too.
            .apply { if (ScriptMeta.goesOnline(script.code)) setConstraints(online()) }
            .setInputData(remoteData(context) {
                putLong(ScriptWorker.KEY_SCRIPT_ID, script.id)
                putBoolean(ScriptWorker.KEY_SCHEDULED, true)
            })
            .addTag("script")
            .build()
        wm.enqueueUniquePeriodicWork(uniqueName(script.id), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Daily times. Each time gets an alarm ([ScriptAlarms]) that runs the script and sets the next
     * day's alarm. The alarm is exact when Kolahal may set exact alarms; otherwise it is at most an
     * hour late. Alarms are set again on every sync, because they don't survive a reboot and the next
     * one moves with the clock. Older versions queued a 24h job per time instead. Those jobs waited
     * for a network and, at night, for Doze, so they are removed.
     */
    private fun syncDaily(context: Context, wm: WorkManager, script: Script) {
        val prefs = Stores.DAILY_SCHEDULE.open(context)
        val key = "script:${script.id}"
        val wanted = if (script.enabled) script.atTimes.mapNotNull(::normalizeTime).distinct().sorted() else emptyList()
        val before = prefs.getString(key, null).orEmpty().split(',').filter { it.isNotEmpty() }
        wm.cancelAllWorkByTag(dailyTag(script.id))
        (before - wanted.toSet()).forEach { ScriptAlarms.cancel(context, script.id, it) }
        wanted.forEach { ScriptAlarms.set(context, script.id, it) }
        prefs.edit { putString(key, wanted.joinToString(",")) }
    }

    /**
     * The `event` of a run at daily [time]: `{ name = "at", time = "23:00" }`. It holds the time the
     * run was set for, even when it runs late, so a script with two times can tell them apart.
     */
    fun atEvent(time: String): String = Json.stringify(mapOf("name" to "at", "time" to time))

    /**
     * Queues the run for a daily [time] when it can't run in the foreground. Like a timer's run, it
     * waits for a network only if the script goes online.
     */
    fun runScheduled(context: Context, script: Script, time: String) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ScriptWorker>()
                .apply {
                    if (ScriptMeta.goesOnline(script.code)) setConstraints(online())
                }
                .setInputData(remoteData(context) {
                    putLong(ScriptWorker.KEY_SCRIPT_ID, script.id)
                    putBoolean(ScriptWorker.KEY_SCHEDULED, true)
                    putString(ScriptWorker.KEY_EVENT, atEvent(time))
                })
                .addTag("script")
                .build()
        )
    }

    private fun online() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun dailyTag(id: Long) = "script-$id-daily"

    /** "8:00" / "08:00" -> "08:00", for an `@at` line and for `script at`. Null for anything that isn't a valid clock time. */
    fun normalizeTime(raw: String): String? = runCatching {
        val parts = raw.trim().split(':').map { it.toInt() }
        if (parts.size == 2) LocalTime.of(parts[0], parts[1]).toString() else null
    }.getOrNull()

    /** Milliseconds from now until the next local [hhmm] ("HH:mm"). Handles DST. */
    fun millisUntil(hhmm: String, now: ZonedDateTime = ZonedDateTime.now()): Long {
        val time = LocalTime.parse(normalizeTime(hhmm) ?: hhmm)
        var next = now.toLocalDate().atTime(time).atZone(now.zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
        return Duration.between(now, next).toMillis()
    }

    /**
     * Runs [syncAll] if the last one was more than [RECHECK_MS] ago. It sets each script's timers
     * again, in case any were lost while the launcher ran for days.
     */
    fun syncAllIfDue(context: Context) {
        val last = Stores.DAILY_SCHEDULE.open(context).getLong(KEY_SYNCED_AT, 0L)
        if (System.currentTimeMillis() - last !in 0 until RECHECK_MS) {
            syncAll(context)
            // Each event run is added to its script's chain, and WorkManager reads the whole chain to add
            // the next one. Otherwise, finished runs are cleared only when home's process starts.
            WorkManager.getInstance(context).pruneWork()
        }
    }

    /** Syncs every script again. One bad script can't stop the rest. */
    fun syncAll(context: Context) {
        Stores.DAILY_SCHEDULE.open(context).edit { putLong(KEY_SYNCED_AT, System.currentTimeMillis()) }
        val wm = WorkManager.getInstance(context)
        ScriptRepository.get(context).all().forEach { s ->
            runCatching {
                syncDaily(context, wm, s)
                syncInterval(context, wm, s)
            }
        }
    }

    fun cancel(context: Context, scriptId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(scriptId))
        WorkManager.getInstance(context).cancelAllWorkByTag(dailyTag(scriptId))
        val prefs = Stores.DAILY_SCHEDULE.open(context)
        prefs.getString("script:$scriptId", null).orEmpty().split(',').filter { it.isNotEmpty() }
            .forEach { ScriptAlarms.cancel(context, scriptId, it) }
        prefs.edit { remove("script:$scriptId") }
    }

    fun runNow(context: Context, script: Script, args: List<String> = emptyList(), event: Map<String, Any?>? = null) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ScriptWorker>()
                .setInputData(remoteData(context) {
                    putLong(ScriptWorker.KEY_SCRIPT_ID, script.id)
                    putBoolean(ScriptWorker.KEY_INTERACTIVE, true)
                    if (args.isNotEmpty()) putStringArray(ScriptWorker.KEY_ARGS, args.toTypedArray())
                    // A sample event, run from the editor.
                    if (event != null) putString(ScriptWorker.KEY_EVENT, Json.stringify(event))
                    fromCommand(context)
                })
                .build()
        )
    }

    /** The `event` of a run that a pinned status line asks for with `refresh`. */
    const val REFRESH = "refresh"

    /**
     * Runs a pinned status line's script again (`status(text, { refresh = s })`). Home asks for this
     * while it shows the line. There are never two at once: while one waits (for a network, say) or
     * runs, the next one is dropped. It shares one queue with the widget's runs ([refresh]).
     */
    fun refreshPinned(context: Context, script: Script) = refresh(context, script, REFRESH)

    /** The `event` of a run the widgets page asks for: its widget was just placed, its content is old, or it set a `refresh`. */
    const val WIDGET = "widget"

    /**
     * The field of a widget run's `event` that says which further widget of the script the run
     * fills: `event.instance = "2"`. It is absent for the script's first widget. Only Kolahal sets
     * it: [refreshEvent] here, and a tap on a card.
     */
    const val INSTANCE = "instance"

    /**
     * Runs a placed widget's script again, so it calls `widget.show`. [widget] is the id of the
     * widget the run fills ([com.mtwabpin.kolahal.widgets.WidgetRef.id]): "" for the script's first
     * widget. Like [refreshPinned], there are never two at once for one widget: while one waits
     * (for a network, say) or runs, the next one is dropped. The run is not interactive, and its
     * `event.name` is [WIDGET]. It shares one queue with the script's other widgets' runs and the
     * pinned line's refreshes ([refresh]).
     *
     * [again]: something the run reads has just changed, one of the widget's options. A run of
     * this widget that is already there may have read the old value. So this one is not dropped:
     * it goes after that one ([queued]).
     */
    fun refreshWidget(context: Context, script: Script, widget: String = "", again: Boolean = false) =
        refresh(context, script, widgetKind(widget), again)

    /**
     * The name of the one queue of a script's refresh runs (WorkManager's unique work): its pinned
     * line's ([REFRESH]) and each of its widgets' ([widgetKind]).
     */
    internal fun refreshQueue(scriptId: Long) = "script-$scriptId-refresh"

    /**
     * The kind of the quiet run that fills the widget [id] of a script: "widget" for its first
     * widget, "widget#2" for a further one. Each widget is a kind of its own, so a run for one
     * never stands for a run of another ([queued]).
     */
    internal fun widgetKind(id: String = ""): String = if (id.isEmpty()) WIDGET else WIDGET + WidgetRef.SEPARATOR + id

    /**
     * The `event` of a quiet run of [kind]: its name, [REFRESH] or [WIDGET]. A further widget's run
     * also says which widget it fills ([INSTANCE]).
     */
    internal fun refreshEvent(kind: String): Map<String, Any?> {
        val id = kind.substringAfter(WidgetRef.SEPARATOR, "")
        return mapOf("name" to kind.substringBefore(WidgetRef.SEPARATOR)) + if (id.isEmpty()) emptyMap() else mapOf(INSTANCE to id)
    }

    /** Where a request for a refresh run goes ([queued]). */
    internal enum class Queued { DROPPED, AFTER, ALONE }

    /**
     * Where a request for a refresh run of [kind] goes. [waiting] has the kinds of the runs in the
     * script's queue that wait or run now.
     * - One of its kind is there: the request is dropped. Home and the widgets page ask again and
     *   again while they show, and one run of a kind answers them all.
     * - Only other kinds are there: the run goes after them, not beside them. It is not dropped,
     *   because each kind has its own event and a script may do other things for each. Also a
     *   widget's card asks once for each state, so a dropped request would leave it waiting.
     * - Nothing is there: the run starts the queue.
     * - [again]: the request is never dropped. What its run reads has just changed, and the run of
     *   its kind that is there may have started before that. It goes after it.
     */
    internal fun queued(kind: String, waiting: Collection<String>, again: Boolean = false): Queued = when {
        waiting.isEmpty() -> Queued.ALONE
        kind in waiting && !again -> Queued.DROPPED
        else -> Queued.AFTER
    }

    /** What the tag of a queued refresh run starts with. Its kind follows. */
    private const val KIND_TAG = "refresh:"

    /** The tag that tells a queued refresh run's kind: `refresh:widget`, `refresh:widget#2`. */
    internal fun kindTag(kind: String) = KIND_TAG + kind

    /**
     * The kind of a queued refresh run, from its tags. A widget's run has its widget's id in the
     * tag. One queued before runs had this tag is a pinned line's.
     */
    internal fun kindOf(tags: Set<String>): String =
        tags.firstOrNull { it == kindTag(WIDGET) || it.startsWith(kindTag(WIDGET) + WidgetRef.SEPARATOR) }?.removePrefix(KIND_TAG) ?: REFRESH

    /** One request at a time reads a refresh queue and adds to it ([refresh]). */
    private val refreshLock = Any()

    /** The longest [refresh] waits for WorkManager to read a queue, in seconds. */
    private const val QUEUE_READ_S = 5L

    /**
     * Queues a quiet run of [script] of the kind [kind]: [REFRESH], or a widget's ([widgetKind]).
     *
     * A script can have a pinned line and widgets, and all can ask for runs while home shows them.
     * The kinds were separate unique work, so they started at once: one ran and the others
     * waited inside the `:script` process for it ([ScriptRun]'s lock). Now they share one queue
     * ([refreshQueue]), and the next starts when the one before has ended ([queued]). Each run keeps
     * what it is for: its own event ([refreshEvent]), and a widget's run notes its end for its card.
     *
     * Call it off the main thread: it waits for WorkManager to read the queue.
     */
    private fun refresh(context: Context, script: Script, kind: String, again: Boolean = false) {
        val wm = WorkManager.getInstance(context)
        val queue = refreshQueue(script.id)
        val request = OneTimeWorkRequestBuilder<ScriptWorker>()
            .apply { if (ScriptMeta.goesOnline(script.code)) setConstraints(online()) }
            .setInputData(remoteData(context) {
                putLong(ScriptWorker.KEY_SCRIPT_ID, script.id)
                putBoolean(ScriptWorker.KEY_SCHEDULED, true)
                putString(ScriptWorker.KEY_EVENT, Json.stringify(refreshEvent(kind)))
            })
            .addTag("script")
            .addTag(kindTag(kind))
            .build()
        // Reading the queue and adding to it are two steps. Under one lock, and because WorkManager
        // does what it is asked in order, the next request sees this one in the queue.
        synchronized(refreshLock) {
            val waiting = runCatching { wm.getWorkInfosForUniqueWork(queue).get(QUEUE_READ_S, TimeUnit.SECONDS) }.getOrNull()
                ?.filter { !it.state.isFinished }?.map { kindOf(it.tags) }
            val policy = when (if (waiting == null) Queued.ALONE else queued(kind, waiting, again)) {
                Queued.DROPPED -> return
                Queued.AFTER -> androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE
                // KEEP never stops a run. If the queue couldn't be read and a run is there, this request is dropped.
                // With nothing waiting, it also clears the queue's finished runs.
                Queued.ALONE -> androidx.work.ExistingWorkPolicy.KEEP
            }
            wm.enqueueUniqueWork(queue, policy, request)
        }
    }

    /**
     * One non-interactive run started by a device event (`@on`). [payload] becomes the script's
     * `event` global. The worker skips the run if the script was disabled in the meantime.
     */
    fun runForEvent(context: Context, scriptId: Long, payload: Map<String, Any?>) {
        // If the payload is too big for WorkManager's data (a long notification in a script with many bytes
        // per character), its texts are cut shorter until it fits. The run is not dropped.
        var trimmed = payload
        var json = Json.stringify(trimmed)
        var cap = 2000
        while (storedSize(json) > MAX_INLINE_CODE && cap >= 125) {
            cap /= 2
            trimmed = trimmed.mapValues { (_, v) -> if (v is String && v.length > cap) v.take(cap) + "…" else v }
            json = Json.stringify(trimmed)
        }
        if (storedSize(json) > MAX_INLINE_CODE) return
        // One script's event runs happen one after another, never side by side with itself (its store,
        // rss.fresh's seen items). A burst of events queues instead of piling up runs.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "script-$scriptId-events",
            androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ScriptWorker>()
                .setInputData(remoteData(context) {
                    putLong(ScriptWorker.KEY_SCRIPT_ID, scriptId)
                    putBoolean(ScriptWorker.KEY_SCHEDULED, true)
                    putString(ScriptWorker.KEY_EVENT, json)
                })
                .addTag("script")
                .build(),
        )
    }

    /**
     * The size of [text] as WorkManager stores it, which is what its 10 KB cap counts. WorkManager uses
     * Java's modified UTF-8: a character outside the basic plane takes two units of 3 bytes each, so
     * an emoji takes 6. Plain UTF-8 counts too few bytes.
     */
    internal fun storedSize(text: String): Int = text.sumOf { c ->
        when (c.code) {
            in 0x01..0x7f -> 1
            0, in 0x80..0x7ff -> 2
            else -> 3
        }
    }

    /** Evaluates a one-liner typed at the prompt (`lua ...` / `js ...`). */
    fun eval(context: Context, lang: ScriptLang, code: String, name: String? = null): Boolean {
        // WorkManager Data is capped at 10 KB of serialized bytes, not characters.
        if (code.toByteArray().size > MAX_INLINE_CODE) return false
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ScriptWorker>()
                .setInputData(remoteData(context) {
                    putString(ScriptWorker.KEY_LANG, lang.ext)
                    putString(ScriptWorker.KEY_CODE, code)
                    name?.let { putString(ScriptWorker.KEY_NAME, it) }
                    fromCommand(context)
                })
                .build()
        )
        return true
    }

    /** Set on the thread that runs a command a script sent with launcher.run ([sentByScript]). */
    private val byScript = ThreadLocal<Boolean>()

    /**
     * Runs [block], a command that a script sent with launcher.run. The runs it starts are marked
     * ([ScriptWorker.KEY_BY_SCRIPT]) and may not send commands themselves.
     */
    fun <T> sentByScript(block: () -> T): T {
        val previous = byScript.get()
        byScript.set(true)
        try {
            return block()
        } finally {
            byScript.set(previous)
        }
    }

    /** Whether this thread is running a command a script sent ([sentByScript]). */
    internal val runningSentCommand: Boolean get() = byScript.get() == true

    /** Marks a run that a typed command starts. Its lines are that command's output ([com.mtwabpin.kolahal.data.LogContext.FROM]). */
    private fun Data.Builder.fromCommand(context: Context) {
        LogRepository.get(context).currentCause?.let { putLong(ScriptWorker.KEY_FROM, it) }
    }

    private fun remoteData(context: Context, block: Data.Builder.() -> Unit): Data =
        Data.Builder()
            .putString(RemoteListenableWorker.ARGUMENT_PACKAGE_NAME, context.packageName)
            .putString(RemoteListenableWorker.ARGUMENT_CLASS_NAME, RemoteWorkerService::class.java.name)
            .apply { if (runningSentCommand) putBoolean(ScriptWorker.KEY_BY_SCRIPT, true) }
            .apply(block)
            .build()
}
