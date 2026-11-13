package com.mtwabpin.kolahal.control

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.net.toUri
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.productivity.TimeSpec
import com.mtwabpin.kolahal.terminal.CommandGroup
import com.mtwabpin.kolahal.terminal.CommandSpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Media and device-toggle commands (`media`, `torch`, `volume`, `ring`, `dnd`, `brightness`,
 * `rotate`, `wifi`, `bt`, `nfc`, `data`, `screen-timeout`, `toggles`). They live here, outside
 * CommandProcessor, so CommandProcessor needs only one line to call them.
 */
class ControlCommands(context: Context) : CommandGroup(context) {
    private val media = MediaRepository.get(context)
    private val audio by lazy { context.getSystemService(AudioManager::class.java) }
    private val notifications by lazy { context.getSystemService(NotificationManager::class.java) }

    private fun fix(text: String, command: String) { logs.add(LogKind.ERROR, text, ctx = LogContext.of(LogContext.RUN to command)) }

    fun run(name: String, args: List<String>) {
        val a = args.firstOrNull()?.lowercase()
        when (name) {
            "media" -> media(args)
            "mp" -> mediaAction(MediaRepository.Action.TOGGLE)
            "next" -> mediaAction(MediaRepository.Action.NEXT)
            "prev" -> mediaAction(MediaRepository.Action.PREV)
            "torch" -> torch(a)
            "volume", "vol" -> volume(a)
            "ring" -> ring(a)
            "dnd" -> dnd(a)
            "brightness" -> brightness(a)
            "rotate" -> rotate(a)
            "screen-timeout" -> screenTimeout(a)
            "wifi" -> wifi()
            "bt", "bluetooth" -> open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Bluetooth settings")
            "nfc" -> open(
                Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_NFC else Settings.ACTION_NFC_SETTINGS),
                "the NFC panel",
            )
            "data" -> open(
                Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS),
                "the internet panel",
            )
            "toggles" -> toggles()
        }
    }

    // ---- media ------------------------------------------------------------------------------

    private fun needMediaAccess(): Boolean {
        if (media.hasAccess()) return false
        fix("Media control needs notification access — tap to allow it", "notifs enable")
        return true
    }

    private fun media(args: List<String>) {
        val action = when (args.firstOrNull()?.lowercase()) {
            null, "status", "now" -> return nowPlaying()
            "play" -> MediaRepository.Action.PLAY
            "pause" -> MediaRepository.Action.PAUSE
            "toggle", "pp" -> MediaRepository.Action.TOGGLE
            "next", "skip" -> MediaRepository.Action.NEXT
            "prev", "previous", "back" -> MediaRepository.Action.PREV
            "stop" -> MediaRepository.Action.STOP
            else -> return error("Usage: media [play|pause|toggle|next|prev|stop]")
        }
        mediaAction(action)
    }

    private fun nowPlaying() {
        if (needMediaAccess()) return
        media.start()
        val np = media.snapshotNow() ?: return info("Nothing playing")
        val pos = np.positionNow()
        val time = when {
            pos >= 0 && np.duration > 0 -> " · ${ControlArgs.clock(pos)} / ${ControlArgs.clock(np.duration)}"
            pos >= 0 -> " · ${ControlArgs.clock(pos)}"
            else -> ""
        }
        out(
            "♪ ${np.line}",
            LogContext.of(
                LogContext.RUN to "media toggle",
                LogContext.PKG to np.pkg,
                LogContext.COPY to np.line,
                LogContext.DESC to "${np.app} · ${np.stateLabel}$time",
            ) + (if (pos >= 0 && np.duration > 0) LogContext.bar(pos.toDouble() / np.duration) else emptyMap()),
        )
        info("Tap to ${if (np.playing) "pause" else "play"} · media next|prev|stop · mp toggles")
    }

    private fun mediaAction(action: MediaRepository.Action) {
        if (needMediaAccess()) return
        media.start()
        val done = media.send(action) ?: return info("Nothing playing")
        val np = media.nowPlaying.value
        val what = when (done) {
            MediaRepository.Action.PLAY -> "▶ play"
            MediaRepository.Action.PAUSE -> "⏸ pause"
            MediaRepository.Action.NEXT -> "⏭ next"
            MediaRepository.Action.PREV -> "⏮ previous"
            MediaRepository.Action.STOP -> "⏹ stop"
            MediaRepository.Action.TOGGLE -> "⏯"
        }
        info(what + (np?.let { " · ${it.line}" } ?: ""), LogContext.of(LogContext.RUN to "media"))
    }

    // ---- torch ------------------------------------------------------------------------------

    private fun torch(arg: String?) {
        val want = ControlArgs.onOff(arg)
        if (arg != null && want == null && arg != "toggle") return error("Usage: torch [on|off]")
        val t = Torch.get(context)
        val id = t.cameraId ?: return error("No flashlight on this device")
        val on = want ?: !t.isOn()
        runCatching { t.camera.setTorchMode(id, on) }
            .onSuccess { info("Torch ${if (on) "on" else "off"}", LogContext.of(LogContext.RUN to "torch")) }
            .onFailure { error("Torch: ${it.message ?: "camera in use"}") }
    }

    /** Torch state from [CameraManager.TorchCallback]. The callback is registered on first use. */
    private class Torch(context: Context) {
        val camera: CameraManager = context.getSystemService(CameraManager::class.java)
        val cameraId: String? = runCatching {
            camera.cameraIdList.filter {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }.minByOrNull {
                if (camera.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) 0 else 1
            }
        }.getOrNull()

        @Volatile private var on: Boolean? = null
        private val first = CountDownLatch(1)

        init {
            if (cameraId != null) {
                camera.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(id: String, enabled: Boolean) {
                        if (id == cameraId) { on = enabled; first.countDown() }
                    }
                    override fun onTorchModeUnavailable(id: String) {
                        if (id == cameraId) { on = false; first.countDown() }
                    }
                }, Handler(Looper.getMainLooper()))
            }
        }

        /** The callback reports the current state right after it is registered, so wait briefly for it. */
        fun isOn(): Boolean {
            if (on == null && Looper.myLooper() != Looper.getMainLooper()) first.await(500, TimeUnit.MILLISECONDS)
            return on == true
        }

        fun known(): Boolean? = on

        companion object : Singleton<Torch>(::Torch)
    }

    // ---- sound ------------------------------------------------------------------------------

    /** "Volume 40%" (or "Volume muted"), with the level as a bar after it. */
    private fun volumeLine(): Pair<String, LogContext> {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        val pct = ControlArgs.toPercent(cur, max)
        return "Volume ${if (muted) "muted" else "$pct%"}" to LogContext(LogContext.bar(if (muted) 0.0 else pct / 100.0))
    }

    private fun volume(arg: String?) {
        val level = ControlArgs.volume(arg)
        if (arg != null && level == null) return error("Usage: volume [0-100|up|down|mute|unmute]")
        val s = AudioManager.STREAM_MUSIC
        val flags = AudioManager.FLAG_SHOW_UI
        runCatching {
            when (level) {
                null -> Unit
                is ControlArgs.Level.Percent -> audio.setStreamVolume(s, ControlArgs.toSteps(level.pct, audio.getStreamMaxVolume(s)), flags)
                ControlArgs.Level.Up -> audio.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, flags)
                ControlArgs.Level.Down -> audio.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, flags)
                ControlArgs.Level.Mute -> audio.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, flags)
                ControlArgs.Level.Unmute -> audio.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, flags)
                ControlArgs.Level.Auto -> Unit
            }
        }.onFailure { return error("Volume: ${it.message ?: it.javaClass.simpleName}") }
        val (line, bar) = volumeLine()
        out(line, bar + mapOf(LogContext.COMMAND to "volume "))
    }

    private fun ringLabel() = when (audio.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> "silent"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        else -> "normal"
    }

    private fun hasPolicyAccess() = notifications.isNotificationPolicyAccessGranted

    private fun needPolicyAccess(what: String): Boolean {
        if (hasPolicyAccess()) return false
        fix("$what needs Do Not Disturb access — tap to allow it", "dnd access")
        return true
    }

    private fun ring(arg: String?) {
        val mode = when (arg) {
            null -> return out("Ringer ${ringLabel()}", LogContext.of(LogContext.COMMAND to "ring "))
            "normal", "on", "sound" -> AudioManager.RINGER_MODE_NORMAL
            "vibrate", "vib" -> AudioManager.RINGER_MODE_VIBRATE
            "silent", "off", "mute" -> AudioManager.RINGER_MODE_SILENT
            else -> return error("Usage: ring [normal|vibrate|silent]")
        }
        // Entering or leaving silent changes Do Not Disturb, which needs policy access.
        if ((mode == AudioManager.RINGER_MODE_SILENT || audio.ringerMode == AudioManager.RINGER_MODE_SILENT) &&
            needPolicyAccess("ring silent")
        ) return
        runCatching { audio.ringerMode = mode }
            .onSuccess { info("Ringer ${ringLabel()}") }
            .onFailure { if (it is SecurityException) needPolicyAccess("Changing the ringer") else error("Ring: ${it.message}") }
    }

    private fun dndLabel(filter: Int = notifications.currentInterruptionFilter) = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "on (priority only)"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "on (alarms only)"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "on (total silence)"
        else -> "unknown"
    }

    private fun dnd(arg: String?) {
        if (arg == "access") return open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "Do Not Disturb access")
        if (arg == "status") return out("Do Not Disturb ${shown(dndLabel())}", LogContext.of(LogContext.RUN to "dnd"))
        val filter = when (arg) {
            null, "toggle" -> if (notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL)
                NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
            "on", "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            "none", "total", "silence" -> NotificationManager.INTERRUPTION_FILTER_NONE
            "off" -> NotificationManager.INTERRUPTION_FILTER_ALL
            else -> return error("Usage: dnd [on|off|alarms|none|status|access]")
        }
        if (needPolicyAccess("dnd")) return
        runCatching { notifications.setInterruptionFilter(filter) }
            .onSuccess {
                // Since Android 15, Kolahal can turn off only the Do Not Disturb that it turned on.
                // Do Not Disturb turned on elsewhere (quick settings, a schedule, another app) stays on.
                if (filter == NotificationManager.INTERRUPTION_FILTER_ALL && !turnsOff()) {
                    error("Do Not Disturb is still on — it was turned on outside Kolahal, so turn it off there", LogContext.of(LogContext.RUN to "dnd"))
                } else {
                    info("Do Not Disturb ${dndLabel(filter)}", LogContext.of(LogContext.RUN to "dnd"))
                }
            }
            .onFailure { error("Do Not Disturb: ${it.message}") }
    }

    /** True when Do Not Disturb turns off within a second. Android applies a change a moment after the request. */
    private fun turnsOff(): Boolean {
        val until = System.currentTimeMillis() + DND_SETTLE_MS
        while (notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            if (System.currentTimeMillis() >= until) return false
            Thread.sleep(50)
        }
        return true
    }

    // ---- system settings (WRITE_SETTINGS) -------------------------------------------------

    private fun canWrite() = Settings.System.canWrite(context)

    private fun needWrite(what: String): Boolean {
        if (canWrite()) return false
        fix("$what needs 'modify system settings' — tap to allow it", "brightness access")
        return true
    }

    private fun writeSettingsIntent() =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, ("package:" + context.packageName).toUri())

    private val resolver get() = context.contentResolver

    private fun brightnessAuto() = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    private fun brightnessPct() = ControlArgs.toPercent(Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128), 255)

    private fun brightnessShort() = "${brightnessPct()}%" + if (brightnessAuto()) " · auto" else ""

    /** "Brightness 50% · auto", with the level as a bar after it. */
    private fun brightnessLine() = "Brightness ${brightnessShort()}" to LogContext(LogContext.bar(brightnessPct() / 100.0))

    private fun brightness(arg: String?) {
        if (arg == "access") return open(writeSettingsIntent(), "modify system settings")
        val level = ControlArgs.brightness(arg)
        if (arg != null && level == null) return error("Usage: brightness [0-100|up|down|auto]")
        if (level != null) {
            if (needWrite("brightness")) return
            val mode = Settings.System.SCREEN_BRIGHTNESS_MODE
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val target = when (level) {
                is ControlArgs.Level.Percent -> ControlArgs.toSteps(level.pct, 255)
                ControlArgs.Level.Up -> (current + 26).coerceAtMost(255)
                ControlArgs.Level.Down -> (current - 26).coerceAtLeast(1)
                else -> null
            }
            if (target == null) {
                Settings.System.putInt(resolver, mode, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            } else {
                Settings.System.putInt(resolver, mode, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target.coerceAtLeast(1))
            }
        }
        val (line, bar) = brightnessLine()
        out(line, bar + mapOf(LogContext.COMMAND to "brightness "))
    }

    private fun rotationOn() = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun rotate(arg: String?) {
        if (arg == "status") return out("Auto-rotate ${shown(if (rotationOn()) "on" else "off")}", LogContext.of(LogContext.RUN to "rotate"))
        val want = ControlArgs.onOff(arg)
        if (arg != null && want == null && arg != "toggle") return error("Usage: rotate [on|off|status]")
        if (needWrite("rotate")) return
        val on = want ?: !rotationOn()
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
        info("Auto-rotate ${if (on) "on" else "off"}", LogContext.of(LogContext.RUN to "rotate"))
    }

    private fun timeoutMs() = Settings.System.getLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000L)

    private fun screenTimeout(arg: String?) {
        if (arg == null) return out("Screen timeout ${TimeSpec.formatDuration(timeoutMs() / 1000)}", LogContext.of(LogContext.COMMAND to "screen-timeout "))
        val ms = ControlArgs.duration(arg) ?: return error("Usage: screen-timeout <15s|30s|1m|2m|5m|10m|30m>")
        if (needWrite("screen-timeout")) return
        Settings.System.putLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT, ms.coerceAtMost(Int.MAX_VALUE.toLong()))
        info("Screen timeout ${TimeSpec.formatDuration(ms / 1000)}")
    }

    // ---- radios: Android lets apps only open the panel or settings page --------------------

    private fun wifi() = open(
        Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS),
        "the Wi-Fi panel",
    )

    private fun open(intent: Intent, what: String) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onSuccess { info("Opened $what") }
            .onFailure { error("Can't open $what: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun wifiOn(): Boolean? = runCatching {
        context.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled
    }.getOrNull()

    private fun btOn(): Boolean? = runCatching { Settings.Global.getInt(resolver, Settings.Global.BLUETOOTH_ON) != 0 }.getOrNull()

    private fun nfcOn(): Boolean? = runCatching { NfcAdapter.getDefaultAdapter(context)?.isEnabled }.getOrNull()

    private fun dataOn(): Boolean? = runCatching { Settings.Global.getInt(resolver, "mobile_data") != 0 }.getOrNull()

    private fun word(b: Boolean?) = when (b) {
        true -> "on"
        false -> "off"
        null -> "?"
    }

    /** Shows one line per toggle. A tap on a line toggles it, or opens its panel. */
    /** "on" and "off" as tags, in the good and muted colours, so a state reads at a glance. Anything else stays as it is. */
    private fun shown(value: String) = when {
        value == "on" || value.startsWith("on ") -> StyledText.tag(StyledText.Colour.GOOD, "on") + value.removePrefix("on")
        value == "off" -> StyledText.tag(StyledText.Colour.MUTED, "off")
        else -> value
    }

    private fun toggles() {
        fun row(name: String, value: String, run: String, hint: String, extra: Map<String, String> = emptyMap()) =
            out(name.padEnd(15) + shown(value), LogContext.of(LogContext.RUN to run, LogContext.DESC to hint) + LogContext.ALIGNED + extra)

        val torch = Torch.get(context)
        if (torch.cameraId != null) {
            torch.isOn()
            row("Torch", word(torch.known()), "torch", "Tap to toggle")
        }
        row("Wi-Fi", word(wifiOn()), "wifi", "Tap for the Wi-Fi panel")
        row("Bluetooth", word(btOn()), "bt", "Tap for Bluetooth settings")
        if (NfcAdapter.getDefaultAdapter(context) != null) row("NFC", word(nfcOn()), "nfc", "Tap for the NFC panel")
        row("Mobile data", word(dataOn()), "data", "Tap for the internet panel")
        val policy = hasPolicyAccess()
        row("Do Not Disturb", dndLabel(), if (policy) "dnd" else "dnd access", if (policy) "Tap to toggle" else "Tap to grant access")
        row("Ringer", ringLabel(), "ring " + if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) "vibrate" else "normal", "Tap to cycle normal / vibrate")
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volume = ControlArgs.toPercent(audio.getStreamVolume(AudioManager.STREAM_MUSIC), max)
        row("Volume", "$volume%", "volume mute", "Tap to mute · volume <0-100>", LogContext.bar(volume / 100.0))
        val write = canWrite()
        row(
            "Auto-rotate", if (rotationOn()) "on" else "off", if (write) "rotate" else "brightness access",
            if (write) "Tap to toggle" else "Tap to allow modifying system settings",
        )
        row("Brightness", brightnessShort(), if (write) "brightness auto" else "brightness access", "Tap for auto · brightness <0-100>", LogContext.bar(brightnessPct() / 100.0))
        row("Screen timeout", TimeSpec.formatDuration(timeoutMs() / 1000), "screen-timeout", "Set with screen-timeout <30s|1m|…>")
        media.nowPlaying.value?.let { row("Media", "${it.stateLabel} · ${it.line}", "media toggle", "Tap to play / pause") }
    }

    companion object {
        /** How long `dnd off` waits for Android to apply it, before it reports that DND stayed on. */
        private const val DND_SETTLE_MS = 1_000L

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
}
