package com.mtwabpin.kolahal.data

import android.content.Context
import com.mtwabpin.kolahal.BuildConfig

/**
 * The one [T] in the process, made from the application context the first time it is requested.
 * Use `companion object : Singleton<Foo>(::Foo)`, then `Foo.get(context)` anywhere. Only the
 * application context is kept, so an activity passed in is not held.
 *
 * [mainOnly]: [T] caches a prefs file that only the main process writes. The :script process would
 * read it once and never see a change, and its own writes would be lost. In a debug build, asking
 * for it there throws, so a test finds the mistake. Settings that :script needs go through
 * [SharedSettings] or a [KvStore].
 */
open class Singleton<T : Any>(private val create: (Context) -> T, private val mainOnly: Boolean = false) {
    @Volatile private var instance: T? = null

    fun get(context: Context): T = instance ?: synchronized(this) {
        instance ?: run {
            if (mainOnly && BuildConfig.DEBUG && inScriptProcess) {
                error("${this::class.java.name} is for the main process only: the :script process can't see its file change")
            }
            create(context.applicationContext).also { instance = it }
        }
    }

    companion object {
        /** True in the :script process, where scripts run; false in the main one. */
        val inScriptProcess: Boolean by lazy {
            runCatching { android.app.Application.getProcessName()?.endsWith(":script") == true }.getOrDefault(false)
        }
    }
}
