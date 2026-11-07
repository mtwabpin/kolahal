package com.mtwabpin.kolahal.notifications

import androidx.compose.runtime.Immutable
import com.mtwabpin.kolahal.data.takeWhole

/** A notification seen by [KolahalNotificationListener], reduced to what the terminal shows. */
@Immutable
data class Notif(
    /** StatusBarNotification key. It stays the same when the notification is updated. */
    val key: String,
    val pkg: String,
    /** Profile serial (UserManager.getSerialNumberForUser) of the posting app. */
    val userSerial: Long,
    /** App label, or the package name when Android has no label for it. */
    val app: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    /** True when the user dismissed it or the app cancelled it. It stays in the feed until it ages out. */
    val removed: Boolean = false,
    /** The [NotifIcons] key of its status-bar icon, or null. The icon is saved in the background. */
    val icon: String? = null,
) {
    val line: String get() = NotifFormat.line(title, text)
}

/** Pure text helpers, kept free of Android types so they can be unit tested on the JVM. */
object NotifFormat {
    const val MAX_LINE = 200
    private val WHITESPACE = Regex("\\s+")
    private val SEPARATORS = charArrayOf(' ', ':', '-', '—')

    /** Returns "title — text" on one line, cut to [max] chars. A blank part is left out. */
    fun line(title: String?, text: String?, max: Int = MAX_LINE): String {
        val t = title?.collapse().orEmpty()
        var b = text?.collapse().orEmpty()
        // Some apps repeat the title as the first words of the text.
        if (t.isNotEmpty() && b.length > t.length && b.startsWith(t) && b[t.length] in SEPARATORS) {
            b = b.substring(t.length).trimStart(*SEPARATORS)
        }
        if (b == t) b = ""
        val joined = when {
            t.isEmpty() -> b
            b.isEmpty() -> t
            else -> "$t — $b"
        }
        return truncate(joined, max)
    }

    fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.takeWhole((max - 1).coerceAtLeast(0)).trimEnd() + "…"

    private fun String.collapse() = replace(WHITESPACE, " ").trim()
}
