/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.commands.BuildFeature
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.data.shortTime
import com.mtwabpin.kolahal.extras.ExtrasContract
import com.mtwabpin.kolahal.extras.ExtrasLink
import com.mtwabpin.kolahal.productivity.TimeSpec
import java.util.concurrent.Executors

/**
 * Shows calls in the log. The call-log permission is the on/off switch: there is no other setting.
 * - Each call gets one line when Android adds it to the call log: `Call from Sarah ↙`,
 *   `Call to Sarah ↗`, `Missed call from Sarah ✕`. Dimmed lines below show the number, then the
 *   time and length.
 * - One line for a call, however many rows the phone writes for it: a later row for a call that
 *   has its line changes that line ([CallLines]).
 * - A tap on the line dials back.
 * - The newest call seen is saved. So calls made while the launcher wasn't running show up on the
 *   next start, and calls from before the permission was granted are never logged.
 * - The Play build has no call log of its own: it reads the calls from Kolahal Extras ([ExtrasLink]), which says
 *   when they change while it runs. Home coming to the front ([check]) catches up on what it missed.
 */
class CallEvents private constructor(private val context: Context) {
    private val store = KvStore(context, Stores.CALLS)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var observing = false
    private val scanSoon = Runnable { io.execute { runCatching { scan() } } }

