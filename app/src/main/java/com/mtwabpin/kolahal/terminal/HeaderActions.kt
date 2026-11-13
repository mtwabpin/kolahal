package com.mtwabpin.kolahal.terminal

import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.status.Status

/** What a tap on a header item does. The header (ui/TerminalHeader) names the item tapped. */
enum class HeaderTarget { CLOCK, UNLOCKS, BATTERY, NETWORK, ALARM, SETTINGS, TODO }

/**
 * What a tap on the header does (the clock, battery, a profile, a pinned script's line), and the
 * quick actions (tap `$` on the prompt, or the `quick` gesture), which offer the same within thumb
 * reach. The view model holds one as `header`. Its own `onHeaderTap` and the rest hand over to it.
 *
 * [taps]: items that run a command or a script, or log a line, run at most once in 2 seconds, so
 * quick repeated taps log once. Items that only open a screen, and the music controls, are not held
 * back. The log's line buttons share the same gate.
 */
class HeaderActions(private val taps: TapGate, private val host: Host) {
    /** What these actions need from the view model. */
    interface Host {
        fun startActivity(intent: Intent)
        fun openScreen(screen: Screen)
        /** Runs [cmd] as if typed, without alias expansion. */
        fun runCommand(cmd: String)
        fun runScriptNamed(name: String)
        fun expandNotifications()
        /** Logs an info line, off the main thread. */
        fun logInfo(text: String, ctx: LogContext)
        val status: Status
        val profiles: List<Profile>
    }

    fun onHeaderTap(target: HeaderTarget) {
        when (target) {
            HeaderTarget.CLOCK, HeaderTarget.ALARM -> host.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            HeaderTarget.UNLOCKS -> if (taps.allow("header:unlocks")) host.runCommand("unlocks 7")
            HeaderTarget.BATTERY -> host.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            HeaderTarget.NETWORK -> host.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            HeaderTarget.SETTINGS -> host.openScreen(Screen.SETTINGS)
            HeaderTarget.TODO -> if (taps.allow("header:todo")) host.runCommand("todo")
        }
    }

    /** A tap on a pinned script's line: runs the script, at most once in 2 seconds. */
    fun onPinTap(name: String) {
        if (taps.allow("pin:$name")) host.runScriptNamed(name)
    }

    /** Everything the header offers, as a list at the bottom of the screen. */
    fun quickActions(): List<LogAction> = buildList {
        // Use the header's icons where it has them (battery and network as the header shows them now).
        fun icon(id: Int) = ActionIcon.Drawable(id)
        val s = host.status
        add(LogAction("Settings", icon = icon(R.drawable.ic_settings)) { host.openScreen(Screen.SETTINGS) })
        add(LogAction("Notifications", icon = icon(R.drawable.ic_notifications)) { host.expandNotifications() })
        add(LogAction("Alarms & clock", icon = icon(R.drawable.ic_alarm)) { onHeaderTap(HeaderTarget.ALARM) })
        add(LogAction("Battery", icon = s.batteryPct?.let { ActionIcon.Battery(it, s.charging) }) { onHeaderTap(HeaderTarget.BATTERY) })
        val net = when (s.network) {
            "wifi" -> icon(R.drawable.ic_wifi)
            "offline" -> ActionIcon.Drawable(R.drawable.ic_cloud_off, alert = true)
            else -> icon(R.drawable.ic_signal)
        }
        add(LogAction("Network", icon = net) { onHeaderTap(HeaderTarget.NETWORK) })
        add(LogAction("Unlocks this week", icon = icon(R.drawable.ic_lock_open)) { onHeaderTap(HeaderTarget.UNLOCKS) })
        host.profiles.filter { it.kind == ProfileKind.WORK || it.kind == ProfileKind.PRIVATE }.forEach { p ->
            val cmd = if (p.kind == ProfileKind.PRIVATE) "private" else "work"
            // As in the header: the profile's colour while it runs, dim while it is paused.
            val res = if (p.kind == ProfileKind.WORK) R.drawable.ic_work else R.drawable.ic_lock
            add(
                LogAction(
                    if (p.quiet) "Resume ${p.kind.label} profile" else "Pause ${p.kind.label} profile",
                    icon = ActionIcon.Drawable(res, profile = p.kind.takeUnless { p.quiet }),
                ) {
                    host.runCommand("$cmd ${if (p.quiet) "on" else "off"}")
                },
            )
        }
        add(LogAction("Apps", icon = icon(R.drawable.ic_apps)) { host.runCommand("apps") })
        add(LogAction("Widgets", icon = icon(R.drawable.ic_widgets)) { host.openScreen(Screen.WIDGETS) })
        add(LogAction("Feeds", icon = icon(R.drawable.ic_rss)) { host.openScreen(Screen.FEEDS) })
        add(LogAction("Scripts", icon = icon(R.drawable.ic_code)) { host.openScreen(Screen.SCRIPTS) })
        if (com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE) {
            add(LogAction("Lock screen", icon = icon(R.drawable.ic_lock)) { if (!com.mtwabpin.kolahal.gesture.LockService.lock()) host.runCommand("lock") })
        }
    }

    /** Resuming is harmless, so it runs at once. Pausing needs a second tap, on the logged line. */
    fun onProfileTap(profile: Profile) {
        if (!taps.allow("profile:${profile.kind}")) return
        val cmd = if (profile.kind == ProfileKind.PRIVATE) "private" else "work"
        if (profile.quiet) host.runCommand("$cmd on")
        else host.logInfo("Tap to pause the ${profile.kind.label} profile", LogContext.of(LogContext.RUN to "$cmd off"))
    }
}
