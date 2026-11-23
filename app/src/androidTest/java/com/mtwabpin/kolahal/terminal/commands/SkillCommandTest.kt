package com.mtwabpin.kolahal.terminal.commands

import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.commands.SkillText
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `skill.md`: typed in full, it copies the guide for an AI assistant and says so in one line. */
@RunWith(AndroidJUnit4::class)
class SkillCommandTest {
    @get:Rule
    val logLines = LogLinesSince()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val commands = CommandRunner(context)
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    /** Runs [block] with a window of Kolahal's in front: Android lets only the app in front read the clipboard. */
    private fun <T> inFront(block: () -> T): T =
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            waitUntil("a window of ours in front") {
                var focused = false
                scenario.onActivity { focused = it.hasWindowFocus() }
                focused
            }
            block()
        }

    @Test
    fun copiesTheWholeGuideAndSaysSo() {
        val guide = context.assets.open(SkillText.NAME).use { it.readBytes().toString(Charsets.UTF_8) }
        inFront {
            val lines = commands.lines("skill.md")
            assertEquals(guide, clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertEquals(listOf(SkillText.copied(guide.length)), lines.map { it.text })
            assertEquals(LogKind.INFO, lines.single().kind)
        }
    }
}
