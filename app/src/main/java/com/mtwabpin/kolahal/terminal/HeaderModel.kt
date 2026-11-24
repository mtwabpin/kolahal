/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import androidx.compose.runtime.Immutable
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.data.headerSide
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.usage.UsageLimits
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One part of the header: what it says and in which ink, what a tap on it does, and what a screen
 * reader hears. The GUI draws [kind] with its icon; [text] is what a part says beside its icon, or
 * in its place.
 */
@Immutable
data class HeaderPart(
    val kind: Kind,
    val text: String,
    val ink: Ink,
    /** The part's name in tests (`header-<tag>`). */
    val tag: String,
    /** What a tap opens, for a screen reader ("clock"). */
    val label: String,
    /** What a screen reader says instead of the part's text, when the text alone doesn't say it. */
    val description: String? = null,
    /** What a tap opens. Null for a profile, whose tap goes to [profile]. */
    val target: HeaderTarget? = null,
    val profile: Profile? = null,
    /** The battery's level, and whether it is charging. */
    val battery: Int? = null,
    val charging: Boolean = false,
) {
    /** [SCRAMBLE]: home is scrambled (`scramble`); it isn't a part one can choose. */
    enum class Kind { TIME, DATE, ALARM, TODO, NETWORK, UNLOCKS, PROFILE, SETTINGS, BATTERY, SCRAMBLE }
}

/** The header's parts on each side, in their order. */
@Immutable
data class HeaderParts(val left: List<HeaderPart>, val right: List<HeaderPart>) {
    /**
     * These parts with `scramble`'s first on the left, where it always shows: home's log has no
     * real text now, and whoever is about to record the screen sees so.
     */
    fun scrambled(): HeaderParts = copy(left = listOf(SCRAMBLED) + left)

    companion object {
        val SCRAMBLED = HeaderPart(
            HeaderPart.Kind.SCRAMBLE,
            "scrambled",
            Ink.WARN,
            "scramble",
            "scramble",
            description = "Home is scrambled",
        )
    }
}

/** The header's parts as [config] lists them (`header.left`, `header.right`), at [now]. */
fun headerParts(
    status: Status,
    unlocks: Int,
    profiles: List<Profile>,
    todoCount: Int,
    config: Config,
    now: Long,
): HeaderParts = headerParts(
    headerSide(config[Keys.HEADER_LEFT]),
    headerSide(config[Keys.HEADER_RIGHT]),
    config[Keys.CLOCK_24H],
    now,
    status,
    unlocks,
    profiles,
    todoCount,
    config[Keys.USAGE_UNLOCK_LIMIT],
)

/**
 * The parts named in [left] and [right] (`time`, `battery`, …), in their order. A part with
 * nothing to show (no alarm set, no todos, no battery level) is left out, and a part listed on
 * both sides shows only on the left (only `config` can list it on both). [unlockLimit] is the
 * daily unlock limit (`usage.unlock-limit`, 0 for none): the unlocks are in the warning or error
 * ink from it ([UsageLimits]).
 */
fun headerParts(
    left: List<String>,
    right: List<String>,
    clock24: Boolean,
    now: Long,
    status: Status,
    unlocks: Int,
    profiles: List<Profile>,
    todoCount: Int,
    unlockLimit: Int = 0,
    locale: Locale = Locale.getDefault(),
): HeaderParts {
    val formats = HeaderFormats(clock24, locale)
    fun side(kinds: List<String>) =
        kinds.flatMap { partsOf(it, formats, now, status, unlocks, unlockLimit, profiles, todoCount) }
    return HeaderParts(side(left), side(right.filter { it !in left }))
}

/** The header's time formats, made for one call: a format keeps the time zone it was made in. */
private class HeaderFormats(clock24: Boolean, locale: Locale) {
    val time = SimpleDateFormat(TimeFormats.clock(clock24), locale)
    val date = SimpleDateFormat(TimeFormats.DAY, locale)