    /** The dialer writes a call in a few steps, so we scan once, after the changes stop. */
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = check(delayMs = SETTLE_MS)
    }

    data class Call(
        val id: Long,
        val number: String,
        val name: String?,
        val type: Int,
        val date: Long,
        val durationSec: Long,
        val presentation: Int,
        val lookupUri: String?,
        /** The contact's photo, as the call log keeps it (CACHED_PHOTO_URI); null without one. */
        val photoUri: String? = null,
    )

    init {
        // The Play build: Kolahal Extras installed, allowed or removed.
        if (!BuildConfig.CALL_LOG) ExtrasLink.get(context).listen { old, new ->
            if (old.status(ExtrasContract.CALLS) != new.status(ExtrasContract.CALLS)) check()
        }
    }

    /** Starts watching if the permission is granted. Call it again after the permission is granted. */
    fun start() {
        if (!hasPermission(context)) return
        if (!observing) {
            val uri = if (BuildConfig.CALL_LOG) CallLog.Calls.CONTENT_URI else ExtrasLink.get(context).callsUri
            observing = runCatching { context.contentResolver.registerContentObserver(uri, true, observer) }.isSuccess
            if (!observing) return
        }
        check()
    }

    /**
     * Catches up on missed calls, for example when home comes back on screen. The observer may miss a
     * call while the app is frozen. If the user granted the permission in Android's settings in the
     * meantime, this starts watching.
     */
    fun check(delayMs: Long = 0) {
        if (!observing) return start()
        main.removeCallbacks(scanSoon)
        main.postDelayed(scanSoon, delayMs)
    }

    @Synchronized
    private fun scan() {
        if (!hasPermission(context)) return
        // A database from another phone has its last call id: clear it first, or new calls would wait to pass it.
        com.mtwabpin.kolahal.data.ThisInstall.check(context)
        val last = store.get(LAST)?.toLongOrNull()
        if (last == null) {
            // First run: start from the newest call and don't log older calls.
            store.set(LAST, (recent(context, 1).firstOrNull()?.id ?: 0L).toString())
            return
        }
        val calls = recent(context, MAX_LINES, afterId = last)
        if (calls.isEmpty()) return
        val config = Config.get(context)
        val now = System.currentTimeMillis()
        val logs = LogRepository.get(context)
        // The calls that have a line, and what these rows are: new calls, or calls written again.
        val lines = CallLines.decode(store.get(LINES))
        val plan = CallLines.plan(calls, lines.keys)
        // A call the phone wrote again keeps its one line, with what the newest row says. A line the
        // user deleted stays deleted.
        plan.again.forEach { c -> lines[CallLines.key(c)]?.let { logs.update(it, line(c, config), ctx(c)) } }
        val ids = logs.addAll(plan.fresh.map { c ->
            LogRepository.Pending(LogKind.INFO, line(c, config), SOURCE, ctx(c), now)
        })
        ids?.let { plan.fresh.zip(it).forEach { (c, id) -> lines[CallLines.key(c)] = id } }
        store.set(LINES, CallLines.encode(lines))
        // The highest id read, not the first row's: a phone that hands the rows over in another
        // order would otherwise leave a row above the mark, to be read again at every change.
        store.set(LAST, plan.last.toString())
    }

    companion object : Singleton<CallEvents>(::CallEvents) {
        const val SOURCE = "calls"
        private const val LAST = "last"

        /** The newest calls that have a line, each with its line's id ([CallLines.encode]). */
        private const val LINES = "lines"
        private const val SETTLE_MS = 1_500L

        /** After a long time away, only this many of the newest calls get a line. */
        private const val MAX_LINES = 30

        /** Whether the calls can be read: READ_CALL_LOG, or in the Play build Kolahal Extras' (as last read). */
        fun hasPermission(context: Context) =
            if (BuildConfig.CALL_LOG) context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
            else ExtrasLink.get(context).ready(BuildFeature.CALL_LOG)

        /**
         * Returns at most [limit] calls, newest first. When [afterId] is given, only calls with a higher id. Off the main
         * thread: in the Play build it asks Kolahal Extras, which reads the same columns and adds the photos.
         */
        fun recent(context: Context, limit: Int, afterId: Long? = null): List<Call> {
            if (!hasPermission(context)) return emptyList()
            val rows: () -> android.database.Cursor? = if (BuildConfig.CALL_LOG) {
                {
                    val uri = CallLog.Calls.CONTENT_URI.buildUpon()
                        .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString()).build()
                    val selection = afterId?.let { "${CallLog.Calls._ID} > ?" }
                    val args = afterId?.let { arrayOf(it.toString()) }
                    context.contentResolver.query(uri, ExtrasContract.CALL_COLUMNS, selection, args, "${CallLog.Calls._ID} DESC")
                }
            } else {
                {
                    val extras = ExtrasLink.get(context)
                    val uri = extras.callsUri.buildUpon()
                        .appendQueryParameter(ExtrasContract.PARAM_LIMIT, limit.toString())
                        .apply { afterId?.let { appendQueryParameter(ExtrasContract.PARAM_AFTER, it.toString()) } }.build()
                    extras.query(uri)
                }
            }
            return runCatching {
                rows()?.use { c ->
                    buildList {
                        while (c.moveToNext() && size < limit) {
                            add(
                                Call(
                                    id = c.getLong(0),
                                    number = c.getString(1).orEmpty(),
                                    name = c.getString(2)?.takeIf { it.isNotBlank() },
                                    type = c.getInt(3),
                                    date = c.getLong(4),
                                    durationSec = c.getLong(5),
                                    presentation = c.getInt(6),
                                    lookupUri = c.getString(7),
                                    photoUri = c.getString(8)?.takeIf { it.isNotBlank() },
                                )
                            )
                        }
                    }
                }
            }.getOrNull().orEmpty().map { call ->
                // Kolahal Extras has done this already.
                if (!BuildConfig.CALL_LOG) return@map call
                // The phone app fills the call log's copy of the contact (name, photo) when it gets to it,
                // and some never copy the photo. A call read right after it ended may have none yet: then
                // the photo comes from the contact itself.
                if (call.photoUri != null || call.presentation != CallLog.Calls.PRESENTATION_ALLOWED) call
                else call.copy(photoUri = contactPhoto(context, call.number))
            }
        }

        /** The photo of the contact with this [number], from the contacts (needs READ_CONTACTS); null without one. */
        private fun contactPhoto(context: Context, number: String): String? {
            if (number.isBlank()) return null
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            val projection = arrayOf(
                android.provider.ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                android.provider.ContactsContract.PhoneLookup.PHOTO_URI
            )
            return runCatching {
                context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    if (c.moveToFirst()) (c.getString(0)
                        ?: c.getString(1))?.takeIf { it.isNotBlank() } else null
                }
            }.getOrNull()
        }

        /**
         * Builds the text for a call: `Call from Sarah ↙`, then detail lines with the number and
         * `10:42 · 4m 12s`. [withDay] adds `Tue 20 Oct, ` before the time.
         */
        fun line(c: Call, config: Config, withDay: Boolean = false): String {
            val who = c.name ?: when (c.presentation) {
                CallLog.Calls.PRESENTATION_RESTRICTED -> "a private number"
                CallLog.Calls.PRESENTATION_PAYPHONE -> "a payphone"
                CallLog.Calls.PRESENTATION_UNKNOWN -> "an unknown number"
                else -> c.number.ifBlank { "an unknown number" }
            }
            val title = when (c.type) {
                CallLog.Calls.OUTGOING_TYPE -> "Call to $who ↗"
                CallLog.Calls.MISSED_TYPE -> "Missed call from $who ✕"
                CallLog.Calls.REJECTED_TYPE -> "Declined call from $who ✕"
                CallLog.Calls.BLOCKED_TYPE -> "Blocked call from $who ⊘"
                CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail from $who ↙"
                else -> "Call from $who ↙"
            }
            val length = when {
                c.durationSec > 0 -> TimeSpec.formatDuration(c.durationSec)
                c.type == CallLog.Calls.OUTGOING_TYPE -> "not answered"
                else -> null
            }
            val number = c.number.takeIf { c.name != null && it.isNotBlank() }
            val time = (if (withDay) java.text.SimpleDateFormat(
                "${TimeFormats.DAY}, ",
                java.util.Locale.getDefault()
            ).format(java.util.Date(c.date)) else "") + config.shortTime(c.date)
            return listOfNotNull(
                title,
                number,
                listOfNotNull(time, length).joinToString(" · ")
            ).joinToString("\n")
        }

        /**
         * A tap dials back. A long-press offers call, message, copy and the contact. Every call line
         * has its person ([LogContext.PERSON]): the name, the number, or "?" for a hidden one, and
         * their photo when the contact has one.
         */
        fun ctx(c: Call): LogContext {
            val shown =
                c.presentation == CallLog.Calls.PRESENTATION_ALLOWED && c.number.isNotBlank()
            val person =
                LogContext.of(LogContext.PERSON to (c.name ?: c.number.takeIf { shown } ?: "?"),
                    LogContext.PHOTO to c.photoUri)
            // An item each: a call is two or three lines beside its picture, and the gap keeps the
            // pictures apart in a list of calls.
            return if (!shown) person + LogContext.ITEM
            else person + LogContext.of(
                LogContext.PHONE to c.number,
                LogContext.CONTACT to c.lookupUri,
                LogContext.COPY to c.number
            ).values + LogContext.ITEM
        }
    }
}
