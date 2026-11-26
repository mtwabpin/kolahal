/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.apps

/**
 * What a private space shows while it is locked, as the phone's own launcher does:
 * - none of its apps: no list, selector, suggestion, pinned or most-used strip, and nothing typed
 *   finds them ([shown]);
 * - one line in their place in `apps`, that it is locked, with a tap that unlocks it;
 * - with Android's "Hide private space when it's locked" on ([Profile.hiddenWhenLocked]), not even
 *   that line, nor the header's lock ([shows]).
 *
 * What points at one of its apps by itself (an alias, a pinned key, a log line, a widget) still finds
 * it ([AppRepository.find]) and offers to unlock the space.
 */
object PrivateSpace {
    /** Whether a profile of [kind] that is [quiet] (paused or locked) is a private space that is locked. */
    fun locked(kind: ProfileKind, quiet: Boolean): Boolean = kind == ProfileKind.PRIVATE && quiet

    fun locked(profile: Profile): Boolean = locked(profile.kind, profile.quiet)

    /** Whether the profile shows at all: not while it is a locked private space that Android hides ([hiddenWhenLocked]). */
    fun shows(kind: ProfileKind, quiet: Boolean, hiddenWhenLocked: Boolean): Boolean =
        !(locked(kind, quiet) && hiddenWhenLocked)

    fun shows(profile: Profile): Boolean = shows(profile.kind, profile.quiet, profile.hiddenWhenLocked)

    /** [apps] without those of a locked private space ([locked]): the same list when there are none. */
    fun <A> shown(apps: List<A>, locked: (A) -> Boolean): List<A> =
        if (apps.none(locked)) apps else apps.filterNot(locked)

    fun shown(apps: List<AppEntry>): List<AppEntry> = shown(apps) { locked(it.profile) }

    /**
     * What an apps grid in the log says when none of its apps can be found any more:
     * - one of them is in a private space that is locked (a grid from before its lines were marked);
     * - one is in a profile Kolahal can't see ([seen] false for its serial), while Android hides the
     *   private space from it because it isn't the home app ([privateHidden]);
     * - else they were uninstalled.
     */
    fun gridGone(
        serials: Collection<Long?>,
        lockedSerials: Set<Long>,
        privateHidden: Boolean,
        seen: (Long) -> Boolean,
    ): String = when {
        serials.any { it != null && it in lockedSerials } -> GRID_LOCKED
        privateHidden && serials.any { it != null && !seen(it) } -> GRID_NOT_HOME
        else -> GRID_GONE
    }

    const val GRID_LOCKED = "(private space locked)"
    const val GRID_NOT_HOME = "(private space: Kolahal must be the home app)"
    const val GRID_GONE = "(apps no longer installed)"
}

/**
 * How a profile and its states are named wherever a person reads them, as the guide names them:
 * the private space is locked or unlocked, the work profile (and a clone) paused or on.
 */
object ProfileWords {
    /** "private space", "work profile". */
    fun name(kind: ProfileKind): String =
        if (kind == ProfileKind.PRIVATE) "private space" else "${kind.label} profile"

    /** [name] with a capital first letter, for the start of a sentence. */
    fun title(kind: ProfileKind): String = name(kind).replaceFirstChar { it.uppercase() }

    /** The state of a profile that is off: "locked", "paused". */
    fun off(kind: ProfileKind): String = if (kind == ProfileKind.PRIVATE) "locked" else "paused"

    /** The state of a profile that is on: "unlocked", "on". */
    fun on(kind: ProfileKind): String = if (kind == ProfileKind.PRIVATE) "unlocked" else "on"

    /** What turns it on: "unlock", "resume". */
    fun resume(kind: ProfileKind): String = if (kind == ProfileKind.PRIVATE) "unlock" else "resume"

    /** What turns it off: "lock", "pause". */
    fun pause(kind: ProfileKind): String = if (kind == ProfileKind.PRIVATE) "lock" else "pause"

    /** "Unlocking", "Resuming" ([on] true); "Locking", "Pausing". */
    fun changing(kind: ProfileKind, on: Boolean): String =
        (if (on) resume(kind) else pause(kind)).replaceFirstChar { it.uppercase() }.removeSuffix("e") + "ing"
}
