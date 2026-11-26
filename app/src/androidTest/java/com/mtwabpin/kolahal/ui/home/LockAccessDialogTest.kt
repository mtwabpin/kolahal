package com.mtwabpin.kolahal.ui.home

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.gesture.LockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Turning the screen lock on, Kolahal first says what the accessibility service is for and asks (Play's prominent disclosure). */
@RunWith(AndroidJUnit4::class)
class LockAccessDialogTest {
    @get:Rule
    val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule
    val compose = createComposeRule()
    @get:Rule
    val logLines = LogLinesSince()
    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    /** What the service is for, what it doesn't read or send, and whose screen is next; Not now and Allow each answer. */
    @Test
    fun theDialogSaysWhatTheServiceDoesAndAsks() {
        var answer: Boolean? = null
        compose.setContent {
            LockAccessDialog(
                onAllow = { answer = true },
                onDismiss = { answer = false })
        }
        compose.onNodeWithTag("lock-access-dialog").assertExists()
        compose.onNodeWithText("Let Kolahal lock the screen?").assertExists()
        compose.onNodeWithText("only to lock the screen when you ask", substring = true).assertExists()
        compose.onNodeWithText("doesn't read the screen, what you type or other apps", substring = true).assertExists()
        compose.onNodeWithText("collects nothing and sends nothing", substring = true).assertExists()
        compose.onNodeWithText("Android's Accessibility settings open next", substring = true).assertExists()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(false, answer)
        compose.onNodeWithText("Allow").performClick()
        assertEquals(true, answer)
    }

    /** `allow lock` with the service off asks before anything opens; Not now leaves a line to ask again. */
    @Test
    fun allowLockAsksFirst() {
        assumeTrue("a build without the lock", BuildConfig.LOCK_SERVICE)
        assumeFalse("the screen lock service is on: allow lock opens Android's page at once", LockService.isEnabled(app))
        val vm = vms.terminal(app)
        val logs = LogRepository.get(app)
        compose.runOnUiThread { vm.runCommand("allow lock") }
        compose.waitUntil(5_000) { vm.lockAccessAsk.value }
        compose.runOnUiThread { vm.answerLockAccess(false) }
        assertFalse(vm.lockAccessAsk.value)
        compose.waitUntil(5_000) {
            logs.recent(50).any {
                it.id > logLines.start && it.text == "The screen lock is off\nTap to turn it on" &&
                        it.ctx.values[LogContext.RUN] == "allow lock"
            }
        }
    }
}
