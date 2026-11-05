package com.mtwabpin.kolahal.productivity

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A reminder firing: a rule it can't read must not take the app down, at this start or the next. */
@RunWith(AndroidJUnit4::class)
class ReminderFiringTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ReminderStore.get(context)
    private val logs = LogRepository.get(context)
    private val before = logs.maxId()

    @After fun tearDown() {
        store.all().filter { it.text.startsWith("firing-") }.forEach { ReminderScheduler.cancel(context, it.id); store.remove(it.id) }
        logs.recent(50).filter { it.id > before }.forEach { logs.delete(it.id) }
    }

    private fun localTime(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime()

    private fun fire(id: Long) = context.sendBroadcast(
        Intent(context, ReminderReceiver::class.java).putExtra(ReminderScheduler.EXTRA_ID, id).setPackage(context.packageName),
    )

    private fun waitFor(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 8_000
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue(what, check())
    }

    @Test fun aRuleItCantReadFiresOnceAndGoes() {
        val r = store.add(System.currentTimeMillis() - 1_000, "firing-bad", "biweekly")
        fire(r.id)
        waitFor("gone") { store.byId(r.id) == null }
        waitFor("and it says so") {
            logs.recent(50).any { it.id > before && it.kind == LogKind.ERROR && "firing-bad" in it.text && "won't repeat" in it.text }
        }
        // Logged after the notification is posted, a moment after the line above.
        waitFor("it still reminds") { logs.recent(50).any { it.id > before && it.text == "Reminder: firing-bad" } }
    }

    @Test fun aRepeatingOneIsSetForItsNextTime() {
        val at = System.currentTimeMillis() - 1_000
        val r = store.add(at, "firing-good", "day")
        fire(r.id)
        waitFor("moved on") { store.byId(r.id)?.at != at }
        assertNotNull(store.byId(r.id))
        assertEquals("day", store.byId(r.id)!!.repeat)
        assertNotEquals(at, store.byId(r.id)!!.at)
        // Set without its time (before 0.4.12): it keeps the time it fired at from now on.
        assertEquals(localTime(at).toString(), store.byId(r.id)!!.time)
    }

    /** Its next time is at the time asked for, not the one it fired at (which a daylight-saving gap can move, 02:30 to 03:30). */
    @Test fun aRepeatingOneKeepsTheTimeAskedFor() {
        val r = store.add(System.currentTimeMillis() - 1_000, "firing-asked", "day", "02:30")
        fire(r.id)
        waitFor("moved on") { store.byId(r.id)!!.at > System.currentTimeMillis() }
        assertEquals(LocalTime.of(2, 30), localTime(store.byId(r.id)!!.at))
    }

    /**
     * What a time-zone change runs (followZone): one whose time no longer reads as the time asked for
     * moves to it, as after a flight; one set without its time stays. The phone's zone itself isn't changed.
     */
    @Test fun aNewZoneMovesItToTheTimeAskedFor() {
        val elsewhere = LocalDate.now().plusDays(1).atTime(3, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val asked = store.add(elsewhere, "firing-zone", "day", "09:00")
        val older = store.add(elsewhere, "firing-older", "day")
        ReminderScheduler.followZone(context)
        assertEquals(LocalTime.of(9, 0), localTime(store.byId(asked.id)!!.at))
        assertEquals(elsewhere, store.byId(older.id)!!.at)
    }

    /**
     * A zone changed while Kolahal wasn't running: nothing heard about it, so the next start
     * (rescheduleAll) moves them, once. The phone's zone itself isn't changed: the one they
     * followed is set to another.
     */
    @Test fun aZoneChangedWhileKolahalWasntRunningMovesThemAtTheNextStart() {
        val prefs = context.getSharedPreferences(ReminderScheduler.PREFS, android.content.Context.MODE_PRIVATE)
        val was = prefs.getString(ReminderScheduler.KEY_ZONE, null)
        val other = if (ZoneId.systemDefault().id == "Asia/Kolkata") "Europe/London" else "Asia/Kolkata"
        try {
            val elsewhere = LocalDate.now().plusDays(1).atTime(3, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val asked = store.add(elsewhere, "firing-zone", "day", "09:00")
            // The same zone as before: nothing moves.
            prefs.edit().putString(ReminderScheduler.KEY_ZONE, ZoneId.systemDefault().id).commit()
            ReminderScheduler.rescheduleAll(context)
            assertEquals(elsewhere, store.byId(asked.id)!!.at)
            prefs.edit().putString(ReminderScheduler.KEY_ZONE, other).commit()
            ReminderScheduler.rescheduleAll(context)
            assertEquals(LocalTime.of(9, 0), localTime(store.byId(asked.id)!!.at))
            assertEquals(ZoneId.systemDefault().id, prefs.getString(ReminderScheduler.KEY_ZONE, null))
        } finally {
            prefs.edit().putString(ReminderScheduler.KEY_ZONE, was).commit()
        }
    }
}
