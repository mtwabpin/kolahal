package com.mtwabpin.kolahal.shader

import android.view.WindowManager
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.commands.Accesses
import com.mtwabpin.kolahal.commands.BuildFeature
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.SettingTitles
import com.mtwabpin.kolahal.sourceFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A one-off shader over the app in front ([ShaderOverApps]): when it plays there, its window, and the build that has none. */
class ShaderOverAppsTest {
    private val phone = ShaderClock.Phone(batterySaver = false, animationsOff = false, hot = false, charging = false, batteryPercent = 80)

    /** Everything says yes. */
    private val yes = ShaderOverApps.Now(
        setting = true, build = true, access = true, sdk = 33, fit = ShaderFit.GOOD, kolahalInFront = false,
        screenOn = true, locked = false, heldBack = false, statusBar = true, screenReader = false, phone = phone,
    )

    private fun asset(name: String) = sourceFile("src/main/assets/shaders/$name.agsl").readText()

    private val overlay = sourceFile("src/main/java/com/mtwabpin/kolahal/ui/shaders/ShaderOverlay.kt").readText()
    private val model = sourceFile("src/main/java/com/mtwabpin/kolahal/shader/ShaderOverApps.kt").readText()

    /** It plays over an app only when every input says yes: each one flipped alone keeps it for home. */
    @Test
    fun everyInputMustSayYes() {
        assertTrue(ShaderOverApps.plays(yes))
        val no = mapOf(
            "the setting is off" to yes.copy(setting = false),
            "the build has none" to yes.copy(build = false),
            "no access" to yes.copy(access = false),
            "Android 12" to yes.copy(sdk = 32),
            "not rated" to yes.copy(fit = null),
            "rated poor" to yes.copy(fit = ShaderFit.POOR),
            "Kolahal in front" to yes.copy(kolahalInFront = true),
            "the screen is off" to yes.copy(screenOn = false),
            "locked" to yes.copy(locked = true),
            "Do Not Disturb holds it back" to yes.copy(heldBack = true),
            "a full-screen app" to yes.copy(statusBar = false),
            "a screen reader" to yes.copy(screenReader = true),
            "battery saver" to yes.copy(phone = phone.copy(batterySaver = true)),
            "animations off" to yes.copy(phone = phone.copy(animationsOff = true)),
            "a hot phone" to yes.copy(phone = phone.copy(hot = true)),
            "a nearly empty battery" to yes.copy(phone = phone.copy(batteryPercent = ShaderClock.LOW_BATTERY - 1)),
        )
        for ((why, now) in no) assertFalse(why, ShaderOverApps.plays(now))
        // What says yes as well: a fair rating, a newer Android, and a nearly empty battery on the charger, as on home.
        assertTrue(ShaderOverApps.plays(yes.copy(fit = ShaderFit.FAIR)))
        assertTrue(ShaderOverApps.plays(yes.copy(sdk = 36)))
        assertTrue(ShaderOverApps.plays(yes.copy(phone = phone.copy(batteryPercent = 5, charging = true))))
        // What home asks of the phone before a one-off is what is asked here.
        assertEquals(ShaderClock.moves(phone, 0L), ShaderOverApps.plays(yes))
    }

    /** Only a one-off whose header rates it for other apps plays there: hearts does, pulse bends the page under it and waits for home. */
    @Test
    fun aShaderNotRatedForAppsWaitsForHome() {
        val hearts = ShaderMeta.parse(asset("hearts"))
        val pulse = ShaderMeta.parse(asset("pulse"))
        assertEquals(ShaderFit.GOOD, ShaderOverApps.fit(hearts))
        assertNull(ShaderOverApps.fit(pulse))
        assertTrue(ShaderOverApps.plays(yes.copy(fit = ShaderOverApps.fit(hearts))))
        assertFalse(ShaderOverApps.plays(yes.copy(fit = ShaderOverApps.fit(pulse))))
        // Of the shaders that come with Kolahal, the hearts alone.
        val rated = ShaderCatalog.BUNDLED.filter { ShaderOverApps.fit(ShaderMeta.parse(asset(it))) != null }
        assertEquals(
            listOf("hearts", "hearts-fountain", "hearts-falling", "hearts-stream", "hearts-poppers", "hearts-fireworks", "hearts-bounce"),
            rated,
        )
        // A rating is a one-off's: a shader that stays on has none, whatever its header says.
        assertNull(ShaderOverApps.fit(ShaderMeta.parse("// @suits apps good\nuniform shader content;")))
        assertEquals(ShaderFit.FAIR, ShaderOverApps.fit(ShaderMeta.parse("// @suits apps fair\n// @once 2\nuniform shader content;")))
        assertNull(ShaderOverApps.fit(ShaderMeta.parse("// @suits dark good\n// @once 2\nuniform shader content;")))
    }

