package com.mtwabpin.kolahal.notifications

import android.content.Context
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification feed settings. Each app's notifications go to the terminal log unless the app is muted.
 * When [logToTerminal] is false, no app is logged. The in-memory feed still works.
 */
class NotifPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("notif_prefs", Context.MODE_PRIVATE)

    private val _muted = MutableStateFlow(prefs.getStringSet(KEY_MUTED, emptySet())!!.toSet())
    val muted: StateFlow<Set<String>> = _muted.asStateFlow()

    var logToTerminal: Boolean
        get() = prefs.getBoolean(KEY_LOG, true)
        set(value) = prefs.edit { putBoolean(KEY_LOG, value) }

    fun isMuted(pkg: String): Boolean = pkg in _muted.value

    fun mute(pkg: String) = setMuted(_muted.value + pkg)

    fun unmute(pkg: String) = setMuted(_muted.value - pkg)

    /** True when a notification from [pkg] should become a terminal line. */
    fun shouldLog(pkg: String): Boolean = logToTerminal && !isMuted(pkg)

    private fun setMuted(set: Set<String>) {
        _muted.value = set
        prefs.edit { putStringSet(KEY_MUTED, set) }
    }

    companion object : Singleton<NotifPrefs>(::NotifPrefs, mainOnly = true) {
        private const val KEY_LOG = "log_to_terminal"
        private const val KEY_MUTED = "muted"
    }
}
