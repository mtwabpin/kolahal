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
import com.mtwabpin.kolahal.productivity.TimeSpec
import com.mtwabpin.kolahal.terminal.CommandGroup
import com.mtwabpin.kolahal.terminal.CommandSpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Media and device-toggle commands (`media`, `torch`, `volume`, `ring`, `dnd`, `brightness`,
 * `rotate`, `wifi`, `bt`, `nfc`, `data`, `screen-timeout`, `toggles`). Kept out of
 * CommandProcessor so it stays a one-line dispatch there.
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
            ),
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

    /** Torch state from [CameraManager.TorchCallback]; registered on first use. */
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

        /** The callback reports the current state right after registering; wait briefly for it. */
        fun isOn(): Boolean {
            if (on == null && Looper.myLooper() != Looper.getMainLooper()) first.await(500, TimeUnit.MILLISECONDS)
            return on == true
        }

        fun known(): Boolean? = on

        companion object {
            @Volatile private var instance: Torch? = null
            fun get(context: Context): Torch = instance ?: synchronized(this) {
                instance ?: Torch(context.applicationContext).also { instance = it }
            }
        }
    }

    // ---- sound ------------------------------------------------------------------------------

    private fun volumeLine(): String {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        val pct = ControlArgs.toPercent(cur, max)
        return "Volume ${ControlArgs.bar(if (muted) 0 else pct)} ${if (muted) "muted" else "$pct%"}"
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
        out(volumeLine(), LogContext.of(LogContext.COMMAND to "volume "))
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
        if (arg == "status") return out("Do Not Disturb ${dndLabel()}", LogContext.of(LogContext.RUN to "dnd"))
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
                // Since Android 15, Kolahal turns off only its own Do Not Disturb: one turned on
                // elsewhere (quick settings, a schedule, another app) stays on.
                if (filter == NotificationManager.INTERRUPTION_FILTER_ALL && !turnsOff()) {
                    error("Do Not Disturb is still on: it was turned on outside Kolahal, so turn it off there", LogContext.of(LogContext.RUN to "dnd"))
                } else {
                    info("Do Not Disturb ${dndLabel(filter)}", LogContext.of(LogContext.RUN to "dnd"))
                }
            }
            .onFailure { error("Do Not Disturb: ${it.message}") }
    }

    /** Whether Do Not Disturb is off within a second: Android applies a change a moment after it's asked. */
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

    private fun brightnessLine() = "Brightness ${ControlArgs.bar(brightnessPct())} ${brightnessShort()}"

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
        out(brightnessLine(), LogContext.of(LogContext.COMMAND to "brightness "))
    }

    private fun rotationOn() = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun rotate(arg: String?) {
        if (arg == "status") return out("Auto-rotate ${if (rotationOn()) "on" else "off"}", LogContext.of(LogContext.RUN to "rotate"))
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

    // ---- radios: Android only lets apps open the panel / settings page ---------------------

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

    /** One line per toggle; tapping a line toggles it (or opens its panel). */
    private fun toggles() {
        fun row(name: String, value: String, run: String, hint: String) =
            out(name.padEnd(15) + value, LogContext.of(LogContext.RUN to run, LogContext.DESC to hint) + LogContext.ALIGNED)

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
        row("Volume", "${ControlArgs.toPercent(audio.getStreamVolume(AudioManager.STREAM_MUSIC), max)}%", "volume mute", "Tap to mute · volume <0-100>")
        val write = canWrite()
        row(
            "Auto-rotate", if (rotationOn()) "on" else "off", if (write) "rotate" else "brightness access",
            if (write) "Tap to toggle" else "Tap to allow modifying system settings",
        )
        row("Brightness", brightnessShort(), if (write) "brightness auto" else "brightness access", "Tap for auto · brightness <0-100>")
        row("Screen timeout", TimeSpec.formatDuration(timeoutMs() / 1000), "screen-timeout", "Set with screen-timeout <30s|1m|…>")
        media.nowPlaying.value?.let { row("Media", "${it.stateLabel} · ${it.line}", "media toggle", "Tap to play / pause") }
    }

    companion object {
        /** How long `dnd off` waits for Android to apply it before saying it stayed on. */
        private const val DND_SETTLE_MS = 1_000L

        val SPECS = listOf(
            CommandSpec("media", "media [play|pause|toggle|next|prev|stop]", "Now playing, or control it (needs notification access)"),
            CommandSpec("mp", "mp", "Play / pause media"),
            CommandSpec("next", "next", "Next track"),
            CommandSpec("prev", "prev", "Previous track"),
            CommandSpec("torch", "torch [on|off]", "Flashlight (no args toggles)"),
            CommandSpec("volume", "volume [0-100|up|down|mute|unmute]", "Media volume"),
            CommandSpec("ring", "ring [normal|vibrate|silent]", "Ringer mode (silent needs DND access)"),
            CommandSpec("dnd", "dnd [on|off|alarms|none|status|access]", "Do Not Disturb (no args toggles)"),
            CommandSpec("brightness", "brightness [0-100|up|down|auto|access]", "Screen brightness (needs modify system settings)"),
            CommandSpec("rotate", "rotate [on|off|status]", "Auto-rotate (no args toggles)"),
            CommandSpec("screen-timeout", "screen-timeout [15s|30s|1m|2m|5m|10m|30m]", "Screen timeout"),
            CommandSpec("wifi", "wifi", "Wi-Fi panel"),
            CommandSpec("bt", "bt", "Bluetooth settings"),
            CommandSpec("nfc", "nfc", "NFC panel"),
            CommandSpec("data", "data", "Internet / mobile data panel"),
            CommandSpec("toggles", "toggles", "Status of every toggle (tap one to change it)"),
        )

        /** Names this class handles (including aliases not listed in help). */
        val NAMES = SPECS.map { it.name }.toSet() + setOf("vol", "bluetooth")

        /** Change device or media state: never run by a prefix, only as a whole line such as `torch on` (InstantRun). */
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

        /** Argument completions for the suggestion strip: (label, replacement line). */
        fun suggest(head: String, arg: String): List<Pair<String, String>> =
            ARGS[head].orEmpty().filter { it.startsWith(arg, ignoreCase = true) && !it.equals(arg, ignoreCase = true) }.map { it to "$head $it" }
    }
}
