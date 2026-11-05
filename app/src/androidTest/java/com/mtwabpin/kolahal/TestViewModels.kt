package com.mtwabpin.kolahal

import android.app.Application
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.rules.ExternalResource

/**
 * View models a test makes, cleared after it as an activity's are. Left alive, each one goes on
 * listening through every later test: it runs the commands scripts send, reads the log again at
 * every line, and an Undo it offered deletes lines seconds later.
 */
class TestViewModels : ExternalResource() {
    private val store = ViewModelStore()
    private var made = 0

    /** A new [TerminalViewModel], cleared after the test; made on the main thread, as the activity makes it. */
    fun terminal(app: Application): TerminalViewModel {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            lateinit var vm: TerminalViewModel
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm = terminal(app) }
            return vm
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TerminalViewModel(app, SavedStateHandle()) as T
        }
        return ViewModelProvider(store, factory)["vm${made++}", TerminalViewModel::class.java]
    }

    override fun after() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    }
}