    // The alarm time has no AM or PM: the user knows their own next alarm, and the line beside a camera is short.
    val alarm = SimpleDateFormat(TimeFormats.lineClock(clock24), locale)
    val day = SimpleDateFormat("EEE ", locale)
}

private fun partsOf(
    kind: String,
    f: HeaderFormats,
    now: Long,
    status: Status,
    unlocks: Int,
    unlockLimit: Int,
    profiles: List<Profile>,
    todoCount: Int,
): List<HeaderPart> = when (kind) {
    "time" -> listOf(
        HeaderPart(
            HeaderPart.Kind.TIME,
            f.time.format(Date(now)),
            Ink.FG,
            "time",
            "clock",
            target = HeaderTarget.CLOCK
        )
    )

    "date" -> listOf(
        HeaderPart(
            HeaderPart.Kind.DATE,
            f.date.format(Date(now)),
            Ink.DIM,
            "date",
            "clock",
            target = HeaderTarget.CLOCK
        )
    )

    "alarm" -> listOfNotNull(status.nextAlarm?.let { at ->
        // If the alarm is a day or more ahead, show its weekday too.
        val text = (if (at - now >= 24 * 3_600_000L) f.day.format(Date(at)) else "") +
                f.alarm.format(Date(at))
        HeaderPart(HeaderPart.Kind.ALARM, text, Ink.FG, "alarm", "alarms", target = HeaderTarget.ALARM)
    })

    "todo" -> if (todoCount > 0) listOf(
        HeaderPart(
            HeaderPart.Kind.TODO,
            "$todoCount",
            Ink.FG,
            "todo",
            "todo",
            target = HeaderTarget.TODO
        )
    ) else emptyList()

    "network" -> listOf(
        HeaderPart(
            HeaderPart.Kind.NETWORK,
            status.network,
            if (status.network == "offline") Ink.ERROR else Ink.FG,
            "network",
            "network",
            description = "network ${status.network}",
            target = HeaderTarget.NETWORK,
        )
    )

    // At or over the daily limit, in the warning or error ink, and a screen reader hears it too.
    "unlocks" -> listOf(
        HeaderPart(
            HeaderPart.Kind.UNLOCKS,
            "$unlocks",
            UsageLimits.role(UsageLimits.unlocks(unlocks, unlockLimit))?.let { Ink.of(it) } ?: Ink.FG,
            "unlocks",
            "unlocks",
            description = UsageLimits.unlockWords(unlocks, unlockLimit)?.let {
                "$unlocks ${if (unlocks == 1) "unlock" else "unlocks"} today, $it"
            },
            target = HeaderTarget.UNLOCKS
        )
    )

    // The work profile and the private space have a part each; the personal and clone profiles have none.
    "profiles" -> profiles.filter { it.kind != ProfileKind.PERSONAL && it.kind != ProfileKind.CLONE }
        .map { p ->
            HeaderPart(
                HeaderPart.Kind.PROFILE,
                p.kind.label,
                if (p.quiet) Ink.DIM else Ink.of(p.kind),
                "profile-${p.kind.label}",
                p.kind.label,
                description = "${p.kind.label} ${if (p.quiet) "off" else "on"}",
                profile = p,
            )
        }

    "settings" -> listOf(
        HeaderPart(
            HeaderPart.Kind.SETTINGS,
            "",
            Ink.DIM,
            "settings",
            "settings",
            description = "Settings",
            target = HeaderTarget.SETTINGS,
        )
    )

    "battery" -> listOfNotNull(status.batteryPct?.let { pct ->
        HeaderPart(
            HeaderPart.Kind.BATTERY,
            "$pct%",
            when {
                status.charging -> Ink.PROMPT
                pct <= 15 -> Ink.ERROR
                else -> Ink.FG
            },
            "battery",
            "battery",
            target = HeaderTarget.BATTERY,
            battery = pct,
            charging = status.charging,
        )
    })

    else -> emptyList()
}
