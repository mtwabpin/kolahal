package com.mtwabpin.kolahal.terminal

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.notify.Notifier
import com.mtwabpin.kolahal.unlock.UnlockCounter

/** One item of the setup checklist. [fix] is a terminal command that opens the matching system page. */
data class SetupCheck(
    val title: String,
    val detail: String,
    val ok: Boolean,
    val fix: String?,
)

/** The setup checks. Both the `setup` GUI page and its text form (`setup text`) use them. */
object SetupChecks {
    fun run(context: Context): List<SetupCheck> {
        val power = context.getSystemService(PowerManager::class.java)
        val profiles = AppRepository.get(context).profiles.value.filter { it.kind != ProfileKind.PERSONAL }
        return listOfNotNull(
            SetupCheck(
                "Default home app",
                "Kolahal opens on Home and can pause or resume the work profile",
                isDefaultLauncher(context),
                "home",
            ),
            SetupCheck(
                "Usage access",
                "Accurate unlock counts, including unlocks while the launcher wasn't running",
                UnlockCounter.get(context).hasUsageAccess(),
                "usage-access",
            ),
            SetupCheck(
                "Notifications",
                "Scripts can alert you about new items",
                Notifier.canPost(context),
                "notifications",
            ),
            SetupCheck(
                "Notification access",
                "Your notifications in the terminal (notifs) and for scripts",
                com.mtwabpin.kolahal.notifications.NotificationActions.isEnabled(context),
                "notifs enable",
            ),
            if (!com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE) null else SetupCheck(
                "Double-tap to lock",
                "Optional accessibility service that only locks the screen",
                com.mtwabpin.kolahal.gesture.LockService.isEnabled(context),
                "accessibility",
            ),
            SetupCheck(
                "Do Not Disturb access (dnd, ring silent, scripts)",
                "Optional: lets dnd, ring silent and scripts with @uses dnd (night-dnd) change Do Not Disturb",
                context.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted,
                "dnd access",
            ),
            SetupCheck(
                "Modify system settings (brightness, rotate)",
                "Optional: lets brightness, rotate and screen-timeout change system settings",
                android.provider.Settings.System.canWrite(context),
                "brightness access",
            ),
            // The Play build has no READ_CONTACTS. There, call, sms and contacts use Android's contact picker.
            if (!com.mtwabpin.kolahal.BuildConfig.CONTACTS) null else SetupCheck(
                "Contacts (call, sms, contacts)",
                "Optional: call and text people by name, with contact suggestions",
                com.mtwabpin.kolahal.contacts.ContactsRepository.get(context).hasPermission(),
                "contacts --allow",
            ),
            SetupCheck(
                "Calendar (agenda)",
                "Optional: upcoming events with the agenda command",
                com.mtwabpin.kolahal.productivity.Agenda.hasPermission(context),
                "agenda --allow",
            ),
            if (!com.mtwabpin.kolahal.BuildConfig.CALL_LOG) null else SetupCheck(
                "Call log (calls in the log)",
                "Optional: incoming, outgoing and missed calls appear in the log",
                com.mtwabpin.kolahal.calls.CallEvents.hasPermission(context),
                "calls --allow",
            ),
            // On Android 12+ the user can turn exact alarms off. On Android 14+ they are off by default.
            if (Build.VERSION.SDK_INT < 31) null else SetupCheck(
                "Alarms & reminders (remind, script times)",
                "Optional: reminders and scripts' daily times at the exact time; night-work needs it to pause the work profile",
                com.mtwabpin.kolahal.productivity.ReminderScheduler.canBeExact(context),
                "remind exact",
            ),
            SetupCheck(
                "Unrestricted battery",
                "Timer scripts run on schedule instead of being deferred",
                power.isIgnoringBatteryOptimizations(context.packageName),
                "battery",
            ),
            SetupCheck(
                "Work / private profiles",
                if (profiles.isEmpty()) "None found on this device"
                else profiles.joinToString { "${it.kind.label} (${if (it.quiet) "paused" else "on"})" },
                profiles.isNotEmpty(),
                null,
            ),
        )
    }

    fun isDefaultLauncher(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 29) {
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    } else {
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName == context.packageName
    }

    /**
     * Android's own dialog that asks to make Kolahal the home app: one tap, no settings page.
     * Android 10 and newer only; null on older versions. Start it for a result: Android requires that.
     */
    fun homeRoleRequest(context: Context): Intent? =
        if (Build.VERSION.SDK_INT < 29) null
        else context.getSystemService(RoleManager::class.java)
            ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }
            ?.createRequestRoleIntent(RoleManager.ROLE_HOME)
}
