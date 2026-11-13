package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetSize

/**
 * What the editor points out when you save a script. It reads only the script's text and finds:
 * - a name the API doesn't have (`http.gte`),
 * - a call that needs a `@uses` line the script lacks,
 * - `@` lines Kolahal doesn't know or can't read (`@evrey 1h`, `@at 25:00`, `@on unlok`, `@widget graph`).
 * These are hints, not errors. The script is saved either way.
 */
object ScriptCheck {
    /** Something to check on [line] (1-based). */
    data class Issue(val line: Int, val text: String)

    /** Every `@` line the header may have ([ScriptMeta]). */
    internal val DIRECTIVES = setOf(
        "command", "desc", "usage", "on", "uses", "secret", "option",
        "title", "author", "version", "tags", "every", "at", "notify", "requires", "widget",
    )

    /** What is wrong with a `@widget <kind> [<size>]` line, whose text after `@widget` is [rest]. Null when it reads well. */
    private fun widgetIssue(rest: String): String? {
        val words = rest.trim().split(Regex("\\s+"))
        val kind = words[0]
        val size = words.getOrNull(1)
        return when {
            kind.isEmpty() -> "@widget needs a kind: ${WidgetKind.entries.joinToString(", ") { it.id }}"
            WidgetKind.named(kind) == null -> "@widget $kind isn't a kind: ${WidgetKind.entries.joinToString(", ") { it.id }}"
            size != null && WidgetSize.named(size) == null -> "@widget $kind $size isn't a size: ${WidgetSize.entries.joinToString(", ") { it.id }}"
            else -> null
        }
    }

    /** What each `@uses` name allows a script to call. */
    private val NEEDS = linkedMapOf(
        "notifications" to Regex("""(?<![.\w])notifications\s*\.\s*recent\b"""),
        "clipboard" to Regex("""(?<![.\w])clipboard\s*\.\s*\w+"""),
        "apps" to Regex("""(?<![.\w])apps\s*\.\s*list\b"""),
        "commands" to Regex("""(?<![.\w])launcher\s*\.\s*run\b"""),
        "dnd" to Regex("""(?<![.\w])device\s*\.\s*(dnd|setDnd)\b"""),
        "work" to Regex("""(?<![.\w])device\s*\.\s*(work|setWork)\b"""),
    )

    private val directive = Regex("""^\s*(--|//)\s*@(\w+)\b[ \t]*(.*)$""")

    fun check(code: String, lang: ScriptLang): List<Issue> {
        val lines = code.lines()
        val out = ArrayList<Issue>()
        val comment = if (lang == ScriptLang.LUA) "--" else "//"

        lines.forEachIndexed { i, line ->
            val m = directive.find(line) ?: return@forEachIndexed
            val (_, name, rest) = m.destructured
            when (name) {
                !in DIRECTIVES -> out += Issue(i + 1, "Unknown directive @$name" + (DIRECTIVES.firstOrNull { CommandParsing.editDistance(it, name) <= 2 }?.let { ": @$it?" } ?: ""))
                "every" if CommandParsing.parseInterval(rest.trim())?.takeIf { it >= 0 } == null ->
                    out += Issue(i + 1, "@every ${rest.trim()} isn't an interval: 15m, 1h, 1d or off")
                "at" -> rest.split(',', ' ').filter { it.isNotBlank() }.firstOrNull { ScriptScheduler.normalizeTime(it) == null }
                    ?.let { out += Issue(i + 1, "@at $it isn't a time: HH:mm, such as 07:30") }
                "on" -> ScriptMeta.unknownEvents(line).firstOrNull()
                    ?.let { out += Issue(i + 1, "@on $it isn't an event: ${ScriptMeta.EVENTS.joinToString(", ")}") }
                // Read the names the same way a run does: a name in capitals, or after a tab, counts.
                "uses" -> ScriptMeta.usesNames(rest).firstOrNull { it !in ScriptMeta.USES }
                    ?.let { out += Issue(i + 1, "@uses $it isn't valid: ${ScriptMeta.USES.joinToString(", ")}") }
                "widget" -> widgetIssue(rest)?.let { out += Issue(i + 1, it) }
            }
        }

        // The code with its comment lines emptied, because a note about `http.get` is not a call.
        val body = lines.joinToString("\n") { if (it.trimStart().startsWith(comment)) "" else it }
        fun lineOf(offset: Int) = body.substring(0, offset).count { it == '\n' } + 1
        val uses = ScriptMeta.uses(code)
        for ((use, call) in NEEDS) {
            if (use in uses) continue
            val hit = call.find(body) ?: continue
            out += Issue(lineOf(hit.range.first), "${hit.value.replace(Regex("\\s"), "")} needs a `$comment @uses $use` line")
        }

        val docs = ApiDocs.all.filter { lang in it.langs }
        val names = docs.mapTo(HashSet()) { it.name }
        val spaces = docs.filter { it.kind == ApiDoc.Kind.NAMESPACE }.map { it.name }
        for (ns in spaces) {
            // If the script defines its own table with that name, the script fills it, so skip it.
            if (Regex("""(?<![.\w])(local\s+|let\s+|const\s+|var\s+)?$ns\s*=[^=]""").containsMatchIn(body)) continue
            for (hit in Regex("""(?<![.\w])$ns\s*\.\s*([A-Za-z_]\w*)""").findAll(body)) {
                val member = hit.groupValues[1]
                if ("$ns.$member" !in names) out += Issue(lineOf(hit.range.first), "$ns.$member isn't in the API" + (
                    names.filter { it.startsWith("$ns.") }.minByOrNull { CommandParsing.editDistance(it, "$ns.$member") }
                        ?.takeIf { CommandParsing.editDistance(it, "$ns.$member") <= 2 }?.let { ": $it?" } ?: ""
                    ))
            }
        }
        return out.distinct().sortedBy { it.line }
    }
}
