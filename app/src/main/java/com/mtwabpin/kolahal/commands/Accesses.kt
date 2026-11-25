/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.commands

import com.mtwabpin.kolahal.shader.ShaderOverApps

/**
 * One access that Kolahal can ask Android for: `allow <word>`.
 * - [word]: the word after `allow`.
 * - [title]: Android's name for it, as the `allow` list and the setup checklist show it.
 * - [short]: what it is for, in a few words, for the `allow` list and suggestions.
 * - [detail]: what it is for, in a sentence, for the setup checklist.
 * - [needs]: the part of the build it is for, which a build can leave out ([BuildFeature]); null when every build has it.
 * - [minSdk]: the first Android version that asks for it. Before that, it is always given, or has no use.
 */
data class Access(
    val word: String,
    val title: String,
    val short: String,
    val detail: String,
    val needs: BuildFeature? = null,
    val minSdk: Int = 0,
)

/**
 * The parts of Kolahal that a build can leave out, which some accesses are for. The Play build leaves out the
 * first three. It has [SHADER_OVER_APPS], whose switch stays so that it can leave it out again (app/build.gradle.kts).
 */
enum class BuildFeature { LOCK_SERVICE, CONTACTS, CALL_LOG, SHADER_OVER_APPS }

/**
 * Every access Kolahal can use, in the order of the setup checklist. `allow` lists them, `allow <word>`
 * asks for one, and the setup checklist and the tour show them (`SetupChecks`, in terminal, says
 * whether each is given).
 *
 * The two about notifications say which way they go, as their short forms were mixed up:
 * `send-notifications` lets Kolahal send you notifications (reminders, scripts, feeds), and
 * `read-notifications` lets it read yours (the `notifs` command, `media`, scripts).
 */
object Accesses {
    const val USAGE = "usage"
    const val SEND_NOTIFICATIONS = "send-notifications"
    const val READ_NOTIFICATIONS = "read-notifications"
    const val LOCK = "lock"
    const val DND = "dnd"
    const val SETTINGS = "settings"
    const val CONTACTS = "contacts"
    const val CALENDAR = "calendar"
    const val CALLS = "calls"
    const val ALARMS = "alarms"
    const val BATTERY = "battery"
    const val OVERLAY = "overlay"

    val ALL = listOf(
        Access(
            USAGE,
            "Usage access",
            "screen time and unlock counts",
            "Accurate unlock counts, including unlocks while the launcher wasn't running",
        ),
        Access(
            SEND_NOTIFICATIONS,
            "Notifications",
            "send you reminders and script alerts",
            "Scripts can alert you about new items",
        ),
        Access(
            READ_NOTIFICATIONS,
            "Notification access",
            "read your notifications: notifs, media",
            "Your notifications in the terminal (notifs) and for scripts",
        ),
        Access(
            LOCK,
            "Double-tap to lock",
            "lock the screen: lock, double-tap",
            "Optional accessibility service that only locks the screen",
            needs = BuildFeature.LOCK_SERVICE,
        ),
        Access(
            DND,
            "Do Not Disturb access",
            "turn Do Not Disturb on and off",
            "Optional: lets dnd, ring silent and scripts with @uses dnd (night-dnd) change Do Not Disturb",
        ),
        Access(
            SETTINGS,
            "Modify system settings",
            "change brightness, rotation and screen timeout",
            "Optional: lets brightness, rotate and screen-timeout change system settings",
        ),
        // The Play build has no READ_CONTACTS. There, call, sms and contacts use Android's contact picker.
        Access(
            CONTACTS,
            "Contacts",
            "call and text people by name",
            "Optional: call and text people by name, with contact suggestions",
            needs = BuildFeature.CONTACTS,
        ),
        Access(
            CALENDAR,
            "Calendar",
            "your events in agenda",
            "Optional: upcoming events with the agenda command",
        ),
        Access(
            CALLS,
            "Call log",
            "calls in the log",
            "Optional: incoming, outgoing and missed calls appear in the log",
            needs = BuildFeature.CALL_LOG,
        ),
        // On Android 12+ the user can turn exact alarms off. On Android 14+ they are off by default.
        Access(
            ALARMS,
            "Alarms & reminders",
            "reminders and script times on the minute",
            "Optional: reminders and scripts' daily times at the exact time; night-work needs it to pause the work profile",
            minSdk = 31,
        ),
        Access(
            BATTERY,
            "Unrestricted battery",
            "script timers on schedule",
            "Timer scripts run on schedule instead of being deferred",
        ),
        // Shaders need Android 13: before that there is nothing to play over an app. A low-memory phone can't
        // give it at all ([offered]).
        Access(
            OVERLAY,
            "Display over other apps",
            "play a one-off shader over other apps",
            "Optional: a one-off shader plays over the app in front when a notification has one of its words",
            needs = BuildFeature.SHADER_OVER_APPS,
            minSdk = 33,
        ),
    )

    /** Whether this build has [feature]. */
    fun inThisBuild(feature: BuildFeature): Boolean = when (feature) {
        BuildFeature.LOCK_SERVICE -> com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE
        BuildFeature.CONTACTS -> com.mtwabpin.kolahal.BuildConfig.CONTACTS
        BuildFeature.CALL_LOG -> com.mtwabpin.kolahal.BuildConfig.CALL_LOG
        BuildFeature.SHADER_OVER_APPS -> com.mtwabpin.kolahal.BuildConfig.SHADER_OVER_APPS
    }

    /** Whether [access] is in a build that has the features [has] says yes to. */
    fun inBuild(access: Access, has: (BuildFeature) -> Boolean = { inThisBuild(it) }): Boolean =
        access.needs?.let(has) ?: true

    /** The accesses this build has. `allow` offers only these; the word of another one says the build leaves it out. */
    val OFFERED: List<Access> = ALL.filter { inBuild(it) }

    /**
     * The accesses in a build that has the features [has] says yes to, which Android version [sdk]
     * asks for and the phone can give: what `allow` lists and the setup checklist shows. A low-memory phone
     * ([lowRam]) can't give Display over other apps ([ShaderOverApps.phoneGives]).
     */
    fun offered(sdk: Int, lowRam: Boolean = false, has: (BuildFeature) -> Boolean = { inThisBuild(it) }): List<Access> =
        ALL.filter { inBuild(it, has) && sdk >= it.minSdk && (it.word != OVERLAY || ShaderOverApps.phoneGives(lowRam)) }

    /** One line of the `allow` list: an access, whether Android gives it now, and what a tap on it runs. */
    data class Listed(val access: Access, val allowed: Boolean) {
        val run: String get() = Accesses.command(access)
    }

    /** The `allow` list: each access of [offered], in order, with whether it is [given]. */
    fun listing(offered: List<Access>, given: (Access) -> Boolean): List<Listed> =
        offered.map { Listed(it, given(it)) }

    /** The access [word] names, in any build, or null when it names none. */
    fun find(word: String): Access? = ALL.firstOrNull { it.word.equals(word, ignoreCase = true) }

    /**
     * The accesses of [among] that [typed] starts to name: by the word's start, or by the start of a part
     * of it after a hyphen. So `notif` finds both `send-notifications` and `read-notifications`, as someone
     * who thinks "notifications" first would type it.
     */
    fun matching(typed: String, among: List<Access>): List<Access> = among.filter { a ->
        a.word.startsWith(typed, ignoreCase = true) || a.word.split('-').any { it.startsWith(typed, ignoreCase = true) }
    }

    /** The command that asks for [access]: what a "tap to allow" line, a setup row and the `allow` list run. */
    fun command(access: Access): String = "allow ${access.word}"
}