    /**
     * On a clear ground hearts returns the hearts alone: what it read, which is nothing there, and its own color over it.
     * It reads `dark` for a heart's own color alone: the Neon style glows differently on a light theme. `dark` says which
     * theme it is, nothing of the page or the app under it, so it is read in heartLook and nowhere else.
     */
    @Test
    fun heartsDrawsNothingButHearts() {
        val code = asset("hearts").replace(Regex("//[^\n]*"), "")
        assertTrue("return half4(here * (1.0 - cover) + float4(color, cover));" in code)
        assertTrue("if (progress <= 0.0 || progress >= 1.0) return half4(here);" in code)
        // No color of the page, and nothing mixed into what is under it but the hearts.
        for (page in listOf("bg", "surface")) assertFalse(page, Regex("""uniform\s+\w+\s+$page\b""").containsMatchIn(code))
        val look = code.substringAfter("half4 heartLook(").substringBefore("\n}\n")
        val dark = Regex("""\bdark\b""")
        assertTrue("uniform float dark;" in code)
        assertEquals("dark is read in heartLook alone", dark.findAll(code).count(), 1 + dark.findAll(look).count())
        assertEquals(2, Regex("""\breturn\b[^;]*\bhere\b""").findAll(code.substringAfter("half4 main")).count())
    }

    /**
     * The window takes no touch and no focus, and is no more opaque than Android passes touches through. The
     * window's code sets its flags and its alpha from here alone: what this lets through is a flag or an alpha
     * set by another name.
     */
    @Test
    fun theWindowTakesNoTouch() {
        val flags = ShaderOverApps.WINDOW_FLAGS
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertEquals(0, flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        // Never more than Android allows, never more than opaque, and nothing where too little would show.
        assertEquals(0.8f, ShaderOverApps.alpha(0.8f))
        assertEquals(1f, ShaderOverApps.alpha(1.5f))
        assertEquals(0f, ShaderOverApps.alpha(0.2f))
        assertEquals(0f, ShaderOverApps.alpha(0f))
        assertEquals(0f, ShaderOverApps.alpha(Float.NaN))
        for (i in 0..120) {
            val most = i / 100f
            assertTrue("$most", ShaderOverApps.alpha(most) <= most)
        }
        assertTrue("TYPE_APPLICATION_OVERLAY" in overlay)
        assertTrue("ShaderOverApps.WINDOW_FLAGS" in overlay)
        assertTrue("ShaderOverApps.alpha(context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch)" in overlay)
        assertTrue("params.alpha = alpha" in overlay)
        assertEquals(1, Regex("""\.alpha\s*=""").findAll(overlay).count())
        assertFalse(Regex("""\bFLAG_\w+""").containsMatchIn(overlay))
        assertFalse(Regex("""\.flags\b""").containsMatchIn(overlay))
    }

    /** No window can stay: it goes after a little more than the longest one-off, when the screen goes off, and when Kolahal comes to front. */
    @Test
    fun theWindowNeverStays() {
        assertTrue(ShaderOverApps.LIMIT_MS > ShaderPlay.MAX_SECONDS * 1000)
        assertTrue(ShaderOverApps.LIMIT_MS <= ShaderPlay.MAX_SECONDS * 1000 + 5_000)
        assertTrue("main.postDelayed(limit, ShaderOverApps.LIMIT_MS)" in overlay)
        assertTrue("Intent.ACTION_SCREEN_OFF" in overlay)
        assertTrue("override fun onActivityStarted" in overlay)
        // What throws while it is drawn takes the window away, too.
        assertTrue(Regex("""catch \(_: RuntimeException\) \{[^}]*remove\(\)""").containsMatchIn(overlay))
        // No activity, no service and no notification of its own.
        for (word in listOf("startActivity", "startService", "startForeground", "NotificationManager")) assertFalse(word, word in overlay)
    }

    /** Nothing of the app in front is read, and nothing is logged or stored but the guard's mark. */
    @Test
    fun nothingOfTheAppInFrontIsRead() {
        for (text in listOf(overlay, model)) {
            val code = text.lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }.joinToString("\n")
            for (word in listOf("UsageStats", "ActivityManager", "getRunningTasks", "AccessibilityEvent", "LogRepository", "KvStore", "Stores.", "android.util.Log", "MediaProjection")) {
                assertFalse(word, word in code)
            }
        }
        // The words match is the listener's only look at the notification, as before; the ranking it already reads says the rest.
        val words = sourceFile("src/main/java/com/mtwabpin/kolahal/shader/ShaderWords.kt").readText()
        assertTrue("ShaderOverApps.onWord(context, name, heldBack)" in words)
    }

