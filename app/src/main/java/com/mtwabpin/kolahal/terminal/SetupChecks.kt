/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.commands.Access
import com.mtwabpin.kolahal.commands.Accesses
import com.mtwabpin.kolahal.notify.Notifier
import com.mtwabpin.kolahal.shader.ShaderPhone

/** One item of the setup checklist. [fix] is a terminal command that opens the matching system page (`allow <word>` for an access). */
data class SetupCheck(
    val title: String,
    val detail: String,
    val ok: Boolean,
    val fix: String?,
)

/** The setup checks. Both the `setup` GUI page and its text form (`setup text`) use them. */
object SetupChecks {
    fun run(context: Context): List<SetupCheck> {
        val profiles =
            AppRepository.get(context).profiles.value.filter { it.kind != ProfileKind.PERSONAL }
        return listOf(
            SetupCheck(
                "Default home app",
                "Kolahal opens on Home and can pause or resume the work profile",
                isDefaultLauncher(context),
                "home",
            ),
        ) + accesses(context).map {
            SetupCheck(it.access.title, it.access.detail, it.allowed, it.run)
        } + SetupCheck(
            "Work / private profiles",
            if (profiles.isEmpty()) "None found on this device"
            else profiles.joinToString { "${it.kind.label} (${if (it.quiet) "paused" else "on"})" },
            profiles.isNotEmpty(),
            null,
        )
    }

    /** The accesses this build, this Android version and this phone have ([Accesses.offered]), each with whether it is given now. */
    fun accesses(context: Context): List<Accesses.Listed> =
        Accesses.listing(Accesses.offered(Build.VERSION.SDK_INT, lowRam = ShaderPhone.lowRam(context))) { given(context, it) }

    /** Whether Android gives Kolahal [access] now. */
    fun given(context: Context, access: Access): Boolean = when (access.word) {
        Accesses.USAGE -> com.mtwabpin.kolahal.usage.UsageAccess.granted(context)
        Accesses.SEND_NOTIFICATIONS -> Notifier.canPost(context)
        Accesses.READ_NOTIFICATIONS -> com.mtwabpin.kolahal.notifications.NotificationActions.isEnabled(context)
        Accesses.LOCK -> com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE && com.mtwabpin.kolahal.gesture.LockService.isEnabled(context)
        Accesses.DND -> context.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted
        Accesses.SETTINGS -> android.provider.Settings.System.canWrite(context)
        Accesses.CONTACTS -> com.mtwabpin.kolahal.BuildConfig.CONTACTS &&
                com.mtwabpin.kolahal.contacts.ContactsRepository.get(context).hasPermission()
        Accesses.CALENDAR -> com.mtwabpin.kolahal.productivity.Agenda.hasPermission(context)
        Accesses.CALLS -> com.mtwabpin.kolahal.BuildConfig.CALL_LOG && com.mtwabpin.kolahal.calls.CallEvents.hasPermission(context)
        Accesses.ALARMS -> com.mtwabpin.kolahal.productivity.ReminderScheduler.canBeExact(context)
        Accesses.BATTERY -> context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        Accesses.OVERLAY -> com.mtwabpin.kolahal.BuildConfig.SHADER_OVER_APPS && android.provider.Settings.canDrawOverlays(context)
        else -> false
    }

    fun isDefaultLauncher(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 29) {
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    } else {
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
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
