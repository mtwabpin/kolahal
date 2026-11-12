package com.mtwabpin.kolahal.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A snapshot of all settings. The UI recomposes when any setting changes. */
class Config private constructor(private val prefs: SharedPreferences) {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    init {
        migrate()
    }

    /**
     * Moves settings saved under names from before 0.3.0 ([Keys.RENAMED]) to today's names. Drops settings that no
     * longer exist ([Keys.REMOVED]), and values that aren't text (only an edited backup can hold one).
     */
    fun migrate() {
        val all = prefs.all
        val saved = all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
        val notText = all.keys - saved.keys
        val moved = Keys.migrate(saved)
        if (moved == saved && notText.isEmpty()) return
        prefs.edit(commit = true) {
            notText.forEach { remove(it) }
            saved.keys.filter { it !in moved }.forEach { remove(it) }
            moved.forEach { (k, v) -> putString(k, v) }
        }
        _version.update { it + 1 }
    }

    // Every change goes through set()/reset(), which bump the version directly. (Android keeps only a
    // weak reference to a SharedPreferences change listener. R8 removed our strong reference in release
    // builds, so the listener was garbage-collected and settings changes stopped applying.)

    /** Values shown but not saved, for example a slider being dragged in Settings, with home visible underneath. */
    private val previews = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: ConfigKey<T>): T {
        val raw = previews[key.key] ?: saved(key.key) ?: return key.default
        return key.parse(raw) ?: key.default
    }

    /** The text saved for [key], or null. A value that isn't text counts as not set: reading it as text would throw, at every start. */
    private fun saved(key: String): String? = try {
        prefs.getString(key, null)
    } catch (_: ClassCastException) {
        null
    }

    /** Shows [raw] for [key] everywhere without saving it. Null goes back to the saved value. */
    fun preview(key: ConfigKey<*>, raw: String?) {
        if (raw == null) {
            if (previews.remove(key.key) == null) return
        } else {
            if (previews.put(key.key, raw) == raw) return
        }
        _version.update { it + 1 }
    }

    /** Returns false if [raw] is not a valid value for [key]. */
    fun <T> set(key: ConfigKey<T>, raw: String): Boolean {
        val value = key.parse(raw) ?: return false
        // Setting the default again means no change: no ↺, no `*`, not in backups, and a later new default applies.
        prefs.edit { if (value == key.default) remove(key.key) else putString(key.key, key.format(value)) }
        _version.update { it + 1 }
        return true
    }

    fun reset(key: ConfigKey<*>) {
        prefs.edit { remove(key.key) }
        _version.update { it + 1 }
    }

    fun isDefault(key: ConfigKey<*>) = !prefs.contains(key.key)

    fun <T> display(key: ConfigKey<T>): String = key.format(get(key))

    companion object : Singleton<Config>({ Config(it.getSharedPreferences("config", Context.MODE_PRIVATE)) })
}

/** "22:40" or "10:40 PM", based on [Keys.CLOCK_24H], for log lines written outside the UI (workers). */
fun Config.shortTime(millis: Long = System.currentTimeMillis()): String =
    java.text.SimpleDateFormat(if (this[Keys.CLOCK_24H]) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(java.util.Date(millis))
