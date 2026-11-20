package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.SearchWords
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.ApiDoc
import com.mtwabpin.kolahal.script.ApiDocs
import com.mtwabpin.kolahal.widgets.WidgetIcons
import com.mtwabpin.kolahal.widgets.WidgetKind

/**
 * What the editor's reference page ([ReferencePage]) lists in each tab, and what its search finds.
 * No Compose here, so unit tests can check it.
 */
internal object ReferenceSearch {
    /** The page's tabs, in their order. */
    enum class Tab(val label: String) {
        FUNCTIONS("Functions"),
        DIRECTIVES("Directives"),
        WIDGETS("Widgets"),
        LOGS("Log lines"),
        ICONS("Icons"),
    }

    /** The name of the group of entries outside any namespace (`log`, `print`, `args`). */
    const val GLOBALS = "Globals"

    /**
     * A group of the Functions tab: a namespace and its entries, or [GLOBALS].
     * [head] is the namespace's own entry, with its line; [GLOBALS] has none.
     */
    class Group(val name: String, val head: ApiDoc?, val entries: List<ApiDoc>)

    /** The API of [lang] in groups, in the order the API lists them: [GLOBALS] first, as `log` is first. */
    fun groups(lang: ScriptLang): List<Group> =
        ApiDocs.forLang(lang).filter { it.kind != ApiDoc.Kind.KEYWORD }
            .groupBy { if (it.kind == ApiDoc.Kind.NAMESPACE) it.name else it.qualifier?.substringBefore('.') ?: GLOBALS }
            .map { (name, docs) ->
                val head = docs.firstOrNull { it.kind == ApiDoc.Kind.NAMESPACE && it.name == name }
                Group(name, head, docs.filter { it !== head })
            }

    /** The guide's widget examples, each followed by its variants. */
    val allWidgets: List<ReferenceExamples.Widget> =
        ReferenceExamples.widgets.flatMap { listOf(it) + ReferenceExamples.variantsOf(it.name) }

    /** What a search finds, by tab. A widget can be a guide's example or one of its variants. */
    class Results(
        val functions: List<ApiDoc>,
        val directives: List<ApiDocs.Directive>,
        val widgets: List<ReferenceExamples.Widget>,
        val logs: List<ReferenceExamples.Log>,
        val icons: List<String>,
    ) {
        val count: Int get() = functions.size + directives.size + widgets.size + logs.size + icons.size
    }

    /**
     * Everything in the reference that has every word of [query] ([SearchWords]): an entry's name,
     * signature or line; a directive's name or line; an example's name, variant or code in [lang]; a
     * log example's lines; an icon's name. A blank query finds nothing.
     */
    fun find(query: String, lang: ScriptLang): Results {
        val words = SearchWords.of(query)
        if (words.isEmpty()) return Results(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        fun has(vararg texts: String?) = SearchWords.allIn(words, texts.filterNotNull())
        return Results(
            functions = ApiDocs.forLang(lang).filter { it.kind != ApiDoc.Kind.KEYWORD && has(it.name, it.signature, it.doc) },
            directives = ApiDocs.directives.filter { has("@${it.name}", it.args, it.doc) },
            widgets = allWidgets.filter { has(it.heading, it.variant, it.title, kindLine(it.kind), it.code(lang)) },
            logs = ReferenceExamples.logs.filter { ex ->
                has(ex.heading, ex.code(lang), *ex.lines.flatMap { listOfNotNull(it.text, it.body) }.map(StyledText::plain).toTypedArray())
            },
            icons = WidgetIcons.NAMES.filter { has(it) },
        )
    }

    /** The first line of [text] that has one of [words], trimmed; null when no line has one. */
    fun lineWith(text: String, words: List<String>): String? =
        text.lineSequence().firstOrNull { line -> words.any { it.isNotEmpty() && line.contains(it, ignoreCase = true) } }?.trim()

    /** What a kind of widget shows, in one line, as the guide's table of kinds says it. */
    fun kindLine(kind: WidgetKind): String = when (kind) {
        WidgetKind.STAT -> "One big value with its label, and smaller ones"
        WidgetKind.TEXT -> "A few lines of text, and a caption"
        WidgetKind.LIST -> "Rows, each with a title and more"
        WidgetKind.PROGRESS -> "A ring or a bar, or up to 4 bars"
        WidgetKind.CHART -> "Numbers over time, as a line or as bars"
        WidgetKind.COUNTDOWN -> "The time to a moment"
        WidgetKind.BUTTONS -> "1 to 8 buttons"
        WidgetKind.TOGGLES -> "1 to 6 switches, each on or off"
        WidgetKind.CLOCK -> "The time in up to 4 places"
        WidgetKind.AGENDA -> "Events by day"
        WidgetKind.IMAGE -> "A picture from the web, with a caption"
        WidgetKind.MEDIA -> "What is playing"
    }
}
