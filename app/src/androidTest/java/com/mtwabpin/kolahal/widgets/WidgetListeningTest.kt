package com.mtwabpin.kolahal.widgets

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home tells the widget host to listen each time it shows. With no widget placed there's nothing to
 * update, so it doesn't (a call to Android saved on each return home); the first widget added starts it.
 */
@RunWith(AndroidJUnit4::class)
class WidgetListeningTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val t = TestWidgets()
    private val widgets = t.host

    @After fun tearDown() {
        instrumentation.runOnMainSync { widgets.stopListening() }
        t.close()
    }

    @Test fun itListensOnlyWithAWidgetPlaced() {
        assumeTrue("widgets were placed before the test", widgets.widgets.value.isEmpty())
        instrumentation.runOnMainSync {
            widgets.stopListening()
            widgets.startListening()
        }
        assertFalse("listening with no widget", widgets.isListening)
        val clock = t.clocks(unconfigured = true).first()
        t.allowBinding(clock)
        t.place(clock)
        waitUntil("listening once a widget is placed") { widgets.isListening }
    }
}
