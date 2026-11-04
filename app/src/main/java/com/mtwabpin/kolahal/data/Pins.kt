package com.mtwabpin.kolahal.data

import android.content.Context
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.Json

/**
 * A script's item in the pinned header, the line at the top of home while the header is off
 * (`pin(text, opts)`): its text, how far along it is when the script gives that ([progress], 0 to
 * 1), how often it asks to run again while it shows ([refresh], seconds), and when it was first
 * pinned ([since]), which keeps the items in their order as their texts change.
 */
data class Pin(val name: String, val text: String, val progress: Float?, val refresh: Int?, val since: Long)

/** The pinned header's items, kept by script name like `status(text)` lines. */
object Pins {
    const val SCOPE = "pin"

    /** The fastest a pinned item runs again, and the slowest worth asking for. */
    const val MIN_REFRESH = 10
    const val MAX_REFRESH = 3600

    fun encode(pin: Pin): String = Json.stringify(
        buildMap {
            put("text", pin.text)
            pin.progress?.let { put("progress", it.toDouble()) }
            pin.refresh?.let { put("refresh", it) }
            put("since", pin.since)
        },
    )

    /** Null for a value that isn't a pin: one kept by a newer or broken write is left out, not shown wrong. */
    fun decode(name: String, raw: String): Pin? {
        val map = runCatching { Json.parse(raw) as? Map<*, *> }.getOrNull() ?: return null
        val text = map["text"] as? String ?: return null
        return Pin(
            name, text,
            (map["progress"] as? Number)?.toFloat()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f),
            (map["refresh"] as? Number)?.toInt()?.takeIf { it > 0 }?.coerceIn(MIN_REFRESH, MAX_REFRESH),
            (map["since"] as? Number)?.toLong() ?: 0L,
        )
    }

    /** Every stored item, first pinned first. */
    fun all(context: Context): List<Pin> = order(KvStore(context, SCOPE).all().mapNotNull { (name, raw) -> decode(name, raw) })

    /** First pinned first; two pinned in the same millisecond by name. */
    fun order(pins: List<Pin>): List<Pin> = pins.sortedWith(compareBy<Pin>({ it.since }, { it.name }))

    /**
     * Takes away what a script left at the top of home: its `status` line and its pinned item, when
     * it's deleted, switched off or on, changed, or loses its header access.
     */
    fun clearLines(context: Context, name: String) {
        KvStore(context, HostApi.STATUS_SCOPE).set(name, null)
        KvStore(context, SCOPE).set(name, null)
    }
}
