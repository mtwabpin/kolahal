package com.mtwabpin.kolahal.widgets

import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which app widgets Arrange widgets can set up again ([setUpAgain]): a setup screen, and a feature
 * that says it may run again. Optional setup counts from Android 12, where the flag exists.
 */
class SetUpAgainTest {
    private val none = 0
    private val both = WIDGET_FEATURE_RECONFIGURABLE or WIDGET_FEATURE_CONFIGURATION_OPTIONAL

    @Test
    fun theTable() {
        // (setup screen, features, Android version) to the answer.
        val table = listOf(
            Triple(true, none, 28) to false,
            Triple(true, none, 34) to false,
            Triple(true, WIDGET_FEATURE_RECONFIGURABLE, 28) to true,
            Triple(true, WIDGET_FEATURE_RECONFIGURABLE, 34) to true,
            Triple(true, WIDGET_FEATURE_CONFIGURATION_OPTIONAL, 30) to false,
            Triple(true, WIDGET_FEATURE_CONFIGURATION_OPTIONAL, 31) to true,
            Triple(true, WIDGET_FEATURE_CONFIGURATION_OPTIONAL, 34) to true,
            Triple(true, both, 28) to true,
            Triple(true, both, 34) to true,
            // A flag that has nothing to do with the setup.
            Triple(true, WIDGET_FEATURE_HIDE_FROM_PICKER, 34) to false,
            // No setup screen: nothing to open, whatever the widget says.
            Triple(false, none, 34) to false,
            Triple(false, WIDGET_FEATURE_RECONFIGURABLE, 28) to false,
            Triple(false, WIDGET_FEATURE_CONFIGURATION_OPTIONAL, 34) to false,
            Triple(false, both, 34) to false,
        )
        table.forEach { (case, expected) ->
            val (hasSetup, features, sdk) = case
            assertEquals(case.toString(), expected, setUpAgain(hasSetup, features, sdk))
        }
    }
}
