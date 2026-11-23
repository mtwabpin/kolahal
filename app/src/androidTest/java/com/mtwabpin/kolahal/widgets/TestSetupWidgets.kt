package com.mtwabpin.kolahal.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.util.Collections

/*
 * Two app widgets with a setup screen, in the test APK (its AndroidManifest.xml and res/xml), for
 * the tests of Widget settings. Each draws its first layout and nothing else.
 */

/** Its setup may run again after it is placed (`reconfigurable`): Arrange widgets offers Widget settings. */
class ReconfigurableTestWidget : AppWidgetProvider()

/** Its setup runs only as it is placed: no Widget settings. */
class SetUpOnceTestWidget : AppWidgetProvider()

/**
 * The widgets [TestWidgetSetup] was opened for while this listens, in order ([opened]), in the tests'
 * process. [close] stops it.
 */
class SetupOpenings(private val context: Context) : AutoCloseable {
    val opened: MutableList<Int> = Collections.synchronizedList(ArrayList())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            opened += intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }
    }

    init {
        // Exported: the setup screen is in another app, the test APK.
        ContextCompat.registerReceiver(context, receiver, IntentFilter(TestWidgetSetup.OPENED), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun close() = context.unregisterReceiver(receiver)
}
