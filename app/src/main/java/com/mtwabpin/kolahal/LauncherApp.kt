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
            // Scheduling happens in the UI process; ScriptWorker itself runs in :script.
            .setDefaultProcessName(packageName)
            .build()

    /**
     * Android short of memory: the log pictures (on disk too) and app icons kept for scrolling back
     * go. The fewer MB home holds then, the less likely it's closed, which would make the return home
     * a cold start. Not merely on going behind another app: pictures scrolled back to would then
     * load again after every visit.
     */
    @Suppress("DEPRECATION") // TRIM_MEMORY_BACKGROUND is still sent, and is the pressure meant here
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            com.mtwabpin.kolahal.ui.LogImages.trim()
            com.mtwabpin.kolahal.ui.AppIcons.trim()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        com.mtwabpin.kolahal.ui.LogImages.trim()
        com.mtwabpin.kolahal.ui.AppIcons.trim()
    }

    override fun onCreate() {
        super.onCreate()
        // Before anything else can fail: a crash in either process leaves a report (see `crashes`).
        CrashReports.install(this)
        Notifier.createChannels(this)
        // The :script process only needs the database and notifications.
        if (getProcessName() != packageName) return

        // Before the theme is applied: `theme.name` may be one of them.
        com.mtwabpin.kolahal.data.CustomThemes.load(this)
        AppRepository.get(this).start()
        UnlockCounter.get(this).start()
        // Runtime receivers + network callback for `@on` scripts (charger/headset need these on Android 8+).
        com.mtwabpin.kolahal.script.TriggerDispatcher.get(this).start()
        DeviceStatus.get(this).start()
        com.mtwabpin.kolahal.contacts.ContactsRepository.get(this).refresh()
        com.mtwabpin.kolahal.apps.PackageEvents.get(this).start()
        com.mtwabpin.kolahal.calls.CallEvents.get(this).start()

        // Set daily script times again, restore schedules after updates, trim old log.
        Thread {
            // First: the first log line with a notification icon would otherwise list the saved icons on the main thread.
            runCatching { com.mtwabpin.kolahal.notifications.NotifIcons.preload(this) }
            runCatching { CrashReports.announce(this) }
            // Library scripts nobody edited follow the app's library.
            runCatching { com.mtwabpin.kolahal.script.ScriptLibrary(this).updateUnedited() }
            runCatching { ScriptScheduler.syncAll(this) }
            runCatching { com.mtwabpin.kolahal.feeds.FeedScheduler.sync(this) }
            runCatching { com.mtwabpin.kolahal.productivity.ReminderScheduler.rescheduleAll(this) }
            runCatching { LogRepository.get(this).trimIfDue(Config.get(this)[Keys.LOG_KEEP_DAYS]) }
            // A whole-table scan: once per app version is enough (it repairs rows from old versions).
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

        // The :script process doesn't see settings changes: home copies `log.keep-lines` where it can
        // read it, and trims to it now and whenever it changes.
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
        // A new time zone, or the clock set by hand, moves every daily time: anchor them again.
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                scope.launch { runCatching { ScriptScheduler.syncAll(this@LauncherApp) } }
                // Repeating reminders keep the time they were asked for, in the new zone.
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
        // Wi-Fi only or not changes every feed's schedule, whether set, reset or restored.
        scope.launch {
            config.version.map { config[Keys.FEEDS_WIFI] }.distinctUntilChanged().drop(1).collect {
                runCatching { com.mtwabpin.kolahal.feeds.FeedScheduler.sync(this@LauncherApp) }
            }
        }
    }
}
