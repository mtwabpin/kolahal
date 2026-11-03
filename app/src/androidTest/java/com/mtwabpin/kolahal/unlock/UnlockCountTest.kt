package com.mtwabpin.kolahal.unlock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Today's unlock count, as home and scripts read it, against every event of the day read one by one. */
@RunWith(AndroidJUnit4::class)
class UnlockCountTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }

    @After fun tearDown() {
        shell("appops set ${context.packageName} GET_USAGE_STATS default")
    }

    @Test fun todaysCountIsEveryUnlockOfTheDay() {
        shell("appops set ${context.packageName} GET_USAGE_STATS allow")
        // Two unlocks: the screen off, then on and the lock screen dismissed.
        try {
            repeat(2) {
                shell("input keyevent KEYCODE_SLEEP")
                Thread.sleep(1_500)
                shell("input keyevent KEYCODE_WAKEUP")
                Thread.sleep(500)
                shell("wm dismiss-keyguard")
                Thread.sleep(1_500)
            }
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(start, System.currentTimeMillis())
        val event = UsageEvents.Event()
        var unlocks = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) unlocks++
        }
        assertEquals(unlocks, UnlockCounter.get(context).countFor(LocalDate.now(zone)))
    }
}
