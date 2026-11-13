package com.mtwabpin.kolahal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.UiEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What a screen asks the activity for, kept for a test. A screen asks through the view model's
 * events ([UiEvent]), and a test has no activity to take them.
 * - Call [Collect] beside the screen. It listens once the content is shown, so before any tap, and
 *   stops when the content goes.
 * - An event arrives a moment after the tap: wait for it ([com.mtwabpin.kolahal.waitFor]).
 */
internal class SentEvents {
    private val seen = CopyOnWriteArrayList<UiEvent>()

    /** The events so far, oldest first. */
    val all: List<UiEvent> get() = seen.toList()

    @Composable
    fun Collect(vm: TerminalViewModel) {
        LaunchedEffect(vm) { vm.events.collect { seen += it } }
    }
}
