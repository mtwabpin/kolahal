/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.mtwabpin.kolahal.commands.Access
import com.mtwabpin.kolahal.commands.Accesses
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.notify.Notifier
import com.mtwabpin.kolahal.terminal.SetupChecks
import com.mtwabpin.kolahal.terminal.TerminalUi
import java.util.concurrent.ConcurrentHashMap

/**
 * `allow`: every access Kolahal can use ([Accesses]), and whether Android gives it now.
 * `allow <word>` asks Android for one, or opens the page in Android's settings where it is turned on.
 * The "tap to allow" lines, the setup checklist and the tour run it too.
 */
class AllowCommands(context: Context, private val ui: TerminalUi) : CommandGroup(context) {

    fun run(args: List<String>) {
        val word = args.firstOrNull() ?: return list()
        if (args.size > 1) return usage("allow")
        val access = Accesses.find(word) ?: return error("Unknown access: $word\n'allow' lists them", LogContext.of(LogContext.COPY to "allow"))
        if (!Accesses.inBuild(access)) return missing(access)
        ask(access)
    }

    /** One line for each access, with whether it is given now. A tap on a line runs `allow <word>`. */
    private fun list() {
        val rows = SetupChecks.accesses(context)
        // One column for the whole list, so the titles line up.
        val column = rows.map { it.access.word }.maxBy { it.length }
        info("Android permissions\nTap one to allow it")
        rows.forEach { row ->
            out(
                row.access.word,
                LogContext.of(
                    LogContext.RUN to row.run,
                    LogContext.DESC to state(row.allowed) + " " + row.access.title,
                    LogContext.NAME_COL to column,
                    LogContext.ARGS to row.access.short,
                ),
            )
        }
    }

    private fun state(given: Boolean) =
        if (given) StyledText.tag(StyledText.ColorRole.GOOD, "allowed")
        else StyledText.tag(StyledText.ColorRole.MUTED, "not allowed")

    /** What an access this build leaves out says. */
    private fun missing(access: Access) = when (access.word) {
        Accesses.CONTACTS -> info("This build doesn't read your contacts\ncall, sms and contacts open Android's contact picker\nThey remember who you pick")
        Accesses.LOCK -> error(NO_LOCK)
        Accesses.CALLS -> error("This build can't read the call log")
        else -> error("This build can't use ${access.title}")
    }

    private fun ask(access: Access) {
        when (access.word) {
            Accesses.USAGE -> startActivity(com.mtwabpin.kolahal.usage.UsageAccess.intent())
            Accesses.SEND_NOTIFICATIONS -> notifications()
            // If access is off, Kolahal first explains what it reads and asks (Play's prominent disclosure).
            // If access is already on, Android's screen opens at once, so the user can turn it off.
            Accesses.READ_NOTIFICATIONS -> {
                val actions = com.mtwabpin.kolahal.notifications.NotificationActions
                if (actions.isEnabled(context)) startActivity(actions.settingsIntent(context)) else ui.askNotificationAccess()
            }

            Accesses.LOCK -> startActivity(com.mtwabpin.kolahal.gesture.LockService.settingsIntent())
            Accesses.DND -> open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "Do Not Disturb access")
            Accesses.SETTINGS -> open(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)),
                "modify system settings",
            )

            Accesses.CONTACTS -> permission(access, "Contacts are allowed", ui::requestContactsPermission)
            Accesses.CALENDAR -> permission(access, "Calendar is allowed", ui::requestCalendarPermission)
            Accesses.CALLS -> permission(access, "Call log is allowed", ui::requestCallLogPermission)
            Accesses.ALARMS -> if (com.mtwabpin.kolahal.productivity.ReminderScheduler.canBeExact(context)) {
                info("Exact alarms are already allowed")
            } else startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
            )

            Accesses.BATTERY -> startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /**
     * A runtime permission: says so when it is allowed, and asks Android when it isn't. Once Android
     * said no ([answered]), it opens Kolahal's page in Android's settings instead, because Android
     * stops asking after a second no.
     */
    private fun permission(access: Access, allowed: String, request: () -> Unit) = when {
        SetupChecks.given(context, access) -> info(allowed)
        access.word in refusedWords -> startActivity(appSettings())
        else -> request()
    }

    /**
     * Asks for the permission to post notifications when Android can ask. Opens Kolahal's
     * notification settings when it can't: before Android 13, after a no, or with Kolahal's
     * notifications turned off there.
     */
    private fun notifications() {
        val missing = Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
        when {
            (missing && Accesses.SEND_NOTIFICATIONS in refusedWords) || (!missing && !Notifier.canPost(context)) ->
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )

            missing -> ui.requestNotificationPermission()
            else -> info("Notifications are allowed")
        }
    }

    private fun appSettings() = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )

    private fun open(intent: Intent, what: String) {
        runCatching { startActivity(intent) }
            .onSuccess { info("Opened $what") }
            .onFailure { error("Can't open $what: ${it.reason()}") }
    }

    companion object {
        /** What `lock` and `allow lock` say in a build without the accessibility service. */
        const val NO_LOCK = "This build can't lock the screen\nIt has no accessibility service"

        /** The runtime permissions Android said no to since Kolahal started: `allow <word>` opens their page in settings. */
        private val refusedWords: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

        /** Android's answer when Kolahal asked for the permission of access [word] ([Accesses]): [granted] or not. */
        fun answered(word: String, granted: Boolean) {
            if (granted) refusedWords.remove(word) else refusedWords.add(word)
        }
    }
}
