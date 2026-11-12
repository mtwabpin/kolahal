package com.mtwabpin.kolahal.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogLinesSince
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Work that fails at start (setting reminders again, scheduling) says so in the log, once a day, and the work after it still runs. */
@RunWith(AndroidJUnit4::class)
class QuietlyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    @get:Rule val logLines = LogLinesSince()
    private val what = "Quietly test step"

    @After fun tearDown() = KvStore(context, "worker").set("failed:$what", null)

    @Test fun aFailureIsLoggedOnceADay() {
        val before = logs.maxId()
        var after = false
        Quietly.run(context, what) { error("no alarms left") }
        Quietly.run(context, what) { error("no alarms left") }
        Quietly.run(context, "$what (fine)") { after = true }
        assertTrue(after)
        val lines = logs.recent(50).filter { it.id > before && it.text.startsWith(what) }
        assertEquals(listOf("$what failed: no alarms left"), lines.map { it.text })
        assertEquals(LogKind.ERROR, lines.single().kind)
    }
}
