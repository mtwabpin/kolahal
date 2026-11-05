package com.mtwabpin.kolahal

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository.Pending
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.reflect.KMutableProperty0

/**
 * [path] in the app's sources (`src/main/…`), from where the tests run: `app/` under Gradle,
 * the repository's root in some IDEs.
 */
fun sourceFile(path: String): File =
    listOf(File(path), File("app", path)).firstOrNull { it.exists() } ?: error("$path isn't here or under app/")

/** A line in the log, what a test doesn't set left plain. */
fun logEntry(
    kind: LogKind,
    text: String = "x",
    source: String? = null,
    ctx: LogContext = LogContext.EMPTY,
    id: Long = 1,
    ts: Long = 0,
) = LogEntry(id, ts, kind, source, text, ctx)

/** A line on its way to the log, as a command's output is before its pipes. */
fun pending(kind: LogKind, text: String = "x", ctx: LogContext = LogContext.EMPTY, ts: Long = 0) =
    Pending(kind, text, null, ctx, ts)

/** Runs [block] with [property] set to [value], and puts back what it was however [block] ends. */
inline fun <V, T> withValue(property: KMutableProperty0<V>, value: V, block: () -> T): T {
    val before = property.get()
    property.set(value)
    try {
        return block()
    } finally {
        property.set(before)
    }
}

/** Runs [block] in [locale], the JVM's own put back after it. */
inline fun <T> withDefaultLocale(locale: Locale, block: () -> T): T {
    val before = Locale.getDefault()
    Locale.setDefault(locale)
    try {
        return block()
    } finally {
        Locale.setDefault(before)
    }
}

/** Runs [block] in the time zone [zone] (`"Asia/Tokyo"`), the JVM's own put back after it. */
inline fun <T> withDefaultZone(zone: String, block: () -> T): T {
    val before = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone(zone))
    try {
        return block()
    } finally {
        TimeZone.setDefault(before)
    }
}
