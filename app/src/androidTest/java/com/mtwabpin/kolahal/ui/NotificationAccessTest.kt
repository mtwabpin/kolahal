package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.notifications.NotificationActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Turning notification access on, Kolahal first says what it reads and asks (Play's prominent disclosure). */
@RunWith(AndroidJUnit4::class)
class NotificationAccessTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    /** What it reads, what for and that it stays on the phone; Not now and Allow each answer. */
    @Test fun theDialogSaysWhatItReadsAndAsks() {
        var answer: Boolean? = null
        compose.setContent { NotificationAccessDialog(onAllow = { answer = true }, onDismiss = { answer = false }) }
        compose.onNodeWithText("Let Kolahal read your notifications?").assertExists()
        compose.onNodeWithText("which app, the title and the text", substring = true).assertExists()
        compose.onNodeWithText("They stay on this phone", substring = true).assertExists()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(false, answer)
        compose.onNodeWithText("Allow").performClick()
        assertEquals(true, answer)
    }

    /** `notifs enable` with access off asks before anything opens; Not now leaves a line to ask again. */
    @Test fun notifsEnableAsksFirst() {
        assumeFalse("notification access is already on here", NotificationActions.isEnabled(app))
        val vm = vms.terminal(app)
        val logs = LogRepository.get(app)
        val before = logs.recent(1).lastOrNull()?.id ?: 0
        compose.runOnUiThread { vm.runCommand("notifs enable") }
        compose.waitUntil(5_000) { vm.notificationAccessAsk.value }
        compose.runOnUiThread { vm.answerNotificationAccess(false) }
        assertFalse(vm.notificationAccessAsk.value)
        compose.waitUntil(5_000) { logs.recent(50).any { it.id > before && it.text.startsWith("Notification access is off") } }
    }
}
