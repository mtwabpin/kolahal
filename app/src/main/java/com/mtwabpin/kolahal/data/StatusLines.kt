package com.mtwabpin.kolahal.data

import android.content.Context
import androidx.core.content.edit
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptMeta

/**
 * A script's status line (`status(text, opts)`): its text, how far along it is when the script
 * gives that ([progress], 0 to 1), and how often it asks to run again while it shows at the top of
 * home ([refresh], seconds). It shows there only once you pin the script.
 */
data class StatusLine(val name: String, val text: String, val progress: Float? = null, val refresh: Int? = null)

/**
 * Scripts' status lines, kept by script name: the text as it always was ([HostApi.STATUS_SCOPE],
 * which backups and older versions read), and beside it what `opts` adds ([EXTRAS_SCOPE]).
 */
object StatusLines {
    const val EXTRAS_SCOPE = "status.extra"

    /** The fastest a status line runs again, and the slowest worth asking for. */
    const val MIN_REFRESH = 10
    const val MAX_REFRESH = 3600

    /** What `opts` adds, as stored; null with neither. */
    fun encodeExtras(progress: Float?, refresh: Int?): String? =
        if (progress == null && refresh == null) null
        else Json.stringify(buildMap {
            progress?.let { put("progress", it.toDouble()) }
            refresh?.let { put("refresh", it) }
        })

    /** [raw] read back; what isn't a progress or a refresh is left out, not shown wrong. */
    fun decodeExtras(raw: String?): Pair<Float?, Int?> {
        val map = raw?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() } ?: return null to null
        return (map["progress"] as? Number)?.toFloat()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) to
            (map["refresh"] as? Number)?.toInt()?.takeIf { it > 0 }?.coerceIn(MIN_REFRESH, MAX_REFRESH)
    }

    /** Every stored line with what it adds, by name. */
    fun all(context: Context): List<StatusLine> {
        val extras = KvStore(context, EXTRAS_SCOPE).all()
        return KvStore(context, HostApi.STATUS_SCOPE).all().map { (name, text) ->
            val (progress, refresh) = decodeExtras(extras[name])
            StatusLine(name, text, progress, refresh)
        }.sortedBy { it.name }
    }

    /**
     * Takes away a script's line and what it adds, when it's deleted, switched off or on, or changed
     * from outside.
     */
    fun clear(context: Context, name: String) {
        KvStore(context, HostApi.STATUS_SCOPE).set(name, null)
        KvStore(context, EXTRAS_SCOPE).set(name, null)
    }

    /** Saved once [pinShownOnce] has run on this phone. */
    private const val PINS_CARRIED = "pins_carried"

    /**
     * Once, on the first start of a version where you pin scripts (0.4.14): the ones whose line the
     * header showed before are pinned, so none goes without a word; one whose header line was switched
     * off stays unpinned. The dev builds' `pin()` items go too.
     */
    fun pinShownOnce(context: Context) {
        val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        if (prefs.getBoolean(PINS_CARRIED, false)) return
        val scripts = ScriptRepository.get(context)
        val lines = KvStore(context, HostApi.STATUS_SCOPE).all().keys
        for (s in scripts.all()) {
            if (s.name in lines && ScriptAccess.HEADER !in s.access && ScriptMeta.setsHeader(s.code)) scripts.setAccess(s.id, ScriptAccess.HEADER, true)
        }
        KvStore(context, "pin").let { old -> old.all().keys.forEach { old.set(it, null) } }
        prefs.edit { putBoolean(PINS_CARRIED, true) }
        LogRepository.get(context).onStatusChanged()
    }
}
