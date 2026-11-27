/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.widgets.WidgetJson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The rules of `web.open(url)` ([HostApi.webOpen]), apart from Android, so a JVM test can check them.
 * - What it opens: an http or https address, as text, of at most [MAX_LENGTH] characters (as a
 *   tap's link). Anything else is an error that says what is wrong ([address]).
 * - When: only in a run you started, as `launcher.run`: from the prompt, `script run`, a one-liner,
 *   a tap on the script's widget, button or line, Run in the editor. Not in a timer, event or
 *   refresh run, nor in one another script started. A script can't put a page in front of you by itself.
 * - How often: at most [MAX_PER_RUN] links a run, so a loop can't open a tab for each turn ([Turns]).
 */
internal object WebOpen {
    /** The longest link it opens: a widget tap's ([WidgetJson.MAX_LINK]). */
    const val MAX_LENGTH = WidgetJson.MAX_LINK

    /** The most links one run may open. */
    const val MAX_PER_RUN = 3

    const val NOT_STARTED = "web.open didn't open the link\nIt works only when you start the script"
    const val BY_SCRIPT = "web.open didn't open the link\nIt doesn't work in a script another script started"
    const val SECRET = "web.open refused a link\nIt contains one of this script's secrets"
    const val TOO_MANY = "web.open: more than $MAX_PER_RUN links in one run\nThe rest don't open"

    private val scheme = Regex("""^([A-Za-z][A-Za-z0-9+.-]*):""")

    /**
     * [url] as it opens, or an error that names what is wrong with it. [shown] is how the link may
     * appear in the error: with the script's secrets masked.
     */
    fun address(url: String, shown: (String) -> String = { it }): String {
        require(url.isNotBlank()) { "web.open needs a link, such as https://example.org" }
        require(url.length <= MAX_LENGTH) { "web.open: the link is over $MAX_LENGTH characters" }
        require(url.none { it.isWhitespace() || it.isISOControl() }) {
            "web.open: a link can't have spaces\nWrite a space as %20"
        }
        val name = scheme.find(url)?.groupValues?.get(1)?.lowercase()
        require(name != null) { "web.open opens only web links\nStart the link with https://" }
        require(name == "http" || name == "https") {
            "web.open opens only web links\nUse http or https, not ${shown(name).take(40)}:"
        }
        require(url.toHttpUrlOrNull() != null) { "web.open: not a web address: ${shown(url).take(80)}" }
        return url
    }

    /** What happens to a run's next link. */
    sealed interface Turn {
        data object Open : Turn

        /** It doesn't open, and the call returns false. [line] says why, logged once a run for each reason; null after. */
        data class Refused(val line: String?) : Turn
    }

    /**
     * One run's links. [started]: you started the run ([HostApi]'s `interactive`); [byScript]:
     * another script's `launcher.run` started it.
     */
    class Turns(private val started: Boolean, private val byScript: Boolean) {
        private var opened = 0
        private val said = HashSet<String>()

        /** The next link's turn. [holdsSecret]: the link contains one of the script's secrets, which never leave the run. */
        fun next(holdsSecret: Boolean): Turn {
            val why = when {
                !started -> NOT_STARTED
                byScript -> BY_SCRIPT
                holdsSecret -> SECRET
                opened >= MAX_PER_RUN -> TOO_MANY
                else -> {
                    opened++
                    return Turn.Open
                }
            }
            // A secret is refused out loud each time, as launcher.run refuses one.
            return Turn.Refused(why.takeIf { said.add(it) || it == SECRET })
        }
    }
}
