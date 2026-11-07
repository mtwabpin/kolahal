package com.mtwabpin.kolahal.data

import android.content.Context

/**
 * The one [T] in the process, made from the application context the first time it is requested.
 * Use `companion object : Singleton<Foo>(::Foo)`, then `Foo.get(context)` anywhere. Only the
 * application context is kept, so an activity passed in is not held.
 */
open class Singleton<T : Any>(private val create: (Context) -> T) {
    @Volatile private var instance: T? = null

    fun get(context: Context): T = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }
}
