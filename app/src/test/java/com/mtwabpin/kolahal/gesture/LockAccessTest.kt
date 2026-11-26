package com.mtwabpin.kolahal.gesture

import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.gesture.LockAccess.Step
import com.mtwabpin.kolahal.sourceFile
import com.mtwabpin.kolahal.ui.home.LOCK_ACCESS_NEXT
import com.mtwabpin.kolahal.ui.home.LOCK_ACCESS_POINTS
import com.mtwabpin.kolahal.ui.home.LOCK_ACCESS_TITLE
import com.mtwabpin.kolahal.ui.home.LOCK_ACCESS_WHAT
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen lock in both builds, with Google Play's prominent disclosure before Android's accessibility settings open
 * ([LockAccess], [com.mtwabpin.kolahal.ui.home.LockAccessDialog]), and a service as narrow as it can be.
 */
class LockAccessTest {
    private val base = "src/main/java/com/mtwabpin/kolahal"

    /** `allow lock` asks first while the service is off; once it is on, Android's page opens at once, to turn it off. */
    @Test
    fun allowLockAsksWhileTheServiceIsOff() {
        assertEquals(Step.ASK, LockAccess.onAllow(serviceOn = false))
        assertEquals(Step.OPEN_SETTINGS, LockAccess.onAllow(serviceOn = true))
    }

    /** Only Allow opens Android's page; Not now opens nothing. */
    @Test
    fun onlyAllowOpensTheSettings() {
        assertEquals(Step.OPEN_SETTINGS, LockAccess.onAnswer(allow = true))
        assertEquals(Step.NOTHING, LockAccess.onAnswer(allow = false))
    }

    /** `allow lock` and the dialog's answer follow [LockAccess]; home shows the dialog over any screen. */
    @Test
    fun theWaysInFollowTheDecision() {
        val allow = sourceFile("$base/terminal/commands/AllowCommands.kt").readText()
        val branch = allow.substringAfter("Accesses.LOCK -> when").substringBefore("Accesses.DND ->")
        assertTrue(branch, "LockAccess.onAllow(SetupChecks.given(context, access))" in branch)
        assertTrue(branch, "Step.ASK -> ui.askLockAccess()" in branch)
        // Nothing else in the app opens Android's accessibility settings.
        val opens = sourceFile(base).walkTopDown().filter { it.extension == "kt" }
            .filter { "LockService.settingsIntent()" in it.readText() || "ACTION_ACCESSIBILITY_SETTINGS" in it.readText() }
            .map { it.name }.toSet()
        assertEquals(setOf("AllowCommands.kt", "RequestActions.kt", "LockService.kt"), opens)
        val requests = sourceFile("$base/terminal/RequestActions.kt").readText()
            .substringAfter("fun answerLockAccess(").substringBefore("// ----")
        assertTrue(requests, "LockAccess.onAnswer(allow)" in requests)
        assertTrue(requests, "Step.OPEN_SETTINGS ->" in requests)
        assertEquals(1, Regex("""LockService\.settingsIntent\(\)""").findAll(requests).count())
        assertTrue(requests, "\"The screen lock is off\\nTap to turn it on\"" in requests)
        assertTrue(requests, "LogContext.RUN to \"allow lock\"" in requests)
        val home = sourceFile("$base/MainActivity.kt").readText()
        assertTrue("if (askingLock) LockAccessDialog(" in home)
        assertTrue("onAllow = { vm.answerLockAccess(true) }" in home)
        assertTrue("onDismiss = { vm.answerLockAccess(false) }" in home)
        // `lock` and a lock gesture without the service leave a line whose tap runs `allow lock`.
        val processor = sourceFile("$base/terminal/commands/CommandProcessor.kt").readText()
        assertTrue("\"Locking needs an accessibility service\\nTap to allow Kolahal screen lock\",\n                        \"allow lock\"" in processor)
    }

