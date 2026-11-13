package com.mtwabpin.kolahal.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the hot paths of a normal session: cold start, typing into the prompt (suggestions),
 * running commands, scrolling the log, the settings screen, the script editor, the app selector
 * on the edge and the widgets page.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    private val target = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "com.mtwabpin.kolahal"

    /** Notification access, as on a phone that uses it, so the listener's start-up is recorded too. */
    private fun MacrobenchmarkScope.allowNotifications() {
        device.executeShellCommand("cmd notification allow_listener $target/com.mtwabpin.kolahal.notifications.KolahalNotificationListener")
    }

    /** Startup profile (dex layout): only the cold start to a drawn terminal. */
    @Test
    fun startup() = rule.collect(packageName = target, includeInStartupProfile = true) {
        allowNotifications()
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    /** Baseline profile: a whole normal session. */
    @Test
    fun generate() = rule.collect(packageName = target) {
        allowNotifications()
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        fun type(text: String) {
            device.executeShellCommand("input text $text")
            device.waitForIdle()
        }
        fun enter() {
            device.executeShellCommand("input keyevent ENTER")
            device.waitForIdle()
        }

        // Prompt suggestions while typing an app name, then clear it.
        type("se")
        type("t")
        device.executeShellCommand("input keyevent ESCAPE")
        // Commands with long output and the log list.
        type("help"); enter()
        type("apps"); enter()
        type("=2*21"); enter()
        val w = device.displayWidth / 2
        val h = device.displayHeight
        device.swipe(w, h / 3, w, h * 2 / 3, 20)
        device.swipe(w, h * 2 / 3, w, h / 3, 20)
        // Settings screen (Compose Material 3) and back.
        type("settings"); enter()
        device.waitForIdle()
        device.swipe(w, h * 2 / 3, w, h / 4, 20)
        device.pressBack()
        device.waitForIdle()

        // The script editor. The first round makes the script and opens it; each round opens it with edit.
        // (%s is a space for `input text`.)
        type("script%snew%sbp"); enter()
        device.pressBack()
        device.waitForIdle()
        type("script%sedit%sbp"); enter()
        device.waitForIdle()
        device.swipe(w, h * 2 / 3, w, h / 3, 20)
        device.pressBack()
        device.waitForIdle()

        // The app selector on the screen's edge (selector.rail), with the keyboard down: slide along the
        // edge, which launches nothing and leaves the list open when the finger lifts there. Back closes it.
        type("config%sset%sselector.rail%son"); enter()
        device.pressBack()
        device.waitForIdle()
        val edge = device.displayWidth - 6
        device.swipe(edge, h * 4 / 5, edge, h / 2, 60)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        // The widgets page, left of the log (widgets.page), and back to the log.
        device.swipe(w / 3, h / 2, w * 5 / 3, h / 2, 20)
        device.waitForIdle()
        device.swipe(w * 5 / 3, h / 2, w / 3, h / 2, 20)
        device.waitForIdle()
    }
}
