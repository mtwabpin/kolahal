package com.mtwabpin.kolahal.control

import android.app.NotificationManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.NotificationAccess
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The device commands: what each says, what it changes, and what it asks for first. Everything goes back as it was. */
@RunWith(AndroidJUnit4::class)
class ControlCommandsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val resolver = context.contentResolver
    private val pkg = context.packageName

    @get:Rule val logLines = LogLinesSince()
    @get:Rule val access = NotificationAccess()
    private val commands = CommandRunner(context)

    // As they were, put back after.
    private var volume = 0
    private var muted = false
    private var ringer = 0
    private var policy = false
    private var brightness = 0
    private var brightnessMode = 0
    private var rotation = 0
    private var timeout = 0L
    private var writeMode = "default"

    @Before fun setUp() {
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        ringer = audio.ringerMode
        policy = notifications.isNotificationPolicyAccessGranted
        brightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        brightnessMode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        rotation = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        timeout = Settings.System.getLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000L)
        writeMode = Regex("WRITE_SETTINGS: (\\w+)").find(shell("appops get $pkg WRITE_SETTINGS"))?.groupValues?.get(1) ?: "default"
    }

    @After fun tearDown() {
        // Settings first, while the app may still write them.
        shell("appops set $pkg WRITE_SETTINGS allow")
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, brightnessMode)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, rotation)
        Settings.System.putLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT, timeout)
        shell("appops set $pkg WRITE_SETTINGS $writeMode")
        shell("cmd notification allow_dnd $pkg")
        // Off, not as it was: one left on by an earlier run would become Kolahal's own, which
        // the shell can't turn off.
        shell("cmd notification set_dnd off")
        runCatching { notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        runCatching { audio.ringerMode = ringer }
        if (!policy) shell("cmd notification disallow_dnd $pkg")
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
    }

    private fun lines(line: String) = commands.lines(line)
    private fun run(line: String) = commands.run(line)

    @Test fun volumeSetsAndSaysTheLevel() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val said = run("volume 40").single()
        assertEquals(ControlArgs.toSteps(40, max), audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertTrue(said, said.startsWith("Volume ") && said.endsWith("${ControlArgs.toPercent(ControlArgs.toSteps(40, max), max)}%"))
        run("volume up")
        assertEquals(ControlArgs.toSteps(40, max) + 1, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        run("volume down")
        assertEquals(ControlArgs.toSteps(40, max), audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertTrue(run("volume mute").single().endsWith("muted"))
        assertTrue(run("volume unmute").single().endsWith("%"))
        assertTrue(run("volume loud").single().startsWith("Usage: volume"))
    }

    /** Silent touches Do Not Disturb, so it asks for that access first; vibrate doesn't. */
    @Test fun theRingerAsksBeforeSilent() {
        shell("cmd notification allow_dnd $pkg")
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        shell("cmd notification disallow_dnd $pkg")
        assertEquals(listOf("Ringer normal"), run("ring"))
        assertEquals(listOf("Ringer vibrate"), run("ring vibrate"))
        val asked = lines("ring silent").single()
        assertEquals("ring silent needs Do Not Disturb access — tap to allow it", asked.text)
        assertEquals("dnd access", asked.ctx.values[LogContext.RUN])
        shell("cmd notification allow_dnd $pkg")
        assertEquals(listOf("Ringer silent"), run("ring silent"))
        assertEquals(listOf("Ringer normal"), run("ring normal"))
        assertTrue(run("ring loudly").single().startsWith("Usage: ring"))
    }

    @Test fun doNotDisturbEachWay() {
        shell("cmd notification disallow_dnd $pkg")
        assertEquals("dnd needs Do Not Disturb access — tap to allow it", run("dnd on").single())
        shell("cmd notification allow_dnd $pkg")
        dndOff()
        assertEquals(listOf("Do Not Disturb off"), run("dnd status").map(com.mtwabpin.kolahal.data.StyledText::plain))
        for ((arg, said) in listOf("on" to "on (priority only)", "alarms" to "on (alarms only)", "none" to "on (total silence)", "off" to "off")) {
            assertEquals(arg, listOf("Do Not Disturb $said"), run("dnd $arg"))
        }
        // From off, toggling turns it on (priority), and back.
        assertEquals(listOf("Do Not Disturb on (priority only)"), run("dnd"))
        assertEquals(listOf("Do Not Disturb off"), run("dnd toggle"))
        assertTrue(run("dnd maybe").single().startsWith("Usage: dnd"))
    }

    /** Since Android 15, Do Not Disturb turned on outside Kolahal stays on: `dnd off` says so, not "off". */
    @Test fun dndOffSaysWhenItStaysOn() {
        assumeTrue("Android 15 and later keep the user's own", Build.VERSION.SDK_INT >= 35)
        shell("cmd notification allow_dnd $pkg")
        dndOff()
        try {
            shell("cmd notification set_dnd priority")
            waitUntil("on from outside") { notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY }
            val said = lines("dnd off").single()
            assertEquals(LogKind.ERROR, said.kind)
            assertEquals("Do Not Disturb is still on — it was turned on outside Kolahal, so turn it off there", said.text)
            // Toggling from on tries off, and says the same.
            assertEquals(listOf(said.text), run("dnd"))
        } finally {
            dndOff()
        }
    }

    /** Off both ways: the shell's (yours) and Kolahal's own, which since Android 15 is a mode of its own. */
    private fun dndOff() {
        shell("cmd notification set_dnd off")
        runCatching { notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        waitUntil("Do Not Disturb off") { notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL }
    }

    /** Brightness, auto-rotate and the screen timeout need "modify system settings", and say so without it. */
    @Test fun systemSettingsAskForAccessThenChange() {
        shell("appops set $pkg WRITE_SETTINGS deny")
        for ((line, what) in listOf("brightness 50" to "brightness", "rotate on" to "rotate", "screen-timeout 2m" to "screen-timeout")) {
            val asked = lines(line).single()
            assertEquals(line, "$what needs 'modify system settings' — tap to allow it", asked.text)
            assertEquals("brightness access", asked.ctx.values[LogContext.RUN])
        }
        shell("appops set $pkg WRITE_SETTINGS allow")
        assertTrue(run("brightness 50").single().endsWith(" 50%"))
        assertEquals(ControlArgs.toSteps(50, 255), Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS))
        assertTrue(run("brightness auto").single().endsWith(" · auto"))
        assertEquals(listOf("Usage: brightness [0-100|up|down|auto|access]"), run("brightness loud"))
        assertEquals(listOf("Auto-rotate on"), run("rotate on"))
        assertEquals(listOf("Auto-rotate on"), run("rotate status").map(com.mtwabpin.kolahal.data.StyledText::plain))
        assertEquals(listOf("Auto-rotate off"), run("rotate"))
        assertTrue(run("rotate sideways").single().startsWith("Usage: rotate"))
        assertEquals(listOf("Screen timeout 2m"), run("screen-timeout 2m"))
        assertEquals(120_000L, Settings.System.getLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT))
        assertEquals(listOf("Screen timeout 2m"), run("screen-timeout"))
        assertEquals(listOf("Usage: screen-timeout [15s|30s|1m|2m|5m|10m|30m]"), run("screen-timeout forever"))
    }

    /** The emulator may have no flashlight: either way the command says what happened, never fails. */
    @Test fun theTorchSaysWhatItDid() {
        val said = run("torch on").single()
        assertTrue(said, said == "Torch on" || said == "No flashlight on this device" || said.startsWith("Torch: "))
        if (said == "Torch on") assertEquals(listOf("Torch off"), run("torch off"))
        assertEquals(listOf("Usage: torch [on|off]"), run("torch maybe"))
    }

    /** Without notification access there's no media session to control: it says how to allow it. */
    @Test fun mediaNeedsNotificationAccess() {
        access.set(false)
        for (line in listOf("media", "mp", "next")) {
            val asked = lines(line).single()
            assertEquals(line, "Media control needs notification access — tap to allow it", asked.text)
            assertEquals("notifs enable", asked.ctx.values[LogContext.RUN])
        }
        assertTrue(run("media loudly").single().startsWith("Usage: media"))
    }

    /** One line per toggle, each with what a tap does. */
    @Test fun togglesListsEachOne() {
        val rows = lines("toggles")
        val names = rows.map { it.text.substring(0, 15).trim() }
        for (name in listOf("Wi-Fi", "Bluetooth", "Mobile data", "Do Not Disturb", "Ringer", "Volume", "Auto-rotate", "Brightness", "Screen timeout")) {
            assertTrue("$name: $names", name in names)
        }
        assertTrue(rows.all { it.ctx.values[LogContext.RUN] != null && it.ctx.values[LogContext.DESC] != null })
    }
}
