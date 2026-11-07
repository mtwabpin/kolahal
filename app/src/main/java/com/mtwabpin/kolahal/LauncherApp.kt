package com.mtwabpin.kolahal

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
    private val awayTrim = Runnable { trimKept() }

    /** [AWAY_TRIM_MS]. Tests make it shorter. */
    @androidx.annotation.VisibleForTesting internal var awayTrimMs = AWAY_TRIM_MS

    /** Call when home shows again (MainActivity's onStart). The kept pictures and icons stay. */
    fun homeShown() = main.removeCallbacks(awayTrim)

    private fun trimKept() {
        com.mtwabpin.kolahal.ui.LogImages.trim()
        com.mtwabpin.kolahal.ui.AppIcons.trim()
    }

    override fun onCreate() {
        super.onCreate()
        // Install this before anything else can fail, so a crash in either process leaves a report (see `crashes`).
        CrashReports.install(this)
        Notifier.createChannels(this)
        // The :script process only needs the database and notifications.
        if (getProcessName() != packageName) return

        // Load custom themes before the theme is applied, because `theme.name` may name one of them.
        com.mtwabpin.kolahal.data.CustomThemes.load(this)
        AppRepository.get(this).start()
        UnlockCounter.get(this).start()
        // Runtime receivers and a network callback for `@on` scripts. Charger and headset events need
        // these on Android 8+.
        com.mtwabpin.kolahal.script.TriggerDispatcher.get(this).start()
        DeviceStatus.get(this).start()
        com.mtwabpin.kolahal.contacts.ContactsRepository.get(this).refresh()
        com.mtwabpin.kolahal.apps.PackageEvents.get(this).start()
        com.mtwabpin.kolahal.calls.CallEvents.get(this).start()

        // Set daily script times again, restore schedules after updates, and trim the old log.
        Thread {
            // Do this first. Otherwise the first log line with a notification icon would list the saved
            // icons on the main thread.
            runCatching { com.mtwabpin.kolahal.notifications.NotifIcons.preload(this) }
            runCatching { CrashReports.announce(this) }
            // Library scripts that nobody edited are updated to the app's library.
            runCatching { com.mtwabpin.kolahal.script.ScriptLibrary(this).updateUnedited() }
            runCatching { ScriptScheduler.syncAll(this) }
            runCatching { com.mtwabpin.kolahal.data.StatusLines.pinShownOnce(this) }
            runCatching { com.mtwabpin.kolahal.feeds.FeedScheduler.sync(this) }
            runCatching { com.mtwabpin.kolahal.productivity.ReminderScheduler.rescheduleAll(this) }
            runCatching { LogRepository.get(this).trimIfDue(Config.get(this)[Keys.LOG_KEEP_DAYS]) }
            // This scans the whole table, so it runs once per app version. It repairs rows from old
            // versions before anything reads them. A backup's export depends on it.
            runCatching {
                val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
                val version = packageManager.getPackageInfo(packageName, 0).longVersionCode
                if (prefs.getLong("repaired_for", -1L) != version) {
                    com.mtwabpin.kolahal.data.KvStore.repairOversized(this)
                    prefs.edit { putLong("repaired_for", version) }
                }
            }
        }.start()

        val logs = LogRepository.get(this)
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val trimmed = intent.getLongExtra(LogRepository.EXTRA_TRIMMED_UP_TO, 0L)
                when {
                    intent.getBooleanExtra(LogRepository.EXTRA_RESTRUCTURE, false) -> logs.onExternalRestructure()
                    trimmed > 0 -> logs.onExternalTrim(trimmed)
                    else -> logs.onExternalChange()
                }
            }
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
        /** The same as the default wait of `log.fresh`. After that time, home starts clean anyway. */
        const val AWAY_TRIM_MS = 10 * 60_000L
    }
}
