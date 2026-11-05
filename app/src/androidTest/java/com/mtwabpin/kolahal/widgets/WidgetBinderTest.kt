package com.mtwabpin.kolahal.widgets

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adding a widget once Kolahal may bind them: no dialog, the widget on the page and "Added" said; a
 * request that outlived Kolahal being stopped finishes, or frees its id, when the widget's setup
 * answers. Without leave, the dialog's No frees the id.
 */
@RunWith(AndroidJUnit4::class)
class WidgetBinderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val t = TestWidgets()
    private val widgets = t.host
    private val added = mutableListOf<PlacedWidget>()

    @After fun tearDown() = t.close()

    /** A test activity with a binder made before it starts, as MainActivity makes its own; [saved] is what Android kept. */
    private fun withBinder(saved: Bundle? = null, block: (ActivityScenario<ComponentActivity>, WidgetBinder) -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            // Stopped, it may register for results again, as before it first started.
            scenario.moveToState(Lifecycle.State.CREATED)
            lateinit var binder: WidgetBinder
            scenario.onActivity { binder = WidgetBinder(it) { w -> added += w }.apply { restore(saved) } }
            scenario.moveToState(Lifecycle.State.RESUMED)
            block(scenario, binder)
        }
    }

    /** What the binder keeps across Android stopping Kolahal, for widget [id]. */
    private fun kept(id: Int, replacing: String? = null) = Bundle().apply {
        putInt("widget_binder_id", id)
        putString("widget_binder_replacing", replacing)
    }

    /** Picking a clock with no setup screen places it at once, at its usual size, and says so. */
    @Test fun aWidgetIsPlacedWithNoDialogAndSaysAdded() {
        val clock = t.clocks(unconfigured = true).first()
        t.allowBinding(clock)
        withBinder { scenario, binder ->
            scenario.onActivity { binder.add(clock) }
            val w = added.single()
            assertEquals(clock.flat, w.provider)
            assertEquals(widgets.defaultHeightDp(clock.info), w.heightDp)
            assertEquals(WidgetLayout.COLUMNS, w.span)
            assertEquals(clock.info.provider, widgets.manager.getAppWidgetInfo(w.id!!).provider)
            assertEquals(w, widgets.widgets.value.single { it.id == w.id })
            assertEquals(listOf("Added ${t.label(clock)}"), widgets.messages.value)
        }
    }

    /** Add again on a widget restored from a backup: it comes back at its size and in its place, the waiting row gone. */
    @Test fun addingAgainKeepsTheRestoredSizeAndPlace() {
        val clock = t.clocks(unconfigured = true).first()
        t.allowBinding(clock)
        val pending = t.restore(clock.flat, clock.serial, height = 200, order = 70, span = 6)
        withBinder { scenario, binder ->
            scenario.onActivity { binder.add(clock, replacing = pending.key) }
            val w = added.single()
            assertEquals(200, w.heightDp)
            assertEquals(6, w.span)
            assertEquals(70, w.order)
            assertNull(t.get(pending.key))
        }
    }

    /**
     * The widget's setup finishing after Android stopped Kolahal: the request comes back, Kolahal starting
     * meanwhile leaves its id alone, and OK places the widget where the restored one waited.
     */
    @Test fun aRequestKeptAcrossAStopFinishes() {
        val clock = t.clocks().first()
        t.allowBinding(clock)
        val pending = t.restore(clock.flat, clock.serial, height = 160, order = 71)
        val id = t.bound(clock)
        withBinder(kept(id, pending.key)) { scenario, binder ->
            widgets.prune()
            assertTrue("prune freed a widget still being set up", id in t.ids())
            // Stopped again before the answer: it's kept once more.
            val again = Bundle().also(binder::save)
            assertEquals(id, again.getInt("widget_binder_id"))
            assertEquals(pending.key, again.getString("widget_binder_replacing"))
            // Some other screen's result is not the widget's.
            scenario.onActivity { binder.onActivityResult(1, Activity.RESULT_OK) }
            assertTrue(added.isEmpty())
            scenario.onActivity { binder.onActivityResult(WidgetBinder.REQUEST_CONFIGURE, Activity.RESULT_OK) }
            val w = added.single()
            assertEquals(id, w.id)
            assertEquals(maxOf(160, widgets.minHeightDp(clock.info)), w.heightDp)
            assertNull(t.get(pending.key))
            assertEquals(listOf("Added ${t.label(clock)}"), widgets.messages.value)
            // Done: nothing is kept for a later stop.
            assertFalse(Bundle().also(binder::save).containsKey("widget_binder_id"))
        }
    }

    /** Backing out of the widget's setup frees its id and says so; nothing is placed. */
    @Test fun aCancelledSetupFreesTheId() {
        val clock = t.clocks().first()
        t.allowBinding(clock)
        val id = t.bound(clock)
        withBinder(kept(id)) { scenario, binder ->
            scenario.onActivity { binder.onActivityResult(WidgetBinder.REQUEST_CONFIGURE, Activity.RESULT_CANCELED) }
            assertTrue(added.isEmpty())
            assertFalse(id in t.ids())
            assertTrue(widgets.widgets.value.none { it.id == id })
            assertEquals(listOf("Widget setup cancelled"), widgets.messages.value)
        }
    }

    /**
     * Without leave to bind widgets, Android's dialog is asked for the widget's id; turned down, the
     * widget isn't added, its id is freed and nothing is placed. The dialog is answered here, unseen.
     */
    @Test fun aBindTurnedDownFreesTheId() {
        val clock = t.clocks(unconfigured = true).first()
        t.forbidBinding(clock)
        val asked = java.util.concurrent.CopyOnWriteArrayList<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != AppWidgetManager.ACTION_APPWIDGET_BIND) return null
                asked += intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        val placedBefore = widgets.widgets.value
        try {
            withBinder { scenario, binder ->
                scenario.onActivity { binder.add(clock) }
                waitUntil("the refusal said") { widgets.messages.value == listOf("Widget not added") }
                val id = asked.single().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                @Suppress("DEPRECATION")
                assertEquals(clock.info.provider, asked.single().getParcelableExtra<android.content.ComponentName>(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER))
                assertFalse("its id kept", id in t.ids())
                assertTrue(added.isEmpty())
                assertEquals(placedBefore, widgets.widgets.value)
                // Nothing is kept for a later stop either.
                assertFalse(Bundle().also(binder::save).containsKey("widget_binder_id"))
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    /** Picking another widget while one still waits for its setup lets the first one's id go. */
    @Test fun aNewPickLetsTheWaitingOneGo() {
        val clock = t.clocks(unconfigured = true).first()
        t.allowBinding(clock)
        val waiting = t.bound(clock)
        withBinder(kept(waiting)) { scenario, binder ->
            scenario.onActivity { binder.add(clock) }
            assertFalse(waiting in t.ids())
            val w = added.single()
            assertTrue(w.id != waiting && w.id in t.ids())
        }
    }
}
