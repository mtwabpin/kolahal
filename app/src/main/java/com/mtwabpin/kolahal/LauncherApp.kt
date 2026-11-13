package com.mtwabpin.kolahal

import com.mtwabpin.kolahal.data.Stores
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Configuration
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.notify.Notifier
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.unlock.UnlockCounter
import com.mtwabpin.kolahal.status.DeviceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LauncherApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            // Scheduling happens in the UI process. ScriptWorker itself runs in :script.
            .setDefaultProcessName(packageName)
            .build()

    /**
     * When Android is short of memory, we drop the log pictures (also those on disk) and the app
     * icons kept for scrolling back. The less memory home uses, the less likely Android closes it.
     * If it is closed, the return to home is a cold start.
     *
     * We don't drop them as soon as another app covers home, because pictures would then load again
     * after every visit. We drop them after [AWAY_TRIM_MS] away. From Android 14, the home app never
     * hears about memory pressure, because it is never cached, and [onLowMemory] isn't called.
     */
    @Suppress("DEPRECATION") // TRIM_MEMORY_BACKGROUND is still sent, and is the pressure meant here
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= TRIM_MEMORY_BACKGROUND -> trimKept()
            level == TRIM_MEMORY_UI_HIDDEN -> {
                main.removeCallbacks(awayTrim)
                main.postDelayed(awayTrim, awayTrimMs)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        trimKept()
    }

    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private val awayTrim = Runnable { trimKept(away = true) }

    /** [AWAY_TRIM_MS]. Tests make it shorter. */
    @androidx.annotation.VisibleForTesting internal var awayTrimMs = AWAY_TRIM_MS

    /** Call when home shows again (MainActivity's onStart). The kept pictures and icons stay. */
    fun homeShown() = main.removeCallbacks(awayTrim)

    /**
     * Drops what the screen keeps in memory ([keepsInMemory]): the log pictures and the app icons.
     * After time [away], the icons of the most-used apps stay (about 1 MB at most): they are the first
     * you type, and would pop in late otherwise. When Android is short of memory, everything goes.
     */
    private fun trimKept(away: Boolean = false) {
        if (caches.isEmpty()) return
        val keep = if (away) runCatching { AppRepository.get(this).top(KEEP_ICONS).map { it.key }.toSet() }.getOrDefault(emptySet()) else emptySet()
        caches.forEach { it(keep) }
    }

    override fun onCreate() {
        super.onCreate()
        // Install this before anything else can fail, so a crash in either process leaves a report (see `crashes`).
        CrashReports.install(this)
        Notifier.createChannels(this)
        // The :script process only needs the database and notifications.
        if (com.mtwabpin.kolahal.data.Singleton.inScriptProcess) return

        // Load custom themes before the theme is applied, because `theme.name` may name one of them.
        com.mtwabpin.kolahal.data.CustomThemes.load(this)
        AppRepository.get(this).start()
        DeviceStatus.get(this).start()
        // What the first frame doesn't need starts once the main thread is idle, after that frame:
        // each is a few calls to Android. Nothing is missed meanwhile: each catches up as it starts.
        android.os.Looper.getMainLooper().queue.addIdleHandler {
            UnlockCounter.get(this).start()
            // Runtime receivers and a network callback for `@on` scripts. Charger and headset events need
            // these on Android 8+.
            com.mtwabpin.kolahal.script.TriggerDispatcher.get(this).start()
            com.mtwabpin.kolahal.contacts.ContactsRepository.get(this).refresh()
            com.mtwabpin.kolahal.apps.PackageEvents.get(this).start()
            com.mtwabpin.kolahal.calls.CallEvents.get(this).start()
            false
        }

        // Set daily script times again, restore schedules after updates, and trim the old log.
        Thread {
            // Do this first. Otherwise the first log line with a notification icon would list the saved
            // icons on the main thread.
            runCatching { com.mtwabpin.kolahal.notifications.NotifIcons.preload(this) }
            runCatching { CrashReports.announce(this) }
            // These must not stop each other, but a failure is logged (once a day): a reminder not set again stays unseen otherwise.
            val quietly = com.mtwabpin.kolahal.data.Quietly
            // Library scripts that nobody edited are updated to the app's library.
            quietly.run(this, "Updating library scripts") { com.mtwabpin.kolahal.script.ScriptLibrary(this).updateUnedited() }
            quietly.run(this, "Scheduling scripts") { ScriptScheduler.syncAll(this) }
            // Once: secret values from before 0.4.0 have no sites, and must be set again. Each script with one says so.
            quietly.run(this, "Checking script secrets") { com.mtwabpin.kolahal.script.ScriptAccess.askForSecretsWithoutSites(this) }
            runCatching { com.mtwabpin.kolahal.data.StatusLines.pinShownOnce(this) }
            quietly.run(this, "Scheduling feeds") { com.mtwabpin.kolahal.feeds.FeedScheduler.sync(this) }
            quietly.run(this, "Setting reminders again") { com.mtwabpin.kolahal.productivity.ReminderScheduler.rescheduleAll(this) }
            quietly.run(this, "Trimming the old log") { LogRepository.get(this).trimIfDue(Config.get(this)[Keys.LOG_KEEP_DAYS]) }
            // This scans the whole table, so it runs once per app version. It repairs rows from old
            // versions before anything reads them. A backup's export depends on it.
            runCatching {
                val prefs = Stores.LAUNCHER.open(this)
                val version = packageManager.getPackageInfo(packageName, 0).longVersionCode
                if (prefs.getLong("repaired_for", -1L) != version) {
                    com.mtwabpin.kolahal.data.KvStore.repairOversized(this)
                    prefs.edit { putLong("repaired_for", version) }
                }
            }
        }.start()

        // The :script process writes to the log too, and says so in a message. The log reads its own message.
        val logs = LogRepository.get(this)
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = logs.onExternal(intent)
        }, IntentFilter(LogRepository.ACTION_LOGS_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)

        // The :script process doesn't see settings changes. So home copies `log.keep-lines` to a place
        // where :script can read it, and trims the log now and whenever the value changes.
        val config = Config.get(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            config.version.map { config[Keys.LOG_KEEP_LINES] }.distinctUntilChanged().collect { keep ->
                runCatching {
                    logs.keepLines = keep
                    logs.trim(keep)
                }
            }
        }
        // A new time zone, or a clock set by hand, moves every daily time, so schedule them again.
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                scope.launch { runCatching { ScriptScheduler.syncAll(this@LauncherApp) } }
                // Repeating reminders keep the time the user asked for, in the new zone.
                if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    scope.launch { runCatching { com.mtwabpin.kolahal.productivity.ReminderScheduler.followZone(this@LauncherApp) } }
                }
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        // Automatic backups follow backup.auto, however it changes (Settings, config, a restore).
        scope.launch {
            config.version.map { config[Keys.BACKUP_AUTO] }.distinctUntilChanged().collect {
                runCatching { com.mtwabpin.kolahal.data.AutoBackup.sync(this@LauncherApp) }
            }
        }
        // The Wi-Fi-only setting changes every feed's schedule, whether it is set, reset or restored.
        scope.launch {
            config.version.map { config[Keys.FEEDS_WIFI] }.distinctUntilChanged().drop(1).collect {
                runCatching { com.mtwabpin.kolahal.feeds.FeedScheduler.sync(this@LauncherApp) }
            }
        }
    }

    companion object {
        /** What the screen keeps in memory, in the order each began to keep something ([keepsInMemory]). */
        private val caches = java.util.concurrent.CopyOnWriteArrayList<(Set<String>) -> Unit>()

        /**
         * A cache of the screen's (pictures, icons) adds itself here the first time it is used, and
         * [trim] lets it go: when home is away a while, or memory is short. [trim] gets the keys of
         * the apps whose icons stay. So the app knows when to let go, but not what the screen keeps.
         */
        fun keepsInMemory(trim: (keepApps: Set<String>) -> Unit) {
            caches += trim
        }

        /**
         * Starts home again in a new process after [afterMs], so every cache is read again: `restart`, and
         * after a full restore replaced the files and tables under it. The wait lets the line saying so show.
         */
        fun restart(context: Context, afterMs: Long) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                android.os.Process.killProcess(android.os.Process.myPid())
            }, afterMs)
        }

        /** The same as the default wait of `log.fresh`. After that time, home starts clean anyway. */
        const val AWAY_TRIM_MS = 10 * 60_000L

        /** The most-used apps whose icons stay after time away: more than a strip of suggestions shows. */
        const val KEEP_ICONS = 24
    }
}
