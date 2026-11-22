package com.mtwabpin.kolahal.terminal

import android.content.ComponentName
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.TerminalUiStub
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.ui.home.ResumeProfileDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A tap on an app whose work profile is paused, or whose private space is locked, asks whether to
 * resume it: a dialog over any screen, where a line in the log could go unseen after a tap in the
 * app selector.
 */
@RunWith(AndroidJUnit4::class)
class ResumeProfileTest {
    @get:Rule
    val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)

    private fun app(kind: ProfileKind) = AppEntry(
        "Mail",
        ComponentName("com.example.mail", "com.example.mail.Main"),
        Profile(Process.myUserHandle(), 99, kind, quiet = true)
    )

    /** The tap asks, and writes nothing in the log: the dialog is the question. */
    @Test
    fun aTapOnAPausedAppAsks() {
        val asked = ArrayList<AppEntry>()
        val commands = CommandRunner(context, object : TerminalUiStub() {
            override fun askResume(app: AppEntry) {
                asked += app
            }
        })
        val paused = app(ProfileKind.WORK)
        val from = logs.maxId()
        commands.processor.launch(paused)
        assertEquals(listOf(paused), asked)
        assertEquals(emptyList<String>(), logs.recent(50, afterId = from).map { it.text })
    }

    /** For the work profile: Resume, or Cancel. Each answers once. */
    @Test
    fun theWorkProfileAsksToResume() {
        val answers = ArrayList<String>()
        compose.setContent {
            ResumeProfileDialog(
                app(ProfileKind.WORK),
                onResume = { answers += "resume" },
                onDismiss = { answers += "cancel" })
        }
        compose.onNodeWithText("Resume work apps?").assertIsDisplayed()
        compose.onNodeWithText("Mail is a work app, and work apps are paused. Kolahal turns them on, then opens Mail.")
            .assertIsDisplayed()
        compose.onNodeWithText("Resume").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(listOf("resume", "cancel"), answers) }
    }

    /** For the private space: Unlock, as Android asks for the lock. */
    @Test
    fun thePrivateSpaceAsksToUnlock() {
        val answers = ArrayList<String>()
        compose.setContent {
            ResumeProfileDialog(
                app(ProfileKind.PRIVATE),
                onResume = { answers += "unlock" },
                onDismiss = {})
        }
        compose.onNodeWithText("Unlock the private space?").assertIsDisplayed()
        compose.onNodeWithText("Unlock").performClick()
        compose.runOnIdle { assertEquals(listOf("unlock"), answers) }
    }
}
