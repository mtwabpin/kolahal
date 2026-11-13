package com.mtwabpin.kolahal.commands

/**
 * The device and media commands (`torch`, `dnd`, `media`, …): their specs, names, which are
 * sensitive, and their argument completions. [com.mtwabpin.kolahal.terminal.ControlCommands] runs them.
 */
object ControlSpecs {
    val SPECS = listOf(
        CommandSpec(
            "media", "media [play|pause|toggle|next|prev|stop]", "Now playing, or control it (needs notification access)",
            short = "Now playing", args = "play · pause · toggle · next · prev · stop",
            uses = listOf(
                "media" to "what is playing now",
                "media play" to "play",
                "media pause" to "pause",
                "media toggle" to "play or pause",
                "media next" to "the next track",
                "media prev" to "the previous track",
                "media stop" to "stop",
            ),
        ),
        CommandSpec("mp", "mp", "Play / pause media", short = "Play or pause"),
        CommandSpec("next", "next", "Next track", short = "Next track"),
        CommandSpec("prev", "prev", "Previous track", short = "Previous track"),
        CommandSpec("torch", "torch [on|off]", "Flashlight (no args toggles)", short = "Flashlight", args = "on · off"),
        CommandSpec(
            "volume", "volume [0-100|up|down|mute|unmute]", "Media volume",
            short = "Media volume", args = "0–100 · up · down · mute · unmute",
            uses = listOf(
                "volume" to "the media volume now",
                "volume <0-100>" to "set it, in percent",
                "volume up" to "one step louder",
                "volume down" to "one step quieter",
                "volume mute" to "mute it",
                "volume unmute" to "unmute it",
            ),
            aka = listOf("vol"),
        ),
        CommandSpec("ring", "ring [normal|vibrate|silent]", "Ringer mode (silent needs DND access)", short = "Ringer mode", args = "normal · vibrate · silent"),
        CommandSpec(
            "dnd", "dnd [on|off|alarms|none|status|access]", "Do Not Disturb (no args toggles)",
            short = "Do Not Disturb", args = "on · off · alarms · none · status · access",
            uses = listOf(
                "dnd" to "turn it on or off",
                "dnd on" to "priority only",
                "dnd alarms" to "alarms only",
                "dnd none" to "total silence",
                "dnd off" to "turn it off",
                "dnd status" to "whether it is on",
                "dnd access" to "allow Do Not Disturb access",
            ),
        ),
        CommandSpec(
            "brightness", "brightness [0-100|up|down|auto|access]", "Screen brightness (needs modify system settings)",
            short = "Screen brightness", args = "0–100 · up · down · auto · access",
            uses = listOf(
                "brightness" to "the brightness now",
                "brightness <0-100>" to "set it, in percent",
                "brightness up" to "about 10% brighter",
                "brightness down" to "about 10% darker",
                "brightness auto" to "adaptive brightness",
                "brightness access" to "allow changing system settings",
            ),
        ),
        CommandSpec("rotate", "rotate [on|off|status]", "Auto-rotate (no args toggles)", short = "Auto-rotate", args = "on · off · status"),
        CommandSpec("screen-timeout", "screen-timeout [15s|30s|1m|2m|5m|10m|30m]", "Screen timeout", short = "Screen timeout", args = "15s · 30s · 1m · 2m · 5m · 10m · 30m"),
        CommandSpec("wifi", "wifi", "Wi-Fi panel", short = "Wi-Fi panel", sensitive = true),
        CommandSpec("bt", "bt", "Bluetooth settings", short = "Bluetooth settings", aka = listOf("bluetooth"), sensitive = true),
        CommandSpec("nfc", "nfc", "NFC panel", short = "NFC panel", sensitive = true),
        CommandSpec("data", "data", "Internet / mobile data panel", short = "Mobile data panel", sensitive = true),
        CommandSpec("toggles", "toggles", "Status of every toggle (tap one to change it)", short = "Every toggle at a glance"),
    )

    /** Command names this class handles, including aliases that help doesn't list. */
    val NAMES = SPECS.map { it.name }.toSet() + setOf("vol", "bluetooth")

    /**
     * Commands that change device or media state. A prefix never runs them. They run only as a
     * whole line, such as `torch on` (InstantRun).
     */
    val SENSITIVE = setOf("media", "mp", "next", "prev", "torch", "volume", "vol", "ring", "dnd", "brightness", "rotate", "screen-timeout")

    private val ARGS = mapOf(
        "media" to listOf("play", "pause", "toggle", "next", "prev", "stop"),
        "torch" to listOf("on", "off"),
        "rotate" to listOf("on", "off", "status"),
        "dnd" to listOf("on", "off", "alarms", "none", "status", "access"),
        "ring" to listOf("normal", "vibrate", "silent"),
        "volume" to listOf("up", "down", "mute", "unmute", "0", "25", "50", "75", "100"),
        "vol" to listOf("up", "down", "mute", "unmute"),
        "brightness" to listOf("auto", "up", "down", "25", "50", "75", "100", "access"),
        "screen-timeout" to listOf("15s", "30s", "1m", "2m", "5m", "10m", "30m"),
    )

    /** Argument completions for the suggestion strip, as (label, replacement line) pairs. */
    fun suggest(head: String, arg: String): List<Pair<String, String>> =
        ARGS[head].orEmpty().filter { it.startsWith(arg, ignoreCase = true) && !it.equals(arg, ignoreCase = true) }.map { it to "$head $it" }
}
