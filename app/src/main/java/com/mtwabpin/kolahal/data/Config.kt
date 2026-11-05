package com.mtwabpin.kolahal.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Snapshot of all settings; recomposes the UI when anything changes. */
class Config private constructor(private val prefs: SharedPreferences) {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    init {
        migrate()
    }

    /** Settings saved under names from before 0.3.0 ([Keys.RENAMED]) move to today's names; ones that are gone ([Keys.REMOVED]) are dropped. */
    fun migrate() {
        val saved = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
        val moved = Keys.migrate(saved)
        if (moved == saved) return
        prefs.edit(commit = true) {
            saved.keys.filter { it !in moved }.forEach { remove(it) }
            moved.forEach { (k, v) -> putString(k, v) }
        }
        _version.update { it + 1 }
    }

    // Every change goes through set()/reset(), which bump the version directly. (A SharedPreferences
    // change listener is only weakly referenced by Android; R8 dropped our strong reference in release
    // builds, so the listener was garbage-collected and settings changes stopped applying.)

    /** Values shown but not saved: a slider being dragged in Settings, seen on home underneath. */
    private val previews = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: ConfigKey<T>): T {
        val raw = previews[key.key] ?: prefs.getString(key.key, null) ?: return key.default
        return key.parse(raw) ?: key.default
    }

    /** Shows [raw] for [key] everywhere without saving it; null goes back to the saved value. */
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
        // The default again is no change: no ↺, no `*`, not in backups, and a later new default applies.
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

/** "22:40" or "10:40 PM" per [Keys.CLOCK_24H], for log lines written outside the UI (workers). */
fun Config.shortTime(millis: Long = System.currentTimeMillis()): String =
    java.text.SimpleDateFormat(if (this[Keys.CLOCK_24H]) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(java.util.Date(millis))