    /**
     * The Play build has none of it: its switch is off, its manifest removes the permission, and with the switch
     * off there is no setting, no `allow` word, and no way into the code.
     */
    @Test
    fun thePlayBuildLeavesItOut() {
        // The app's own folder: the repository's root has a build file of the same name.
        val app = sourceFile("src/main").absoluteFile.parentFile.parentFile
        val gradle = File(app, "build.gradle.kts").readText()
        val on = """buildConfigField("boolean", "SHADER_OVER_APPS", "true")"""
        val off = """buildConfigField("boolean", "SHADER_OVER_APPS", "false")"""
        assertTrue(on in gradle.substringAfter("defaultConfig {").substringBefore("signingConfigs {"))
        assertTrue(off in gradle.substringAfter("""create("play") {"""))
        assertFalse(off in gradle.substringBefore("""create("play") {"""))

        val permission = "android.permission.SYSTEM_ALERT_WINDOW"
        assertTrue("""<uses-permission android:name="$permission" />""" in sourceFile("src/main/AndroidManifest.xml").readText())
        assertTrue("""<uses-permission android:name="$permission" tools:node="remove" />""" in sourceFile("src/play/AndroidManifest.xml").readText())

        // The `allow` word and the setup checklist's row.
        val access = Accesses.find("overlay")!!
        assertEquals("Display over other apps", access.title)
        assertEquals(BuildFeature.SHADER_OVER_APPS, access.needs)
        assertFalse(Accesses.inBuild(access) { false })
        assertFalse(Accesses.offered(36) { it != BuildFeature.SHADER_OVER_APPS }.any { it.word == "overlay" })
        assertEquals(BuildConfig.SHADER_OVER_APPS, Accesses.OFFERED.any { it.word == "overlay" })
        // Shaders need Android 13: before that there is nothing to ask for.
        assertFalse(Accesses.offered(32) { true }.any { it.word == "overlay" })
        assertTrue(Accesses.offered(33) { true }.any { it.word == "overlay" })

        // The setting: `config` knows it only in a build that has it, and the Shaders screen has its switch.
        val key = Keys.SHADER_OVER_APPS
        assertEquals("screen.shader-over-apps", key.key)
        assertFalse(key.default)
        assertEquals("Play over other apps", SettingTitles[key.key])
        assertEquals(BuildConfig.SHADER_OVER_APPS, key in Keys.ALL)
        assertEquals(BuildConfig.SHADER_OVER_APPS, Keys.byName(key.key) != null)
        assertTrue(key in Keys.CONFIG_ONLY)
        assertTrue(""".takeIf { com.mtwabpin.kolahal.BuildConfig.SHADER_OVER_APPS }""" in sourceFile("src/main/java/com/mtwabpin/kolahal/data/Keys.kt").readText())
        assertTrue("if (actions.overAppsOffered && once.isNotEmpty())" in sourceFile("src/main/java/com/mtwabpin/kolahal/ui/shaders/ShadersScreen.kt").readText())
        assertTrue("val offered: Boolean get() = BuildConfig.SHADER_OVER_APPS && Shaders.supported" in model)

        // The two ways in, each behind the switch: a word's shader, and the window that home sets.
        val words = sourceFile("src/main/java/com/mtwabpin/kolahal/shader/ShaderWords.kt").readText()
        assertTrue("if (BuildConfig.SHADER_OVER_APPS && context != null) ShaderOverApps.onWord(" in words)
        assertEquals(1, Regex("""ShaderOverApps\.""").findAll(words.lines().filterNot { it.trimStart().startsWith("*") }.joinToString("\n")).count())
        val home = sourceFile("src/main/java/com/mtwabpin/kolahal/MainActivity.kt").readText()
        assertTrue("if (BuildConfig.SHADER_OVER_APPS) com.mtwabpin.kolahal.ui.shaders.ShaderOverlay.install(application)" in home)
        assertEquals(1, Regex("""ShaderOverlay\b""").findAll(home).count())
        // What the full build's check script looks for in the Play build's code is the window's name.
        assertTrue("""const val TITLE = "Kolahal one-off shader"""" in overlay)
        assertTrue("Kolahal one-off shader" in File(app.parentFile, "tools/check-play-build.sh").readText())
    }
}
