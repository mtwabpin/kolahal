package com.mtwabpin.kolahal.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Every preferences file and key-value scope Kolahal keeps, and where each one travels. Code opens a
 * preferences file only through [Prefs.open], and names a scope only by a constant here, so a new one
 * can't be added without saying where it goes. StoresTest checks that, and that the backup rules in
 * res/xml say the same as this list.
 */
object Stores {
    /**
     * A preferences file.
     * - [inBackup]: copied as it is into Kolahal's own backup file ([FullBackup]). Some others travel there
     *   in a readable section of their own instead ([Backup]: widgets, the script folder's scripts).
     * - [toCloud]: in Android's cloud backup. The others are excluded in data_extraction_rules.xml
     *   (cloud-backup) and backup_rules.xml (Android 11 and older).
     * - [toNewPhone]: in a transfer from your old phone, over a cable or Wi-Fi. The others are excluded
     *   in data_extraction_rules.xml (device-transfer).
     */
    class Prefs internal constructor(val name: String, val inBackup: Boolean, val toCloud: Boolean = true, val toNewPhone: Boolean = true) {
        fun open(context: Context): SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        override fun toString() = name
    }

    /** Settings (`config`). */
    val CONFIG = Prefs("config", inBackup = true)
    val ALIASES = Prefs("aliases", inBackup = true)
    /** Hidden apps, labels, pins. */
    val APPS = Prefs("apps", inBackup = true)
    /** Which apps you open and when: not for the cloud. */
    val APP_USAGE = Prefs("app_usage", inBackup = true, toCloud = false)
    val NOTIF_PREFS = Prefs("notif_prefs", inBackup = true)
    val SCRIPT_LIBRARY = Prefs("script_library", inBackup = true)
    val SCRIPT_CATALOG = Prefs("script_catalog", inBackup = true)
    /** How often you unlock, from before 0.5.7 (now in the [UNLOCKS] scope): not for the cloud. */
    val OLD_UNLOCKS = Prefs("unlocks", inBackup = true, toCloud = false)
    /** Home's own state: the first run's tour, one-time moves, the pinned status lines. */
    val LAUNCHER = Prefs("launcher", inBackup = true)
    /**
     * The log's marks (cleared, hidden, seen), from before 0.5.8: now in the [LOG_MARKS] scope. A backup
     * from before still brings them here, and [LogMarks] moves them on. Not for the cloud, like the log.
     */
    val OLD_LOGS = Prefs("logs", inBackup = true, toCloud = false)
    /** The Play build's picked contacts: names and numbers stay on the phone. */
    val PICKED_CONTACTS = Prefs("picked_contacts", inBackup = false, toCloud = false)
    /** `wallpaper match`'s wallpaper id: another phone's same number is someone's own wallpaper. */
    val WALLPAPER_MATCH = Prefs("wallpaper_match", inBackup = false, toCloud = false, toNewPhone = false)
    /** Crash reports belong to this phone. */
    val CRASH = Prefs("crash", inBackup = false, toCloud = false, toNewPhone = false)
    /** The boot that `@on boot` last fired for: restored from a backup, an older one would look like a new boot. */
    val TRIGGERS = Prefs("triggers", inBackup = false)
    val DAILY_SCHEDULE = Prefs("daily_schedule", inBackup = false)
    /** The picked script folder: a storage permission, which doesn't move. */
    val SCRIPT_FOLDER = Prefs("script_folder", inBackup = false)
    val LIBRARY_UPDATES = Prefs("library_updates", inBackup = false)
    /** The widgets placed: a backup has them in a section of their own. */
    val WIDGETS = Prefs("widgets", inBackup = false)
    val TIPS = Prefs("tips", inBackup = false)
    val SCRIPTS_PAGE = Prefs("scripts_page", inBackup = false)
    val AUTO_BACKUP = Prefs("auto_backup", inBackup = false)
    val REMINDERS = Prefs("reminders", inBackup = false)

    val PREFS = listOf(
        CONFIG, ALIASES, APPS, APP_USAGE, NOTIF_PREFS, SCRIPT_LIBRARY, SCRIPT_CATALOG, OLD_UNLOCKS, LAUNCHER, OLD_LOGS,
        PICKED_CONTACTS, WALLPAPER_MATCH, CRASH, TRIGGERS, DAILY_SCHEDULE, SCRIPT_FOLDER, LIBRARY_UPDATES, WIDGETS,
        TIPS, SCRIPTS_PAGE, AUTO_BACKUP, REMINDERS,
    )

    // Key-value scopes ([KvStore]), all in kolahal.db. The database stays out of the cloud and goes to a new phone.
    // In a backup file, a scope goes as it is, except those below that say otherwise.

    /** The last call seen. This phone only. */
    const val CALLS = "calls"
    /** The apps seen: another phone's list would log every app as installed or updated. This phone only. */
    const val PACKAGES = "packages"
    /** Daily notes of the scripts' and start-up's work ([Quietly], ScriptWorker). This phone only. */
    const val WORKER = "worker"
    /** Settings copied for the :script process ([SharedSettings]). This phone only. */
    const val SHARED_SETTINGS = "shared-settings"
    /** The copy of recent notifications kept for scripts. This phone only. */
    const val NOTIFICATIONS_MIRROR = "system"
    /** Which install wrote the database ([ThisInstall]). This phone only. */
    const val INSTALL = "install"
    /**
     * What each placed script widget shows, by script name: the checked description as JSON
     * ([com.mtwabpin.kolahal.widgets.WidgetStates]). A run makes it again, and it may hold private text. This phone only.
     */
    const val WIDGET = "widget"
    /** When a widget's content was set, when a run was last tried, and its last error, by script name. This phone only. */
    const val WIDGET_META = "widget.meta"
    /** Scripts' status lines, by script name. */
    const val STATUS = "status"
    /** What a status line shows besides its text, by script name. */
    const val STATUS_EXTRAS = "status.extra"
    /** Exchange rates for `fx`. */
    const val FX = "fx"
    /** Unlocks counted without Usage Access, by day ([com.mtwabpin.kolahal.unlock.UnlockCounter]). */
    const val UNLOCKS = "unlocks"
    /** The log's marks ([LogMarks]): line ids that go with the lines, in a backup and to a new phone. */
    const val LOG_MARKS = "log-marks"
    /** Pinned status lines, from before they moved to [LAUNCHER]: emptied at start. */
    const val OLD_PINS = "pin"

    /** A script's own store (`store`, `rss.fresh`): "script:<id>". A backup keys it by the script's name, as ids differ between phones. */
    const val SCRIPT_PREFIX = "script:"
    /** A script's secret values: "secret:<name>". Never in a backup. */
    const val SECRET_PREFIX = "secret:"
    /** A script's option values: "option:<name>". */
    const val OPTION_PREFIX = "option:"

    /**
     * The scopes about this phone. They don't go into a backup, and a restore keeps this phone's. If a
     * backup file holds them, they are skipped. A transfer to a new phone copies them, so [ThisInstall]
     * clears them there.
     */
    val THIS_PHONE = listOf(CALLS, PACKAGES, WORKER, SHARED_SETTINGS, NOTIFICATIONS_MIRROR, INSTALL, WIDGET, WIDGET_META)

    val SCOPES = THIS_PHONE + listOf(STATUS, STATUS_EXTRAS, FX, UNLOCKS, LOG_MARKS, OLD_PINS)
}
