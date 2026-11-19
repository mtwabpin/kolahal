package com.mtwabpin.kolahal.apps

import android.content.Context
import com.mtwabpin.kolahal.data.StyledText

/**
 * An app's name for a package, the same everywhere: the log, notifications, Now playing, usage.
 * - [label] (home's process): the app list's name, so a name you set with `rename` counts; the
 *   app in profile `serial` first, then in any profile; else Android's own name.
 * - [android] (any process, also `:script`): Android's own name.
 * Both remove styling: an app's name must not style a log line.
 */
object AppNames {
    /**
     * Android's own name for [pkg], without styling. Null when Android gives none: the app is not
     * installed, Android hides it from Kolahal, or it has no name of its own (only its package).
     */
    fun android(context: Context, pkg: String): String? {
        val pm = context.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
            ?.let(StyledText::strip)?.takeIf { it.isNotBlank() && it != pkg }
    }

    /** The name Kolahal shows for [pkg]: the app list's, in profile [serial] first, else in any profile, else [android]. */
    fun label(context: Context, pkg: String, serial: Long? = null): String? {
        val apps = AppRepository.get(context)
        if (serial != null) apps.find(pkg, null, serial)?.let { return it.label }
        return apps.find(pkg, null, null)?.label ?: android(context, pkg)
    }
}
