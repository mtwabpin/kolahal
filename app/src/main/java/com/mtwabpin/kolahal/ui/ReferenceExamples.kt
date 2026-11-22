/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSpec
import java.time.ZoneId


/**
 * The widget and log examples of the scripting guide (site/src/content/docs/scripting/api.mdx), for
 * the script editor's API reference: each one's code in Lua and in JavaScript, and what it draws.
 * - The code is the guide's, as it is there. ReferenceExamplesTest checks that the guide has it.
 * - Each widget example's `widget.show` names its kind. The reference shows no `@widget` line, so
 *   the code draws the same card under any script's line.
 * - [Widget.spec] is the card and [Log.lines] the lines, made here: the reference runs no script to
 *   draw them. Device tests (ReferenceCardsTest, ReferenceLinesTest) run both codes and check that
 *   they make this card and these lines.
 * - The website's pictures come from the Lua code (DocsExamples, WidgetGalleryTest, LogGalleryTest).
 * - The picture example and the picture line aren't here: drawing them would fetch the picture.
 */
internal object ReferenceExamples {
    class Widget(
        /** Its name in the website's pictures: example-<name>-<size>.png. */
        val name: String,
        val kind: WidgetKind,
        val size: WidgetSize,
        /** The script's @title: the card's title when the code gives none. */
        val title: String,
        val lua: String,
        val js: String,
        /**
         * For a variant ([variants]): the name of the guide's example it varies, and what it shows
         * differently ("4 switches"), for its chip in the reference. Null for the guide's examples.
         */
        val of: String? = null,
        val variant: String? = null,
        /** The card that the code makes at `now` (seconds) in `zone`. */
        val spec: (now: Long, zone: ZoneId) -> WidgetSpec,
    ) {
        /** What the reference calls it: the kind, as `kind` names it in the code. */
        val heading: String get() = kind.id

        /**
         * The line under its name in the reference: what the kind shows, or for the two examples that
         * show something more, what that is.
         */
        val line: String
            get() = when (name) {
                "styles" -> "Rows with styles: bold, a color, struck-through text"
                "water" -> "A tap that adds a glass, kept in the store"
                else -> ReferenceSearch.kindLine(kind)
            }

        fun code(lang: ScriptLang): String = (if (lang == ScriptLang.LUA) lua else js).trimIndent()
    }

    val widgets: List<Widget> = ReferenceExamplesWidgets.widgets

    /**
     * More versions of some of the guide's examples, for the reference only: another number of rows
     * or switches, another style. Each is real code in both languages, and ReferenceCardsTest runs it
     * as it runs the guide's. [Widget.of] names the example it varies; every name is unique among all
     * the examples. The guide doesn't have these, so ReferenceExamplesTest doesn't look for them there.
     */
    val variants: List<Widget> = ReferenceExamplesVariants.variants

    /** The variants of the guide's example [name] ([variants]), in their order. */
    fun variantsOf(name: String): List<Widget> = variants.filter { it.of == name }

    /**
     * One line a log example makes: its title and body, as `log` stores them, and what its context
     * holds (`url`, `gap`, `copy`, `actions`).
     */
    data class Line(
        val text: String,
        val body: String? = null,
        val url: String? = null,
        val gap: Boolean = false,
        val copy: String? = null,
        /** The buttons, as `log` keeps them: `[[label, args], …]` as JSON. */
        val actions: String? = null,
    ) {
        /** Its context, as a line's has it. */
        val ctx: Map<String, String>
            get() = listOfNotNull(
                LogContext.TITLE to "1",
                body?.let { LogContext.BODY to it },
                url?.let { LogContext.URL to it },
                (LogContext.GAP to "1").takeIf { gap },
                copy?.let { LogContext.COPY to it },
                actions?.let { LogContext.ACTIONS to it },
            ).toMap()
    }

    class Log(
        val name: String,
        /** What the reference calls it. */
        val heading: String,
        val lua: String,
        val js: String,
        val lines: List<Line>,
    ) {
        fun code(lang: ScriptLang): String = (if (lang == ScriptLang.LUA) lua else js).trimIndent()

        /** The lines as log entries of a script called "example", for the log's own line to draw. */
        fun entries(now: Long): List<LogEntry> = lines.mapIndexed { i, l ->
            LogEntry(-1L - i, now, LogKind.SCRIPT, "example", l.text, LogContext(l.ctx))
        }
    }

    val logs: List<Log> = ReferenceExamplesLogs.logs

    /**
     * A sample for each `fmt` function: one short `log` call, and the line it makes. The reference
     * draws it under the function's entry ([com.mtwabpin.kolahal.script.ApiDoc] of the same name), and the website shows it in
     * the guide's table of `fmt` functions. `fmt.plain` has none: its text has no styles to show.
     */
    val fmt: List<Log> = ReferenceExamplesLogs.fmt

    private val fmtByName = fmt.associateBy { it.name }

    /** The sample of the `fmt` function [name] ("fmt.bold"), or null when it has none. */
    fun fmtSample(name: String): Log? = fmtByName[name]
}