    /**
     * What Play asks the disclosure to say: what the service is used for, that it is the only way, what it doesn't
     * read or send, and that Android's screen comes next. In plain words, Kolahal never speaking as a person.
     */
    @Test
    fun theDisclosureSaysWhatPlayAsks() {
        assertEquals("Let Kolahal lock the screen?", LOCK_ACCESS_TITLE)
        assertTrue(LOCK_ACCESS_WHAT, "accessibility service only to lock the screen when you ask" in LOCK_ACCESS_WHAT)
        assertTrue(LOCK_ACCESS_WHAT, "the lock command" in LOCK_ACCESS_WHAT && "double-tap" in LOCK_ACCESS_WHAT)
        assertTrue(LOCK_ACCESS_WHAT, "Android lets an app lock the screen only this way." in LOCK_ACCESS_WHAT)
        assertTrue(LOCK_ACCESS_POINTS, "doesn't read the screen, what you type or other apps" in LOCK_ACCESS_POINTS)
        assertTrue(LOCK_ACCESS_POINTS, "collects nothing and sends nothing" in LOCK_ACCESS_POINTS)
        assertTrue(LOCK_ACCESS_NEXT, LOCK_ACCESS_NEXT.startsWith("Android's Accessibility settings open next"))
        assertTrue(LOCK_ACCESS_NEXT, "Kolahal screen lock" in LOCK_ACCESS_NEXT)
        val all = listOf(LOCK_ACCESS_TITLE, LOCK_ACCESS_WHAT, LOCK_ACCESS_POINTS, LOCK_ACCESS_NEXT)
        for (text in all) {
            val words = text.split(Regex("""[^A-Za-z']+""")).map { it.lowercase() }
            for (person in listOf("i", "i'm", "i'll", "we", "we'll", "our", "us", "me", "my", "please", "sorry")) {
                assertFalse("\"$person\" in: $text", person in words)
            }
            assertFalse(text, " · " in text)
        }
        // Each point on a line of its own.
        assertEquals(3, LOCK_ACCESS_POINTS.lines().size)
        assertTrue(LOCK_ACCESS_POINTS.lines().all { it.startsWith("• ") && it.endsWith(".") })
        // The buttons are the other two disclosures' own.
        val dialog = sourceFile("$base/ui/home/LockAccessDialog.kt").readText()
        val notifications = sourceFile("$base/ui/home/NotificationAccessDialog.kt").readText()
        for (button in listOf("{ Text(\"Allow\") }", "{ Text(\"Not now\") }")) {
            assertTrue(button, button in dialog && button in notifications)
        }
        assertTrue("onClick = onAllow" in dialog && "onClick = onDismiss" in dialog)
        // The label Android's settings show is the one the dialog names.
        val strings = sourceFile("src/main/res/values/strings.xml").readText()
        assertTrue("<string name=\"lock_service_label\">Kolahal screen lock</string>" in strings)
    }

    /**
     * Both builds have the lock, behind a switch that can take it out of the Play build again: the Play build's switch
     * and its manifest agree (on and the service kept, or off and the service removed).
     */
    @Test
    fun thePlayBuildHasItBehindItsSwitch() {
        val app = sourceFile("src/main").absoluteFile.parentFile.parentFile
        val gradle = File(app, "build.gradle.kts").readText()
        fun switch(on: Boolean) = """buildConfigField("boolean", "LOCK_SERVICE", "$on")"""
        assertTrue(switch(true) in gradle.substringAfter("defaultConfig {").substringBefore("signingConfigs {"))
        val play = gradle.substringAfter("""create("play") {""").substringBefore("\n        }\n")
        val playHas = switch(true) in play
        assertTrue("the play build type sets the switch", playHas || switch(false) in play)
        assertTrue("the Play build has the lock", playHas)
        assertTrue(BuildConfig.LOCK_SERVICE)

        assertTrue("android:name=\".gesture.LockService\"" in sourceFile("src/main/AndroidManifest.xml").readText())
        val removed = Regex("""android:name="\.gesture\.LockService"\s+tools:node="remove"""")
            .containsMatchIn(sourceFile("src/play/AndroidManifest.xml").readText())
        assertEquals("the Play build's switch and its manifest agree", playHas, !removed)

        // The check script expects this one service, off until turned on, and fails on any other.
        val check = File(app.parentFile, "tools/check-play-build.sh").readText()
        assertTrue("com.mtwabpin.kolahal.gesture.LockService" in check)
        assertTrue("isAccessibilityTool" in check && "canRetrieveWindowContent" in check)
    }

    /** The service asks for nothing it doesn't use: no events, no window content, no flags, not an accessibility tool. */
    @Test
    fun theServiceIsAsNarrowAsItCanBe() {
        val xml = sourceFile("src/main/res/xml/lock_service.xml").readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        for (wide in listOf(
            "accessibilityEventTypes", "accessibilityFlags", "isAccessibilityTool", "canRequest", "canPerformGestures",
            "canTakeScreenshot", "packageNames", "canControlMagnification",
        )) assertFalse(wide, wide in xml)
        assertTrue("android:canRetrieveWindowContent=\"false\"" in xml)
        assertTrue("android:description=\"@string/lock_service_description\"" in xml)
        val strings = sourceFile("src/main/res/values/strings.xml").readText()
        val description = Regex("""<string name="lock_service_description">(.*?)</string>""").find(strings)!!.groupValues[1]
        assertTrue(description, description.startsWith("Locks the screen when you ask Kolahal to"))
        assertTrue(description, "does not read the screen, what you type or other apps" in description)
        // The service's code only locks.
        val service = sourceFile("$base/gesture/LockService.kt").readText()
        assertEquals(1, Regex("""performGlobalAction\(""").findAll(service).count())
        assertTrue("performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)" in service)
        assertTrue("override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit" in service)
        assertFalse("rootInActiveWindow" in service || "getWindows" in service || ".windows" in service)
    }
}
