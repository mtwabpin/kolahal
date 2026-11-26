/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.apps.ProfileWords

/**
 * What the app commands say about a profile that is off, or can't be seen, in the guide's words
 * ([ProfileWords]): the private space is locked or unlocked, the work profile paused or on.
 */
internal object ProfileMessages {
    /** Why Android may refuse to pause or resume a profile, and the command that fixes it. */
    const val NOT_HOME = "Is Kolahal the default launcher?\n'home' chooses the default launcher"

    /** An app of a profile that is off, typed: the line's tap turns the profile on and opens [app]. */
    fun offOpen(kind: ProfileKind, app: String): String =
        "${ProfileWords.title(kind)} is ${ProfileWords.off(kind)}\nTap to ${ProfileWords.resume(kind)} it and open $app"

    /** A profile that is off, where its apps were asked for (`apps private`, a shortcut): the line's tap turns it on. */
    fun off(kind: ProfileKind): String =
        "${ProfileWords.title(kind)} is ${ProfileWords.off(kind)}\nTap to ${ProfileWords.resume(kind)} it"

    /** The command a tap on [off]'s line runs: `private on`, `work on`. */
    fun onCommand(kind: ProfileKind): String = "${command(kind)} on"

    /** The command that turns [kind] on or off: `private`, `work`. A clone has none of its own. */
    fun command(kind: ProfileKind): String = if (kind == ProfileKind.PRIVATE) "private" else "work"

    /**
     * No profile of [kind] was found. [hidden]: Android hides the private space from Kolahal, which
     * isn't the home app (Android 15+), so there may be one.
     */
    fun none(kind: ProfileKind, hidden: Boolean): String =
        if (kind == ProfileKind.PRIVATE && hidden) NOT_HOME_PRIVATE
        else "No ${ProfileWords.name(kind)} on this device"

    /** What [none] says while the private space is hidden from Kolahal. Its Copy is `home`. */
    const val NOT_HOME_PRIVATE =
        "Kolahal can't see a private space\nIt must be the home app to see one\n'home' chooses the default launcher"

    /** A profile that has no apps to list. */
    fun noApps(kind: ProfileKind): String = "No apps in the ${ProfileWords.name(kind)}"

    /** `private` or `work`: the profile's name, then its state as a tag. */
    fun statusName(kind: ProfileKind): String = "${ProfileWords.title(kind)}: "

    /** `private on` or `work off` was done: [on], what was asked. */
    fun changing(kind: ProfileKind, on: Boolean): String =
        "${ProfileWords.changing(kind, on)} the ${ProfileWords.name(kind)}…"

    /** Turning [kind] on needs its lock first: Android asks for it. */
    fun needsLock(kind: ProfileKind): String =
        "${changing(kind, true)}\nAndroid asks for ${if (kind == ProfileKind.PRIVATE) "your lock" else "its PIN"} first"

    /** Android refused to turn [kind] on ([on]) or off. */
    fun refused(kind: ProfileKind, on: Boolean): String =
        "Couldn't ${if (on) ProfileWords.resume(kind) else ProfileWords.pause(kind)} the ${ProfileWords.name(kind)}\n$NOT_HOME"

    /** [kind] didn't come on in the time waited for it. */
    fun late(kind: ProfileKind): String =
        "${ProfileWords.title(kind)} didn't ${ProfileWords.resume(kind)} in time\nTry again"

    /** A tap on the header's running profile: the logged line whose tap turns it off. */
    fun tapToPause(kind: ProfileKind): String = "Tap to ${ProfileWords.pause(kind)} the ${ProfileWords.name(kind)}"
}
