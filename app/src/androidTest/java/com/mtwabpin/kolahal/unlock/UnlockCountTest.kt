package com.mtwabpin.kolahal.unlock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.shell
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Today's unlock count, as home and scripts read it, against every event of the day read one by one. */
@RunWith(AndroidJUnit4::class)
class UnlockCountTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** Usage access as it was, put back after: `allow`, `ignore`, … or `default`. */
    private val accessBefore = Regex("GET_USAGE_STATS: (\\w+)").find(shell("appops get ${context.packageName} GET_USAGE_STATS"))?.groupValues?.get(1) ?: "default"

    @After fun tearDown() {
        shell("appops set ${context.packageName} GET_USAGE_STATS $accessBefore")
    }

    /** Every unlock today, read one event at a time. */
    private fun unlocksToday(): Int {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(start, System.currentTimeMillis())
        val event = UsageEvents.Event()
        var unlocks = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) unlocks++
        }
        return unlocks
    }

    @Test fun todaysCountIsEveryUnlockOfTheDay() {
        shell("appops set ${context.packageName} GET_USAGE_STATS allow")
        val before = unlocksToday()
        // A lock screen to dismiss (a swipe, no PIN): test phones often have it off.
        val lockOff = shell("locksettings get-disabled").trim() == "true"
        if (lockOff) shell("locksettings set-disabled false")
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
            if (lockOff) shell("locksettings set-disabled true")
        }
        val unlocks = unlocksToday()
        // A phone without a lock screen to dismiss records none: nothing to compare then.
        assumeTrue("no unlocks recorded here", unlocks >= before + 2)
        assertEquals(unlocks, UnlockCounter.get(context).countFor(LocalDate.now()))
    }
}
