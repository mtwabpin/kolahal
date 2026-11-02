package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The tour's previews. */
@RunWith(AndroidJUnit4::class)
class TourPreviewsTest {
    @get:Rule val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application

    private fun shown() = compose.onRoot(useUnmergedTree = true).printToString()

    /** The typing demo plays only while it shows: behind another app it would wake twice a second and search the apps. */
    @Test fun theTypingDemoStopsOffScreen() {
        lateinit var vm: TerminalViewModel
        instrumentation.runOnMainSync { vm = TerminalViewModel(app, SavedStateHandle()) }
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { KolahalTheme { Column { TypingPreview(vm) } } }
        }
        compose.waitForIdle()
        // On screen it types: something changes within a few seconds.
        val first = shown()
        var moved = false
        repeat(40) {
            if (!moved) {
                compose.mainClock.advanceTimeBy(200)
                moved = shown() != first
            }
        }
        assumeTrue("no app here for the demo to type", moved)

        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.mainClock.advanceTimeBy(200)
        val stopped = shown()
        repeat(20) { compose.mainClock.advanceTimeBy(500) }
        assertEquals(stopped, shown())
    }
}
