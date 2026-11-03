package com.mtwabpin.kolahal.widgets

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogRepository
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The check at each start, on its own thread: a widget whose id was lost waits to be added again, and the log says so. */
@RunWith(AndroidJUnit4::class)
class WidgetCheckTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val logs = LogRepository.get(instrumentation.targetContext)
    private val t = TestWidgets()
    private val widgets = t.host
    private val before = logs.maxId()

    @After fun tearDown() {
        t.close()
        logs.recent(50).filter { it.id > before }.forEach { logs.delete(it.id) }
    }

    private fun waitUntil(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 8_000
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue(what, check())
    }

    @Test fun aLostIdWaitsToBeAddedAgain() {
        val clock = t.clocks().first()
        // An id Android never gave out, as after a restore on another phone.
        val lost = Int.MAX_VALUE - 9
        widgets.place(lost, clock.info)
        val placed = widgets.widgets.value.single { it.id == lost }
        instrumentation.runOnMainSync {
            widgets.stopListening()
            widgets.startListening()
        }
        // Waiting, it's known by its provider and place: it has no id.
        waitUntil("waiting to be added again") {
            widgets.widgets.value.any { it.pending && it.provider == placed.provider && it.order == placed.order }
        }
        assertNull(widgets.widgets.value.firstOrNull { it.id == lost })
        waitUntil("and the log says so") { logs.recent(20).any { it.id > before && "needs adding again" in it.text } }
    }
}
