package com.mtwabpin.kolahal.terminal

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home away from the screen reads nothing: the log page and a `/` search left in the prompt wait,
 * and catch up once it's back. A second home that stays shown says when the lines have been read.
 */
@RunWith(AndroidJUnit4::class)
class HomeAwayTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val logs = LogRepository.get(app)
    private val config = Config.get(app)
    private val token = "away${System.nanoTime()}"
    private var before = 0L

    @Before fun setUp() {
        before = logs.maxId()
        // No tip line on return, which would land among the lines checked.
        config.set(Keys.LOG_TIPS, "off")
    }

    @After fun tearDown() {
        config.reset(Keys.LOG_TIPS)
        logs.recent(200).filter { it.id > before }.forEach { logs.delete(it.id) }
    }

    private fun home(): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        instrumentation.runOnMainSync { vm = vms.terminal(app) }
        return vm
    }

    private fun add(text: String) = logs.add(LogKind.OUTPUT, "$token $text")

    private fun waitUntil(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 8_000
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue(what, check())
    }

    /** Types [text] at the prompt; with no screen showing it, Compose is told of the change by hand. */
    private fun type(vm: TerminalViewModel, text: String) = instrumentation.runOnMainSync {
        vm.onInputChange(TextFieldValue(text, TextRange(text.length)))
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
    }

    private fun away(vm: TerminalViewModel) = instrumentation.runOnMainSync {
        vm.onPause()
        vm.onStarted(false)
    }

    private fun back(vm: TerminalViewModel) = instrumentation.runOnMainSync {
        vm.onStarted(true)
        vm.onResume()
    }

    @Test fun theLogIsReadOnlyWhileShownThenOnReturn() {
        val vm = home()
        val shown = home()
        val seen = add("seen")
        waitUntil("the first line") { vm.entries.value.any { it.id == seen } }
        away(vm)
        val meanwhile = add("meanwhile")
        // Read by the home still shown: the one away had every chance to read it too.
        waitUntil("the home still shown reads it") { shown.entries.value.any { it.id == meanwhile } }
        Thread.sleep(300)
        assertTrue("read while away", vm.entries.value.none { it.id == meanwhile })
        back(vm)
        waitUntil("read on return") { vm.entries.value.any { it.id == meanwhile } }
    }

    @Test fun aSearchLeftOpenWaitsForTheReturn() {
        val vm = home()
        val shown = home()
        add("one")
        type(vm, "/$token")
        type(shown, "/$token")
        waitUntil("the first result") { vm.search.value?.total == 1 && shown.search.value?.total == 1 }
        away(vm)
        add("two")
        waitUntil("the home still shown finds the new line") { shown.search.value?.total == 2 }
        Thread.sleep(300)
        assertEquals("searched while away", 1, vm.search.value?.total)
        back(vm)
        waitUntil("searched again on return") { vm.search.value?.total == 2 }
    }
}
