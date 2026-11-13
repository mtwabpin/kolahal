package com.mtwabpin.kolahal.terminal

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which page is open, and the way back from it. The view model holds one as `navigation`. Its own
 * [openScreen], [closeScreen] and the rest hand over to it.
 */
class Navigation(private val scripts: ScriptActions, private val host: Host) {
    /** What the pages need from the view model. */
    interface Host {
        /** Clears the message on the GUI pages. */
        fun clearNotice()
        /** Ends Settings' look at home through itself. */
        fun clearPeek()
        /** Runs the setup checks again, for the Setup page and the tour. */
        fun refreshSetup()
    }

    private val _screen = MutableStateFlow(Screen.TERMINAL)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** Pages opened from other pages (Settings → Script manager). Back returns through them in order. */
    private val backStack = ArrayDeque<Screen>()

    fun openScreen(screen: Screen) {
        host.clearNotice()
        host.clearPeek()
        synchronized(backStack) {
            val current = _screen.value
            if (screen == Screen.TERMINAL) backStack.clear()
            else if (current != Screen.TERMINAL && current != screen) backStack.addLast(current)
            _screen.value = screen
        }
        if (screen == Screen.SCRIPTS || screen == Screen.SCRIPT_LIBRARY || screen == Screen.GET_SCRIPTS) scripts.refreshScripts()
        if (screen == Screen.GET_SCRIPTS) scripts.loadSharedScripts()
        if (screen == Screen.SETUP || screen == Screen.ONBOARDING) host.refreshSetup()
    }

    /** Settings, Feeds and Scripts are tabs of one place. Switching replaces the screen, so Back leaves all of them. */
    fun switchTab(screen: Screen) {
        host.clearNotice()
        host.clearPeek()
        // Next time, Settings opens on its main page, not on the category that was open when the tab changed.
        settingsCategory.value = null
        _screen.value = screen
        if (screen == Screen.SCRIPTS) scripts.refreshScripts()
    }

    /** Back from a page: returns to the page it was opened from, or to the terminal. */
    fun closeScreen() {
        host.clearNotice()
        val previous = synchronized(backStack) { backStack.removeLastOrNull() } ?: Screen.TERMINAL
        // A category stays open only while a manager screen opened from it is on top, not after going home.
        if (_screen.value == Screen.SETTINGS && previous == Screen.TERMINAL) settingsCategory.value = null
        _screen.value = previous
        if (previous == Screen.SCRIPTS) scripts.refreshScripts()
        if (previous == Screen.SETUP || previous == Screen.ONBOARDING) host.refreshSetup()
    }

    /** The open Settings category page. It is kept while a manager screen opened from it is on top. */
    val settingsCategory = mutableStateOf<String?>(null)

    /** The custom theme that the theme builder edits. Null while it creates a new one. */
    val themeBuilderName = mutableStateOf<String?>(null)

    fun openThemeBuilder(name: String?) {
        themeBuilderName.value = name
        openScreen(Screen.THEME_BUILDER)
    }
}
