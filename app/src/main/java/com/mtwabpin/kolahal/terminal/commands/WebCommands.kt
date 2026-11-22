/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext

/** The commands that open a web address or a search: `url` / `web`, `search` and its engines, `maps` and `play`. */
class WebCommands(context: Context) : CommandGroup(context) {
    private val conf = Config.get(context)

    fun openUrl(raw: String) {
        if (raw.isBlank()) return usage("url")
        val url = if (raw.contains("://")) raw else "https://$raw"
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        info("Opened $url", LogContext.of(LogContext.URL to url))
    }

    fun search(query: String, engine: String? = null) {
        if (query.isBlank()) return usage("search")
        val template = when (engine ?: conf[Keys.SEARCH_ENGINE]) {
            "google" -> "https://www.google.com/search?q=%s"
            "bing" -> "https://www.bing.com/search?q=%s"
            "brave" -> "https://search.brave.com/search?q=%s"
            "startpage" -> "https://www.startpage.com/do/search?q=%s"
            "kagi" -> "https://kagi.com/search?q=%s"
            "youtube" -> "https://www.youtube.com/results?search_query=%s"
            "wikipedia" -> "https://en.wikipedia.org/w/index.php?search=%s"
            "custom" -> conf[Keys.SEARCH_URL].takeIf { it.contains("%s") }
                ?: "https://duckduckgo.com/?q=%s"

            else -> "https://duckduckgo.com/?q=%s"
        }
        val url = template.replace("%s", Uri.encode(query))
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        info("Search: $query", LogContext.of(LogContext.URL to url))
    }

    /** Opens [uri] in the app that handles it (maps, store, ...). */
    fun openUri(uri: String, what: String) {
        startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        info(what, LogContext.of(LogContext.URL to uri))
    }
}
