package com.mtwabpin.kolahal.data

import android.content.Context
import androidx.core.content.edit
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptMeta

/**
 * A script's status line (`status(text, opts)`). It holds:
 * - the text,
 * - how far along the script is, if it gives that ([progress], 0 to 1),
 * - how often it asks to run again while it shows at the top of home ([refresh], seconds),
 * - its bar's colour, by role ([colour]; null: the theme's accent).
 * It shows at the top of home only after you pin the script.
 */
data class StatusLine(val name: String, val text: String, val progress: Float? = null, val refresh: Int? = null, val colour: StyledText.Colour? = null)

/**
 * Scripts' status lines, stored by script name. The text is stored as it always was
 * ([HostApi.STATUS_SCOPE], which backups and older versions read). What `opts` adds is stored
 * next to it ([EXTRAS_SCOPE]).
 */
object StatusLines {
    const val EXTRAS_SCOPE = "status.extra"

    /** The fastest a status line can run again, and the slowest refresh worth asking for. */
    const val MIN_REFRESH = 10
    const val MAX_REFRESH = 3600

    /** What `opts` adds, as stored. Null when there is none of it. */
    fun encodeExtras(progress: Float?, refresh: Int?, colour: StyledText.Colour? = null): String? =
        if (progress == null && refresh == null && colour == null) null
        else Json.stringify(buildMap {
            progress?.let { put("progress", it.toDouble()) }
            refresh?.let { put("refresh", it) }
            colour?.let { put("color", it.role) }
        })

    /** What `opts` added, read back. */
    data class Extras(val progress: Float? = null, val refresh: Int? = null, val colour: StyledText.Colour? = null)

    /**
     * Reads [raw] back. A value that isn't a valid progress, refresh or colour role is left out,
     * so it isn't shown wrong.
     */
    fun decodeExtras(raw: String?): Extras {
        val map = raw?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() } ?: return Extras()
        return Extras(
            progress = (map["progress"] as? Number)?.toFloat()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f),
            refresh = (map["refresh"] as? Number)?.toInt()?.takeIf { it > 0 }?.coerceIn(MIN_REFRESH, MAX_REFRESH),
            colour = (map["color"] as? String)?.let(StyledText.Colour::named),
        )
    }

    /** Every stored line with what it adds, by name. */
    fun all(context: Context): List<StatusLine> {
        val extras = KvStore(context, EXTRAS_SCOPE).all()
        return KvStore(context, HostApi.STATUS_SCOPE).all().map { (name, text) ->
            val (progress, refresh, colour) = decodeExtras(extras[name])
            StatusLine(name, text, progress, refresh, colour)
        }.sortedBy { it.name }
    }

    /**
     * Removes a script's line and what it adds. Called when the script is deleted, switched off or
     * on, or changed from outside.
     */
    fun clear(context: Context, name: String) {
        KvStore(context, HostApi.STATUS_SCOPE).set(name, null)
        KvStore(context, EXTRAS_SCOPE).set(name, null)
    }

    /** Saved after [pinShownOnce] has run on this phone. */
    private const val PINS_CARRIED = "pins_carried"

    /**
     * Runs once, on the first start of a version where you pin scripts (0.4.14).
     * - Scripts whose line the header showed before are pinned, so no line disappears without notice.
     * - A script whose line was switched off there stays unpinned.
     * - The dev builds' `pin()` items are removed too.
     */
    fun pinShownOnce(context: Context) {
        val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        if (prefs.getBoolean(PINS_CARRIED, false)) return
        val scripts = ScriptRepository.get(context)
        val lines = KvStore(context, HostApi.STATUS_SCOPE).all().keys
        for (s in scripts.all()) {
            if (s.name in lines && ScriptAccess.HEADER !in s.access && ScriptMeta.setsStatus(s.code)) scripts.setAccess(s.id, ScriptAccess.HEADER, true)
        }
        KvStore(context, "pin").let { old -> old.all().keys.forEach { old.set(it, null) } }
        prefs.edit { putBoolean(PINS_CARRIED, true) }
        LogRepository.get(context).onStatusChanged()
    }
}
